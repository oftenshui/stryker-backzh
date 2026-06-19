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

import static de.mrapp.android.util.Condition.ensureNotNull;

public class SwipeAnimation extends Animation {

  public enum SwipeDirection {

    LEFT,

    RIGHT

  }

  public static class Builder extends Animation.Builder<SwipeAnimation, Builder> {

    private SwipeDirection direction;

    public Builder() {
      setDirection(SwipeDirection.RIGHT);
    }

    @NonNull
    public final Builder setDirection(@NonNull final SwipeDirection direction) {
      ensureNotNull(direction, "The direction may not be null");
      this.direction = direction;
      return self();
    }

    @NonNull
    @Override
    public final SwipeAnimation create() {
      return new SwipeAnimation(duration, interpolator, direction);
    }

  }

  private final SwipeDirection direction;

  private SwipeAnimation(final long duration, @Nullable final Interpolator interpolator,
                         @NonNull final SwipeDirection direction) {
    super(duration, interpolator);
    ensureNotNull(direction, "The direction may not be null");
    this.direction = direction;
  }

  @NonNull
  public final SwipeDirection getDirection() {
    return direction;
  }

}
