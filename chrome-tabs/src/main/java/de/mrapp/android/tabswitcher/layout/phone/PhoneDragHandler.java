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
package de.mrapp.android.tabswitcher.layout.phone;

import android.content.res.Resources;
import android.view.Gravity;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import de.mrapp.android.tabswitcher.Layout;
import de.mrapp.android.tabswitcher.R;
import de.mrapp.android.tabswitcher.TabSwitcher;
import de.mrapp.android.tabswitcher.iterator.AbstractTabItemIterator;
import de.mrapp.android.tabswitcher.iterator.TabItemIterator;
import de.mrapp.android.tabswitcher.layout.AbstractDragHandler;
import de.mrapp.android.tabswitcher.layout.Arithmetics;
import de.mrapp.android.tabswitcher.layout.Arithmetics.Axis;
import de.mrapp.android.tabswitcher.model.State;
import de.mrapp.android.tabswitcher.model.TabItem;
import de.mrapp.android.util.gesture.DragHelper;
import de.mrapp.android.util.view.AttachedViewRecycler;

import static de.mrapp.android.util.Condition.ensureNotNull;

public class PhoneDragHandler extends AbstractDragHandler<PhoneDragHandler.Callback> {

  public interface Callback extends AbstractDragHandler.Callback {

    void onStartOvershoot(float position);

    void onTiltOnStartOvershoot(float angle);

    void onTiltOnEndOvershoot(float angle);

  }

  private final AttachedViewRecycler<TabItem, ?> viewRecycler;

  private final DragHelper overshootDragHelper;

  private final int maxOvershootDistance;

  private final float maxStartOvershootAngle;

  private final float maxEndOvershootAngle;

  private final int stackedTabCount;

  private final int tabInset;

  private void notifyOnStartOvershoot(final float position) {
    if (getCallback() != null) {
      getCallback().onStartOvershoot(position);
    }
  }

  private void notifyOnTiltOnStartOvershoot(final float angle) {
    if (getCallback() != null) {
      getCallback().onTiltOnStartOvershoot(angle);
    }
  }

  private void notifyOnTiltOnEndOvershoot(final float angle) {
    if (getCallback() != null) {
      getCallback().onTiltOnEndOvershoot(angle);
    }
  }

  public PhoneDragHandler(@NonNull final TabSwitcher tabSwitcher,
                          @NonNull final Arithmetics arithmetics,
                          @NonNull final AttachedViewRecycler<TabItem, ?> viewRecycler) {
    super(tabSwitcher, arithmetics, true);
    ensureNotNull(viewRecycler, "The view recycler may not be null");
    this.viewRecycler = viewRecycler;
    this.overshootDragHelper = new DragHelper(0);
    Resources resources = tabSwitcher.getResources();
    this.tabInset = resources.getDimensionPixelSize(R.dimen.tab_inset);
    this.stackedTabCount = resources.getInteger(R.integer.stacked_tab_count);
    this.maxOvershootDistance = resources.getDimensionPixelSize(R.dimen.max_overshoot_distance);
    this.maxStartOvershootAngle = resources.getInteger(R.integer.max_start_overshoot_angle);
    this.maxEndOvershootAngle = resources.getInteger(R.integer.max_end_overshoot_angle);
  }

  @Override
  @Nullable
  protected final TabItem getFocusedTab(final float position) {
    AbstractTabItemIterator iterator =
      new TabItemIterator.Builder(getTabSwitcher(), viewRecycler).create();
    TabItem tabItem;

    while ((tabItem = iterator.next()) != null) {
      if (tabItem.getTag().getState() == State.FLOATING ||
        tabItem.getTag().getState() == State.STACKED_START_ATOP ||
        tabItem.getTag().getState() == State.STACKED_START) {
        View view = tabItem.getView();
        Toolbar[] toolbars = getTabSwitcher().getToolbars();
        float toolbarHeight = getTabSwitcher().getLayout() != Layout.PHONE_LANDSCAPE &&
          getTabSwitcher().areToolbarsShown() && toolbars != null ?
          toolbars[0].getHeight() - tabInset : 0;
        float viewPosition =
          getArithmetics().getPosition(Axis.DRAGGING_AXIS, view) + toolbarHeight +
            getArithmetics().getPadding(Axis.DRAGGING_AXIS, Gravity.START,
              getTabSwitcher());

        if (viewPosition <= position) {
          return tabItem;
        }
      }
    }

    return null;
  }

  @Override
  protected final float onOvershootStart(final float dragPosition,
                                         final float overshootThreshold) {
    float result = overshootThreshold;
    overshootDragHelper.update(dragPosition);
    float overshootDistance = overshootDragHelper.getDragDistance();

    if (overshootDistance < 0) {
      float absOvershootDistance = Math.abs(overshootDistance);
      float startOvershootDistance =
        getTabSwitcher().getCount() >= stackedTabCount ? maxOvershootDistance :
          (getTabSwitcher().getCount() > 1 ? (float) maxOvershootDistance /
            (float) getTabSwitcher().getCount() : 0);

      if (absOvershootDistance <= startOvershootDistance) {
        float ratio =
          Math.max(0, Math.min(1, absOvershootDistance / startOvershootDistance));
        AbstractTabItemIterator iterator =
          new TabItemIterator.Builder(getTabSwitcher(), viewRecycler).create();
        TabItem tabItem = iterator.getItem(0);
        float currentPosition = tabItem.getTag().getPosition();
        float position = currentPosition - (currentPosition * ratio);
        notifyOnStartOvershoot(position);
      } else {
        float ratio =
          (absOvershootDistance - startOvershootDistance) / maxOvershootDistance;

        if (ratio >= 1) {
          overshootDragHelper.setMinDragDistance(overshootDistance);
          result = dragPosition + maxOvershootDistance + startOvershootDistance;
        }

        notifyOnTiltOnStartOvershoot(
          Math.max(0, Math.min(1, ratio)) * maxStartOvershootAngle);
      }
    }

    return result;
  }

  @Override
  protected final float onOvershootEnd(final float dragPosition, final float overshootThreshold) {
    float result = overshootThreshold;
    overshootDragHelper.update(dragPosition);
    float overshootDistance = overshootDragHelper.getDragDistance();
    float ratio = overshootDistance / maxOvershootDistance;

    if (ratio >= 1) {
      overshootDragHelper.setMaxDragDistance(overshootDistance);
      result = dragPosition - maxOvershootDistance;
    }

    notifyOnTiltOnEndOvershoot(Math.max(0, Math.min(1, ratio)) *
      -(getTabSwitcher().getCount() > 1 ? maxEndOvershootAngle : maxStartOvershootAngle));
    return result;
  }

  @Override
  protected final void onOvershootReverted() {
    overshootDragHelper.reset();
  }

  @Override
  protected final void onReset() {
    overshootDragHelper.reset();
  }

  @Override
  protected final boolean isSwipeThresholdReached(@NonNull final TabItem swipedTabItem) {
    View view = swipedTabItem.getView();
    return Math.abs(getArithmetics().getPosition(Axis.ORTHOGONAL_AXIS, view)) >
      getArithmetics().getTabContainerSize(Axis.ORTHOGONAL_AXIS) / 2f;
  }

}
