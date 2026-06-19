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
package de.mrapp.android.tabswitcher.model;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import androidx.annotation.*;
import androidx.appcompat.widget.Toolbar.OnMenuItemClickListener;
import androidx.core.content.ContextCompat;
import de.mrapp.android.tabswitcher.*;
import de.mrapp.android.tabswitcher.layout.ChildRecyclerAdapter;
import de.mrapp.android.util.logging.LogLevel;

import java.util.*;

import static de.mrapp.android.util.Condition.ensureNotEqual;
import static de.mrapp.android.util.Condition.ensureNotNull;

public class TabSwitcherModel implements Model, Restorable {

  public static final String FIRST_VISIBLE_TAB_INDEX_EXTRA =
    TabSwitcherModel.class.getName() + "::FirstVisibleIndex";

  public static final String FIRST_VISIBLE_TAB_POSITION_EXTRA =
    TabSwitcherModel.class.getName() + "::FirstVisiblePosition";

  private static final String LOG_LEVEL_EXTRA = TabSwitcherModel.class.getName() + "::LogLevel";

  private static final String TABS_EXTRA = TabSwitcherModel.class.getName() + "::Tabs";

  private static final String SWITCHER_SHOWN_EXTRA =
    TabSwitcherModel.class.getName() + "::SwitcherShown";

  private static final String SELECTED_TAB_EXTRA =
    TabSwitcherModel.class.getName() + "::SelectedTab";

  private static final String PADDING_EXTRA = TabSwitcherModel.class.getName() + "::Padding";

  private static final String TAB_ICON_ID_EXTRA =
    TabSwitcherModel.class.getName() + "::TabIconId";

  private static final String TAB_ICON_BITMAP_EXTRA =
    TabSwitcherModel.class.getName() + "::TabIconBitmap";

  private static final String TAB_BACKGROUND_COLOR_EXTRA =
    TabSwitcherModel.class.getName() + "::TabBackgroundColor";

  private static final String TAB_TITLE_TEXT_COLOR_EXTRA =
    TabSwitcherModel.class.getName() + "::TabTitleTextColor";

  private static final String TAB_CLOSE_BUTTON_ICON_ID_EXTRA =
    TabSwitcherModel.class.getName() + "::TabCloseButtonIconId";

  private static final String TAB_CLOSE_BUTTON_ICON_BITMAP_EXTRA =
    TabSwitcher.class.getName() + "::TabCloseButtonIconBitmap";

  private static final String SHOW_TOOLBARS_EXTRA =
    TabSwitcher.class.getName() + "::ShowToolbars";

  private static final String TOOLBAR_TITLE_EXTRA =
    TabSwitcher.class.getName() + "::ToolbarTitle";

  private final TabSwitcher tabSwitcher;

  private final Set<Listener> listeners;

  private int firstVisibleTabIndex;

  private float firstVisibleTabPosition;

  private LogLevel logLevel;

  private ArrayList<Tab> tabs;

  private boolean switcherShown;

  private Tab selectedTab;

  private TabSwitcherDecorator decorator;

  private ChildRecyclerAdapter childRecyclerAdapter;

  private int[] padding;

  private int tabIconId;

  private Bitmap tabIconBitmap;

  private ColorStateList tabBackgroundColor;

  private ColorStateList tabTitleTextColor;

  private int tabCloseButtonIconId;

  private Bitmap tabCloseButtonIconBitmap;

  private boolean showToolbars;

  private CharSequence toolbarTitle;

  private Drawable toolbarNavigationIcon;

  private OnClickListener toolbarNavigationIconListener;

  private int toolbarMenuId;

  private OnMenuItemClickListener toolbarMenuItemListener;

  private final Set<TabCloseListener> tabCloseListeners;

  private final Set<TabPreviewListener> tabPreviewListeners;

  private int indexOfOrThrowException(@NonNull final Tab tab) {
    int index = indexOf(tab);
    ensureNotEqual(index, -1, "No such tab: " + tab, NoSuchElementException.class);
    return index;
  }

