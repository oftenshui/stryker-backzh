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

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Transformation;
import androidx.annotation.CallSuper;
import androidx.annotation.MenuRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.appcompat.widget.Toolbar.OnMenuItemClickListener;
import androidx.core.util.Pair;
import de.mrapp.android.tabswitcher.R;
import de.mrapp.android.tabswitcher.TabSwitcher;
import de.mrapp.android.tabswitcher.TabSwitcherDecorator;
import de.mrapp.android.tabswitcher.model.Model;
import de.mrapp.android.tabswitcher.model.TabItem;
import de.mrapp.android.tabswitcher.model.TabSwitcherModel;
import de.mrapp.android.util.ViewUtil;
import de.mrapp.android.util.logging.Logger;

import static de.mrapp.android.util.Condition.ensureNotNull;

public abstract class AbstractTabSwitcherLayout
  implements TabSwitcherLayout, OnGlobalLayoutListener, Model.Listener,
  AbstractDragHandler.Callback {

  public interface Callback {

    void onAnimationsEnded();

  }

  public static class LayoutListenerWrapper implements OnGlobalLayoutListener {

    private final View view;

    private final OnGlobalLayoutListener listener;

    public LayoutListenerWrapper(@NonNull final View view,
                                 @Nullable final OnGlobalLayoutListener listener) {
      ensureNotNull(view, "The view may not be null");
      this.view = view;
      this.listener = listener;
    }

    @Override
    public void onGlobalLayout() {
      ViewUtil.removeOnGlobalLayoutListener(view.getViewTreeObserver(), this);

      if (listener != null) {
        listener.onGlobalLayout();
      }
    }

  }

  protected class AnimationListenerWrapper extends AnimatorListenerAdapter {

    private final AnimatorListener listener;

    private void endAnimation() {
      if (--runningAnimations == 0) {
        notifyOnAnimationsEnded();
      }
    }

    public AnimationListenerWrapper(@Nullable final AnimatorListener listener) {
      this.listener = listener;
    }

    @Override
    public void onAnimationStart(final Animator animation) {
      super.onAnimationStart(animation);
      runningAnimations++;

      if (listener != null) {
        listener.onAnimationStart(animation);
      }
    }

    @Override
    public void onAnimationEnd(final Animator animation) {
      super.onAnimationEnd(animation);

      if (listener != null) {
        listener.onAnimationEnd(animation);
      }

      endAnimation();
    }

    @Override
    public void onAnimationCancel(final Animator animation) {
      super.onAnimationCancel(animation);

      if (listener != null) {
        listener.onAnimationCancel(animation);
      }

      endAnimation();
    }

  }

  private class FlingAnimation extends android.view.animation.Animation {

    private final float distance;

    FlingAnimation(final float distance) {
      this.distance = distance;
    }

    @Override
    protected void applyTransformation(final float interpolatedTime, final Transformation t) {
      if (flingAnimation != null) {
        dragHandler.handleDrag(distance * interpolatedTime, 0);
      }
    }

  }

  private final TabSwitcher tabSwitcher;

  private final TabSwitcherModel model;

  private final Arithmetics arithmetics;

  private final int dragThreshold;

  private final Logger logger;

  private Callback callback;

  private int runningAnimations;

  private android.view.animation.Animation flingAnimation;

  private AbstractDragHandler<?> dragHandler;

  private void adaptToolbarVisibility() {
    Toolbar[] toolbars = getToolbars();

    if (toolbars != null) {
      for (Toolbar toolbar : toolbars) {
        toolbar.setVisibility(
          getModel().areToolbarsShown() ? View.VISIBLE : View.INVISIBLE);
      }
    }
  }

  private void adaptToolbarTitle() {
    Toolbar[] toolbars = getToolbars();

    if (toolbars != null) {
      toolbars[0].setTitle(getModel().getToolbarTitle());
    }
  }

  private void adaptToolbarNavigationIcon() {
    Toolbar[] toolbars = getToolbars();

    if (toolbars != null) {
      Toolbar toolbar = toolbars[0];
      toolbar.setNavigationIcon(getModel().getToolbarNavigationIcon());
      toolbar.setNavigationOnClickListener(getModel().getToolbarNavigationIconListener());
    }
  }

  private void inflateToolbarMenu() {
    Toolbar[] toolbars = getToolbars();
    int menuId = getModel().getToolbarMenuId();

    if (toolbars != null && menuId != -1) {
      Toolbar toolbar = toolbars.length > 1 ? toolbars[1] : toolbars[0];
      toolbar.inflateMenu(menuId);
      toolbar.setOnMenuItemClickListener(getModel().getToolbarMenuItemListener());
    }
  }

  @NonNull
  private Animation.AnimationListener createFlingAnimationListener() {
    return new Animation.AnimationListener() {

      @Override
      public void onAnimationStart(final android.view.animation.Animation animation) {

      }

      @Override
      public void onAnimationEnd(final android.view.animation.Animation animation) {
        dragHandler.handleRelease(null, dragThreshold);
        flingAnimation = null;
        notifyOnAnimationsEnded();
      }

      @Override
      public void onAnimationRepeat(final android.view.animation.Animation animation) {

      }

    };
  }

  private void notifyOnAnimationsEnded() {
    if (callback != null) {
      callback.onAnimationsEnded();
    }
  }

  @NonNull
  protected final TabSwitcher getTabSwitcher() {
    return tabSwitcher;
  }

  @NonNull
  protected final TabSwitcherModel getModel() {
    return model;
  }

  @NonNull
  protected final Arithmetics getArithmetics() {
    return arithmetics;
  }

  protected final int getDragThreshold() {
    return dragThreshold;
  }

  @NonNull
  protected final Logger getLogger() {
    return logger;
  }

  @NonNull
  protected final Context getContext() {
    return tabSwitcher.getContext();
  }

  public AbstractTabSwitcherLayout(@NonNull final TabSwitcher tabSwitcher,
                                   @NonNull final TabSwitcherModel model,
                                   @NonNull final Arithmetics arithmetics) {
    ensureNotNull(tabSwitcher, "The tab switcher may not be null");
    ensureNotNull(model, "The model may not be null");
    ensureNotNull(arithmetics, "The arithmetics may not be null");
    this.tabSwitcher = tabSwitcher;
    this.model = model;
    this.arithmetics = arithmetics;
    this.dragThreshold =
      getTabSwitcher().getResources().getDimensionPixelSize(R.dimen.drag_threshold);
    this.logger = new Logger(model.getLogLevel());
    this.callback = null;
    this.runningAnimations = 0;
    this.flingAnimation = null;
    this.dragHandler = null;
  }

  @Nullable
  protected abstract AbstractDragHandler<?> onInflateLayout(final boolean tabsOnly);

  @Nullable
  protected abstract Pair<Integer, Float> onDetachLayout(final boolean tabsOnly);

  public abstract boolean handleTouchEvent(@NonNull final MotionEvent event);

  public final void inflateLayout(final boolean tabsOnly) {
    dragHandler = onInflateLayout(tabsOnly);

    if (!tabsOnly) {
      adaptToolbarVisibility();
      adaptToolbarTitle();
      adaptToolbarNavigationIcon();
      inflateToolbarMenu();
    }
  }

  @Nullable
  public final Pair<Integer, Float> detachLayout(final boolean tabsOnly) {
    return onDetachLayout(tabsOnly);
  }

  public final void setCallback(@Nullable final Callback callback) {
    this.callback = callback;
  }

  @Override
  public final boolean isAnimationRunning() {
    return runningAnimations > 0 || flingAnimation != null;
  }

  @Nullable
  @Override
  public final Menu getToolbarMenu() {
    Toolbar[] toolbars = getToolbars();

    if (toolbars != null) {
      Toolbar toolbar = toolbars.length > 1 ? toolbars[1] : toolbars[0];
      return toolbar.getMenu();
    }

    return null;
  }

  @CallSuper
  @Override
  public void onDecoratorChanged(@NonNull final TabSwitcherDecorator decorator) {
    detachLayout(true);
    onGlobalLayout();
  }

  @Override
  public final void onToolbarVisibilityChanged(final boolean visible) {
    adaptToolbarVisibility();
  }

  @Override
  public final void onToolbarTitleChanged(@Nullable final CharSequence title) {
    adaptToolbarTitle();
  }

  @Override
  public final void onToolbarNavigationIconChanged(@Nullable final Drawable icon,
                                                   @Nullable final OnClickListener listener) {
    adaptToolbarNavigationIcon();
  }

  @Override
  public final void onToolbarMenuInflated(@MenuRes final int resourceId,
                                          @Nullable final OnMenuItemClickListener listener) {
    inflateToolbarMenu();
  }

  @Override
  public final void onFling(final float distance, final long duration) {
    if (dragHandler != null) {
      flingAnimation = new FlingAnimation(distance);
      flingAnimation.setFillAfter(true);
      flingAnimation.setAnimationListener(createFlingAnimationListener());
      flingAnimation.setDuration(duration);
      flingAnimation.setInterpolator(new DecelerateInterpolator());
      getTabSwitcher().startAnimation(flingAnimation);
      logger.logVerbose(getClass(),
        "Started fling animation using a distance of " + distance +
          " pixels and a duration of " + duration + " milliseconds");
    }
  }

  @Override
  public final void onCancelFling() {
    if (flingAnimation != null) {
      flingAnimation.cancel();
      flingAnimation = null;
      dragHandler.handleRelease(null, dragThreshold);
      logger.logVerbose(getClass(), "Canceled fling animation");
    }
  }

  @Override
  public void onRevertStartOvershoot() {

  }

  @Override
  public void onRevertEndOvershoot() {

  }

  @Override
  public void onSwipe(@NonNull final TabItem tabItem, final float distance) {

  }

  @Override
  public void onSwipeEnded(@NonNull final TabItem tabItem, final boolean remove,
                           final float velocity) {

  }

}
