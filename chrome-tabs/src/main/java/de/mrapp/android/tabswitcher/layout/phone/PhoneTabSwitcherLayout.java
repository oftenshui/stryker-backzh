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

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.*;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.util.Pair;
import androidx.core.view.ViewCompat;
import de.mrapp.android.tabswitcher.*;
import de.mrapp.android.tabswitcher.SwipeAnimation.SwipeDirection;
import de.mrapp.android.tabswitcher.iterator.AbstractTabItemIterator;
import de.mrapp.android.tabswitcher.iterator.ArrayTabItemIterator;
import de.mrapp.android.tabswitcher.iterator.TabItemIterator;
import de.mrapp.android.tabswitcher.layout.AbstractDragHandler;
import de.mrapp.android.tabswitcher.layout.AbstractDragHandler.DragState;
import de.mrapp.android.tabswitcher.layout.AbstractTabSwitcherLayout;
import de.mrapp.android.tabswitcher.layout.Arithmetics.Axis;
import de.mrapp.android.tabswitcher.model.State;
import de.mrapp.android.tabswitcher.model.TabItem;
import de.mrapp.android.tabswitcher.model.TabSwitcherModel;
import de.mrapp.android.tabswitcher.model.Tag;
import de.mrapp.android.util.logging.LogLevel;
import de.mrapp.android.util.view.AttachedViewRecycler;
import de.mrapp.android.util.view.ViewRecycler;

import java.util.Collections;

import static de.mrapp.android.util.Condition.*;