  private boolean setSwitcherShown(final boolean shown) {
    if (switcherShown != shown) {
      switcherShown = shown;
      return true;
    }

    return false;
  }

  private void notifyOnLogLevelChanged(@NonNull final LogLevel logLevel) {
    for (Listener listener : listeners) {
      listener.onLogLevelChanged(logLevel);
    }
  }

  private void notifyOnDecoratorChanged(@NonNull final TabSwitcherDecorator decorator) {
    for (Listener listener : listeners) {
      listener.onDecoratorChanged(decorator);
    }
  }

  private void notifyOnSwitcherShown() {
    for (Listener listener : listeners) {
      listener.onSwitcherShown();
    }
  }

  private void notifyOnSwitcherHidden() {
    for (Listener listener : listeners) {
      listener.onSwitcherHidden();
    }
  }

  private void notifyOnSelectionChanged(final int previousIndex, final int index,
                                        @Nullable final Tab tab, final boolean switcherHidden) {
    for (Listener listener : listeners) {
      listener.onSelectionChanged(previousIndex, index, tab, switcherHidden);
    }
  }

  private void notifyOnTabAdded(final int index, @NonNull final Tab tab,
                                final int previousSelectedTabIndex, final int selectedTabIndex,
                                final boolean switcherVisibilityChanged,
                                @NonNull final Animation animation) {
    for (Listener listener : listeners) {
      listener.onTabAdded(index, tab, previousSelectedTabIndex, selectedTabIndex,
        switcherVisibilityChanged, animation);
    }
  }

  private void notifyOnAllTabsAdded(final int index, @NonNull final Tab[] tabs,
                                    final int previousSelectedTabIndex,
                                    final int selectedTabIndex,
                                    @NonNull final Animation animation) {
    for (Listener listener : listeners) {
      listener.onAllTabsAdded(index, tabs, previousSelectedTabIndex, selectedTabIndex,
        animation);
    }
  }

  private void notifyOnTabRemoved(final int index, @NonNull final Tab tab,
                                  final int previousSelectedTabIndex, final int selectedTabIndex,
                                  @NonNull final Animation animation) {
    for (Listener listener : listeners) {
      listener.onTabRemoved(index, tab, previousSelectedTabIndex, selectedTabIndex,
        animation);
    }
  }

  private void notifyOnAllTabsRemoved(@NonNull final Tab[] tabs,
                                      @NonNull final Animation animation) {
    for (Listener listener : listeners) {
      listener.onAllTabsRemoved(tabs, animation);
    }
  }

  private void notifyOnPaddingChanged(final int left, final int top, final int right,
                                      final int bottom) {
    for (Listener listener : listeners) {
      listener.onPaddingChanged(left, top, right, bottom);
    }
  }

  private void notifyOnTabIconChanged(@Nullable final Drawable icon) {
    for (Listener listener : listeners) {
      listener.onTabIconChanged(icon);
    }
  }

  private void notifyOnTabBackgroundColorChanged(@Nullable final ColorStateList colorStateList) {
    for (Listener listener : listeners) {
      listener.onTabBackgroundColorChanged(colorStateList);
    }
  }

  private void notifyOnTabTitleColorChanged(@Nullable final ColorStateList colorStateList) {
    for (Listener listener : listeners) {
      listener.onTabTitleColorChanged(colorStateList);
    }
  }

  private void notifyOnTabCloseButtonIconChanged(@Nullable final Drawable icon) {
    for (Listener listener : listeners) {
      listener.onTabCloseButtonIconChanged(icon);
    }
  }

  private void notifyOnToolbarVisibilityChanged(final boolean visible) {
    for (Listener listener : listeners) {
      listener.onToolbarVisibilityChanged(visible);
    }
  }

  private void notifyOnToolbarTitleChanged(@Nullable final CharSequence title) {
    for (Listener listener : listeners) {
      listener.onToolbarTitleChanged(title);
    }
  }

