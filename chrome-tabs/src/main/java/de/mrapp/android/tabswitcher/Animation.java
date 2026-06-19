/*
 * Copyright 2016 - 2017 Michael Rapp
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package de.mrapp.android.tabswitcher;

import android.view.animation.Interpolator;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import static de.mrapp.android.util.Condition.ensureAtLeast;

public abstract class Animation {

  protected static abstract class Builder<AnimationType, BuilderType> {

    protected long duration;

    protected Interpolator interpolator;

    @NonNull
    @SuppressWarnings("unchecked")
    protected final BuilderType self() {
      return (BuilderType) this;
    }

    public Builder() {
      setDuration(-1);
      setInterpolator(null);
    }

    @NonNull
    public abstract AnimationType create();

    @NonNull
    public final BuilderType setDuration(final long duration) {
      ensureAtLeast(duration, -1, "The duration must be at least -1");
      this.duration = duration;
      return self();
    }

    @NonNull
    public final BuilderType setInterpolator(@Nullable final Interpolator interpolator) {
      this.interpolator = interpolator;
      return self();
    }

  }

  private final long duration;

  private final Interpolator interpolator;

  protected Animation(final long duration, @Nullable final Interpolator interpolator) {
    ensureAtLeast(duration, -1, "The duration must be at least -1");
    this.duration = duration;
    this.interpolator = interpolator;
  }

  public final long getDuration() {
    return duration;
  }

  @Nullable
  public final Interpolator getInterpolator() {
    return interpolator;
  }

}