public class PhoneTabSwitcherLayout extends AbstractTabSwitcherLayout
  implements PhoneDragHandler.Callback {

  private class InitialTabItemIterator extends AbstractTabItemIterator {

    private final TabItem[] array;

    private void calculateAndClipStartPosition(@NonNull final TabItem tabItem,
                                               @Nullable final TabItem predecessor) {
      float position = calculateStartPosition(tabItem);
      Pair<Float, State> pair =
        clipTabPosition(getModel().getCount(), tabItem.getIndex(), position,
          predecessor);
      tabItem.getTag().setPosition(pair.first);
      tabItem.getTag().setState(pair.second);
    }

    private float calculateStartPosition(@NonNull final TabItem tabItem) {
      if (tabItem.getIndex() == 0) {
        return getCount() > stackedTabCount ? stackedTabCount * stackedTabSpacing :
          (getCount() - 1) * stackedTabSpacing;

      } else {
        return -1;
      }
    }

    private InitialTabItemIterator(@NonNull final TabItem[] array, final boolean reverse,
                                   final int start) {
      ensureNotNull(array, "The array may not be null");
      ensureEqual(array.length, getModel().getCount(),
        "The array's length must be " + getModel().getCount());
      this.array = array;
      initialize(reverse, start);
    }

    @Override
    public final int getCount() {
      return array.length;
    }

    @NonNull
    @Override
    public final TabItem getItem(final int index) {
      TabItem tabItem = array[index];

      if (tabItem == null) {
        tabItem = TabItem.create(getModel(), viewRecycler, index);
        calculateAndClipStartPosition(tabItem, index > 0 ? getItem(index - 1) : null);
        array[index] = tabItem;
      }

      return tabItem;
    }

  }

  private class CompoundLayoutListener implements OnGlobalLayoutListener {

    private int count;

    private final OnGlobalLayoutListener listener;

    CompoundLayoutListener(final int count, @Nullable final OnGlobalLayoutListener listener) {
      ensureGreater(count, 0, "The count must be greater than 0");
      this.count = count;
      this.listener = listener;
    }

    @Override
    public void onGlobalLayout() {
      if (--count == 0) {
        if (listener != null) {
          listener.onGlobalLayout();
        }
      }
    }

  }

  private static final float SELECTED_TAB_SPACING_RATIO = 1.5f;

  private static final float MIN_TAB_SPACING_RATIO = 0.375f;

  private final int tabInset;

  private final int tabBorderWidth;

  private final int tabTitleContainerHeight;

  private final int stackedTabCount;

  private final int stackedTabSpacing;

  private final int maxCameraDistance;

  private final float swipedTabAlpha;

  private final float swipedTabScale;

  private final long showSwitcherAnimationDuration;

  private final long hideSwitcherAnimationDuration;

  private final long toolbarVisibilityAnimationDuration;

  private final long toolbarVisibilityAnimationDelay;

  private final long swipeAnimationDuration;

  private final long clearAnimationDelay;

  private final long relocateAnimationDuration;

  private final long relocateAnimationDelay;

  private final long revertOvershootAnimationDuration;

  private final long revealAnimationDuration;

  private final long peekAnimationDuration;

  private final float maxStartOvershootAngle;

  private final float maxEndOvershootAngle;

  private PhoneDragHandler dragHandler;

  private ViewRecycler<Tab, Void> childViewRecycler;

  private PhoneRecyclerAdapter recyclerAdapter;

  private AttachedViewRecycler<TabItem, Integer> viewRecycler;

  private ViewGroup tabContainer;

  private Toolbar toolbar;

  private int tabViewBottomMargin;

  private ViewPropertyAnimator toolbarAnimation;

  private int firstVisibleIndex;

  private void adaptLogLevel() {
    viewRecycler.setLogLevel(getModel().getLogLevel());
    childViewRecycler.setLogLevel(getModel().getLogLevel());
  }

  private void adaptDecorator() {
    childViewRecycler.setAdapter(getModel().getChildRecyclerAdapter());
    recyclerAdapter.clearCachedPreviews();
  }

  private void adaptToolbarMargin() {
    FrameLayout.LayoutParams layoutParams =
      (FrameLayout.LayoutParams) toolbar.getLayoutParams();
    layoutParams.setMargins(getModel().getPaddingLeft(), getModel().getPaddingTop(),
      getModel().getPaddingRight(), 0);
  }

  private void calculatePositionsWhenDraggingToEnd(final float dragDistance) {
    firstVisibleIndex = -1;
    AbstractTabItemIterator iterator =
      new TabItemIterator.Builder(getTabSwitcher(), viewRecycler)
        .start(Math.max(0, firstVisibleIndex)).create();
    TabItem tabItem;
    boolean abort = false;

    while ((tabItem = iterator.next()) != null && !abort) {
      if (getTabSwitcher().getCount() - tabItem.getIndex() > 1) {
        abort = calculatePositionWhenDraggingToEnd(dragDistance, tabItem,
          iterator.previous());

        if (firstVisibleIndex == -1 && tabItem.getTag().getState() == State.FLOATING) {
          firstVisibleIndex = tabItem.getIndex();
        }
      } else {
        Pair<Float, State> pair =
          clipTabPosition(getTabSwitcher().getCount(), tabItem.getIndex(),
            tabItem.getTag().getPosition(), iterator.previous());
        tabItem.getTag().setPosition(pair.first);
        tabItem.getTag().setState(pair.second);
      }

      inflateOrRemoveView(tabItem);
    }
  }

  private boolean calculatePositionWhenDraggingToEnd(final float dragDistance,
                                                     @NonNull final TabItem tabItem,
                                                     @Nullable final TabItem predecessor) {
    if (predecessor == null || predecessor.getTag().getState() != State.FLOATING) {
      if ((tabItem.getTag().getState() == State.STACKED_START_ATOP &&
        tabItem.getIndex() == 0) || tabItem.getTag().getState() == State.FLOATING) {
        float currentPosition = tabItem.getTag().getPosition();
        float thresholdPosition = calculateEndPosition(tabItem.getIndex());
        float newPosition = Math.min(currentPosition + dragDistance, thresholdPosition);
        Pair<Float, State> pair =
          clipTabPosition(getTabSwitcher().getCount(), tabItem.getIndex(),
            newPosition, predecessor);
        tabItem.getTag().setPosition(pair.first);
        tabItem.getTag().setState(pair.second);
      } else if (tabItem.getTag().getState() == State.STACKED_START_ATOP) {
        return true;
      }
    } else {
      float thresholdPosition = calculateEndPosition(tabItem.getIndex());
      float newPosition =
        Math.min(calculateNonLinearPosition(tabItem, predecessor), thresholdPosition);
      Pair<Float, State> pair =
        clipTabPosition(getTabSwitcher().getCount(), tabItem.getIndex(), newPosition,
          predecessor);
      tabItem.getTag().setPosition(pair.first);
      tabItem.getTag().setState(pair.second);
    }

    return false;
  }

  private void calculatePositionsWhenDraggingToStart(final float dragDistance) {
    AbstractTabItemIterator iterator =
      new TabItemIterator.Builder(getTabSwitcher(), viewRecycler)
        .start(Math.max(0, firstVisibleIndex)).create();
    TabItem tabItem;
    boolean abort = false;

    while ((tabItem = iterator.next()) != null && !abort) {
      if (getTabSwitcher().getCount() - tabItem.getIndex() > 1) {
        abort = calculatePositionWhenDraggingToStart(dragDistance, tabItem,
          iterator.previous());
      } else {
        Pair<Float, State> pair =
          clipTabPosition(getTabSwitcher().getCount(), tabItem.getIndex(),
            tabItem.getTag().getPosition(), iterator.previous());
        tabItem.getTag().setPosition(pair.first);
        tabItem.getTag().setState(pair.second);
      }

      inflateOrRemoveView(tabItem);
    }

    if (firstVisibleIndex > 0) {
      int start = firstVisibleIndex - 1;
      iterator = new TabItemIterator.Builder(getTabSwitcher(), viewRecycler).reverse(true)
        .start(start).create();

      while ((tabItem = iterator.next()) != null) {
        TabItem predecessor = iterator.previous();
        float predecessorPosition = predecessor.getTag().getPosition();

        if (tabItem.getIndex() < start) {
          Pair<Float, State> pair =
            clipTabPosition(getTabSwitcher().getCount(), predecessor.getIndex(),
              predecessorPosition, tabItem);
          predecessor.getTag().setPosition(pair.first);
          predecessor.getTag().setState(pair.second);
          inflateOrRemoveView(predecessor);

          if (predecessor.getTag().getState() == State.FLOATING) {
            firstVisibleIndex = predecessor.getIndex();
          } else {
            break;
          }
        }

        float newPosition = predecessorPosition +
          calculateMaxTabSpacing(getTabSwitcher().getCount(), predecessor);
        tabItem.getTag().setPosition(newPosition);

        if (!iterator.hasNext()) {
          Pair<Float, State> pair =
            clipTabPosition(getTabSwitcher().getCount(), tabItem.getIndex(),
              newPosition, (TabItem) null);
          tabItem.getTag().setPosition(pair.first);
          tabItem.getTag().setState(pair.second);
          inflateOrRemoveView(tabItem);

          if (tabItem.getTag().getState() == State.FLOATING) {
            firstVisibleIndex = tabItem.getIndex();
          }
        }
      }
    }
  }

  private boolean calculatePositionWhenDraggingToStart(final float dragDistance,
                                                       @NonNull final TabItem tabItem,
                                                       @Nullable final TabItem predecessor) {
    if (predecessor == null || predecessor.getTag().getState() != State.FLOATING ||
      predecessor.getTag().getPosition() >
        calculateAttachedPosition(getTabSwitcher().getCount())) {
      if (tabItem.getTag().getState() == State.FLOATING) {
        float currentPosition = tabItem.getTag().getPosition();
        float newPosition = currentPosition + dragDistance;
        Pair<Float, State> pair =
          clipTabPosition(getTabSwitcher().getCount(), tabItem.getIndex(),
            newPosition, predecessor);
        tabItem.getTag().setPosition(pair.first);
        tabItem.getTag().setState(pair.second);
      } else if (tabItem.getTag().getState() == State.STACKED_START_ATOP) {
        float currentPosition = tabItem.getTag().getPosition();
        Pair<Float, State> pair =
          clipTabPosition(getTabSwitcher().getCount(), tabItem.getIndex(),
            currentPosition, predecessor);
        tabItem.getTag().setPosition(pair.first);
        tabItem.getTag().setState(pair.second);
        return true;
      } else if (tabItem.getTag().getState() == State.HIDDEN ||
        tabItem.getTag().getState() == State.STACKED_START) {
        return true;
      }
    } else {
      float newPosition = calculateNonLinearPosition(tabItem, predecessor);
      Pair<Float, State> pair =
        clipTabPosition(getTabSwitcher().getCount(), tabItem.getIndex(), newPosition,
          predecessor);
      tabItem.getTag().setPosition(pair.first);
      tabItem.getTag().setState(pair.second);
    }

    return false;
  }

  private float calculateNonLinearPosition(@NonNull final TabItem tabItem,
                                           @NonNull final TabItem predecessor) {
    float predecessorPosition = predecessor.getTag().getPosition();
    float maxTabSpacing = calculateMaxTabSpacing(getTabSwitcher().getCount(), tabItem);
    return calculateNonLinearPosition(predecessorPosition, maxTabSpacing);
  }

  private float calculateNonLinearPosition(final float predecessorPosition,
                                           final float maxTabSpacing) {
    float ratio = Math.min(1,
      predecessorPosition / calculateAttachedPosition(getTabSwitcher().getCount()));
    float minTabSpacing = calculateMinTabSpacing(getTabSwitcher().getCount());
    return predecessorPosition - minTabSpacing - (ratio * (maxTabSpacing - minTabSpacing));
  }

  private float calculateEndPosition(final int index) {
    float defaultMaxTabSpacing = calculateMaxTabSpacing(getTabSwitcher().getCount(), null);
    int selectedTabIndex = getTabSwitcher().getSelectedTabIndex();

    if (selectedTabIndex > index) {
      AbstractTabItemIterator iterator =
        new TabItemIterator.Builder(getTabSwitcher(), viewRecycler).create();
      TabItem selectedTabItem = iterator.getItem(selectedTabIndex);
      float selectedTabSpacing =
        calculateMaxTabSpacing(getTabSwitcher().getCount(), selectedTabItem);
      return (getTabSwitcher().getCount() - 2 - index) * defaultMaxTabSpacing +
        selectedTabSpacing;
    }

    return (getTabSwitcher().getCount() - 1 - index) * defaultMaxTabSpacing;
  }

  private float calculateSwipePosition() {
    return getArithmetics().getTabContainerSize(Axis.ORTHOGONAL_AXIS, true);
  }

  private float calculateMaxTabSpacing(final int count, @Nullable final TabItem tabItem) {
    float totalSpace = getArithmetics().getTabContainerSize(Axis.DRAGGING_AXIS, false);
    float maxTabSpacing;

    if (count <= 2) {
      maxTabSpacing = totalSpace * 0.66f;
    } else if (count == 3) {
      maxTabSpacing = totalSpace * 0.33f;
    } else if (count == 4) {
      maxTabSpacing = totalSpace * 0.3f;
    } else {
      maxTabSpacing = totalSpace * 0.25f;
    }

    return count > 4 && tabItem != null &&
      tabItem.getTab() == getTabSwitcher().getSelectedTab() ?
      maxTabSpacing * SELECTED_TAB_SPACING_RATIO : maxTabSpacing;
  }

  private float calculateMinTabSpacing(final int count) {
    return calculateMaxTabSpacing(count, null) * MIN_TAB_SPACING_RATIO;
  }

  private float calculateAttachedPosition(final int count) {
    float totalSpace = getArithmetics().getTabContainerSize(Axis.DRAGGING_AXIS, false);
    float attachedPosition;

    if (count == 3) {
      attachedPosition = totalSpace * 0.66f;
    } else if (count == 4) {
      attachedPosition = totalSpace * 0.6f;
    } else {
      attachedPosition = totalSpace * 0.5f;
    }

    return attachedPosition;
  }

  @NonNull
  private Pair<Float, State> clipTabPosition(final int count, final int index,
                                             final float position,
                                             @Nullable final TabItem predecessor) {
    return clipTabPosition(count, index, position,
      predecessor != null ? predecessor.getTag().getState() : null);
  }

  private Pair<Float, State> clipTabPosition(final int count, final int index,
                                             final float position,
                                             @Nullable final State predecessorState) {
    Pair<Float, State> startPair =
      calculatePositionAndStateWhenStackedAtStart(count, index, predecessorState);
    float startPosition = startPair.first;

    if (position <= startPosition) {
      State state = startPair.second;
      return Pair.create(startPosition, state);
    } else {
      Pair<Float, State> endPair = calculatePositionAndStateWhenStackedAtEnd(index);
      float endPosition = endPair.first;

      if (position >= endPosition) {
        State state = endPair.second;
        return Pair.create(endPosition, state);
      } else {
        State state = State.FLOATING;
        return Pair.create(position, state);
      }
    }
  }

  @NonNull
  private Pair<Float, State> calculatePositionAndStateWhenStackedAtStart(final int count,
                                                                         final int index,
                                                                         @Nullable final TabItem predecessor) {
    return calculatePositionAndStateWhenStackedAtStart(count, index,
      predecessor != null ? predecessor.getTag().getState() : null);
  }

  @NonNull
  private Pair<Float, State> calculatePositionAndStateWhenStackedAtStart(final int count,
                                                                         final int index,
                                                                         @Nullable final State predecessorState) {
    if ((count - index) <= stackedTabCount) {
      float position = stackedTabSpacing * (count - (index + 1));
      return Pair.create(position,
        (predecessorState == null || predecessorState == State.FLOATING) ?
          State.STACKED_START_ATOP : State.STACKED_START);
    } else {
      float position = stackedTabSpacing * stackedTabCount;
      return Pair.create(position,
        (predecessorState == null || predecessorState == State.FLOATING) ?
          State.STACKED_START_ATOP : State.HIDDEN);
    }
  }

  @NonNull
  private Pair<Float, State> calculatePositionAndStateWhenStackedAtEnd(final int index) {
    float size = getArithmetics().getTabContainerSize(Axis.DRAGGING_AXIS, false);

    if (index < stackedTabCount) {
      float position = size - tabInset - (stackedTabSpacing * (index + 1));
      return Pair.create(position, State.STACKED_END);
    } else {
      float position = size - tabInset - (stackedTabSpacing * stackedTabCount);
      return Pair.create(position, State.HIDDEN);
    }
  }

  private boolean isOvershootingAtStart() {
    if (getTabSwitcher().getCount() <= 1) {
      return true;
    } else {
      AbstractTabItemIterator iterator =
        new TabItemIterator.Builder(getTabSwitcher(), viewRecycler).create();
      TabItem tabItem = iterator.getItem(0);
      return tabItem.getTag().getState() == State.STACKED_START_ATOP;
    }
  }

  private boolean isOvershootingAtEnd(@NonNull final AbstractTabItemIterator iterator) {
    if (getTabSwitcher().getCount() <= 1) {
      return true;
    } else {
      TabItem lastTabItem = iterator.getItem(getTabSwitcher().getCount() - 1);
      TabItem predecessor = iterator.getItem(getTabSwitcher().getCount() - 2);
      return Math.round(predecessor.getTag().getPosition()) >=
        Math.round(calculateMaxTabSpacing(getTabSwitcher().getCount(), lastTabItem));
    }
  }

  private int calculateBottomMargin(@NonNull final View view) {
    float tabHeight = (view.getHeight() - 2 * tabInset) * getArithmetics().getScale(view, true);
    float containerHeight = getArithmetics().getTabContainerSize(Axis.Y_AXIS, false);
    int stackHeight = getTabSwitcher().getLayout() == Layout.PHONE_LANDSCAPE ? 0 :
      stackedTabCount * stackedTabSpacing;
    return Math.round(tabHeight + tabInset + stackHeight - containerHeight);
  }

  private void animateBottomMargin(@NonNull final View view, final int margin,
                                   final long duration, final long delay) {
    FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) view.getLayoutParams();
    final int initialMargin = layoutParams.bottomMargin;
    ValueAnimator animation = ValueAnimator.ofInt(margin - initialMargin);
    animation.setDuration(duration);
    animation.addListener(new AnimationListenerWrapper(null));
    animation.setInterpolator(new AccelerateDecelerateInterpolator());
    animation.setStartDelay(delay);
    animation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {

      @Override
      public void onAnimationUpdate(ValueAnimator animation) {
        FrameLayout.LayoutParams layoutParams =
          (FrameLayout.LayoutParams) view.getLayoutParams();
        layoutParams.bottomMargin = initialMargin + (int) animation.getAnimatedValue();
        view.setLayoutParams(layoutParams);
      }

    });

    animation.start();
  }

  private void animateToolbarVisibility(final boolean visible, final long delay) {
    if (toolbarAnimation != null) {
      toolbarAnimation.cancel();
    }

    float targetAlpha = visible ? 1 : 0;

    if (toolbar.getAlpha() != targetAlpha) {
      toolbarAnimation = toolbar.animate();
      toolbarAnimation.setInterpolator(new AccelerateDecelerateInterpolator());
      toolbarAnimation.setDuration(toolbarVisibilityAnimationDuration);
      toolbarAnimation.setStartDelay(delay);
      toolbarAnimation.alpha(targetAlpha);
      toolbarAnimation.start();
    }
  }

  private void animateShowSwitcher() {
    TabItem[] tabItems = calculateInitialTabItems(-1, -1);
    AbstractTabItemIterator iterator = new InitialTabItemIterator(tabItems, false, 0);
    TabItem tabItem;

    while ((tabItem = iterator.next()) != null) {
      if (tabItem.getTab() == getModel().getSelectedTab() || tabItem.isVisible()) {
        viewRecycler.inflate(tabItem);
        View view = tabItem.getView();

        if (!ViewCompat.isLaidOut(view)) {
          view.getViewTreeObserver().addOnGlobalLayoutListener(
            new LayoutListenerWrapper(view,
              createShowSwitcherLayoutListener(tabItem)));
        } else {
          animateShowSwitcher(tabItem, createUpdateViewAnimationListener(tabItem));
        }
      }
    }

    animateToolbarVisibility(getModel().areToolbarsShown(), toolbarVisibilityAnimationDelay);
  }

  @NonNull
  private TabItem[] calculateInitialTabItems(final int firstVisibleTabIndex,
                                             final float firstVisibleTabPosition) {
    dragHandler.reset(getDragThreshold());
    firstVisibleIndex = -1;
    TabItem[] tabItems = new TabItem[getModel().getCount()];

    if (!getModel().isEmpty()) {
      int selectedTabIndex = getModel().getSelectedTabIndex();
      float attachedPosition = calculateAttachedPosition(getModel().getCount());
      int referenceIndex = firstVisibleTabIndex != -1 && firstVisibleTabPosition != -1 ?
        firstVisibleTabIndex : selectedTabIndex;
      float referencePosition = firstVisibleTabIndex != -1 && firstVisibleTabPosition != -1 ?
        firstVisibleTabPosition : attachedPosition;
      referencePosition = Math.min(calculateEndPosition(referenceIndex), referencePosition);
      AbstractTabItemIterator iterator =
        new InitialTabItemIterator(tabItems, false, referenceIndex);
      TabItem tabItem;

      while ((tabItem = iterator.next()) != null) {
        TabItem predecessor = iterator.previous();
        float position;

        if (tabItem.getIndex() == getModel().getCount() - 1) {
          position = 0;
        } else if (tabItem.getIndex() == referenceIndex) {
          position = referencePosition;
        } else {
          position = calculateNonLinearPosition(tabItem, predecessor);
        }

        Pair<Float, State> pair =
          clipTabPosition(getModel().getCount(), tabItem.getIndex(), position,
            predecessor);
        tabItem.getTag().setPosition(pair.first);
        tabItem.getTag().setState(pair.second);

        if (firstVisibleIndex == -1 && pair.second != State.STACKED_END &&
          pair.second != State.HIDDEN) {
          firstVisibleIndex = tabItem.getIndex();
        }

        if (pair.second == State.STACKED_START || pair.second == State.STACKED_START_ATOP) {
          break;
        }
      }

      boolean overshooting =
        referenceIndex == getModel().getCount() - 1 || isOvershootingAtEnd(iterator);
      iterator = new InitialTabItemIterator(tabItems, true, referenceIndex - 1);
      float minTabSpacing = calculateMinTabSpacing(getModel().getCount());
      float defaultTabSpacing = calculateMaxTabSpacing(getModel().getCount(), null);
      TabItem selectedTabItem =
        TabItem.create(getTabSwitcher(), viewRecycler, selectedTabIndex);
      float maxTabSpacing = calculateMaxTabSpacing(getModel().getCount(), selectedTabItem);
      TabItem currentReferenceTabItem = iterator.getItem(referenceIndex);

      while ((tabItem = iterator.next()) != null &&
        (overshooting || tabItem.getIndex() < referenceIndex)) {
        float currentTabSpacing =
          calculateMaxTabSpacing(getModel().getCount(), currentReferenceTabItem);
        TabItem predecessor = iterator.peek();
        Pair<Float, State> pair;

        if (overshooting) {
          float position;

          if (referenceIndex > tabItem.getIndex()) {
            position = maxTabSpacing +
              ((getModel().getCount() - 1 - tabItem.getIndex() - 1) *
                defaultTabSpacing);
          } else {
            position = (getModel().getCount() - 1 - tabItem.getIndex()) *
              defaultTabSpacing;
          }

          pair = clipTabPosition(getModel().getCount(), tabItem.getIndex(), position,
            predecessor);
        } else if (referencePosition >= attachedPosition - currentTabSpacing) {
          float position;

          if (selectedTabIndex > tabItem.getIndex() &&
            selectedTabIndex <= referenceIndex) {
            position = referencePosition + maxTabSpacing +
              ((referenceIndex - tabItem.getIndex() - 1) * defaultTabSpacing);
          } else {
            position = referencePosition +
              ((referenceIndex - tabItem.getIndex()) * defaultTabSpacing);
          }

          pair = clipTabPosition(getModel().getCount(), tabItem.getIndex(), position,
            predecessor);
        } else {
          TabItem successor = iterator.previous();
          float successorPosition = successor.getTag().getPosition();
          float position = (attachedPosition * (successorPosition + minTabSpacing)) /
            (minTabSpacing + attachedPosition - currentTabSpacing);
          pair = clipTabPosition(getModel().getCount(), tabItem.getIndex(), position,
            predecessor);

          if (pair.first >= attachedPosition - currentTabSpacing) {
            currentReferenceTabItem = tabItem;
            referencePosition = pair.first;
            referenceIndex = tabItem.getIndex();
          }
        }

        tabItem.getTag().setPosition(pair.first);
        tabItem.getTag().setState(pair.second);

        if ((firstVisibleIndex == -1 || firstVisibleIndex > tabItem.getIndex()) &&
          pair.second == State.FLOATING) {
          firstVisibleIndex = tabItem.getIndex();
        }
      }
    }

    dragHandler.setCallback(this);
    return tabItems;
  }

  private void addAllTabs(final int index, @NonNull final Tab[] tabs,
                          @NonNull final Animation animation) {
    if (tabs.length > 0) {
      if (getModel().isSwitcherShown()) {
        SwipeAnimation swipeAnimation =
          animation instanceof SwipeAnimation ? (SwipeAnimation) animation :
            new SwipeAnimation.Builder().create();
        TabItem[] tabItems = new TabItem[tabs.length];
        OnGlobalLayoutListener compoundListener = new CompoundLayoutListener(tabs.length,
          createSwipeLayoutListener(tabItems, swipeAnimation));

        for (int i = 0; i < tabs.length; i++) {
          Tab tab = tabs[i];
          TabItem tabItem = new TabItem(index + i, tab);
          tabItems[i] = tabItem;
          inflateView(tabItem, compoundListener);
        }
      } else if (!getModel().isSwitcherShown()) {
        toolbar.setAlpha(0);

        if (getModel().getSelectedTab() == tabs[0]) {
          TabItem tabItem = TabItem.create(getTabSwitcher(), viewRecycler, index);
          inflateView(tabItem, createAddSelectedTabLayoutListener(tabItem));
        }
      }
    }
  }

  private void animateShowSwitcher(@NonNull final TabItem tabItem,
                                   @Nullable final AnimatorListener listener) {
    animateShowSwitcher(tabItem, showSwitcherAnimationDuration,
      new AccelerateDecelerateInterpolator(), listener);
  }

  private void animateShowSwitcher(@NonNull final TabItem tabItem, final long duration,
                                   @NonNull final Interpolator interpolator,
                                   @Nullable final AnimatorListener listener) {
    View view = tabItem.getView();
    FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) view.getLayoutParams();
    view.setX(layoutParams.leftMargin);
    view.setY(layoutParams.topMargin);
    getArithmetics().setScale(Axis.DRAGGING_AXIS, view, 1);
    getArithmetics().setScale(Axis.ORTHOGONAL_AXIS, view, 1);
    getArithmetics().setPivot(Axis.DRAGGING_AXIS, view,
      getArithmetics().getPivot(Axis.DRAGGING_AXIS, view, DragState.NONE));
    getArithmetics().setPivot(Axis.ORTHOGONAL_AXIS, view,
      getArithmetics().getPivot(Axis.ORTHOGONAL_AXIS, view, DragState.NONE));
    float scale = getArithmetics().getScale(view, true);
    int selectedTabIndex = getModel().getSelectedTabIndex();

    if (tabItem.getIndex() < selectedTabIndex) {
      getArithmetics().setPosition(Axis.DRAGGING_AXIS, view,
        getArithmetics().getTabContainerSize(Axis.DRAGGING_AXIS));
    } else if (tabItem.getIndex() > selectedTabIndex) {
      getArithmetics().setPosition(Axis.DRAGGING_AXIS, view,
        getTabSwitcher().getLayout() == Layout.PHONE_LANDSCAPE ? 0 :
          layoutParams.topMargin);
    }

    if (tabViewBottomMargin == -1) {
      tabViewBottomMargin = calculateBottomMargin(view);
    }

    animateBottomMargin(view, tabViewBottomMargin, duration, 0);
    ViewPropertyAnimator animation = view.animate();
    animation.setDuration(duration);
    animation.setInterpolator(interpolator);
    animation.setListener(new AnimationListenerWrapper(listener));
    getArithmetics().animateScale(Axis.DRAGGING_AXIS, animation, scale);
    getArithmetics().animateScale(Axis.ORTHOGONAL_AXIS, animation, scale);
    getArithmetics().animatePosition(Axis.DRAGGING_AXIS, animation, view,
      tabItem.getTag().getPosition(), true);
    getArithmetics().animatePosition(Axis.ORTHOGONAL_AXIS, animation, view, 0, true);
    animation.setStartDelay(0);
    animation.start();
  }

  private void animateHideSwitcher() {
    dragHandler.setCallback(null);
    TabItemIterator iterator =
      new TabItemIterator.Builder(getTabSwitcher(), viewRecycler).create();
    TabItem tabItem;

    while ((tabItem = iterator.next()) != null) {
      if (tabItem.isInflated()) {
        animateHideSwitcher(tabItem,
          tabItem.getIndex() == getModel().getSelectedTabIndex() ?
            createHideSwitcherAnimationListener() : null);
      } else if (tabItem.getTab() == getModel().getSelectedTab()) {
        inflateAndUpdateView(tabItem, createHideSwitcherLayoutListener(tabItem));
      }
    }

    animateToolbarVisibility(getModel().areToolbarsShown() && getModel().isEmpty(), 0);
  }

  private void animateHideSwitcher(@NonNull final TabItem tabItem,
                                   @Nullable final AnimatorListener listener) {
    animateHideSwitcher(tabItem, hideSwitcherAnimationDuration,
      new AccelerateDecelerateInterpolator(), 0, listener);
  }

  private void animateHideSwitcher(@NonNull final TabItem tabItem, final long duration,
                                   @NonNull final Interpolator interpolator, final long delay,
                                   @Nullable final AnimatorListener listener) {
    View view = tabItem.getView();
    animateBottomMargin(view, -(tabInset + tabBorderWidth), duration, delay);
    ViewPropertyAnimator animation = view.animate();
    animation.setDuration(duration);
    animation.setInterpolator(interpolator);
    animation.setListener(new AnimationListenerWrapper(listener));
    getArithmetics().animateScale(Axis.DRAGGING_AXIS, animation, 1);
    getArithmetics().animateScale(Axis.ORTHOGONAL_AXIS, animation, 1);
    FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) view.getLayoutParams();
    getArithmetics().animatePosition(Axis.ORTHOGONAL_AXIS, animation, view,
      getTabSwitcher().getLayout() == Layout.PHONE_LANDSCAPE ? layoutParams.topMargin : 0,
      false);
    int selectedTabIndex = getModel().getSelectedTabIndex();

    if (tabItem.getIndex() < selectedTabIndex) {
      getArithmetics().animatePosition(Axis.DRAGGING_AXIS, animation, view,
        getArithmetics().getTabContainerSize(Axis.DRAGGING_AXIS), false);
    } else if (tabItem.getIndex() > selectedTabIndex) {
      getArithmetics().animatePosition(Axis.DRAGGING_AXIS, animation, view,
        getTabSwitcher().getLayout() == Layout.PHONE_LANDSCAPE ? 0 :
          layoutParams.topMargin, false);
    } else {
      getArithmetics().animatePosition(Axis.DRAGGING_AXIS, animation, view,
        getTabSwitcher().getLayout() == Layout.PHONE_LANDSCAPE ? 0 :
          layoutParams.topMargin, false);
    }

    animation.setStartDelay(delay);
    animation.start();
  }

  private void animateSwipe(@NonNull final TabItem tabItem, final boolean remove,
                            final long delay, @NonNull final SwipeAnimation swipeAnimation,
                            @Nullable final AnimatorListener listener) {
    View view = tabItem.getView();
    float currentScale = getArithmetics().getScale(view, true);
    float swipePosition = calculateSwipePosition();
    float targetPosition = remove ?
      (swipeAnimation.getDirection() == SwipeDirection.LEFT ? -1 * swipePosition :
        swipePosition) : 0;
    float currentPosition = getArithmetics().getPosition(Axis.ORTHOGONAL_AXIS, view);
    float distance = Math.abs(targetPosition - currentPosition);
    long animationDuration = swipeAnimation.getDuration() != -1 ? swipeAnimation.getDuration() :
      Math.round(swipeAnimationDuration * (distance / swipePosition));
    ViewPropertyAnimator animation = view.animate();
    animation.setInterpolator(
      swipeAnimation.getInterpolator() != null ? swipeAnimation.getInterpolator() :
        new AccelerateDecelerateInterpolator());
    animation.setListener(new AnimationListenerWrapper(listener));
    animation.setDuration(animationDuration);
    getArithmetics()
      .animatePosition(Axis.ORTHOGONAL_AXIS, animation, view, targetPosition, true);
    getArithmetics().animateScale(Axis.ORTHOGONAL_AXIS, animation,
      remove ? swipedTabScale * currentScale : currentScale);
    getArithmetics().animateScale(Axis.DRAGGING_AXIS, animation,
      remove ? swipedTabScale * currentScale : currentScale);
    animation.alpha(remove ? swipedTabAlpha : 1);
    animation.setStartDelay(delay);
    animation.start();
  }

  private void animateRemove(@NonNull final TabItem removedTabItem,
                             @NonNull final SwipeAnimation swipeAnimation) {
    View view = removedTabItem.getView();
    getArithmetics().setPivot(Axis.DRAGGING_AXIS, view,
      getArithmetics().getPivot(Axis.DRAGGING_AXIS, view, DragState.SWIPE));
    getArithmetics().setPivot(Axis.ORTHOGONAL_AXIS, view,
      getArithmetics().getPivot(Axis.ORTHOGONAL_AXIS, view, DragState.SWIPE));
    animateSwipe(removedTabItem, true, 0, swipeAnimation,
      createRemoveAnimationListener(removedTabItem));
  }

  private void animateRelocate(@NonNull final TabItem tabItem, final float position,
                               @Nullable final Tag tag, final long delay,
                               @Nullable final AnimatorListener listener) {
    if (tag != null) {
      tabItem.getView().setTag(R.id.tag_properties, tag);
      tabItem.setTag(tag);
    }

    View view = tabItem.getView();
    ViewPropertyAnimator animation = view.animate();
    animation.setListener(new AnimationListenerWrapper(listener));
    animation.setInterpolator(new AccelerateDecelerateInterpolator());
    animation.setDuration(relocateAnimationDuration);
    getArithmetics().animatePosition(Axis.DRAGGING_AXIS, animation, view, position, true);
    animation.setStartDelay(delay);
    animation.start();
  }

  private void animateRevertStartOvershoot() {
    boolean tilted = animateTilt(new AccelerateInterpolator(), maxStartOvershootAngle,
      createRevertStartOvershootAnimationListener());

    if (!tilted) {
      animateRevertStartOvershoot(new AccelerateDecelerateInterpolator());
    }
  }

  private void animateRevertStartOvershoot(@NonNull final Interpolator interpolator) {
    TabItem tabItem = TabItem.create(getTabSwitcher(), viewRecycler, 0);
    View view = tabItem.getView();
    getArithmetics().setPivot(Axis.DRAGGING_AXIS, view,
      getArithmetics().getPivot(Axis.DRAGGING_AXIS, view, DragState.NONE));
    getArithmetics().setPivot(Axis.ORTHOGONAL_AXIS, view,
      getArithmetics().getPivot(Axis.ORTHOGONAL_AXIS, view, DragState.NONE));
    float position = getArithmetics().getPosition(Axis.DRAGGING_AXIS, view);
    float targetPosition = tabItem.getTag().getPosition();
    final float startPosition = getArithmetics().getPosition(Axis.DRAGGING_AXIS, view);
    ValueAnimator animation = ValueAnimator.ofFloat(targetPosition - position);
    animation.setDuration(Math.round(revertOvershootAnimationDuration * Math.abs(
      (targetPosition - position) / (float) (stackedTabCount * stackedTabSpacing))));
    animation.addListener(new AnimationListenerWrapper(null));
    animation.setInterpolator(interpolator);
    animation.setStartDelay(0);
    animation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {

      @Override
      public void onAnimationUpdate(final ValueAnimator animation) {
        TabItemIterator iterator =
          new TabItemIterator.Builder(getTabSwitcher(), viewRecycler).create();
        TabItem tabItem;

        while ((tabItem = iterator.next()) != null) {
          if (tabItem.getIndex() == 0) {
            View view = tabItem.getView();
            getArithmetics().setPosition(Axis.DRAGGING_AXIS, view,
              startPosition + (float) animation.getAnimatedValue());
          } else if (tabItem.isInflated()) {
            View firstView = iterator.first().getView();
            View view = tabItem.getView();
            view.setVisibility(
              getArithmetics().getPosition(Axis.DRAGGING_AXIS, firstView) <=
                getArithmetics().getPosition(Axis.DRAGGING_AXIS, view) ?
                View.INVISIBLE : View.VISIBLE);
          }
        }
      }

    });

    animation.start();
  }

  private void animateRevertEndOvershoot() {
    animateTilt(new AccelerateDecelerateInterpolator(), maxEndOvershootAngle, null);
  }

  private boolean animateTilt(@NonNull final Interpolator interpolator, final float maxAngle,
                              @Nullable final AnimatorListener listener) {
    TabItemIterator iterator =
      new TabItemIterator.Builder(getTabSwitcher(), viewRecycler).reverse(true).create();
    TabItem tabItem;
    boolean result = false;

    while ((tabItem = iterator.next()) != null) {
      if (tabItem.isInflated()) {
        View view = tabItem.getView();

        if (getArithmetics().getRotation(Axis.ORTHOGONAL_AXIS, view) != 0) {
          ViewPropertyAnimator animation = view.animate();
          animation.setListener(new AnimationListenerWrapper(
            createRevertOvershootAnimationListener(view,
              !result ? listener : null)));
          animation.setDuration(Math.round(revertOvershootAnimationDuration *
            (Math.abs(getArithmetics().getRotation(Axis.ORTHOGONAL_AXIS, view)) /
              maxAngle)));
          animation.setInterpolator(interpolator);
          getArithmetics().animateRotation(Axis.ORTHOGONAL_AXIS, animation, 0);
          animation.setStartDelay(0);
          animation.start();
          result = true;
        }
      }
    }

    return result;
  }

  private void animateReveal(@NonNull final TabItem tabItem,
                             @NonNull final RevealAnimation revealAnimation) {
    tabViewBottomMargin = -1;
    recyclerAdapter.clearCachedPreviews();
    dragHandler.setCallback(null);
    View view = tabItem.getView();
    ViewPropertyAnimator animation = view.animate();
    animation.setInterpolator(
      revealAnimation.getInterpolator() != null ? revealAnimation.getInterpolator() :
        new AccelerateDecelerateInterpolator());
    animation.setListener(new AnimationListenerWrapper(createHideSwitcherAnimationListener()));
    animation.setStartDelay(0);
    animation.setDuration(revealAnimation.getDuration() != -1 ? revealAnimation.getDuration() :
      revealAnimationDuration);
    getArithmetics().animateScale(Axis.DRAGGING_AXIS, animation, 1);
    getArithmetics().animateScale(Axis.ORTHOGONAL_AXIS, animation, 1);
    animation.start();
    animateToolbarVisibility(getModel().areToolbarsShown() && getModel().isEmpty(), 0);
  }

  private void animatePeek(@NonNull final TabItem tabItem, final long duration,
                           @NonNull final Interpolator interpolator, final float peekPosition,
                           @NonNull final PeekAnimation peekAnimation) {
    PhoneTabViewHolder viewHolder = tabItem.getViewHolder();
    viewHolder.closeButton.setVisibility(View.GONE);
    View view = tabItem.getView();
    float x = peekAnimation.getX();
    float y = peekAnimation.getY() + tabTitleContainerHeight;
    FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) view.getLayoutParams();
    view.setAlpha(1f);
    getArithmetics().setPivot(Axis.X_AXIS, view, x);
    getArithmetics().setPivot(Axis.Y_AXIS, view, y);
    view.setX(layoutParams.leftMargin);
    view.setY(layoutParams.topMargin);
    getArithmetics().setScale(Axis.DRAGGING_AXIS, view, 0);
    getArithmetics().setScale(Axis.ORTHOGONAL_AXIS, view, 0);
    ViewPropertyAnimator animation = view.animate();
    animation.setInterpolator(interpolator);
    animation.setListener(
      new AnimationListenerWrapper(createPeekAnimationListener(tabItem, peekAnimation)));
    animation.setStartDelay(0);
    animation.setDuration(duration);
    getArithmetics().animateScale(Axis.DRAGGING_AXIS, animation, 1);
    getArithmetics().animateScale(Axis.ORTHOGONAL_AXIS, animation, 1);
    getArithmetics().animatePosition(Axis.DRAGGING_AXIS, animation, view, peekPosition, true);
    animation.start();
    int selectedTabIndex = getModel().getSelectedTabIndex();
    TabItem selectedTabItem = TabItem.create(getModel(), viewRecycler, selectedTabIndex);
    viewRecycler.inflate(selectedTabItem);
    selectedTabItem.getTag().setPosition(0);
    PhoneTabViewHolder selectedTabViewHolder = selectedTabItem.getViewHolder();
    selectedTabViewHolder.closeButton.setVisibility(View.GONE);
    animateShowSwitcher(selectedTabItem, duration, interpolator,
      createZoomOutAnimationListener(selectedTabItem, peekAnimation));
  }

  @NonNull
  private OnGlobalLayoutListener createShowSwitcherLayoutListener(
    @NonNull final TabItem tabItem) {
    return new OnGlobalLayoutListener() {

      @Override
      public void onGlobalLayout() {
        animateShowSwitcher(tabItem, createUpdateViewAnimationListener(tabItem));
      }

    };
  }

  @NonNull
  private OnGlobalLayoutListener createHideSwitcherLayoutListener(
    @NonNull final TabItem tabItem) {
    return new OnGlobalLayoutListener() {

      @Override
      public void onGlobalLayout() {
        animateHideSwitcher(tabItem,
          tabItem.getIndex() == getModel().getSelectedTabIndex() ?
            createHideSwitcherAnimationListener() : null);
      }

    };
  }

  @NonNull
  private OnGlobalLayoutListener createRemoveLayoutListener(@NonNull final TabItem tabItem,
                                                            @NonNull final SwipeAnimation swipeAnimation) {
    return new OnGlobalLayoutListener() {

      @Override
      public void onGlobalLayout() {
        animateRemove(tabItem, swipeAnimation);
      }

    };
  }

  @NonNull
  private OnGlobalLayoutListener createRelocateLayoutListener(@NonNull final TabItem tabItem,
                                                              final float position,
                                                              @Nullable final Tag tag,
                                                              final long delay,
                                                              @Nullable final AnimatorListener listener) {
    return new OnGlobalLayoutListener() {

      @Override
      public void onGlobalLayout() {
        animateRelocate(tabItem, position, tag, delay, listener);
      }

    };
  }

  @NonNull
  private OnGlobalLayoutListener createAddSelectedTabLayoutListener(
    @NonNull final TabItem tabItem) {
    return new OnGlobalLayoutListener() {

      @Override
      public void onGlobalLayout() {
        View view = tabItem.getView();
        FrameLayout.LayoutParams layoutParams =
          (FrameLayout.LayoutParams) view.getLayoutParams();
        view.setAlpha(1f);
        getArithmetics().setPivot(Axis.DRAGGING_AXIS, view,
          getArithmetics().getPivot(Axis.DRAGGING_AXIS, view, DragState.NONE));
        getArithmetics().setPivot(Axis.ORTHOGONAL_AXIS, view,
          getArithmetics().getPivot(Axis.ORTHOGONAL_AXIS, view, DragState.NONE));
        view.setX(layoutParams.leftMargin);
        view.setY(layoutParams.topMargin);
        getArithmetics().setScale(Axis.DRAGGING_AXIS, view, 1);
        getArithmetics().setScale(Axis.ORTHOGONAL_AXIS, view, 1);
      }

    };
  }

  @NonNull
  private OnGlobalLayoutListener createRevealLayoutListener(@NonNull final TabItem tabItem,
                                                            @NonNull final RevealAnimation revealAnimation) {
    return new OnGlobalLayoutListener() {

      @Override
      public void onGlobalLayout() {
        View view = tabItem.getView();
        float x = revealAnimation.getX();
        float y = revealAnimation.getY() + tabTitleContainerHeight;
        FrameLayout.LayoutParams layoutParams =
          (FrameLayout.LayoutParams) view.getLayoutParams();
        view.setAlpha(1f);
        getArithmetics().setPivot(Axis.X_AXIS, view, x);
        getArithmetics().setPivot(Axis.Y_AXIS, view, y);
        view.setX(layoutParams.leftMargin);
        view.setY(layoutParams.topMargin);
        getArithmetics().setScale(Axis.DRAGGING_AXIS, view, 0);
        getArithmetics().setScale(Axis.ORTHOGONAL_AXIS, view, 0);
        animateReveal(tabItem, revealAnimation);
      }

    };
  }

  private OnGlobalLayoutListener createPeekLayoutListener(@NonNull final TabItem tabItem,
                                                          @NonNull final PeekAnimation peekAnimation) {
    return new OnGlobalLayoutListener() {

      @Override
      public void onGlobalLayout() {
        long totalDuration =
          peekAnimation.getDuration() != -1 ? peekAnimation.getDuration() :
            peekAnimationDuration;
        long duration = totalDuration / 3;
        Interpolator interpolator =
          peekAnimation.getInterpolator() != null ? peekAnimation.getInterpolator() :
            new AccelerateDecelerateInterpolator();
        float peekPosition =
          getArithmetics().getTabContainerSize(Axis.DRAGGING_AXIS, false) * 0.66f;
        animatePeek(tabItem, duration, interpolator, peekPosition, peekAnimation);
      }

    };
  }

  @NonNull
  private OnGlobalLayoutListener createSwipeLayoutListener(@NonNull final TabItem[] addedTabItems,
                                                           @NonNull final SwipeAnimation swipeAnimation) {
    return new OnGlobalLayoutListener() {

      @Override
      public void onGlobalLayout() {
        int count = getModel().getCount();
        float previousAttachedPosition =
          calculateAttachedPosition(count - addedTabItems.length);
        float attachedPosition = calculateAttachedPosition(count);
        TabItem[] tabItems;

        if (count - addedTabItems.length == 0) {
          tabItems = calculateInitialTabItems(-1, -1);
        } else {
          TabItem firstAddedTabItem = addedTabItems[0];
          int index = firstAddedTabItem.getIndex();
          boolean isReferencingPredecessor = index > 0;
          int referenceIndex = isReferencingPredecessor ? index - 1 :
            (index + addedTabItems.length - 1 < count - 1 ?
              index + addedTabItems.length : -1);
          TabItem referenceTabItem = referenceIndex != -1 ?
            TabItem.create(getTabSwitcher(), viewRecycler, referenceIndex) : null;
          State state =
            referenceTabItem != null ? referenceTabItem.getTag().getState() : null;

          if (state == null || state == State.STACKED_START) {
            tabItems = relocateWhenAddingStackedTabs(true, addedTabItems);
          } else if (state == State.STACKED_END) {
            tabItems = relocateWhenAddingStackedTabs(false, addedTabItems);
          } else if (state == State.FLOATING ||
            (state == State.STACKED_START_ATOP && (index > 0 || count <= 2))) {
            tabItems = relocateWhenAddingFloatingTabs(addedTabItems, referenceTabItem,
              isReferencingPredecessor, attachedPosition,
              attachedPosition != previousAttachedPosition);
          } else {
            tabItems = relocateWhenAddingHiddenTabs(addedTabItems, referenceTabItem);
          }
        }

        Tag previousTag = null;

        for (TabItem tabItem : tabItems) {
          Tag tag = tabItem.getTag();

          if (previousTag == null || tag.getPosition() != previousTag.getPosition()) {
            createBottomMarginLayoutListener(tabItem).onGlobalLayout();
            View view = tabItem.getView();
            view.setTag(R.id.tag_properties, tag);
            view.setAlpha(swipedTabAlpha);
            float swipePosition = calculateSwipePosition();
            float scale = getArithmetics().getScale(view, true);
            getArithmetics().setPivot(Axis.DRAGGING_AXIS, view, getArithmetics()
              .getPivot(Axis.DRAGGING_AXIS, view, DragState.NONE));
            getArithmetics().setPivot(Axis.ORTHOGONAL_AXIS, view, getArithmetics()
              .getPivot(Axis.ORTHOGONAL_AXIS, view, DragState.NONE));
            getArithmetics().setPosition(Axis.DRAGGING_AXIS, view, tag.getPosition());
            getArithmetics().setPosition(Axis.ORTHOGONAL_AXIS, view,
              swipeAnimation.getDirection() == SwipeDirection.LEFT ?
                -1 * swipePosition : swipePosition);
            getArithmetics().setScale(Axis.DRAGGING_AXIS, view, scale);
            getArithmetics().setScale(Axis.ORTHOGONAL_AXIS, view, scale);
            getArithmetics().setPivot(Axis.DRAGGING_AXIS, view, getArithmetics()
              .getPivot(Axis.DRAGGING_AXIS, view, DragState.SWIPE));
            getArithmetics().setPivot(Axis.ORTHOGONAL_AXIS, view, getArithmetics()
              .getPivot(Axis.ORTHOGONAL_AXIS, view, DragState.SWIPE));
            getArithmetics().setScale(Axis.DRAGGING_AXIS, view, swipedTabScale * scale);
            getArithmetics()
              .setScale(Axis.ORTHOGONAL_AXIS, view, swipedTabScale * scale);
            animateSwipe(tabItem, false, 0, swipeAnimation,
              createSwipeAnimationListener(tabItem));
          } else {
            viewRecycler.remove(tabItem);
          }

          previousTag = tag;
        }
      }

    };
  }

  private OnGlobalLayoutListener createBottomMarginLayoutListener(
    @NonNull final TabItem tabItem) {
    return new OnGlobalLayoutListener() {

      @Override
      public void onGlobalLayout() {
        View view = tabItem.getView();

        if (tabViewBottomMargin == -1) {
          tabViewBottomMargin = calculateBottomMargin(view);
        }

        FrameLayout.LayoutParams layoutParams =
          (FrameLayout.LayoutParams) view.getLayoutParams();
        layoutParams.bottomMargin = tabViewBottomMargin;
        view.setLayoutParams(layoutParams);
      }

    };
  }

  @NonNull
  private OnGlobalLayoutListener createInflateViewLayoutListener(@NonNull final TabItem tabItem,
                                                                 @Nullable final OnGlobalLayoutListener layoutListener) {
    return new OnGlobalLayoutListener() {

      @Override
      public void onGlobalLayout() {
        adaptViewSize(tabItem);
        updateView(tabItem);

        if (layoutListener != null) {
          layoutListener.onGlobalLayout();
        }
      }

    };
  }

  @NonNull
  private AnimatorListener createUpdateViewAnimationListener(@NonNull final TabItem tabItem) {
    return new AnimatorListenerAdapter() {

      @Override
      public void onAnimationEnd(final Animator animation) {
        super.onAnimationEnd(animation);
        inflateOrRemoveView(tabItem);
      }

    };
  }

  @NonNull
  private AnimatorListener createHideSwitcherAnimationListener() {
    return new AnimatorListenerAdapter() {

      @Override
      public void onAnimationEnd(final Animator animation) {
        super.onAnimationEnd(animation);
        AbstractTabItemIterator iterator =
          new TabItemIterator.Builder(getTabSwitcher(), viewRecycler).create();
        TabItem tabItem;

        while ((tabItem = iterator.next()) != null) {
          if (tabItem.getTab() == getModel().getSelectedTab()) {
            Pair<View, Boolean> pair = viewRecycler.inflate(tabItem);
            View view = pair.first;
            FrameLayout.LayoutParams layoutParams =
              (FrameLayout.LayoutParams) view.getLayoutParams();
            view.setAlpha(1f);
            getArithmetics().setScale(Axis.DRAGGING_AXIS, view, 1);
            getArithmetics().setScale(Axis.ORTHOGONAL_AXIS, view, 1);
            view.setX(layoutParams.leftMargin);
            view.setY(layoutParams.topMargin);
          } else {
            viewRecycler.remove(tabItem);
          }
        }

        viewRecycler.clearCache();
        recyclerAdapter.clearCachedPreviews();
        tabViewBottomMargin = -1;
      }

    };
  }

  @NonNull
  private AnimatorListener createClearAnimationListener() {
    return new AnimatorListenerAdapter() {

      @Override
      public void onAnimationEnd(final Animator animation) {
        super.onAnimationEnd(animation);
        viewRecycler.removeAll();
        animateToolbarVisibility(getModel().areToolbarsShown(), 0);
      }

    };
  }

  @NonNull
  private AnimatorListener createSwipeAnimationListener(@NonNull final TabItem tabItem) {
    return new AnimatorListenerAdapter() {

      @Override
      public void onAnimationEnd(final Animator animation) {
        super.onAnimationEnd(animation);
        inflateOrRemoveView(tabItem);
        View view = tabItem.getView();
        adaptStackOnSwipeAborted(tabItem, tabItem.getIndex() + 1);
        tabItem.getTag().setClosing(false);
        getArithmetics().setPivot(Axis.DRAGGING_AXIS, view,
          getArithmetics().getPivot(Axis.DRAGGING_AXIS, view, DragState.NONE));
        animateToolbarVisibility(true, 0);
      }

    };
  }

  @NonNull
  private AnimatorListener createRemoveAnimationListener(@NonNull final TabItem removedTabItem) {
    return new AnimatorListenerAdapter() {

      @Override
      public void onAnimationStart(final Animator animation) {
        super.onAnimationStart(animation);

        if (getModel().isEmpty()) {
          animateToolbarVisibility(getModel().areToolbarsShown(), 0);
        }

        float previousAttachedPosition =
          calculateAttachedPosition(getModel().getCount() + 1);
        float attachedPosition = calculateAttachedPosition(getModel().getCount());
        State state = removedTabItem.getTag().getState();

        if (state == State.STACKED_END) {
          relocateWhenRemovingStackedTab(removedTabItem, false);
        } else if (state == State.STACKED_START) {
          relocateWhenRemovingStackedTab(removedTabItem, true);
        } else if (state == State.FLOATING || state == State.STACKED_START_ATOP) {
          relocateWhenRemovingFloatingTab(removedTabItem, attachedPosition,
            previousAttachedPosition != attachedPosition);
        }
      }

      @Override
      public void onAnimationEnd(final Animator animation) {
        super.onAnimationEnd(animation);
        viewRecycler.remove(removedTabItem);
      }

    };
  }

  @NonNull
  private AnimatorListener createRelocateAnimationListener(@NonNull final TabItem tabItem) {
    return new AnimatorListenerAdapter() {

      @Override
      public void onAnimationStart(final Animator animation) {
        super.onAnimationStart(animation);
        tabItem.getView().setVisibility(View.VISIBLE);
      }

      @Override
      public void onAnimationEnd(final Animator animation) {
        super.onAnimationEnd(animation);

        if (tabItem.getTag().getState() == State.STACKED_START_ATOP) {
          adaptStackOnSwipeAborted(tabItem, tabItem.getIndex() + 1);
        }

        if (tabItem.isVisible()) {
          updateView(tabItem);
        } else {
          viewRecycler.remove(tabItem);
        }
      }

    };
  }

  @NonNull
  private AnimatorListener createRevertOvershootAnimationListener(@NonNull final View view,
                                                                  @Nullable final AnimatorListener listener) {
    return new AnimatorListenerAdapter() {

      @Override
      public void onAnimationEnd(final Animator animation) {
        super.onAnimationEnd(animation);
        getArithmetics().setPivot(Axis.DRAGGING_AXIS, view,
          getArithmetics().getPivot(Axis.DRAGGING_AXIS, view, DragState.NONE));
        getArithmetics().setPivot(Axis.ORTHOGONAL_AXIS, view,
          getArithmetics().getPivot(Axis.DRAGGING_AXIS, view, DragState.NONE));

        if (listener != null) {
          listener.onAnimationEnd(animation);
        }
      }

    };
  }

  @NonNull
  private AnimatorListener createRevertStartOvershootAnimationListener() {
    return new AnimatorListenerAdapter() {

      @Override
      public void onAnimationEnd(final Animator animation) {
        super.onAnimationEnd(animation);
        animateRevertStartOvershoot(new DecelerateInterpolator());
      }

    };
  }

  @NonNull
  private AnimatorListener createPeekAnimationListener(@NonNull final TabItem tabItem,
                                                       @NonNull final PeekAnimation peekAnimation) {
    return new AnimatorListenerAdapter() {

      @Override
      public void onAnimationEnd(final Animator animation) {
        super.onAnimationEnd(animation);
        long totalDuration =
          peekAnimation.getDuration() != -1 ? peekAnimation.getDuration() :
            peekAnimationDuration;
        long duration = totalDuration / 3;
        Interpolator interpolator =
          peekAnimation.getInterpolator() != null ? peekAnimation.getInterpolator() :
            new AccelerateDecelerateInterpolator();
        View view = tabItem.getView();
        getArithmetics().setPivot(Axis.DRAGGING_AXIS, view, tabTitleContainerHeight);
        getArithmetics().setPivot(Axis.ORTHOGONAL_AXIS, view,
          getArithmetics().getSize(Axis.ORTHOGONAL_AXIS, view) / 2f);
        ViewPropertyAnimator animator = view.animate();
        animator.setDuration(duration);
        animator.setStartDelay(duration);
        animator.setInterpolator(interpolator);
        animator.setListener(
          new AnimationListenerWrapper(createRevertPeekAnimationListener(tabItem)));
        animator.alpha(0);
        getArithmetics().animatePosition(Axis.DRAGGING_AXIS, animator, view,
          getArithmetics().getPosition(Axis.DRAGGING_AXIS, view) * 1.5f, false);
        getArithmetics().animateScale(Axis.DRAGGING_AXIS, animator, 0);
        getArithmetics().animateScale(Axis.ORTHOGONAL_AXIS, animator, 0);
        animator.start();
      }

    };
  }

  @NonNull
  private AnimatorListener createRevertPeekAnimationListener(@NonNull final TabItem tabItem) {
    return new AnimatorListenerAdapter() {

      @Override
      public void onAnimationEnd(final Animator animation) {
        super.onAnimationEnd(animation);
        viewRecycler.remove(tabItem);
      }

    };
  }

  @NonNull
  private AnimatorListener createZoomOutAnimationListener(@NonNull final TabItem selectedTabItem,
                                                          @NonNull final PeekAnimation peekAnimation) {
    return new AnimatorListenerAdapter() {

      @Override
      public void onAnimationEnd(final Animator animation) {
        super.onAnimationEnd(animation);
        getModel().removeListener(PhoneTabSwitcherLayout.this);
        getModel().hideSwitcher();
        long totalDuration =
          peekAnimation.getDuration() != -1 ? peekAnimation.getDuration() :
            peekAnimationDuration;
        long duration = totalDuration / 3;
        Interpolator interpolator =
          peekAnimation.getInterpolator() != null ? peekAnimation.getInterpolator() :
            new AccelerateDecelerateInterpolator();
        animateHideSwitcher(selectedTabItem, duration, interpolator, duration,
          createZoomInAnimationListener(selectedTabItem));
      }

    };
  }

  private AnimatorListener createZoomInAnimationListener(@NonNull final TabItem tabItem) {
    return new AnimatorListenerAdapter() {

      @Override
      public void onAnimationEnd(final Animator animation) {
        super.onAnimationEnd(animation);
        getModel().addListener(PhoneTabSwitcherLayout.this);
        viewRecycler.inflate(tabItem);
        viewRecycler.clearCache();
        recyclerAdapter.clearCachedPreviews();
        tabViewBottomMargin = -1;
      }

    };
  }

  private void adaptStackOnSwipe(@NonNull final TabItem swipedTabItem, final int successorIndex,
                                 final int count) {
    if (swipedTabItem.getTag().getState() == State.STACKED_START_ATOP &&
      successorIndex < getModel().getCount()) {
      TabItem tabItem = TabItem.create(getTabSwitcher(), viewRecycler, successorIndex);
      State state = tabItem.getTag().getState();

      if (state == State.HIDDEN || state == State.STACKED_START) {
        Pair<Float, State> pair =
          calculatePositionAndStateWhenStackedAtStart(count, swipedTabItem.getIndex(),
            (TabItem) null);
        tabItem.getTag().setPosition(pair.first);
        tabItem.getTag().setState(pair.second);
        inflateOrRemoveView(tabItem);
      }
    }
  }

  private void adaptStackOnSwipeAborted(@NonNull final TabItem swipedTabItem,
                                        final int successorIndex) {
    if (swipedTabItem.getTag().getState() == State.STACKED_START_ATOP &&
      successorIndex < getModel().getCount()) {
      TabItem tabItem = TabItem.create(getTabSwitcher(), viewRecycler, successorIndex);

      if (tabItem.getTag().getState() == State.STACKED_START_ATOP) {
        Pair<Float, State> pair =
          calculatePositionAndStateWhenStackedAtStart(getTabSwitcher().getCount(),
            tabItem.getIndex(), swipedTabItem);
        tabItem.getTag().setPosition(pair.first);
        tabItem.getTag().setState(pair.second);
        inflateOrRemoveView(tabItem);
      }
    }
  }

  private void inflateOrRemoveView(@NonNull final TabItem tabItem) {
    if (tabItem.isInflated() && !tabItem.isVisible()) {
      viewRecycler.remove(tabItem);
    } else if (tabItem.isVisible()) {
      if (!tabItem.isInflated()) {
        inflateAndUpdateView(tabItem, null);
      } else {
        updateView(tabItem);
      }
    }
  }

  private void inflateAndUpdateView(@NonNull final TabItem tabItem,
                                    @Nullable final OnGlobalLayoutListener listener) {
    inflateView(tabItem, createInflateViewLayoutListener(tabItem, listener),
      tabViewBottomMargin);
  }

  private void inflateView(@NonNull final TabItem tabItem,
                           @Nullable final OnGlobalLayoutListener listener,
                           @NonNull final Integer... params) {
    Pair<View, Boolean> pair = viewRecycler.inflate(tabItem, params);

    if (listener != null) {
      boolean inflated = pair.second;

      if (inflated) {
        View view = pair.first;
        view.getViewTreeObserver()
          .addOnGlobalLayoutListener(new LayoutListenerWrapper(view, listener));
      } else {
        listener.onGlobalLayout();
      }
    }
  }

  private void adaptViewSize(@NonNull final TabItem tabItem) {
    View view = tabItem.getView();
    getArithmetics().setPivot(Axis.DRAGGING_AXIS, view,
      getArithmetics().getPivot(Axis.DRAGGING_AXIS, view, DragState.NONE));
    getArithmetics().setPivot(Axis.ORTHOGONAL_AXIS, view,
      getArithmetics().getPivot(Axis.ORTHOGONAL_AXIS, view, DragState.NONE));
    float scale = getArithmetics().getScale(view, true);
    getArithmetics().setScale(Axis.DRAGGING_AXIS, view, scale);
    getArithmetics().setScale(Axis.ORTHOGONAL_AXIS, view, scale);
  }

  private void updateView(@NonNull final TabItem tabItem) {
    float position = tabItem.getTag().getPosition();
    View view = tabItem.getView();
    view.setAlpha(1f);
    view.setVisibility(View.VISIBLE);
    getArithmetics().setPivot(Axis.DRAGGING_AXIS, view,
      getArithmetics().getPivot(Axis.DRAGGING_AXIS, view, DragState.NONE));
    getArithmetics().setPivot(Axis.ORTHOGONAL_AXIS, view,
      getArithmetics().getPivot(Axis.ORTHOGONAL_AXIS, view, DragState.NONE));
    getArithmetics().setPosition(Axis.DRAGGING_AXIS, view, position);
    getArithmetics().setPosition(Axis.ORTHOGONAL_AXIS, view, 0);
    getArithmetics().setRotation(Axis.ORTHOGONAL_AXIS, view, 0);
  }

  private void relocateWhenRemovingFloatingTab(@NonNull final TabItem removedTabItem,
                                               final float attachedPosition,
                                               boolean attachedPositionChanged) {
    AbstractTabItemIterator iterator;
    TabItem tabItem;
    float defaultTabSpacing = calculateMaxTabSpacing(getModel().getCount(), null);
    float minTabSpacing = calculateMinTabSpacing(getModel().getCount());
    int referenceIndex = removedTabItem.getIndex();
    TabItem currentReferenceTabItem = removedTabItem;
    float referencePosition = removedTabItem.getTag().getPosition();

    if (attachedPositionChanged && getModel().getCount() > 0) {
      int neighboringIndex =
        removedTabItem.getIndex() > 0 ? referenceIndex - 1 : referenceIndex;
      referencePosition += Math.abs(
        TabItem.create(getTabSwitcher(), viewRecycler, neighboringIndex).getTag()
          .getPosition() - referencePosition) / 2f;
    }

    referencePosition =
      Math.min(calculateEndPosition(removedTabItem.getIndex() - 1), referencePosition);
    float initialReferencePosition = referencePosition;

    if (removedTabItem.getIndex() > 0) {
      int selectedTabIndex = getModel().getSelectedTabIndex();
      TabItem selectedTabItem =
        TabItem.create(getTabSwitcher(), viewRecycler, selectedTabIndex);
      float maxTabSpacing = calculateMaxTabSpacing(getModel().getCount(), selectedTabItem);
      iterator = new TabItemIterator.Builder(getTabSwitcher(), viewRecycler)
        .start(removedTabItem.getIndex() - 1).reverse(true).create();

      while ((tabItem = iterator.next()) != null) {
        TabItem predecessor = iterator.peek();
        float currentTabSpacing =
          calculateMaxTabSpacing(getModel().getCount(), currentReferenceTabItem);
        Pair<Float, State> pair;

        if (tabItem.getIndex() == removedTabItem.getIndex() - 1) {
          pair = clipTabPosition(getModel().getCount(), tabItem.getIndex(),
            referencePosition, predecessor);
          currentReferenceTabItem = tabItem;
          referencePosition = pair.first;
          referenceIndex = tabItem.getIndex();
        } else if (referencePosition >= attachedPosition - currentTabSpacing) {
          float position;

          if (selectedTabIndex > tabItem.getIndex() &&
            selectedTabIndex <= referenceIndex) {
            position = referencePosition + maxTabSpacing +
              ((referenceIndex - tabItem.getIndex() - 1) * defaultTabSpacing);
          } else {
            position = referencePosition +
              ((referenceIndex - tabItem.getIndex()) * defaultTabSpacing);
          }

          pair = clipTabPosition(getModel().getCount(), tabItem.getIndex(), position,
            predecessor);
        } else {
          TabItem successor = iterator.previous();
          float successorPosition = successor.getTag().getPosition();
          float position = (attachedPosition * (successorPosition + minTabSpacing)) /
            (minTabSpacing + attachedPosition - currentTabSpacing);
          pair = clipTabPosition(getModel().getCount(), tabItem.getIndex(), position,
            predecessor);

          if (pair.first >= attachedPosition - currentTabSpacing) {
            currentReferenceTabItem = tabItem;
            referencePosition = pair.first;
            referenceIndex = tabItem.getIndex();
          }
        }

        Tag tag = tabItem.getTag().clone();
        tag.setPosition(pair.first);
        tag.setState(pair.second);

        if (tag.getState() != State.HIDDEN) {
          long startDelay = Math.abs(removedTabItem.getIndex() - tabItem.getIndex()) *
            relocateAnimationDelay;

          if (!tabItem.isInflated()) {
            Pair<Float, State> pair2 =
              calculatePositionAndStateWhenStackedAtEnd(tabItem.getIndex());
            tabItem.getTag().setPosition(pair2.first);
            tabItem.getTag().setState(pair2.second);
          }

          relocate(tabItem, tag.getPosition(), tag, startDelay);
        } else {
          break;
        }
      }
    }

    if (attachedPositionChanged && getModel().getCount() > 2 &&
      removedTabItem.getTag().getState() != State.STACKED_START_ATOP) {
      iterator = new TabItemIterator.Builder(getTabSwitcher(), viewRecycler)
        .start(removedTabItem.getIndex()).create();
      float previousPosition = initialReferencePosition;
      Tag previousTag = removedTabItem.getTag();

      while ((tabItem = iterator.next()) != null &&
        tabItem.getIndex() < getModel().getCount() - 1) {
        float position = calculateNonLinearPosition(previousPosition,
          calculateMaxTabSpacing(getModel().getCount(), tabItem));
        Pair<Float, State> pair =
          clipTabPosition(getModel().getCount(), tabItem.getIndex(), position,
            previousTag.getState());
        Tag tag = tabItem.getTag().clone();
        tag.setPosition(pair.first);
        tag.setState(pair.second);
        long startDelay = (Math.abs(removedTabItem.getIndex() - tabItem.getIndex()) + 1) *
          relocateAnimationDelay;

        if (!tabItem.isInflated()) {
          Pair<Float, State> pair2 =
            calculatePositionAndStateWhenStackedAtStart(getModel().getCount(),
              tabItem.getIndex(), iterator.previous());
          tabItem.getTag().setPosition(pair2.first);
          tabItem.getTag().setState(pair2.second);
        }

        relocate(tabItem, tag.getPosition(), tag, startDelay);
        previousPosition = pair.first;
        previousTag = tag;

        if (pair.second == State.HIDDEN || pair.second == State.STACKED_START) {
          break;
        }
      }
    }
  }

  private void relocateWhenRemovingStackedTab(@NonNull final TabItem removedTabItem,
                                              final boolean start) {
    int startIndex = removedTabItem.getIndex() + (start ? -1 : 0);
    TabItemIterator iterator =
      new TabItemIterator.Builder(getTabSwitcher(), viewRecycler).reverse(start)
        .start(startIndex).create();
    TabItem tabItem;
    float previousProjectedPosition = removedTabItem.getTag().getPosition();

    while ((tabItem = iterator.next()) != null &&
      (tabItem.getTag().getState() == State.HIDDEN ||
        tabItem.getTag().getState() == State.STACKED_START ||
        tabItem.getTag().getState() == State.STACKED_START_ATOP ||
        tabItem.getTag().getState() == State.STACKED_END)) {
      float projectedPosition = tabItem.getTag().getPosition();

      if (tabItem.getTag().getState() == State.HIDDEN) {
        TabItem previous = iterator.previous();
        tabItem.getTag().setState(previous.getTag().getState());

        if (tabItem.isVisible()) {
          Pair<Float, State> pair = start ?
            calculatePositionAndStateWhenStackedAtStart(getTabSwitcher().getCount(),
              tabItem.getIndex(), tabItem) :
            calculatePositionAndStateWhenStackedAtEnd(tabItem.getIndex());
          tabItem.getTag().setPosition(pair.first);
          tabItem.getTag().setState(pair.second);
          inflateAndUpdateView(tabItem, null);
        }

        break;
      } else {
        tabItem.getTag().setPosition(previousProjectedPosition);
        long startDelay =
          (Math.abs(startIndex - tabItem.getIndex()) + 1) * relocateAnimationDelay;
        animateRelocate(tabItem, previousProjectedPosition, null, startDelay,
          createRelocateAnimationListener(tabItem));
      }

      previousProjectedPosition = projectedPosition;
    }
  }

  @NonNull
  private TabItem[] relocateWhenAddingFloatingTabs(@NonNull final TabItem[] addedTabItems,
                                                   @NonNull final TabItem referenceTabItem,
                                                   final boolean isReferencingPredecessor,
                                                   final float attachedPosition,
                                                   final boolean attachedPositionChanged) {
    int count = getTabSwitcher().getCount();
    TabItem firstAddedTabItem = addedTabItems[0];
    TabItem lastAddedTabItem = addedTabItems[addedTabItems.length - 1];

    float referencePosition = referenceTabItem.getTag().getPosition();

    if (isReferencingPredecessor && attachedPositionChanged &&
      lastAddedTabItem.getIndex() < count - 1) {
      int neighboringIndex = lastAddedTabItem.getIndex() + 1;
      referencePosition -= Math.abs(referencePosition -
        TabItem.create(getTabSwitcher(), viewRecycler, neighboringIndex).getTag()
          .getPosition()) / 2f;
    }

    float initialReferencePosition = referencePosition;
    int selectedTabIndex = getModel().getSelectedTabIndex();
    TabItem selectedTabItem = TabItem.create(getTabSwitcher(), viewRecycler, selectedTabIndex);
    float defaultTabSpacing = calculateMaxTabSpacing(count, null);
    float maxTabSpacing = calculateMaxTabSpacing(count, selectedTabItem);
    float minTabSpacing = calculateMinTabSpacing(count);
    TabItem currentReferenceTabItem = referenceTabItem;
    int referenceIndex = referenceTabItem.getIndex();

    AbstractTabItemIterator.AbstractBuilder builder =
      new TabItemIterator.Builder(getTabSwitcher(), viewRecycler);

    for (TabItem addedTabItem : addedTabItems) {
      int iterationReferenceIndex = referenceIndex;
      float iterationReferencePosition = referencePosition;
      TabItem iterationReferenceTabItem = currentReferenceTabItem;
      AbstractTabItemIterator iterator =
        builder.start(addedTabItem.getIndex()).reverse(true).create();
      TabItem tabItem;

      while ((tabItem = iterator.next()) != null) {
        TabItem predecessor = iterator.peek();
        Pair<Float, State> pair;
        float currentTabSpacing = calculateMaxTabSpacing(count, iterationReferenceTabItem);

        if (isReferencingPredecessor && tabItem.getIndex() == addedTabItem.getIndex()) {
          State predecessorState =
            predecessor != null ? predecessor.getTag().getState() : null;
          pair = clipTabPosition(count, tabItem.getIndex(), iterationReferencePosition,
            predecessorState == State.STACKED_START_ATOP ? State.FLOATING :
              predecessorState);
          currentReferenceTabItem = iterationReferenceTabItem = tabItem;
          initialReferencePosition =
            referencePosition = iterationReferencePosition = pair.first;
          referenceIndex = iterationReferenceIndex = tabItem.getIndex();
        } else if (iterationReferencePosition >= attachedPosition - currentTabSpacing) {
          float position;

          if (selectedTabIndex > tabItem.getIndex() &&
            selectedTabIndex <= iterationReferenceIndex) {
            position = iterationReferencePosition + maxTabSpacing +
              ((iterationReferenceIndex - tabItem.getIndex() - 1) *
                defaultTabSpacing);
          } else {
            position = iterationReferencePosition +
              ((iterationReferenceIndex - tabItem.getIndex()) *
                defaultTabSpacing);
          }

          pair = clipTabPosition(count, tabItem.getIndex(), position, predecessor);
        } else {
          TabItem successor = iterator.previous();
          float successorPosition = successor.getTag().getPosition();
          float position = (attachedPosition * (successorPosition + minTabSpacing)) /
            (minTabSpacing + attachedPosition - currentTabSpacing);
          pair = clipTabPosition(count, tabItem.getIndex(), position, predecessor);

          if (pair.first >= attachedPosition - currentTabSpacing) {
            iterationReferenceTabItem = tabItem;
            iterationReferencePosition = pair.first;
            iterationReferenceIndex = tabItem.getIndex();
          }
        }

        if (tabItem.getIndex() >= firstAddedTabItem.getIndex() &&
          tabItem.getIndex() <= lastAddedTabItem.getIndex()) {
          if (!isReferencingPredecessor && attachedPositionChanged && count > 3) {
            TabItem successor = iterator.previous();
            float successorPosition = successor.getTag().getPosition();
            float position = pair.first - Math.abs(pair.first - successorPosition) / 2f;
            pair = clipTabPosition(count, tabItem.getIndex(), position, predecessor);
            initialReferencePosition = pair.first;
          }

          Tag tag = addedTabItems[tabItem.getIndex() - firstAddedTabItem.getIndex()]
            .getTag();
          tag.setPosition(pair.first);
          tag.setState(pair.second);
        } else {
          Tag tag = tabItem.getTag().clone();
          tag.setPosition(pair.first);
          tag.setState(pair.second);

          if (!tabItem.isInflated()) {
            Pair<Float, State> pair2 =
              calculatePositionAndStateWhenStackedAtEnd(tabItem.getIndex());
            tabItem.getTag().setPosition(pair2.first);
            tabItem.getTag().setState(pair2.second);
          }

          relocate(tabItem, tag.getPosition(), tag, 0);
        }

        if (pair.second == State.HIDDEN || pair.second == State.STACKED_END) {
          firstVisibleIndex++;
          break;
        }
      }
    }

    if (attachedPositionChanged && count > 3) {
      AbstractTabItemIterator iterator =
        builder.start(lastAddedTabItem.getIndex() + 1).reverse(false).create();
      TabItem tabItem;
      float previousPosition = initialReferencePosition;
      Tag previousTag = lastAddedTabItem.getTag();

      while ((tabItem = iterator.next()) != null && tabItem.getIndex() < count - 1) {
        float position = calculateNonLinearPosition(previousPosition,
          calculateMaxTabSpacing(count, tabItem));
        Pair<Float, State> pair = clipTabPosition(count, tabItem.getIndex(), position,
          previousTag.getState());
        Tag tag = tabItem.getTag().clone();
        tag.setPosition(pair.first);
        tag.setState(pair.second);

        if (!tabItem.isInflated()) {
          Pair<Float, State> pair2 =
            calculatePositionAndStateWhenStackedAtStart(count, tabItem.getIndex(),
              iterator.previous());
          tabItem.getTag().setPosition(pair2.first);
          tabItem.getTag().setState(pair2.second);
        }

        relocate(tabItem, tag.getPosition(), tag, 0);
        previousPosition = pair.first;
        previousTag = tag;

        if (pair.second == State.HIDDEN || pair.second == State.STACKED_START) {
          break;
        }
      }
    }

    return addedTabItems;
  }

  @NonNull
  private TabItem[] relocateWhenAddingStackedTabs(final boolean start,
                                                  @NonNull final TabItem[] addedTabItems) {
    if (!start) {
      firstVisibleIndex += addedTabItems.length;
    }

    int count = getTabSwitcher().getCount();
    TabItem firstAddedTabItem = addedTabItems[0];
    TabItem lastAddedTabItem = addedTabItems[addedTabItems.length - 1];
    AbstractTabItemIterator iterator =
      new TabItemIterator.Builder(getTabSwitcher(), viewRecycler)
        .start(start ? lastAddedTabItem.getIndex() : firstAddedTabItem.getIndex())
        .reverse(start).create();
    TabItem tabItem;

    while ((tabItem = iterator.next()) != null &&
      (tabItem.getTag().getState() == State.STACKED_START ||
        tabItem.getTag().getState() == State.STACKED_START_ATOP ||
        tabItem.getTag().getState() == State.STACKED_END ||
        tabItem.getTag().getState() == State.HIDDEN)) {
      TabItem predecessor = start ? iterator.peek() : iterator.previous();
      Pair<Float, State> pair = start ?
        calculatePositionAndStateWhenStackedAtStart(count, tabItem.getIndex(),
          predecessor) :
        calculatePositionAndStateWhenStackedAtEnd(tabItem.getIndex());

      if (start && predecessor != null && predecessor.getTag().getState() == State.FLOATING) {
        float predecessorPosition = predecessor.getTag().getPosition();
        float distance = predecessorPosition - pair.first;

        if (distance > calculateMinTabSpacing(count)) {
          float position = calculateNonLinearPosition(tabItem, predecessor);
          pair = clipTabPosition(count, tabItem.getIndex(), position, predecessor);
        }
      }

      if (tabItem.getIndex() >= firstAddedTabItem.getIndex() &&
        tabItem.getIndex() <= lastAddedTabItem.getIndex()) {
        Tag tag = addedTabItems[tabItem.getIndex() - firstAddedTabItem.getIndex()].getTag();
        tag.setPosition(pair.first);
        tag.setState(pair.second);
      } else if (tabItem.isInflated()) {
        Tag tag = tabItem.getTag().clone();
        tag.setPosition(pair.first);
        tag.setState(pair.second);
        animateRelocate(tabItem, tag.getPosition(), tag, 0,
          createRelocateAnimationListener(tabItem));
      } else {
        break;
      }
    }

    return addedTabItems;
  }

  @NonNull
  private TabItem[] relocateWhenAddingHiddenTabs(@NonNull final TabItem[] addedTabItems,
                                                 @NonNull final TabItem referenceTabItem) {
    boolean stackedAtStart = isStackedAtStart(referenceTabItem.getIndex());

    for (TabItem tabItem : addedTabItems) {
      Pair<Float, State> pair;

      if (stackedAtStart) {
        TabItem predecessor = tabItem.getIndex() > 0 ?
          TabItem.create(getTabSwitcher(), viewRecycler, tabItem.getIndex() - 1) :
          null;
        pair = calculatePositionAndStateWhenStackedAtStart(getModel().getCount(),
          tabItem.getIndex(), predecessor);
      } else {
        pair = calculatePositionAndStateWhenStackedAtEnd(tabItem.getIndex());
      }

      Tag tag = tabItem.getTag();
      tag.setPosition(pair.first);
      tag.setState(pair.second);
    }

    return addedTabItems;
  }

  private void relocate(@NonNull final TabItem tabItem, final float relocatePosition,
                        @Nullable final Tag tag, final long startDelay) {
    if (tabItem.isInflated()) {
      animateRelocate(tabItem, relocatePosition, tag, startDelay,
        createRelocateAnimationListener(tabItem));
    } else {
      inflateAndUpdateView(tabItem,
        createRelocateLayoutListener(tabItem, relocatePosition, tag, startDelay,
          createRelocateAnimationListener(tabItem)));
      tabItem.getView().setVisibility(View.INVISIBLE);
    }
  }

  private void swipe(@NonNull final TabItem tabItem, final float distance) {
    View view = tabItem.getView();

    if (!tabItem.getTag().isClosing()) {
      adaptStackOnSwipe(tabItem, tabItem.getIndex() + 1, getModel().getCount() - 1);
    }

    tabItem.getTag().setClosing(true);
    float dragDistance = distance;

    if (!tabItem.getTab().isCloseable()) {
      dragDistance = (float) Math.pow(Math.abs(distance), 0.75);
      dragDistance = distance < 0 ? dragDistance * -1 : dragDistance;
    }

    getArithmetics().setPivot(Axis.DRAGGING_AXIS, view,
      getArithmetics().getPivot(Axis.DRAGGING_AXIS, view, DragState.SWIPE));
    getArithmetics().setPivot(Axis.ORTHOGONAL_AXIS, view,
      getArithmetics().getPivot(Axis.ORTHOGONAL_AXIS, view, DragState.SWIPE));
    float scale = getArithmetics().getScale(view, true);
    float ratio = 1 - (Math.abs(dragDistance) / calculateSwipePosition());
    float scaledClosedTabScale = swipedTabScale * scale;
    float targetScale = scaledClosedTabScale + ratio * (scale - scaledClosedTabScale);
    getArithmetics().setScale(Axis.DRAGGING_AXIS, view, targetScale);
    getArithmetics().setScale(Axis.ORTHOGONAL_AXIS, view, targetScale);
    view.setAlpha(swipedTabAlpha + ratio * (1 - swipedTabAlpha));
    getArithmetics().setPosition(Axis.ORTHOGONAL_AXIS, view, dragDistance);
  }

  private void startOvershoot(final float position) {
    TabItemIterator iterator =
      new TabItemIterator.Builder(getTabSwitcher(), viewRecycler).create();
    TabItem tabItem;

    while ((tabItem = iterator.next()) != null) {
      if (tabItem.getIndex() == 0) {
        View view = tabItem.getView();
        getArithmetics().setPivot(Axis.DRAGGING_AXIS, view,
          getArithmetics().getPivot(Axis.DRAGGING_AXIS, view, DragState.NONE));
        getArithmetics().setPivot(Axis.ORTHOGONAL_AXIS, view,
          getArithmetics().getPivot(Axis.ORTHOGONAL_AXIS, view, DragState.NONE));
        getArithmetics().setPosition(Axis.DRAGGING_AXIS, view, position);
      } else if (tabItem.isInflated()) {
        View firstView = iterator.first().getView();
        View view = tabItem.getView();
        view.setVisibility(getArithmetics().getPosition(Axis.DRAGGING_AXIS, firstView) <=
          getArithmetics().getPosition(Axis.DRAGGING_AXIS, view) ? View.INVISIBLE :
          View.VISIBLE);
      }
    }
  }

  private void tiltOnStartOvershoot(final float angle) {
    TabItemIterator iterator =
      new TabItemIterator.Builder(getTabSwitcher(), viewRecycler).create();
    TabItem tabItem;

    while ((tabItem = iterator.next()) != null) {
      View view = tabItem.getView();

      if (tabItem.getIndex() == 0) {
        view.setCameraDistance(maxCameraDistance);
        getArithmetics().setPivot(Axis.DRAGGING_AXIS, view, getArithmetics()
          .getPivot(Axis.DRAGGING_AXIS, view, DragState.OVERSHOOT_START));
        getArithmetics().setPivot(Axis.ORTHOGONAL_AXIS, view, getArithmetics()
          .getPivot(Axis.ORTHOGONAL_AXIS, view, DragState.OVERSHOOT_START));
        getArithmetics().setRotation(Axis.ORTHOGONAL_AXIS, view, angle);
      } else if (tabItem.isInflated()) {
        tabItem.getView().setVisibility(View.INVISIBLE);
      }
    }
  }

  private void tiltOnEndOvershoot(final float angle) {
    float minCameraDistance = maxCameraDistance / 2f;
    int firstVisibleIndex = -1;
    TabItemIterator iterator =
      new TabItemIterator.Builder(getTabSwitcher(), viewRecycler).create();
    TabItem tabItem;

    while ((tabItem = iterator.next()) != null) {
      if (tabItem.isInflated()) {
        View view = tabItem.getView();

        if (!iterator.hasNext()) {
          view.setCameraDistance(maxCameraDistance);
        } else if (firstVisibleIndex == -1) {
          view.setCameraDistance(minCameraDistance);

          if (tabItem.getTag().getState() == State.FLOATING) {
            firstVisibleIndex = tabItem.getIndex();
          }
        } else {
          int diff = tabItem.getIndex() - firstVisibleIndex;
          float ratio =
            (float) diff / (float) (getModel().getCount() - firstVisibleIndex);
          view.setCameraDistance(
            minCameraDistance + (maxCameraDistance - minCameraDistance) * ratio);
        }

        getArithmetics().setPivot(Axis.DRAGGING_AXIS, view, getArithmetics()
          .getPivot(Axis.DRAGGING_AXIS, view, DragState.OVERSHOOT_END));
        getArithmetics().setPivot(Axis.ORTHOGONAL_AXIS, view, getArithmetics()
          .getPivot(Axis.ORTHOGONAL_AXIS, view, DragState.OVERSHOOT_END));
        getArithmetics().setRotation(Axis.ORTHOGONAL_AXIS, view, angle);
      }
    }
  }

  private boolean isStackedAtStart(final int index) {
    boolean start = true;
    AbstractTabItemIterator iterator =
      new TabItemIterator.Builder(getTabSwitcher(), viewRecycler).start(index + 1)
        .create();
    TabItem tabItem;

    while ((tabItem = iterator.next()) != null) {
      State state = tabItem.getTag().getState();

      if (state == State.STACKED_START) {
        start = true;
        break;
      } else if (state == State.FLOATING) {
        start = false;
        break;
      }
    }

    return start;
  }

  public PhoneTabSwitcherLayout(@NonNull final TabSwitcher tabSwitcher,
                                @NonNull final TabSwitcherModel model,
                                @NonNull final PhoneArithmetics arithmetics) {
    super(tabSwitcher, model, arithmetics);
    Resources resources = tabSwitcher.getResources();
    tabInset = resources.getDimensionPixelSize(R.dimen.tab_inset);
    tabBorderWidth = resources.getDimensionPixelSize(R.dimen.tab_border_width);
    tabTitleContainerHeight =
      resources.getDimensionPixelSize(R.dimen.tab_title_container_height);
    stackedTabCount = resources.getInteger(R.integer.stacked_tab_count);
    stackedTabSpacing = resources.getDimensionPixelSize(R.dimen.stacked_tab_spacing);
    maxCameraDistance = resources.getDimensionPixelSize(R.dimen.max_camera_distance);
    TypedValue typedValue = new TypedValue();
    resources.getValue(R.dimen.swiped_tab_scale, typedValue, true);
    swipedTabScale = typedValue.getFloat();
    resources.getValue(R.dimen.swiped_tab_alpha, typedValue, true);
    swipedTabAlpha = typedValue.getFloat();
    showSwitcherAnimationDuration =
      resources.getInteger(R.integer.show_switcher_animation_duration);
    hideSwitcherAnimationDuration =
      resources.getInteger(R.integer.hide_switcher_animation_duration);
    toolbarVisibilityAnimationDuration =
      resources.getInteger(R.integer.toolbar_visibility_animation_duration);
    toolbarVisibilityAnimationDelay =
      resources.getInteger(R.integer.toolbar_visibility_animation_delay);
    swipeAnimationDuration = resources.getInteger(R.integer.swipe_animation_duration);
    clearAnimationDelay = resources.getInteger(R.integer.clear_animation_delay);
    relocateAnimationDuration = resources.getInteger(R.integer.relocate_animation_duration);
    relocateAnimationDelay = resources.getInteger(R.integer.relocate_animation_delay);
    revertOvershootAnimationDuration =
      resources.getInteger(R.integer.revert_overshoot_animation_duration);
    revealAnimationDuration = resources.getInteger(R.integer.reveal_animation_duration);
    peekAnimationDuration = resources.getInteger(R.integer.peek_animation_duration);
    maxStartOvershootAngle = resources.getInteger(R.integer.max_start_overshoot_angle);
    maxEndOvershootAngle = resources.getInteger(R.integer.max_end_overshoot_angle);
    tabViewBottomMargin = -1;
    toolbarAnimation = null;
  }

  @NonNull
  @Override
  protected final AbstractDragHandler<?> onInflateLayout(final boolean tabsOnly) {
    LayoutInflater inflater = LayoutInflater.from(getContext());

    if (tabsOnly) {
      toolbar = (Toolbar) getTabSwitcher().findViewById(R.id.primary_toolbar);
    } else {
      toolbar = (Toolbar) inflater.inflate(R.layout.phone_toolbar, getTabSwitcher(), false);
      toolbar.setVisibility(getModel().areToolbarsShown() ? View.VISIBLE : View.INVISIBLE);
      getTabSwitcher().addView(toolbar);
    }

    tabContainer = new FrameLayout(getContext());
    getTabSwitcher().addView(tabContainer, FrameLayout.LayoutParams.MATCH_PARENT,
      FrameLayout.LayoutParams.MATCH_PARENT);
    childViewRecycler = new ViewRecycler<>(inflater);
    recyclerAdapter = new PhoneRecyclerAdapter(getTabSwitcher(), getModel(), childViewRecycler);
    getModel().addListener(recyclerAdapter);
    viewRecycler = new AttachedViewRecycler<>(tabContainer, inflater,
      Collections.reverseOrder(new TabItem.Comparator(getTabSwitcher())));
    viewRecycler.setAdapter(recyclerAdapter);
    recyclerAdapter.setViewRecycler(viewRecycler);
    dragHandler = new PhoneDragHandler(getTabSwitcher(), getArithmetics(), viewRecycler);
    adaptLogLevel();
    adaptDecorator();
    adaptToolbarMargin();
    return dragHandler;
  }

  @Nullable
  @Override
  protected final Pair<Integer, Float> onDetachLayout(final boolean tabsOnly) {
    Pair<Integer, Float> result = null;

    if (getModel().isSwitcherShown() && firstVisibleIndex != -1) {
      TabItem tabItem = TabItem.create(getModel(), viewRecycler, firstVisibleIndex);
      Tag tag = tabItem.getTag();

      if (tag.getState() != State.HIDDEN) {
        float firstVisibleTabPosition = tabItem.getTag().getPosition();
        result = Pair.create(firstVisibleIndex, firstVisibleTabPosition);
      }
    }

    childViewRecycler.removeAll();
    childViewRecycler.clearCache();
    viewRecycler.removeAll();
    viewRecycler.clearCache();
    recyclerAdapter.clearCachedPreviews();

    if (!tabsOnly) {
      getModel().removeListener(recyclerAdapter);
      getTabSwitcher().removeView(toolbar);
      getTabSwitcher().removeView(tabContainer);
    }

    return result;
  }

  @Override
  public final boolean handleTouchEvent(@NonNull final MotionEvent event) {
    return dragHandler.handleTouchEvent(event);
  }

  @Nullable
  @Override
  public final ViewGroup getTabContainer() {
    return tabContainer;
  }

  @Nullable
  @Override
  public final Toolbar[] getToolbars() {
    return new Toolbar[]{toolbar};
  }

  @Override
  public final void onLogLevelChanged(@NonNull final LogLevel logLevel) {
    adaptLogLevel();
  }

  @Override
  public final void onDecoratorChanged(@NonNull final TabSwitcherDecorator decorator) {
    adaptDecorator();
    super.onDecoratorChanged(decorator);
  }

  @Override
  public final void onSwitcherShown() {
    getLogger().logInfo(getClass(), "Showed tab switcher");
    animateShowSwitcher();
  }

  @Override
  public final void onSwitcherHidden() {
    getLogger().logInfo(getClass(), "Hid tab switcher");
    animateHideSwitcher();
  }

  @Override
  public final void onSelectionChanged(final int previousIndex, final int index,
                                       @Nullable final Tab selectedTab,
                                       final boolean switcherHidden) {
    getLogger().logInfo(getClass(), "Selected tab at index " + index);

    if (switcherHidden) {
      animateHideSwitcher();
    } else {
      viewRecycler.remove(TabItem.create(getTabSwitcher(), viewRecycler, previousIndex));
      viewRecycler.inflate(TabItem.create(getTabSwitcher(), viewRecycler, index));
    }
  }

  @Override
  public final void onTabAdded(final int index, @NonNull final Tab tab,
                               final int previousSelectedTabIndex, final int selectedTabIndex,
                               final boolean switcherVisibilityChanged,
                               @NonNull final Animation animation) {
    getLogger().logInfo(getClass(),
      "Added tab at index " + index + " using a " + animation.getClass().getSimpleName());

    if (animation instanceof PeekAnimation && !getModel().isEmpty()) {
      ensureTrue(switcherVisibilityChanged, animation.getClass().getSimpleName() +
        " not supported when the tab switcher is shown");
      PeekAnimation peekAnimation = (PeekAnimation) animation;
      TabItem tabItem = new TabItem(0, tab);
      inflateView(tabItem, createPeekLayoutListener(tabItem, peekAnimation));
    } else if (animation instanceof RevealAnimation && switcherVisibilityChanged) {
      TabItem tabItem = new TabItem(0, tab);
      RevealAnimation revealAnimation = (RevealAnimation) animation;
      inflateView(tabItem, createRevealLayoutListener(tabItem, revealAnimation));
    } else {
      addAllTabs(index, new Tab[]{tab}, animation);
    }
  }

  @Override
  public final void onAllTabsAdded(final int index, @NonNull final Tab[] tabs,
                                   final int previousSelectedTabIndex, final int selectedTabIndex,
                                   @NonNull final Animation animation) {
    ensureTrue(animation instanceof SwipeAnimation,
      animation.getClass().getSimpleName() + " not supported for adding multiple tabs");
    getLogger().logInfo(getClass(),
      "Added " + tabs.length + " tabs at index " + index + " using a " +
        animation.getClass().getSimpleName());
    addAllTabs(index, tabs, animation);
  }

  @Override
  public final void onTabRemoved(final int index, @NonNull final Tab tab,
                                 final int previousSelectedTabIndex, final int selectedTabIndex,
                                 @NonNull final Animation animation) {
    ensureTrue(animation instanceof SwipeAnimation,
      animation.getClass().getSimpleName() + " not supported for removing tabs");
    getLogger().logInfo(getClass(), "Removed tab at index " + index + " using a " +
      animation.getClass().getSimpleName());
    TabItem removedTabItem = TabItem.create(viewRecycler, index, tab);

    if (!getModel().isSwitcherShown()) {
      viewRecycler.remove(removedTabItem);

      if (getModel().isEmpty()) {
        toolbar.setAlpha(getModel().areToolbarsShown() ? 1 : 0);
      } else if (selectedTabIndex != previousSelectedTabIndex) {
        viewRecycler
          .inflate(TabItem.create(getTabSwitcher(), viewRecycler, selectedTabIndex));
      }
    } else {
      adaptStackOnSwipe(removedTabItem, removedTabItem.getIndex(), getModel().getCount());
      removedTabItem.getTag().setClosing(true);
      SwipeAnimation swipeAnimation =
        animation instanceof SwipeAnimation ? (SwipeAnimation) animation :
          new SwipeAnimation.Builder().create();

      if (removedTabItem.isInflated()) {
        animateRemove(removedTabItem, swipeAnimation);
      } else {
        boolean start = isStackedAtStart(index);
        TabItem predecessor = TabItem.create(getTabSwitcher(), viewRecycler, index - 1);
        Pair<Float, State> pair = start ?
          calculatePositionAndStateWhenStackedAtStart(getModel().getCount(), index,
            predecessor) : calculatePositionAndStateWhenStackedAtEnd(index);
        removedTabItem.getTag().setPosition(pair.first);
        removedTabItem.getTag().setState(pair.second);
        inflateAndUpdateView(removedTabItem,
          createRemoveLayoutListener(removedTabItem, swipeAnimation));
      }
    }
  }

  @Override
  public final void onAllTabsRemoved(@NonNull final Tab[] tabs,
                                     @NonNull final Animation animation) {
    ensureTrue(animation instanceof SwipeAnimation,
      animation.getClass().getSimpleName() + " not supported for removing tabs ");
    getLogger().logInfo(getClass(),
      "Removed all tabs using a " + animation.getClass().getSimpleName());

    if (!getModel().isSwitcherShown()) {
      viewRecycler.removeAll();
      toolbar.setAlpha(getModel().areToolbarsShown() ? 1 : 0);
    } else {
      SwipeAnimation swipeAnimation =
        animation instanceof SwipeAnimation ? (SwipeAnimation) animation :
          new SwipeAnimation.Builder().create();
      AbstractTabItemIterator iterator =
        new ArrayTabItemIterator.Builder(viewRecycler, tabs).reverse(true).create();
      TabItem tabItem;
      int startDelay = 0;

      while ((tabItem = iterator.next()) != null) {
        TabItem previous = iterator.previous();

        if (tabItem.getTag().getState() == State.FLOATING ||
          (previous != null && previous.getTag().getState() == State.FLOATING)) {
          startDelay += clearAnimationDelay;
        }

        if (tabItem.isInflated()) {
          animateSwipe(tabItem, true, startDelay, swipeAnimation,
            !iterator.hasNext() ? createClearAnimationListener() : null);
        }
      }
    }
  }

  @Override
  public final void onPaddingChanged(final int left, final int top, final int right,
                                     final int bottom) {
    adaptToolbarMargin();
  }

  @Override
  public final void onTabIconChanged(@Nullable final Drawable icon) {

  }

  @Override
  public final void onTabBackgroundColorChanged(@Nullable final ColorStateList colorStateList) {

  }

  @Override
  public final void onTabTitleColorChanged(@Nullable final ColorStateList colorStateList) {

  }

  @Override
  public final void onTabCloseButtonIconChanged(@Nullable final Drawable icon) {

  }

  @Override
  public final void onGlobalLayout() {
    if (getModel().isSwitcherShown()) {
      TabItem[] tabItems = calculateInitialTabItems(getModel().getFirstVisibleTabIndex(),
        getModel().getFirstVisibleTabPosition());
      AbstractTabItemIterator iterator = new InitialTabItemIterator(tabItems, false, 0);
      TabItem tabItem;

      while ((tabItem = iterator.next()) != null) {
        if (tabItem.isVisible()) {
          inflateAndUpdateView(tabItem, createBottomMarginLayoutListener(tabItem));
        }
      }

      toolbar.setAlpha(getModel().areToolbarsShown() ? 1 : 0);
    } else if (getModel().getSelectedTab() != null) {
      TabItem tabItem = TabItem.create(getTabSwitcher(), viewRecycler,
        getModel().getSelectedTabIndex());
      viewRecycler.inflate(tabItem);
    }
  }

  @Nullable
  @Override
  public final DragState onDrag(@NonNull final DragState dragState, final float dragDistance) {
    if (dragDistance != 0) {
      if (dragState == DragState.DRAG_TO_END) {
        calculatePositionsWhenDraggingToEnd(dragDistance);
      } else {
        calculatePositionsWhenDraggingToStart(dragDistance);
      }
    }

    DragState overshoot = isOvershootingAtEnd(
      new TabItemIterator.Builder(getTabSwitcher(), viewRecycler).create()) ?
      DragState.OVERSHOOT_END :
      (isOvershootingAtStart() ? DragState.OVERSHOOT_START : null);
    getLogger().logVerbose(getClass(),
      "Dragging using a distance of " + dragDistance + " pixels. Drag state is " +
        dragState + ", overshoot is " + overshoot);
    return overshoot;
  }

  @Override
  public final void onClick(@NonNull final TabItem tabItem) {
    getModel().selectTab(tabItem.getTab());
    getLogger().logVerbose(getClass(), "Clicked tab at index " + tabItem.getIndex());
  }

  @Override
  public final void onRevertStartOvershoot() {
    animateRevertStartOvershoot();
    getLogger().logVerbose(getClass(), "Reverting overshoot at the start");
  }

  @Override
  public final void onRevertEndOvershoot() {
    animateRevertEndOvershoot();
    getLogger().logVerbose(getClass(), "Reverting overshoot at the end");
  }

  public final void onStartOvershoot(final float position) {
    startOvershoot(position);
    getLogger().logVerbose(getClass(),
      "Overshooting at the start using a position of " + position + " pixels");
  }

  @Override
  public final void onTiltOnStartOvershoot(final float angle) {
    tiltOnStartOvershoot(angle);
    getLogger().logVerbose(getClass(),
      "Tilting on start overshoot using an angle of " + angle + " degrees");
  }

  @Override
  public final void onTiltOnEndOvershoot(final float angle) {
    tiltOnEndOvershoot(angle);
    getLogger().logVerbose(getClass(),
      "Tilting on end overshoot using an angle of " + angle + " degrees");
  }

  @Override
  public final void onSwipe(@NonNull final TabItem tabItem, final float distance) {
    swipe(tabItem, distance);
    getLogger().logVerbose(getClass(),
      "Swiping tab at index " + tabItem.getIndex() + ". Current swipe distance is " +
        distance + " pixels");
  }

  @Override
  public final void onSwipeEnded(@NonNull final TabItem tabItem, final boolean remove,
                                 final float velocity) {
    if (remove) {
      View view = tabItem.getView();
      SwipeDirection direction =
        getArithmetics().getPosition(Axis.ORTHOGONAL_AXIS, view) < 0 ?
          SwipeDirection.LEFT : SwipeDirection.RIGHT;
      long animationDuration =
        velocity > 0 ? Math.round((calculateSwipePosition() / velocity) * 1000) : -1;
      Animation animation = new SwipeAnimation.Builder().setDirection(direction)
        .setDuration(animationDuration).create();
      getModel().removeTab(tabItem.getTab(), animation);
    } else {
      animateSwipe(tabItem, false, 0, new SwipeAnimation.Builder().create(),
        createSwipeAnimationListener(tabItem));
    }

    getLogger().logVerbose(getClass(),
      "Ended swiping tab at index " + tabItem.getIndex() + ". Tab will " +
        (remove ? "" : "not ") + "be removed");
  }

}