  private void notifyOnToolbarMenuInflated(@MenuRes final int resourceId,
                                           @Nullable final OnMenuItemClickListener menuItemClickListener) {
    for (Listener listener : listeners) {
      listener.onToolbarMenuInflated(resourceId, menuItemClickListener);
    }
  }

  private void notifyOnToolbarNavigationIconChanged(@Nullable final Drawable icon,
                                                    @Nullable final OnClickListener clickListener) {
    for (Listener listener : listeners) {
      listener.onToolbarNavigationIconChanged(icon, clickListener);
    }
  }

  public TabSwitcherModel(@NonNull final TabSwitcher tabSwitcher) {
    ensureNotNull(tabSwitcher, "The tab switcher may not be null");
    this.tabSwitcher = tabSwitcher;
    this.listeners = new LinkedHashSet<>();
    this.firstVisibleTabIndex = -1;
    this.firstVisibleTabPosition = -1;
    this.logLevel = LogLevel.INFO;
    this.tabs = new ArrayList<>();
    this.switcherShown = false;
    this.selectedTab = null;
    this.decorator = null;
    this.childRecyclerAdapter = null;
    this.padding = new int[]{0, 0, 0, 0};
    this.tabIconId = -1;
    this.tabIconBitmap = null;
    this.tabBackgroundColor = null;
    this.tabTitleTextColor = null;
    this.tabCloseButtonIconId = -1;
    this.tabCloseButtonIconBitmap = null;
    this.showToolbars = false;
    this.toolbarTitle = null;
    this.toolbarNavigationIcon = null;
    this.toolbarNavigationIconListener = null;
    this.toolbarMenuId = -1;
    this.toolbarMenuItemListener = null;
    this.tabCloseListeners = new LinkedHashSet<>();
    this.tabPreviewListeners = new LinkedHashSet<>();
  }

  public final void addListener(@NonNull final Listener listener) {
    ensureNotNull(listener, "The listener may not be null");
    listeners.add(listener);
  }

  public final void removeListener(@NonNull final Listener listener) {
    ensureNotNull(listener, "The listener may not be null");
    listeners.remove(listener);
  }

  public final int getFirstVisibleTabIndex() {
    return firstVisibleTabIndex;
  }

  public final void setFirstVisibleTabIndex(final int firstVisibleTabIndex) {
    this.firstVisibleTabIndex = firstVisibleTabIndex;
  }

  public final float getFirstVisibleTabPosition() {
    return firstVisibleTabPosition;
  }

  public final void setFirstVisibleTabPosition(final float firstVisibleTabPosition) {
    this.firstVisibleTabPosition = firstVisibleTabPosition;
  }

  @Nullable
  public final OnClickListener getToolbarNavigationIconListener() {
    return toolbarNavigationIconListener;
  }

  @MenuRes
  public final int getToolbarMenuId() {
    return toolbarMenuId;
  }

  @Nullable
  public final OnMenuItemClickListener getToolbarMenuItemListener() {
    return toolbarMenuItemListener;
  }

  @NonNull
  public final Set<TabCloseListener> getTabCloseListeners() {
    return tabCloseListeners;
  }

  @NonNull
  public final Set<TabPreviewListener> getTabPreviewListeners() {
    return tabPreviewListeners;
  }

  public final ChildRecyclerAdapter getChildRecyclerAdapter() {
    return childRecyclerAdapter;
  }

  @NonNull
  @Override
  public final Context getContext() {
    return tabSwitcher.getContext();
  }

  @Override
  public final void setDecorator(@NonNull final TabSwitcherDecorator decorator) {
    ensureNotNull(decorator, "The decorator may not be null");
    this.decorator = decorator;
    this.childRecyclerAdapter = new ChildRecyclerAdapter(tabSwitcher, decorator);
    notifyOnDecoratorChanged(decorator);
  }

  @Override
  public final TabSwitcherDecorator getDecorator() {
    return decorator;
  }

  @NonNull
  @Override
  public final LogLevel getLogLevel() {
    return logLevel;
  }

