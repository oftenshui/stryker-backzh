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
import android.graphics.drawable.Drawable;
import android.view.View.OnClickListener;
import androidx.annotation.*;
import androidx.appcompat.widget.Toolbar.OnMenuItemClickListener;
import de.mrapp.android.tabswitcher.*;
import de.mrapp.android.tabswitcher.SwipeAnimation.SwipeDirection;
import de.mrapp.android.util.logging.LogLevel;

import java.util.Collection;
import java.util.NoSuchElementException;

public interface Model extends Iterable<Tab> {

  interface Listener {

    void onLogLevelChanged(@NonNull LogLevel logLevel);

    void onDecoratorChanged(@NonNull TabSwitcherDecorator decorator);

    void onSwitcherShown();

    void onSwitcherHidden();

    void onSelectionChanged(int previousIndex, int index, @Nullable Tab selectedTab,
                            boolean switcherHidden);

    void onTabAdded(int index, @NonNull Tab tab, int previousSelectedTabIndex,
                    int selectedTabIndex, boolean switcherVisibilityChanged,
                    @NonNull Animation animation);

    void onAllTabsAdded(int index, @NonNull Tab[] tabs, int previousSelectedTabIndex,
                        int selectedTabIndex, @NonNull Animation animation);

    void onTabRemoved(int index, @NonNull Tab tab, int previousSelectedTabIndex,
                      int selectedTabIndex, @NonNull Animation animation);

    void onAllTabsRemoved(@NonNull Tab[] tabs, @NonNull Animation animation);

    void onPaddingChanged(int left, int top, int right, int bottom);

    void onTabIconChanged(@Nullable Drawable icon);

    void onTabBackgroundColorChanged(@Nullable ColorStateList colorStateList);

    void onTabTitleColorChanged(@Nullable ColorStateList colorStateList);

    void onTabCloseButtonIconChanged(@Nullable Drawable icon);

    void onToolbarVisibilityChanged(boolean visible);

    void onToolbarTitleChanged(@Nullable CharSequence title);

    void onToolbarNavigationIconChanged(@Nullable Drawable icon,
                                        @Nullable OnClickListener listener);

    void onToolbarMenuInflated(@MenuRes int resourceId,
                               @Nullable OnMenuItemClickListener listener);

  }

  @NonNull
  Context getContext();

  void setDecorator(@NonNull TabSwitcherDecorator decorator);

  TabSwitcherDecorator getDecorator();

  @NonNull
  LogLevel getLogLevel();

  void setLogLevel(@NonNull LogLevel logLevel);

  boolean isEmpty();

  int getCount();

  @NonNull
  Tab getTab(int index);

  int indexOf(@NonNull Tab tab);

  void addTab(@NonNull Tab tab);

  void addTab(@NonNull Tab tab, int index);

  void addTab(@NonNull Tab tab, int index, @NonNull Animation animation);

  void addAllTabs(@NonNull Collection<? extends Tab> tabs);

  void addAllTabs(@NonNull Collection<? extends Tab> tabs, int index);

  void addAllTabs(@NonNull Collection<? extends Tab> tabs, int index,
                  @NonNull Animation animation);

  void addAllTabs(@NonNull Tab[] tabs);

  void addAllTabs(@NonNull Tab[] tabs, int index);

  void addAllTabs(@NonNull Tab[] tabs, int index, @NonNull Animation animation);

  void removeTab(@NonNull Tab tab);

  void removeTab(@NonNull Tab tab, @NonNull Animation animation);

  void clear();

  void clear(@NonNull Animation animation);

  boolean isSwitcherShown();

  void showSwitcher();

  void hideSwitcher();

  void toggleSwitcherVisibility();

  @Nullable
  Tab getSelectedTab();

  int getSelectedTabIndex();

  void selectTab(@NonNull Tab tab);

  void setPadding(int left, int top, int right, int bottom);

  int getPaddingLeft();

  int getPaddingTop();

  int getPaddingRight();

  int getPaddingBottom();

  int getPaddingStart();

  int getPaddingEnd();

  @Nullable
  Drawable getTabIcon();

  void setTabIcon(@DrawableRes int resourceId);

  void setTabIcon(@Nullable Bitmap icon);

  @Nullable
  ColorStateList getTabBackgroundColor();

  void setTabBackgroundColor(@ColorInt int color);

  void setTabBackgroundColor(@Nullable ColorStateList colorStateList);

  @Nullable
  ColorStateList getTabTitleTextColor();

  void setTabTitleTextColor(@ColorInt int color);

  void setTabTitleTextColor(@Nullable ColorStateList colorStateList);

  @Nullable
  Drawable getTabCloseButtonIcon();

  void setTabCloseButtonIcon(@DrawableRes int resourceId);

  void setTabCloseButtonIcon(@Nullable final Bitmap icon);

  boolean areToolbarsShown();

  void showToolbars(boolean show);

  @Nullable
  CharSequence getToolbarTitle();

  void setToolbarTitle(@StringRes int resourceId);

  void setToolbarTitle(@Nullable CharSequence title);

  @Nullable
  Drawable getToolbarNavigationIcon();

  void setToolbarNavigationIcon(@DrawableRes int resourceId, @Nullable OnClickListener listener);

  void setToolbarNavigationIcon(@Nullable Drawable icon, @Nullable OnClickListener listener);

  void inflateToolbarMenu(@MenuRes int resourceId, @Nullable OnMenuItemClickListener listener);

  void addCloseTabListener(@NonNull TabCloseListener listener);

  void removeCloseTabListener(@NonNull TabCloseListener listener);

  void addTabPreviewListener(@NonNull TabPreviewListener listener);

  void removeTabPreviewListener(@NonNull TabPreviewListener listener);

}
