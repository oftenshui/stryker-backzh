package com.zalexdev.stryker.hid.exec;

import androidx.annotation.NonNull;

import com.zalexdev.stryker.hid.capability.HidCapabilities;

public final class ExecutionResult {

    public enum Kind { OK, CANCELLED, PARSE_ERROR, CAPABILITY_ERROR, IO_ERROR }

    public final Kind kind;
    public final String message;
    public final int errorLine;
    public final HidCapabilities.Verdict verdict;

    private ExecutionResult(Kind kind, @NonNull String message, int errorLine,
                            HidCapabilities.Verdict verdict) {
        this.kind = kind;
        this.message = message;
        this.errorLine = errorLine;
        this.verdict = verdict;
    }

    public static ExecutionResult ok() {
        return new ExecutionResult(Kind.OK, "Done", 0, null);
    }

    public static ExecutionResult cancelled() {
        return new ExecutionResult(Kind.CANCELLED, "Cancelled by user", 0, null);
    }

    public static ExecutionResult parseError(int line, String msg) {
        return new ExecutionResult(Kind.PARSE_ERROR, msg, line, null);
    }

    public static ExecutionResult capabilityError(HidCapabilities.Verdict v, String msg) {
        return new ExecutionResult(Kind.CAPABILITY_ERROR, msg, 0, v);
    }

    public static ExecutionResult ioError(String msg) {
        return new ExecutionResult(Kind.IO_ERROR, msg, 0, null);
    }

    public boolean isSuccess() {
        return kind == Kind.OK;
    }
}
