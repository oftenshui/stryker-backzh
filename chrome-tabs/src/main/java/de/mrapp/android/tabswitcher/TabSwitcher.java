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

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.*;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.FrameLayout;
import androidx.annotation.*;
import androidx.appcompat.widget.Toolbar;
import androidx.appcompat.widget.Toolbar.OnMenuItemClickListener;
import androidx.core.content.ContextCompat;
import androidx.core.util.Pair;
import androidx.core.view.ViewCompat;
import de.mrapp.android.tabswitcher.layout.AbstractTabSwitcherLayout;
import de.mrapp.android.tabswitcher.layout.AbstractTabSwitcherLayout.LayoutListenerWrapper;
import de.mrapp.android.tabswitcher.layout.TabSwitcherLayout;
import de.mrapp.android.tabswitcher.layout.phone.PhoneArithmetics;
import de.mrapp.android.tabswitcher.layout.phone.PhoneTabSwitcherLayout;
import de.mrapp.android.tabswitcher.model.Model;
import de.mrapp.android.tabswitcher.model.TabSwitcherModel;
import de.mrapp.android.tabswitcher.view.TabSwitcherButton;
import de.mrapp.android.util.DisplayUtil.DeviceType;
import de.mrapp.android.util.DisplayUtil.Orientation;
import de.mrapp.android.util.ViewUtil;
import de.mrapp.android.util.logging.LogLevel;
import de.mrapp.android.util.view.AbstractSavedState;

import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;

import static de.mrapp.android.util.Condition.ensureNotNull;
import static de.mrapp.android.util.DisplayUtil.getDeviceType;
import static de.mrapp.android.util.DisplayUtil.getOrientation;

public class TabSwitcher extends FrameLayout implements TabSwitcherLayout, Model {

  private static class TabSwitcherState extends AbstractSavedState {

    public static Creator<TabSwitcherState> CREATOR = new Creator<TabSwitcherState>() {

      @Override
      public TabSwitcherState createFromParcel(final Parcel source) {
        return new TabSwitcherState(source);
      }

      @Override
      public TabSwitcherState[] newArray(final int size) {
        return new TabSwitcherState[size];
      }

    };

    private LayoutPolicy layoutPolicy;

    private Bundle modelState;

    private TabSwitcherState(@NonNull final Parcel source) {
      super(source);
      layoutPolicy = (LayoutPolicy) source.readSerializable();
      modelState = source.readBundle(getClass().getClassLoader());
    }

    TabSwitcherState(@Nullable final Parcelable superState) {
      super(superState);
    }

    @Override
    public void writeToParcel(final Parcel dest, final int flags) {
      super.writeToParcel(dest, flags);
      dest.writeSerializable(layoutPolicy);
      dest.writeBundle(modelState);
    }

  }

  private Queue<Runnable> pendingActions;

  private Set<TabSwitcherListener> listeners;

  private LayoutPolicy layoutPolicy;

  private TabSwitcherModel model;

  private AbstractTabSwitcherLayout layout;

  private void initialize(@Nullable final AttributeSet attributeSet,
                          @AttrRes final int defaultStyle,
                          @StyleRes final int defaultStyleResource) {
    pendingActions = new LinkedList<>();
    listeners = new CopyOnWriteArraySet<>(new LinkedHashSet<TabSwitcherListener>());
    model = new TabSwitcherModel(this);
    model.addListener(createModelListener());
    getViewTreeObserver().addOnGlobalLayoutListener(
      new LayoutListenerWrapper(this, createGlobalLayoutListener(false)));
    setPadding(super.getPaddingLeft(), super.getPaddingTop(), super.getPaddingRight(),
      super.getPaddingBottom());
    obtainStyledAttributes(attributeSet, defaultStyle, defaultStyleResource);
  }

