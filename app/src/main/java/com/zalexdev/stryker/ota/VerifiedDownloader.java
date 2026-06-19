package com.zalexdev.stryker.ota;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class VerifiedDownloader {

    public interface Progress {
        void onProgress(long downloaded, long total);
    }

    public static final class Result {
        public final boolean ok;
        public final String error;
        public final String sha256;

        private Result(boolean ok, String error, String sha256) {
            this.ok = ok;
            this.error = error;
            this.sha256 = sha256;
        }

        static Result ok(String sha256) {
            return new Result(true, null, sha256);
        }

        static Result fail(String error) {
            return new Result(false, error, null);
        }
    }

    private static final int MAX_RETRIES = 3;
    private static final int BUFFER = 256 * 1024;
    private static final long SPACE_MARGIN = 8L * 1024 * 1024;

    private VerifiedDownloader() {
    }

    public static Result download(String url, File dest, String expectedSha256,
                                  long expectedSize, Progress progress) {
        if (url == null || !url.startsWith("https://")) {
            return Result.fail("Refusing non-HTTPS URL");
        }
        File part = new File(dest.getAbsolutePath() + ".part");
        String lastError = "Download failed";
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                Result result = attempt(url, dest, part, expectedSha256, expectedSize, progress);
                if (result.ok) {
                    return result;
                }
                lastError = result.error;
                if (lastError != null && lastError.startsWith("Checksum")) {
                    deleteQuietly(part);
                    return result;
                }
            } catch (IOException e) {
                lastError = e.getMessage() != null ? e.getMessage() : e.toString();
            }
            sleepBackoff(attempt);
        }
        deleteQuietly(part);
        return Result.fail(lastError);
    }

    private static Result attempt(String url, File dest, File part, String expectedSha256,
                                  long expectedSize, Progress progress) throws IOException {
        long existing = part.exists() ? part.length() : 0;
        if (expectedSize > 0 && existing >= expectedSize) {
            deleteQuietly(part);
            existing = 0;
        }
        if (expectedSize > 0) {
            File dir = dest.getParentFile();
            if (dir != null) {
                long usable = dir.getUsableSpace();
                if (usable > 0 && usable < (expectedSize - existing) + SPACE_MARGIN) {
                    return Result.fail("Not enough free space");
                }
            }
        }

        Request.Builder builder = new Request.Builder()
                .url(url)
                .header("User-Agent", Net.userAgent())
                .header("Accept-Encoding", "identity");
        if (existing > 0) {
            builder.header("Range", "bytes=" + existing + "-");
        }

        Response response = Net.client().newCall(builder.build()).execute();
        try {
            int code = response.code();
            if (code == 416) {
                deleteQuietly(part);
                return Result.fail("Range rejected, will restart");
            }
            if (code != 200 && code != 206) {
                return Result.fail("HTTP " + code);
            }
            ResponseBody body = response.body();
            if (body == null) {
                return Result.fail("Empty response");
            }
            boolean resume = code == 206 && existing > 0;
            if (code == 206) {
                long start = parseRangeStart(response.header("Content-Range"));
                if (start < 0 || start != existing) {
                    deleteQuietly(part);
                    return Result.fail("Unexpected range response");
                }
            }
            if (!resume) {
                existing = 0;
            }
            long bodyLength = body.contentLength();
            long advertisedTotal = bodyLength > 0 ? (resume ? existing : 0) + bodyLength : -1;
            long total = expectedSize > 0 ? expectedSize : advertisedTotal;

            MessageDigest digest = sha256();
            RandomAccessFile out = new RandomAccessFile(part, "rw");
            try {
                if (resume) {
                    out.seek(0);
                    byte[] rehash = new byte[BUFFER];
                    long left = existing;
                    int n;
                    while (left > 0 && (n = out.read(rehash, 0, (int) Math.min(rehash.length, left))) != -1) {
                        digest.update(rehash, 0, n);
                        left -= n;
                    }
                    out.seek(existing);
                } else {
                    out.setLength(0);
                }

                InputStream in = body.byteStream();
                byte[] buffer = new byte[BUFFER];
                long downloaded = existing;
                long lastReport = 0;
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    digest.update(buffer, 0, read);
                    downloaded += read;
                    long now = System.currentTimeMillis();
                    if (progress != null && now - lastReport >= 150) {
                        lastReport = now;
                        progress.onProgress(downloaded, total);
                    }
                }
                out.getFD().sync();
            } finally {
                closeQuietly(out);
            }

            long finalLength = part.length();
            if (expectedSize > 0) {
                if (finalLength != expectedSize) {
                    deleteQuietly(part);
                    return Result.fail("Size mismatch: got " + finalLength + ", expected " + expectedSize);
                }
            } else if (advertisedTotal > 0) {
                if (finalLength != advertisedTotal) {
                    deleteQuietly(part);
                    return Result.fail("Truncated: got " + finalLength + ", expected " + advertisedTotal);
                }
            } else if (finalLength == 0) {
                deleteQuietly(part);
                return Result.fail("Empty download");
            }
            String actualSha = toHex(digest.digest());
            if (expectedSha256 != null && !expectedSha256.isEmpty()
                    && !expectedSha256.equalsIgnoreCase(actualSha)) {
                deleteQuietly(part);
                return Result.fail("Checksum mismatch");
            }

            deleteQuietly(dest);
            if (!part.renameTo(dest)) {
                if (!copy(part, dest)) {
                    return Result.fail("Could not finalize download");
                }
                deleteQuietly(part);
            }
            if (progress != null && total > 0) {
                progress.onProgress(total, total);
            }
            return Result.ok(actualSha);
        } finally {
            response.close();
        }
    }

    private static long parseRangeStart(String contentRange) {
        if (contentRange == null) {
            return -1;
        }
        int space = contentRange.indexOf(' ');
        int dash = contentRange.indexOf('-');
        if (space < 0 || dash < 0 || dash <= space) {
            return -1;
        }
        try {
            return Long.parseLong(contentRange.substring(space + 1, dash).trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static MessageDigest sha256() throws IOException {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 unavailable");
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    private static boolean copy(File from, File to) {
        try (InputStream in = new java.io.FileInputStream(from);
             java.io.OutputStream os = new java.io.FileOutputStream(to)) {
            byte[] buffer = new byte[BUFFER];
            int read;
            while ((read = in.read(buffer)) != -1) {
                os.write(buffer, 0, read);
            }
            os.flush();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static void sleepBackoff(int attempt) {
        try {
            Thread.sleep(1000L * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void closeQuietly(java.io.Closeable c) {
        if (c == null) return;
        try {
            c.close();
        } catch (IOException ignored) {
        }
    }

    private static void deleteQuietly(File file) {
        if (file != null && file.exists()) {
            if (!file.delete()) {
                file.deleteOnExit();
            }
        }
    }
}