  @Override
  public final void setLogLevel(@NonNull final LogLevel logLevel) {
    ensureNotNull(logLevel, "The log level may not be null");
    this.logLevel = logLevel;
    notifyOnLogLevelChanged(logLevel);
  }

  @Override
  public final boolean isEmpty() {
    return tabs.isEmpty();
  }

  @Override
  public final int getCount() {
    return tabs.size();
  }

  @NonNull
  @Override
  public final Tab getTab(final int index) {
    return tabs.get(index);
  }

  @Override
  public final int indexOf(@NonNull final Tab tab) {
    ensureNotNull(tab, "The tab may not be null");
    return tabs.indexOf(tab);
  }

  @Override
  public final void addTab(@NonNull Tab tab) {
    addTab(tab, getCount());
  }

  @Override
  public final void addTab(@NonNull final Tab tab, final int index) {
    addTab(tab, index, new SwipeAnimation.Builder().create());
  }

  @Override
  public final void addTab(@NonNull final Tab tab, final int index,
                           @NonNull final Animation animation) {
    ensureNotNull(tab, "The tab may not be null");
    ensureNotNull(animation, "The animation may not be null");
    tabs.add(index, tab);
    int previousSelectedTabIndex = getSelectedTabIndex();
    int selectedTabIndex = previousSelectedTabIndex;
    boolean switcherVisibilityChanged = false;

    if (previousSelectedTabIndex == -1) {
      selectedTab = tab;
      selectedTabIndex = index;
    }

    if (animation instanceof RevealAnimation) {
      selectedTab = tab;
      selectedTabIndex = index;
      switcherVisibilityChanged = setSwitcherShown(false);
    }

    if (animation instanceof PeekAnimation) {
      switcherVisibilityChanged = setSwitcherShown(true);
    }

    notifyOnTabAdded(index, tab, previousSelectedTabIndex, selectedTabIndex,
      switcherVisibilityChanged, animation);
  }

  @Override
  public final void addAllTabs(@NonNull final Collection<? extends Tab> tabs) {
    addAllTabs(tabs, getCount());
  }

  @Override
  public final void addAllTabs(@NonNull final Collection<? extends Tab> tabs, final int index) {
    addAllTabs(tabs, index, new SwipeAnimation.Builder().create());
  }

  @Override
  public final void addAllTabs(@NonNull final Collection<? extends Tab> tabs, final int index,
                               @NonNull final Animation animation) {
    ensureNotNull(tabs, "The collection may not be null");
    Tab[] array = new Tab[tabs.size()];
    tabs.toArray(array);
    addAllTabs(array, index, animation);
  }

  @Override
  public final void addAllTabs(@NonNull final Tab[] tabs) {
    addAllTabs(tabs, getCount());
  }

  @Override
  public final void addAllTabs(@NonNull final Tab[] tabs, final int index) {
    addAllTabs(tabs, index, new SwipeAnimation.Builder().create());
  }

  @Override
  public final void addAllTabs(@NonNull final Tab[] tabs, final int index,
                               @NonNull final Animation animation) {
    ensureNotNull(tabs, "The array may not be null");
    ensureNotNull(animation, "The animation may not be null");

    if (tabs.length > 0) {
      int previousSelectedTabIndex = getSelectedTabIndex();
      int selectedTabIndex = previousSelectedTabIndex;

      for (int i = 0; i < tabs.length; i++) {
        Tab tab = tabs[i];
        this.tabs.add(index + i, tab);
      }

      if (previousSelectedTabIndex == -1) {
        selectedTabIndex = 0;
        selectedTab = tabs[selectedTabIndex];
      }

      notifyOnAllTabsAdded(index, tabs, previousSelectedTabIndex, selectedTabIndex,
        animation);
    }
  }

  @Override
  public final void removeTab(@NonNull final Tab tab) {
    removeTab(tab, new SwipeAnimation.Builder().create());
  }