  private void initializeLayout(@NonNull final Layout layout, final boolean inflatedTabsOnly) {
    if (layout == Layout.TABLET) {
      PhoneArithmetics arithmetics = new PhoneArithmetics(TabSwitcher.this);
      this.layout = new PhoneTabSwitcherLayout(TabSwitcher.this, model, arithmetics);
    } else {
      PhoneArithmetics arithmetics = new PhoneArithmetics(TabSwitcher.this);
      this.layout = new PhoneTabSwitcherLayout(TabSwitcher.this, model, arithmetics);
    }

    this.layout.setCallback(createLayoutCallback());
    this.model.addListener(this.layout);
    this.layout.inflateLayout(inflatedTabsOnly);
    final ViewGroup tabContainer = getTabContainer();
    assert tabContainer != null;

    if (ViewCompat.isLaidOut(tabContainer)) {
      this.layout.onGlobalLayout();
    } else {
      tabContainer.getViewTreeObserver()
        .addOnGlobalLayoutListener(new OnGlobalLayoutListener() {

          @Override
          public void onGlobalLayout() {
            ViewUtil.removeOnGlobalLayoutListener(
              tabContainer.getViewTreeObserver(), this);
            if (TabSwitcher.this.layout != null) {
              TabSwitcher.this.layout.onGlobalLayout();
            }
          }

        });
    }
  }

  private void obtainStyledAttributes(@Nullable final AttributeSet attributeSet,
                                      @AttrRes final int defaultStyle,
                                      @StyleRes final int defaultStyleResource) {
    TypedArray typedArray = getContext()
      .obtainStyledAttributes(attributeSet, R.styleable.TabSwitcher, defaultStyle,
        defaultStyleResource);

    try {
      obtainLayoutPolicy(typedArray);
      obtainBackground(typedArray);
      obtainTabIcon(typedArray);
      obtainTabBackgroundColor(typedArray);
      obtainTabTitleTextColor(typedArray);
      obtainTabCloseButtonIcon(typedArray);
      obtainToolbarTitle(typedArray);
      obtainToolbarNavigationIcon(typedArray);
      obtainToolbarMenu(typedArray);
    } finally {
      typedArray.recycle();
    }
  }

  private void obtainLayoutPolicy(@NonNull final TypedArray typedArray) {
    int defaultValue = LayoutPolicy.AUTO.getValue();
    int value = typedArray.getInt(R.styleable.TabSwitcher_layoutPolicy, defaultValue);
    setLayoutPolicy(LayoutPolicy.fromValue(value));
  }

  private void obtainBackground(@NonNull final TypedArray typedArray) {
    int resourceId = typedArray.getResourceId(R.styleable.TabSwitcher_android_background, 0);

    if (resourceId != 0) {
      ViewUtil.setBackground(this, ContextCompat.getDrawable(getContext(), resourceId));
    } else {
      int defaultValue =
        ContextCompat.getColor(getContext(), R.color.tab_switcher_background_color);
      int color =
        typedArray.getColor(R.styleable.TabSwitcher_android_background, defaultValue);
      setBackgroundColor(color);
    }
  }

  private void obtainTabIcon(@NonNull final TypedArray typedArray) {
    int resourceId = typedArray.getResourceId(R.styleable.TabSwitcher_tabIcon, -1);

    if (resourceId != -1) {
      setTabIcon(resourceId);
    }
  }

  private void obtainTabBackgroundColor(@NonNull final TypedArray typedArray) {
    ColorStateList colorStateList =
      typedArray.getColorStateList(R.styleable.TabSwitcher_tabBackgroundColor);

    if (colorStateList != null) {
      setTabBackgroundColor(colorStateList);
    }
  }

  private void obtainTabTitleTextColor(@NonNull final TypedArray typedArray) {
    ColorStateList colorStateList =
      typedArray.getColorStateList(R.styleable.TabSwitcher_tabTitleTextColor);

    if (colorStateList != null) {
      setTabTitleTextColor(colorStateList);
    }
  }

  private void obtainTabCloseButtonIcon(@NonNull final TypedArray typedArray) {
    int resourceId = typedArray.getResourceId(R.styleable.TabSwitcher_tabCloseButtonIcon, -1);

    if (resourceId != -1) {
      setTabCloseButtonIcon(resourceId);
    }
  }

  private void obtainToolbarTitle(@NonNull final TypedArray typedArray) {
    CharSequence title = typedArray.getText(R.styleable.TabSwitcher_toolbarTitle);

    if (!TextUtils.isEmpty(title)) {
      setToolbarTitle(title);
    }
  }

  private void obtainToolbarNavigationIcon(@NonNull final TypedArray typedArray) {
    Drawable icon = typedArray.getDrawable(R.styleable.TabSwitcher_toolbarNavigationIcon);

    if (icon != null) {
      setToolbarNavigationIcon(icon, null);
    }
  }

