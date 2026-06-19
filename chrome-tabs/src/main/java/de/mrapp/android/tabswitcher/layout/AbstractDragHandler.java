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

import android.content.res.Resources;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import de.mrapp.android.tabswitcher.R;
import de.mrapp.android.tabswitcher.TabSwitcher;
import de.mrapp.android.tabswitcher.layout.Arithmetics.Axis;
import de.mrapp.android.tabswitcher.model.TabItem;
import de.mrapp.android.util.gesture.DragHelper;

import static de.mrapp.android.util.Condition.ensureNotNull;

public abstract class AbstractDragHandler<CallbackType extends AbstractDragHandler.Callback> {

  public enum DragState {

    NONE,

    DRAG_TO_START,

    DRAG_TO_END,

    OVERSHOOT_START,

    OVERSHOOT_END,

    SWIPE

  }

  public interface Callback {

    @Nullable
    DragState onDrag(@NonNull DragState dragState, float dragDistance);

    void onClick(@NonNull TabItem tabItem);

    void onFling(float distance, long duration);

    void onCancelFling();

    void onRevertStartOvershoot();

    void onRevertEndOvershoot();

    void onSwipe(@NonNull TabItem tabItem, float distance);

    void onSwipeEnded(@NonNull TabItem tabItem, boolean remove, float velocity);

  }

  private final TabSwitcher tabSwitcher;

  private final Arithmetics arithmetics;

  private final boolean swipeEnabled;

  private final DragHelper dragHelper;

  private final DragHelper swipeDragHelper;

  private final float minFlingVelocity;

  private final float maxFlingVelocity;

  private final float minSwipeVelocity;

  private int dragThreshold;

  private VelocityTracker velocityTracker;

  private int pointerId;

  private TabItem swipedTabItem;

  private DragState dragState;

  private float dragDistance;

  private float startOvershootThreshold;

  private float endOvershootThreshold;

  private CallbackType callback;

  private void resetDragging(final int dragThreshold) {
    if (this.velocityTracker != null) {
      this.velocityTracker.recycle();
      this.velocityTracker = null;
    }

    this.pointerId = -1;
    this.dragState = DragState.NONE;
    this.swipedTabItem = null;
    this.dragDistance = 0;
    this.startOvershootThreshold = -Float.MAX_VALUE;
    this.endOvershootThreshold = Float.MAX_VALUE;
    this.dragThreshold = dragThreshold;
    this.dragHelper.reset(dragThreshold);
    this.swipeDragHelper.reset();
  }

  private void handleDown(@NonNull final MotionEvent event) {
    pointerId = event.getPointerId(0);

    if (velocityTracker == null) {
      velocityTracker = VelocityTracker.obtain();
    } else {
      velocityTracker.clear();
    }

    velocityTracker.addMovement(event);
  }

  private void handleClick(@NonNull final MotionEvent event) {
    TabItem tabItem = getFocusedTab(arithmetics.getPosition(Axis.DRAGGING_AXIS, event));

    if (tabItem != null) {
      notifyOnClick(tabItem);
    }
  }

  private void handleFling(@NonNull final MotionEvent event, @NonNull final DragState dragState) {
    int pointerId = event.getPointerId(0);
    velocityTracker.computeCurrentVelocity(1000, maxFlingVelocity);
    float flingVelocity = Math.abs(velocityTracker.getYVelocity(pointerId));

    if (flingVelocity > minFlingVelocity) {
      float flingDistance = 0.25f * flingVelocity;

      if (dragState == DragState.DRAG_TO_START) {
        flingDistance = -1 * flingDistance;
      }

      long duration = Math.round(Math.abs(flingDistance) / flingVelocity * 3000);
      notifyOnFling(flingDistance, duration);
    }
  }

  private void handleOvershoot() {
    if (!dragHelper.isReset()) {
      dragHelper.reset(0);
      dragDistance = 0;
    }
  }

  private DragState notifyOnDrag(@NonNull final DragState dragState, final float dragDistance) {
    if (callback != null) {
      return callback.onDrag(dragState, dragDistance);
    }

    return null;
  }

  private void notifyOnClick(@NonNull final TabItem tabItem) {
    if (callback != null) {
      callback.onClick(tabItem);
    }
  }

