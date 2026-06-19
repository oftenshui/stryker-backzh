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

package de.mrapp.android.tabswitcher.layout;

import android.view.MotionEvent;
import android.view.View;
import android.view.ViewPropertyAnimator;
import androidx.annotation.NonNull;
import de.mrapp.android.tabswitcher.TabSwitcher;
import de.mrapp.android.tabswitcher.layout.AbstractDragHandler.DragState;

public interface Arithmetics {

  enum Axis {

    DRAGGING_AXIS,

    ORTHOGONAL_AXIS,

    X_AXIS,

    Y_AXIS

  }

  float getPosition(@NonNull Axis axis, @NonNull MotionEvent event);

  float getPosition(@NonNull Axis axis, @NonNull View view);

  void setPosition(@NonNull Axis axis, @NonNull View view, float position);

  void animatePosition(@NonNull Axis axis, @NonNull ViewPropertyAnimator animator,
                       @NonNull View view, float position, boolean includePadding);

  int getPadding(@NonNull Axis axis, int gravity, @NonNull View view);

  float getScale(@NonNull final View view, final boolean includePadding);

  void setScale(@NonNull Axis axis, @NonNull View view, float scale);

  void animateScale(@NonNull Axis axis, @NonNull ViewPropertyAnimator animator, float scale);

  float getSize(@NonNull Axis axis, @NonNull View view);

  float getTabContainerSize(@NonNull Axis axis);

  float getTabContainerSize(@NonNull Axis axis, boolean includePadding);

  float getPivot(@NonNull Axis axis, @NonNull View view, @NonNull DragState dragState);

  void setPivot(@NonNull Axis axis, @NonNull View view, float pivot);

  float getRotation(@NonNull Axis axis, @NonNull View view);

  void setRotation(@NonNull Axis axis, @NonNull View view, float angle);

  void animateRotation(@NonNull Axis axis, @NonNull ViewPropertyAnimator animator, float angle);

}