  private void obtainToolbarMenu(@NonNull final TypedArray typedArray) {
    int resourceId = typedArray.getResourceId(R.styleable.TabSwitcher_toolbarMenu, -1);

    if (resourceId != -1) {
      inflateToolbarMenu(resourceId, null);
    }
  }

  private void enqueuePendingAction(@NonNull final Runnable action) {
    ensureNotNull(action, "The action may not be null");
    pendingActions.add(action);
    executePendingAction();
  }

  private void executePendingAction() {
    if (!isAnimationRunning()) {
      final Runnable action = pendingActions.poll();

      if (action != null) {
        new Runnable() {

          @Override
          public void run() {
            action.run();
            executePendingAction();
          }

        }.run();
      }
    }
  }

  @NonNull
  private Model.Listener createModelListener() {
    return new Model.Listener() {

      @Override
      public void onLogLevelChanged(@NonNull final LogLevel logLevel) {

      }

      @Override
      public void onDecoratorChanged(@NonNull final TabSwitcherDecorator decorator) {

      }

      @Override
      public void onSwitcherShown() {
        notifyOnSwitcherShown();
      }

      @Override
      public void onSwitcherHidden() {
        notifyOnSwitcherHidden();
      }

      @Override
      public void onSelectionChanged(final int previousIndex, final int index,
                                     @Nullable final Tab selectedTab,
                                     final boolean switcherHidden) {
        notifyOnSelectionChanged(index, selectedTab);

        if (switcherHidden) {
          notifyOnSwitcherHidden();
        }
      }

      @Override
      public void onTabAdded(final int index, @NonNull final Tab tab,
                             final int previousSelectedTabIndex, final int selectedTabIndex,
                             final boolean switcherVisibilityChanged,
                             @NonNull final Animation animation) {
        notifyOnTabAdded(index, tab, animation);

        if (previousSelectedTabIndex != selectedTabIndex) {
          notifyOnSelectionChanged(selectedTabIndex,
            selectedTabIndex != -1 ? getTab(selectedTabIndex) : null);
        }

        if (switcherVisibilityChanged) {
          notifyOnSwitcherHidden();
        }
      }

      @Override
      public void onAllTabsAdded(final int index, @NonNull final Tab[] tabs,
                                 final int previousSelectedTabIndex,
                                 final int selectedTabIndex,
                                 @NonNull final Animation animation) {
        for (Tab tab : tabs) {
          notifyOnTabAdded(index, tab, animation);
        }

        if (previousSelectedTabIndex != selectedTabIndex) {
          notifyOnSelectionChanged(selectedTabIndex,
            selectedTabIndex != -1 ? getTab(selectedTabIndex) : null);
        }
      }

      @Override
      public void onTabRemoved(final int index, @NonNull final Tab tab,
                               final int previousSelectedTabIndex, final int selectedTabIndex,
                               @NonNull final Animation animation) {
        notifyOnTabRemoved(index, tab, animation);

        if (previousSelectedTabIndex != selectedTabIndex) {
          notifyOnSelectionChanged(selectedTabIndex,
            selectedTabIndex != -1 ? getTab(selectedTabIndex) : null);
        }
      }

      @Override
      public void onAllTabsRemoved(@NonNull final Tab[] tabs,
                                   @NonNull final Animation animation) {
        notifyOnAllTabsRemoved(tabs, animation);
        notifyOnSelectionChanged(-1, null);
      }

      @Override
      public void onPaddingChanged(final int left, final int top, final int right,
                                   final int bottom) {

      }

      @Override
      public void onTabIconChanged(@Nullable final Drawable icon) {

      }

      @Override
      public void onTabBackgroundColorChanged(@Nullable final ColorStateList colorStateList) {

      }

      @Override
      public void onTabTitleColorChanged(@Nullable final ColorStateList colorStateList) {

      }

      @Override
      public void onTabCloseButtonIconChanged(@Nullable final Drawable icon) {

      }

      @Override
      public void onToolbarVisibilityChanged(final boolean visible) {

      }

      @Override
      public void onToolbarTitleChanged(@Nullable final CharSequence title) {

      }

      @Override
      public void onToolbarNavigationIconChanged(@Nullable final Drawable icon,
                                                 @Nullable final OnClickListener listener) {

      }

      @Override
      public void onToolbarMenuInflated(@MenuRes final int resourceId,
                                        @Nullable final OnMenuItemClickListener listener) {

      }

    };
  }

