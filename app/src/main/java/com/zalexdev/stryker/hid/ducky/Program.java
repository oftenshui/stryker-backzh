package com.zalexdev.stryker.hid.ducky;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.List;

public final class Program {

    public final List<Step> steps;
    public final long defaultDelay;
    public final long defaultCharDelay;

    public Program(@NonNull List<Step> steps, long defaultDelay, long defaultCharDelay) {
        this.steps = Collections.unmodifiableList(steps);
        this.defaultDelay = defaultDelay;
        this.defaultCharDelay = defaultCharDelay;
    }
}