  private void notifyOnFling(final float distance, final long duration) {
    if (callback != null) {
      callback.onFling(distance, duration);
    }
  }

  private void notifyOnCancelFling() {
    if (callback != null) {
      callback.onCancelFling();
    }
  }

  private void notifyOnRevertStartOvershoot() {
    if (callback != null) {
      callback.onRevertStartOvershoot();
    }
  }

  private void notifyOnRevertEndOvershoot() {
    if (callback != null) {
      callback.onRevertEndOvershoot();
    }
  }

  private void notifyOnSwipe(@NonNull final TabItem tabItem, final float distance) {
    if (callback != null) {
      callback.onSwipe(tabItem, distance);
    }
  }

  private void notifyOnSwipeEnded(@NonNull final TabItem tabItem, final boolean remove,
                                  final float velocity) {
    if (callback != null) {
      callback.onSwipeEnded(tabItem, remove, velocity);
    }
  }

  @NonNull
  protected TabSwitcher getTabSwitcher() {
    return tabSwitcher;
  }

  @NonNull
  protected Arithmetics getArithmetics() {
    return arithmetics;
  }

  @Nullable
  protected CallbackType getCallback() {
    return callback;
  }

  public AbstractDragHandler(@NonNull final TabSwitcher tabSwitcher,
                             @NonNull final Arithmetics arithmetics, final boolean swipeEnabled) {
    ensureNotNull(tabSwitcher, "The tab switcher may not be null");
    ensureNotNull(arithmetics, "The arithmetics may not be null");
    this.tabSwitcher = tabSwitcher;
    this.arithmetics = arithmetics;
    this.swipeEnabled = swipeEnabled;
    this.dragHelper = new DragHelper(0);
    Resources resources = tabSwitcher.getResources();
    this.swipeDragHelper =
      new DragHelper(resources.getDimensionPixelSize(R.dimen.swipe_threshold));
    this.callback = null;
    ViewConfiguration configuration = ViewConfiguration.get(tabSwitcher.getContext());
    this.minFlingVelocity = configuration.getScaledMinimumFlingVelocity();
    this.maxFlingVelocity = configuration.getScaledMaximumFlingVelocity();
    this.minSwipeVelocity = resources.getDimensionPixelSize(R.dimen.min_swipe_velocity);
    resetDragging(resources.getDimensionPixelSize(R.dimen.drag_threshold));
  }

  protected abstract TabItem getFocusedTab(final float position);

  protected float onOvershootStart(final float dragPosition, final float overshootThreshold) {
    return overshootThreshold;
  }

  protected float onOvershootEnd(final float dragPosition, final float overshootThreshold) {
    return overshootThreshold;
  }

  protected void onOvershootReverted() {

  }

  protected void onReset() {

  }

  protected boolean isSwipeThresholdReached(@NonNull final TabItem swipedTabItem) {
    return false;
  }

  public final void setCallback(@Nullable final CallbackType callback) {
    this.callback = callback;
  }

  public final boolean handleTouchEvent(@NonNull final MotionEvent event) {
    ensureNotNull(event, "The motion event may not be null");

    if (tabSwitcher.isSwitcherShown() && !tabSwitcher.isEmpty()) {
      notifyOnCancelFling();

      switch (event.getAction()) {
        case MotionEvent.ACTION_DOWN:
          handleDown(event);
          return true;
        case MotionEvent.ACTION_MOVE:
          if (!tabSwitcher.isAnimationRunning() && event.getPointerId(0) == pointerId) {
            if (velocityTracker == null) {
              velocityTracker = VelocityTracker.obtain();
            }

            velocityTracker.addMovement(event);
            handleDrag(arithmetics.getPosition(Axis.DRAGGING_AXIS, event),
              arithmetics.getPosition(Axis.ORTHOGONAL_AXIS, event));
          } else {
            handleRelease(null, dragThreshold);
            handleDown(event);
          }

          return true;
        case MotionEvent.ACTION_UP:
          if (!tabSwitcher.isAnimationRunning() && event.getPointerId(0) == pointerId) {
            handleRelease(event, dragThreshold);
          }

          return true;
        default:
          break;
      }
    }

    return false;
  }