  @NonNull
  private AbstractTabSwitcherLayout.Callback createLayoutCallback() {
    return new AbstractTabSwitcherLayout.Callback() {

      @Override
      public void onAnimationsEnded() {
        executePendingAction();
      }

    };
  }

  @NonNull
  private OnGlobalLayoutListener createGlobalLayoutListener(final boolean inflateTabsOnly) {
    return new OnGlobalLayoutListener() {

      @Override
      public void onGlobalLayout() {
        ensureNotNull(getDecorator(), "No decorator has been set",
          IllegalStateException.class);
        initializeLayout(getLayout(), inflateTabsOnly);
      }

    };
  }

  private void notifyOnSwitcherShown() {
    for (TabSwitcherListener listener : listeners) {
      listener.onSwitcherShown(this);
    }
  }

  private void notifyOnSwitcherHidden() {
    for (TabSwitcherListener listener : listeners) {
      listener.onSwitcherHidden(this);
    }
  }

  private void notifyOnSelectionChanged(final int selectedTabIndex,
                                        @Nullable final Tab selectedTab) {
    for (TabSwitcherListener listener : listeners) {
      listener.onSelectionChanged(this, selectedTabIndex, selectedTab);
    }
  }

  private void notifyOnTabAdded(final int index, @NonNull final Tab tab,
                                @NonNull final Animation animation) {
    for (TabSwitcherListener listener : listeners) {
      listener.onTabAdded(this, index, tab, animation);
    }
  }

  private void notifyOnTabRemoved(final int index, @NonNull final Tab tab,
                                  @NonNull final Animation animation) {
    for (TabSwitcherListener listener : listeners) {
      listener.onTabRemoved(this, index, tab, animation);
    }
  }

  private void notifyOnAllTabsRemoved(@NonNull final Tab[] tabs,
                                      @NonNull final Animation animation) {
    for (TabSwitcherListener listener : listeners) {
      listener.onAllTabsRemoved(this, tabs, animation);
    }
  }

  public TabSwitcher(@NonNull final Context context) {
    this(context, null);
  }

  public TabSwitcher(@NonNull final Context context, @Nullable final AttributeSet attributeSet) {
    super(context, attributeSet);
    initialize(attributeSet, 0, 0);
  }