  @Override
  public final void removeTab(@NonNull final Tab tab, @NonNull final Animation animation) {
    ensureNotNull(tab, "The tab may not be null");
    ensureNotNull(animation, "The animation may not be null");
    int index = indexOfOrThrowException(tab);
    int previousSelectedTabIndex = getSelectedTabIndex();
    int selectedTabIndex = previousSelectedTabIndex;
    tabs.remove(index);

    if (isEmpty()) {
      selectedTabIndex = -1;
      selectedTab = null;
    } else if (index == previousSelectedTabIndex) {
      if (index > 0) {
        selectedTabIndex = index - 1;
      }

      selectedTab = getTab(selectedTabIndex);
    }

    notifyOnTabRemoved(index, tab, previousSelectedTabIndex, selectedTabIndex, animation);

  }

  @Override
  public final void clear() {
    clear(new SwipeAnimation.Builder().create());
  }

  @Override
  public final void clear(@NonNull final Animation animation) {
    ensureNotNull(animation, "The animation may not be null");
    Tab[] result = new Tab[tabs.size()];
    tabs.toArray(result);
    tabs.clear();
    notifyOnAllTabsRemoved(result, animation);
    selectedTab = null;
  }

  @Override
  public final boolean isSwitcherShown() {
    return switcherShown;
  }

  @Override
  public final void showSwitcher() {
    setSwitcherShown(true);
    notifyOnSwitcherShown();
  }

  @Override
  public final void hideSwitcher() {
    setSwitcherShown(false);
    notifyOnSwitcherHidden();
  }

  @Override
  public final void toggleSwitcherVisibility() {
    if (isSwitcherShown()) {
      hideSwitcher();
    } else {
      showSwitcher();
    }
  }

  @Nullable
  @Override
  public final Tab getSelectedTab() {
    return selectedTab;
  }

  @Override
  public final int getSelectedTabIndex() {
    return selectedTab != null ? indexOf(selectedTab) : -1;
  }

  @Override
  public final void selectTab(@NonNull final Tab tab) {
    ensureNotNull(tab, "The tab may not be null");
    int previousIndex = getSelectedTabIndex();
    int index = indexOfOrThrowException(tab);
    selectedTab = tab;
    boolean switcherHidden = setSwitcherShown(false);
    notifyOnSelectionChanged(previousIndex, index, tab, switcherHidden);
  }

  @Override
  public final Iterator<Tab> iterator() {
    return tabs.iterator();
  }

  @Override
  public final void setPadding(final int left, final int top, final int right, final int bottom) {
    padding = new int[]{left, top, right, bottom};
    notifyOnPaddingChanged(left, top, right, bottom);
  }

  @Override
  public final int getPaddingLeft() {
    return padding[0];
  }

  @Override
  public final int getPaddingTop() {
    return padding[1];
  }

  @Override
  public final int getPaddingRight() {
    return padding[2];
  }

  @Override
  public final int getPaddingBottom() {
    return padding[3];
  }