  public final boolean handleDrag(final float dragPosition, final float orthogonalPosition) {
    if (dragPosition <= startOvershootThreshold) {
      handleOvershoot();
      dragState = DragState.OVERSHOOT_START;
      startOvershootThreshold = onOvershootStart(dragPosition, startOvershootThreshold);
    } else if (dragPosition >= endOvershootThreshold) {
      handleOvershoot();
      dragState = DragState.OVERSHOOT_END;
      endOvershootThreshold = onOvershootEnd(dragPosition, endOvershootThreshold);
    } else {
      onOvershootReverted();
      float previousDistance = dragHelper.isReset() ? 0 : dragHelper.getDragDistance();
      dragHelper.update(dragPosition);

      if (swipeEnabled) {
        swipeDragHelper.update(orthogonalPosition);

        if (dragState == DragState.NONE && swipeDragHelper.hasThresholdBeenReached()) {
          TabItem tabItem = getFocusedTab(dragHelper.getDragStartPosition());

          if (tabItem != null) {
            dragState = DragState.SWIPE;
            swipedTabItem = tabItem;
          }
        }
      }

      if (dragState != DragState.SWIPE && dragHelper.hasThresholdBeenReached()) {
        if (dragState == DragState.OVERSHOOT_START) {
          dragState = DragState.DRAG_TO_END;
        } else if (dragState == DragState.OVERSHOOT_END) {
          dragState = DragState.DRAG_TO_START;
        } else {
          float dragDistance = dragHelper.getDragDistance();

          if (dragDistance == 0) {
            dragState = DragState.NONE;
          } else {
            dragState = previousDistance - dragDistance < 0 ? DragState.DRAG_TO_END :
              DragState.DRAG_TO_START;
          }
        }
      }

      if (dragState == DragState.SWIPE) {
        notifyOnSwipe(swipedTabItem, swipeDragHelper.getDragDistance());
      } else if (dragState != DragState.NONE) {
        float currentDragDistance = dragHelper.getDragDistance();
        float distance = currentDragDistance - dragDistance;
        dragDistance = currentDragDistance;
        DragState overshoot = notifyOnDrag(dragState, distance);

        if (overshoot == DragState.OVERSHOOT_END && (dragState == DragState.DRAG_TO_END ||
          dragState == DragState.OVERSHOOT_END)) {
          endOvershootThreshold = dragPosition;
          dragState = DragState.OVERSHOOT_END;
        } else if (overshoot == DragState.OVERSHOOT_START &&
          (dragState == DragState.DRAG_TO_START ||
            dragState == DragState.OVERSHOOT_START)) {
          startOvershootThreshold = dragPosition;
          dragState = DragState.OVERSHOOT_START;
        }

        return true;
      }
    }

    return false;
  }

  public final void handleRelease(@Nullable final MotionEvent event, final int dragThreshold) {
    if (dragState == DragState.SWIPE) {
      float swipeVelocity = 0;

      if (event != null && velocityTracker != null) {
        int pointerId = event.getPointerId(0);
        velocityTracker.computeCurrentVelocity(1000, maxFlingVelocity);
        swipeVelocity = Math.abs(velocityTracker.getXVelocity(pointerId));
      }

      boolean remove = swipedTabItem.getTab().isCloseable() &&
        (swipeVelocity >= minSwipeVelocity || isSwipeThresholdReached(swipedTabItem));
      notifyOnSwipeEnded(swipedTabItem, remove,
        swipeVelocity >= minSwipeVelocity ? swipeVelocity : 0);
    } else if (dragState == DragState.DRAG_TO_START || dragState == DragState.DRAG_TO_END) {
      if (event != null && velocityTracker != null && dragHelper.hasThresholdBeenReached()) {
        handleFling(event, dragState);
      }
    } else if (dragState == DragState.OVERSHOOT_END) {
      notifyOnRevertEndOvershoot();
    } else if (dragState == DragState.OVERSHOOT_START) {
      notifyOnRevertStartOvershoot();
    } else if (event != null) {
      handleClick(event);
    }

    resetDragging(dragThreshold);
  }

  public final void reset(final int dragThreshold) {
    resetDragging(dragThreshold);
    onReset();
  }

}
