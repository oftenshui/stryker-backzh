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

public class PeekAnimation extends Animation {

  public static class Builder extends Animation.Builder<PeekAnimation, Builder> {

    private float x;

    private float y;

    public Builder() {
      setX(0);
      setY(0);
    }

    @NonNull
    public final Builder setX(final float x) {
      this.x = x;
      return self();
    }

    @NonNull
    public final Builder setY(final float y) {
      this.y = y;
      return self();
    }

    @NonNull
    @Override
    public final PeekAnimation create() {
      return new PeekAnimation(duration, interpolator, x, y);
    }

  }

  private final float x;

  private final float y;

  private PeekAnimation(final long duration, @Nullable final Interpolator interpolator,
                        final float x, final float y) {
    super(duration, interpolator);
    this.x = x;
    this.y = y;
  }

  public final float getX() {
    return x;
  }

  public final float getY() {
    return y;
  }

}