  @Override
  public final int getPaddingStart() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
      return tabSwitcher.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL ?
        getPaddingRight() : getPaddingLeft();
    }

    return getPaddingLeft();
  }

  @Override
  public final int getPaddingEnd() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
      return tabSwitcher.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL ?
        getPaddingLeft() : getPaddingRight();
    }

    return getPaddingRight();
  }

  @Nullable
  @Override
  public final Drawable getTabIcon() {
    if (tabIconId != -1) {
      return ContextCompat.getDrawable(getContext(), tabIconId);
    } else {
      return tabIconBitmap != null ?
        new BitmapDrawable(getContext().getResources(), tabIconBitmap) : null;
    }
  }

  @Override
  public final void setTabIcon(@DrawableRes final int resourceId) {
    this.tabIconId = resourceId;
    this.tabIconBitmap = null;
    notifyOnTabIconChanged(getTabIcon());
  }

  @Override
  public final void setTabIcon(@Nullable final Bitmap icon) {
    this.tabIconId = -1;
    this.tabIconBitmap = icon;
    notifyOnTabIconChanged(getTabIcon());
  }

  @Nullable
  @Override
  public final ColorStateList getTabBackgroundColor() {
    return tabBackgroundColor;
  }

  @Override
  public final void setTabBackgroundColor(@ColorInt final int color) {
    setTabBackgroundColor(color != -1 ? ColorStateList.valueOf(color) : null);
  }

  @Override
  public final void setTabBackgroundColor(@Nullable final ColorStateList colorStateList) {
    this.tabBackgroundColor = colorStateList;
    notifyOnTabBackgroundColorChanged(colorStateList);
  }

  @Nullable
  @Override
  public final ColorStateList getTabTitleTextColor() {
    return tabTitleTextColor;
  }

  @Override
  public final void setTabTitleTextColor(@ColorInt final int color) {
    setTabTitleTextColor(color != -1 ? ColorStateList.valueOf(color) : null);
  }

  @Override
  public final void setTabTitleTextColor(@Nullable final ColorStateList colorStateList) {
    this.tabTitleTextColor = colorStateList;
    notifyOnTabTitleColorChanged(colorStateList);
  }

  @Nullable
  @Override
  public final Drawable getTabCloseButtonIcon() {
    if (tabCloseButtonIconId != -1) {
      return ContextCompat.getDrawable(getContext(), tabCloseButtonIconId);
    } else {
      return tabCloseButtonIconBitmap != null ?
        new BitmapDrawable(getContext().getResources(), tabCloseButtonIconBitmap) :
        null;
    }
  }

  @Override
  public final void setTabCloseButtonIcon(@DrawableRes final int resourceId) {
    tabCloseButtonIconId = resourceId;
    tabCloseButtonIconBitmap = null;
    notifyOnTabCloseButtonIconChanged(getTabCloseButtonIcon());
  }

  @Override
  public final void setTabCloseButtonIcon(@Nullable final Bitmap icon) {
    tabCloseButtonIconId = -1;
    tabCloseButtonIconBitmap = icon;
    notifyOnTabCloseButtonIconChanged(getTabCloseButtonIcon());
  }

  @Override
  public final boolean areToolbarsShown() {
    return showToolbars;
  }

  @Override
  public final void showToolbars(final boolean show) {
    this.showToolbars = show;
    notifyOnToolbarVisibilityChanged(show);
  }

  @Nullable
  @Override
  public final CharSequence getToolbarTitle() {
    return toolbarTitle;
  }

  @Override
  public void setToolbarTitle(@StringRes final int resourceId) {
    setToolbarTitle(getContext().getText(resourceId));
  }

  @Override
  public final void setToolbarTitle(@Nullable final CharSequence title) {
    this.toolbarTitle = title;
    notifyOnToolbarTitleChanged(title);
  }

  @Nullable
  @Override
  public final Drawable getToolbarNavigationIcon() {
    return toolbarNavigationIcon;
  }

  @Override
  public final void setToolbarNavigationIcon(@DrawableRes final int resourceId,
                                             @Nullable final OnClickListener listener) {
    setToolbarNavigationIcon(ContextCompat.getDrawable(getContext(), resourceId), listener);
  }

  @Override
  public final void setToolbarNavigationIcon(@Nullable final Drawable icon,
                                             @Nullable final OnClickListener listener) {
    this.toolbarNavigationIcon = icon;
    this.toolbarNavigationIconListener = listener;
    notifyOnToolbarNavigationIconChanged(icon, listener);
  }

  @Override
  public final void inflateToolbarMenu(@MenuRes final int resourceId,
                                       @Nullable final OnMenuItemClickListener listener) {
    this.toolbarMenuId = resourceId;
    this.toolbarMenuItemListener = listener;
    notifyOnToolbarMenuInflated(resourceId, listener);
  }

  @Override
  public final void addCloseTabListener(@NonNull final TabCloseListener listener) {
    ensureNotNull(listener, "The listener may not be null");
    tabCloseListeners.add(listener);
  }

  @Override
  public final void removeCloseTabListener(@NonNull final TabCloseListener listener) {
    ensureNotNull(listener, "The listener may not be null");
    tabCloseListeners.remove(listener);
  }

  @Override
  public final void addTabPreviewListener(@NonNull final TabPreviewListener listener) {
    ensureNotNull(listener, "The listener may not be null");
    tabPreviewListeners.add(listener);
  }

  @Override
  public final void removeTabPreviewListener(@NonNull final TabPreviewListener listener) {
    ensureNotNull(listener, "The listener may not be null");
    tabPreviewListeners.remove(listener);
  }

  @Override
  public final void saveInstanceState(@NonNull final Bundle outState) {
    outState.putSerializable(LOG_LEVEL_EXTRA, logLevel);
    outState.putParcelableArrayList(TABS_EXTRA, tabs);
    outState.putBoolean(SWITCHER_SHOWN_EXTRA, switcherShown);
    outState.putParcelable(SELECTED_TAB_EXTRA, selectedTab);
    outState.putIntArray(PADDING_EXTRA, padding);
    outState.putInt(TAB_ICON_ID_EXTRA, tabIconId);
    outState.putParcelable(TAB_ICON_BITMAP_EXTRA, tabIconBitmap);
    outState.putParcelable(TAB_BACKGROUND_COLOR_EXTRA, tabBackgroundColor);
    outState.putParcelable(TAB_TITLE_TEXT_COLOR_EXTRA, tabTitleTextColor);
    outState.putInt(TAB_CLOSE_BUTTON_ICON_ID_EXTRA, tabCloseButtonIconId);
    outState.putParcelable(TAB_CLOSE_BUTTON_ICON_BITMAP_EXTRA, tabCloseButtonIconBitmap);
    outState.putBoolean(SHOW_TOOLBARS_EXTRA, showToolbars);
    outState.putCharSequence(TOOLBAR_TITLE_EXTRA, toolbarTitle);
    childRecyclerAdapter.saveInstanceState(outState);
  }

  @Override
  public final void restoreInstanceState(@Nullable final Bundle savedInstanceState) {
    if (savedInstanceState != null) {
      firstVisibleTabIndex = savedInstanceState.getInt(FIRST_VISIBLE_TAB_INDEX_EXTRA, -1);
      firstVisibleTabPosition =
        savedInstanceState.getFloat(FIRST_VISIBLE_TAB_POSITION_EXTRA, -1);
      logLevel = (LogLevel) savedInstanceState.getSerializable(LOG_LEVEL_EXTRA);
      tabs = savedInstanceState.getParcelableArrayList(TABS_EXTRA);
      switcherShown = savedInstanceState.getBoolean(SWITCHER_SHOWN_EXTRA);
      selectedTab = savedInstanceState.getParcelable(SELECTED_TAB_EXTRA);
      padding = savedInstanceState.getIntArray(PADDING_EXTRA);
      tabIconId = savedInstanceState.getInt(TAB_ICON_ID_EXTRA);
      tabIconBitmap = savedInstanceState.getParcelable(TAB_ICON_BITMAP_EXTRA);
      tabBackgroundColor = savedInstanceState.getParcelable(TAB_BACKGROUND_COLOR_EXTRA);
      tabTitleTextColor = savedInstanceState.getParcelable(TAB_TITLE_TEXT_COLOR_EXTRA);
      tabCloseButtonIconId = savedInstanceState.getInt(TAB_CLOSE_BUTTON_ICON_ID_EXTRA);
      tabCloseButtonIconBitmap =
        savedInstanceState.getParcelable(TAB_CLOSE_BUTTON_ICON_BITMAP_EXTRA);
      showToolbars = savedInstanceState.getBoolean(SHOW_TOOLBARS_EXTRA);
      toolbarTitle = savedInstanceState.getCharSequence(TOOLBAR_TITLE_EXTRA);
      childRecyclerAdapter.restoreInstanceState(savedInstanceState);
    }
  }

}