  public TabSwitcher(@NonNull final Context context, @Nullable final AttributeSet attributeSet,
                     @AttrRes final int defaultStyle) {
    super(context, attributeSet, defaultStyle);
    initialize(attributeSet, defaultStyle, 0);
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  public TabSwitcher(@NonNull final Context context, @Nullable final AttributeSet attributeSet,
                     @AttrRes final int defaultStyle, @StyleRes final int defaultStyleResource) {
    super(context, attributeSet, defaultStyle, defaultStyleResource);
    initialize(attributeSet, defaultStyle, defaultStyleResource);
  }

  public static void setupWithMenu(@NonNull final TabSwitcher tabSwitcher,
                                   @NonNull final Menu menu,
                                   @Nullable final OnClickListener listener) {
    ensureNotNull(tabSwitcher, "The tab switcher may not be null");
    ensureNotNull(menu, "The menu may not be null");

    for (int i = 0; i < menu.size(); i++) {
      MenuItem menuItem = menu.getItem(i);
      View view = menuItem.getActionView();

      if (view instanceof TabSwitcherButton) {
        TabSwitcherButton tabSwitcherButton = (TabSwitcherButton) view;
        tabSwitcherButton.setOnClickListener(listener);
        tabSwitcherButton.setCount(tabSwitcher.getCount());
        tabSwitcher.addListener(tabSwitcherButton);
      }
    }
  }

  public final void addListener(@NonNull final TabSwitcherListener listener) {
    ensureNotNull(listener, "The listener may not be null");
    this.listeners.add(listener);
  }

  public final void removeListener(@NonNull final TabSwitcherListener listener) {
    ensureNotNull(listener, "The listener may not be null");
    this.listeners.remove(listener);
  }

  @NonNull
  public final LayoutPolicy getLayoutPolicy() {
    return layoutPolicy;
  }

  public final void setLayoutPolicy(@NonNull final LayoutPolicy layoutPolicy) {
    ensureNotNull(layoutPolicy, "The layout policy may not be null");

    if (this.layoutPolicy != layoutPolicy) {
      Layout previousLayout = getLayout();
      this.layoutPolicy = layoutPolicy;

      if (layout != null) {
        Layout newLayout = getLayout();

        if (previousLayout != newLayout) {
          layout.detachLayout(false);
          model.removeListener(layout);
          initializeLayout(newLayout, false);
        }
      }
    }
  }

  @NonNull
  public final Layout getLayout() {
    if (layoutPolicy == LayoutPolicy.TABLET || (layoutPolicy == LayoutPolicy.AUTO &&
      getDeviceType(getContext()) == DeviceType.TABLET)) {
      return Layout.TABLET;
    } else {
      return getOrientation(getContext()) == Orientation.LANDSCAPE ? Layout.PHONE_LANDSCAPE :
        Layout.PHONE_PORTRAIT;
    }
  }

  @Override
  public final void addTab(@NonNull final Tab tab) {
    enqueuePendingAction(new Runnable() {

      @Override
      public void run() {
        model.addTab(tab);
      }

    });
  }

  @Override
  public final void addTab(@NonNull final Tab tab, final int index) {
    enqueuePendingAction(new Runnable() {

      @Override
      public void run() {
        model.addTab(tab, index);
      }

    });
  }

  @Override
  public final void addTab(@NonNull final Tab tab, final int index,
                           @NonNull final Animation animation) {
    enqueuePendingAction(new Runnable() {

      @Override
      public void run() {
        model.addTab(tab, index, animation);
      }

    });
  }

  @Override
  public final void addAllTabs(@NonNull final Collection<? extends Tab> tabs) {
    enqueuePendingAction(new Runnable() {

      @Override
      public void run() {
        model.addAllTabs(tabs);
      }

    });
  }

  @Override
  public final void addAllTabs(@NonNull final Collection<? extends Tab> tabs, final int index) {
    enqueuePendingAction(new Runnable() {

      @Override
      public void run() {
        model.addAllTabs(tabs, index);
      }

    });
  }

  @Override
  public final void addAllTabs(@NonNull final Collection<? extends Tab> tabs, final int index,
                               @NonNull final Animation animation) {
    enqueuePendingAction(new Runnable() {

      @Override
      public void run() {
        model.addAllTabs(tabs, index, animation);
      }

    });
  }

  @Override
  public final void addAllTabs(@NonNull final Tab[] tabs) {
    enqueuePendingAction(new Runnable() {

      @Override
      public void run() {
        model.addAllTabs(tabs);
      }

    });
  }

  @Override
  public final void addAllTabs(@NonNull final Tab[] tabs, final int index) {
    enqueuePendingAction(new Runnable() {

      @Override
      public void run() {
        model.addAllTabs(tabs, index);
      }

    });
  }

  @Override
  public final void addAllTabs(@NonNull final Tab[] tabs, final int index,
                               @NonNull final Animation animation) {
    enqueuePendingAction(new Runnable() {

      @Override
      public void run() {
        model.addAllTabs(tabs, index, animation);
      }

    });
  }

  @Override
  public final void removeTab(@NonNull final Tab tab) {
    enqueuePendingAction(new Runnable() {

      @Override
      public void run() {
        model.removeTab(tab);
      }

    });
  }

  @Override
  public final void removeTab(@NonNull final Tab tab, @NonNull final Animation animation) {
    enqueuePendingAction(new Runnable() {

      @Override
      public void run() {
        model.removeTab(tab, animation);
      }

    });
  }

  @Override
  public final void clear() {
    enqueuePendingAction(new Runnable() {

      @Override
      public void run() {
        model.clear();
      }

    });
  }

  @Override
  public final void clear(@NonNull final Animation animationType) {
    enqueuePendingAction(new Runnable() {

      @Override
      public void run() {
        model.clear(animationType);
      }

    });
  }

  @Override
  public final void selectTab(@NonNull final Tab tab) {
    enqueuePendingAction(new Runnable() {

      @Override
      public void run() {
        model.selectTab(tab);
      }

    });
  }

  @Nullable
  @Override
  public final Tab getSelectedTab() {
    return model.getSelectedTab();
  }

  @Override
  public final int getSelectedTabIndex() {
    return model.getSelectedTabIndex();
  }

  @Override
  public final Iterator<Tab> iterator() {
    return model.iterator();
  }

  @Override
  public final boolean isEmpty() {
    return model.isEmpty();
  }

  @Override
  public final int getCount() {
    return model.getCount();
  }

  @NonNull
  @Override
  public final Tab getTab(final int index) {
    return model.getTab(index);
  }

  @Override
  public final int indexOf(@NonNull final Tab tab) {
    return model.indexOf(tab);
  }

  @Override
  public final boolean isSwitcherShown() {
    return model.isSwitcherShown();
  }

  @Override
  public final void showSwitcher() {
    enqueuePendingAction(new Runnable() {

      @Override
      public void run() {
        model.showSwitcher();
      }

    });
  }

  @Override
  public final void hideSwitcher() {
    enqueuePendingAction(new Runnable() {

      @Override
      public void run() {
        model.hideSwitcher();
      }

    });
  }

  @Override
  public final void toggleSwitcherVisibility() {
    enqueuePendingAction(new Runnable() {

      @Override
      public void run() {
        model.toggleSwitcherVisibility();
      }

    });
  }

  @Override
  public final void setDecorator(@NonNull final TabSwitcherDecorator decorator) {
    model.setDecorator(decorator);
  }

  @Override
  public final TabSwitcherDecorator getDecorator() {
    return model.getDecorator();
  }

  @NonNull
  @Override
  public final LogLevel getLogLevel() {
    return model.getLogLevel();
  }

  @Override
  public final void setLogLevel(@NonNull final LogLevel logLevel) {
    model.setLogLevel(logLevel);
  }

  @Override
  public final void setPadding(final int left, final int top, final int right, final int bottom) {
    model.setPadding(left, top, right, bottom);
  }

  @Override
  public final int getPaddingLeft() {
    return model.getPaddingLeft();
  }

  @Override
  public final int getPaddingTop() {
    return model.getPaddingTop();
  }

  @Override
  public final int getPaddingRight() {
    return model.getPaddingRight();
  }

  @Override
  public final int getPaddingBottom() {
    return model.getPaddingBottom();
  }

  @Override
  public final int getPaddingStart() {
    return model.getPaddingStart();
  }

  @Override
  public final int getPaddingEnd() {
    return model.getPaddingEnd();
  }

  @Nullable
  @Override
  public final Drawable getTabIcon() {
    return model.getTabIcon();
  }

  @Override
  public final void setTabIcon(@DrawableRes final int resourceId) {
    model.setTabIcon(resourceId);
  }

  @Override
  public final void setTabIcon(@Nullable final Bitmap icon) {
    model.setTabIcon(icon);
  }

  @Nullable
  @Override
  public final ColorStateList getTabBackgroundColor() {
    return model.getTabBackgroundColor();
  }

  @Override
  public final void setTabBackgroundColor(@ColorInt final int color) {
    model.setTabBackgroundColor(color);
  }

  @Override
  public final void setTabBackgroundColor(@Nullable final ColorStateList colorStateList) {
    model.setTabBackgroundColor(colorStateList);
  }

  @Nullable
  @Override
  public final ColorStateList getTabTitleTextColor() {
    return model.getTabTitleTextColor();
  }

  @Override
  public final void setTabTitleTextColor(@ColorInt final int color) {
    model.setTabTitleTextColor(color);
  }

  @Override
  public final void setTabTitleTextColor(@Nullable final ColorStateList colorStateList) {
    model.setTabTitleTextColor(colorStateList);
  }

  @Nullable
  @Override
  public final Drawable getTabCloseButtonIcon() {
    return model.getTabCloseButtonIcon();
  }

  @Override
  public final void setTabCloseButtonIcon(@DrawableRes final int resourceId) {
    model.setTabCloseButtonIcon(resourceId);
  }

  @Override
  public final void setTabCloseButtonIcon(@Nullable final Bitmap icon) {
    model.setTabCloseButtonIcon(icon);
  }

  @Override
  public final boolean areToolbarsShown() {
    return model.areToolbarsShown();
  }

  @Override
  public final void showToolbars(final boolean show) {
    model.showToolbars(show);
  }

  @Nullable
  @Override
  public final CharSequence getToolbarTitle() {
    Toolbar[] toolbars = getToolbars();
    return toolbars != null ? toolbars[0].getTitle() : model.getToolbarTitle();
  }

  @Override
  public final void setToolbarTitle(@StringRes final int resourceId) {
    model.setToolbarTitle(resourceId);
  }

  @Override
  public final void setToolbarTitle(@Nullable final CharSequence title) {
    model.setToolbarTitle(title);
  }

  @Nullable
  @Override
  public final Drawable getToolbarNavigationIcon() {
    Toolbar[] toolbars = getToolbars();
    return toolbars != null ? toolbars[0].getNavigationIcon() :
      model.getToolbarNavigationIcon();
  }

  @Override
  public final void setToolbarNavigationIcon(@Nullable final Drawable icon,
                                             @Nullable final OnClickListener listener) {
    model.setToolbarNavigationIcon(icon, listener);
  }

  @Override
  public final void setToolbarNavigationIcon(@DrawableRes final int resourceId,
                                             @Nullable final OnClickListener listener) {
    model.setToolbarNavigationIcon(resourceId, listener);
  }

  @Override
  public final void inflateToolbarMenu(@MenuRes final int resourceId,
                                       @Nullable final OnMenuItemClickListener listener) {
    model.inflateToolbarMenu(resourceId, listener);
  }

  @Override
  public final void addCloseTabListener(@NonNull final TabCloseListener listener) {
    model.addCloseTabListener(listener);
  }

  @Override
  public final void removeCloseTabListener(@NonNull final TabCloseListener listener) {
    model.removeCloseTabListener(listener);
  }

  @Override
  public final void addTabPreviewListener(@NonNull final TabPreviewListener listener) {
    model.addTabPreviewListener(listener);
  }

  @Override
  public final void removeTabPreviewListener(@NonNull final TabPreviewListener listener) {
    model.removeTabPreviewListener(listener);
  }

  @Override
  public final boolean isAnimationRunning() {
    return layout != null && layout.isAnimationRunning();
  }

  @Nullable
  @Override
  public final ViewGroup getTabContainer() {
    return layout != null ? layout.getTabContainer() : null;
  }

  @Override
  public final Toolbar[] getToolbars() {
    return layout != null ? layout.getToolbars() : null;
  }

  @Nullable
  @Override
  public final Menu getToolbarMenu() {
    return layout != null ? layout.getToolbarMenu() : null;
  }

  @Override
  public final boolean onTouchEvent(final MotionEvent event) {
    return (layout != null && layout.handleTouchEvent(event)) || super.onTouchEvent(event);
  }

  @Override
  public final Parcelable onSaveInstanceState() {
    Parcelable superState = super.onSaveInstanceState();
    TabSwitcherState savedState = new TabSwitcherState(superState);
    savedState.layoutPolicy = layoutPolicy;
    savedState.modelState = new Bundle();

    Pair<Integer, Float> pair = null;
    if (getCount() > 0 && layout != null) {
      pair = layout.detachLayout(true);
    }

    if (pair != null) {
      savedState.modelState
        .putInt(TabSwitcherModel.FIRST_VISIBLE_TAB_INDEX_EXTRA, pair.first);
      savedState.modelState
        .putFloat(TabSwitcherModel.FIRST_VISIBLE_TAB_POSITION_EXTRA, pair.second);
      model.setFirstVisibleTabIndex(pair.first);
      model.setFirstVisibleTabPosition(pair.second);
    } else {
      model.setFirstVisibleTabPosition(-1);
      model.setFirstVisibleTabIndex(-1);
    }

    if (layout != null) {
      model.removeListener(layout);
      layout = null;
    }
    executePendingAction();
    getViewTreeObserver().addOnGlobalLayoutListener(
      new LayoutListenerWrapper(this, createGlobalLayoutListener(true)));
    model.saveInstanceState(savedState.modelState);
    return savedState;
  }

  @Override
  public final void onRestoreInstanceState(final Parcelable state) {
    if (state instanceof TabSwitcherState) {
      TabSwitcherState savedState = (TabSwitcherState) state;
      this.layoutPolicy = savedState.layoutPolicy;
      model.restoreInstanceState(savedState.modelState);
      super.onRestoreInstanceState(savedState.getSuperState());
    } else {
      super.onRestoreInstanceState(state);
    }
  }

}
