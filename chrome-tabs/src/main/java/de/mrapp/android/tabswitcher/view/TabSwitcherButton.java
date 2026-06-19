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
package de.mrapp.android.tabswitcher.view;

import android.content.Context;
import android.util.AttributeSet;
import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageButton;
import de.mrapp.android.tabswitcher.*;
import de.mrapp.android.tabswitcher.drawable.TabSwitcherDrawable;
import de.mrapp.android.util.ThemeUtil;
import de.mrapp.android.util.ViewUtil;

public class TabSwitcherButton extends AppCompatImageButton implements TabSwitcherListener {

  private TabSwitcherDrawable drawable;

  private void initialize() {
    drawable = new TabSwitcherDrawable(getContext());
    setImageDrawable(drawable);
    ViewUtil.setBackground(this,
      ThemeUtil.getDrawable(getContext(), R.attr.selectableItemBackgroundBorderless));
    setContentDescription(null);
    setClickable(true);
    setFocusable(true);
  }

  public TabSwitcherButton(@NonNull final Context context) {
    this(context, null);
  }

  public TabSwitcherButton(@NonNull final Context context,
                           @Nullable final AttributeSet attributeSet) {
    super(context, attributeSet);
    initialize();
  }

  public TabSwitcherButton(@NonNull final Context context,
                           @Nullable final AttributeSet attributeSet,
                           @AttrRes final int defaultStyle) {
    super(context, attributeSet, defaultStyle);
    initialize();
  }

  public final void setCount(final int count) {
    drawable.setCount(count);
  }

  @Override
  public final void onSwitcherShown(@NonNull final TabSwitcher tabSwitcher) {
    drawable.onSwitcherShown(tabSwitcher);
  }

  @Override
  public final void onSwitcherHidden(@NonNull final TabSwitcher tabSwitcher) {
    drawable.onSwitcherHidden(tabSwitcher);
  }

  @Override
  public final void onSelectionChanged(@NonNull final TabSwitcher tabSwitcher,
                                       final int selectedTabIndex,
                                       @Nullable final Tab selectedTab) {
    drawable.onSelectionChanged(tabSwitcher, selectedTabIndex, selectedTab);
  }

  @Override
  public final void onTabAdded(@NonNull final TabSwitcher tabSwitcher, final int index,
                               @NonNull final Tab tab, @NonNull final Animation animation) {
    drawable.onTabAdded(tabSwitcher, index, tab, animation);
  }

  @Override
  public final void onTabRemoved(@NonNull final TabSwitcher tabSwitcher, final int index,
                                 @NonNull final Tab tab, @NonNull final Animation animation) {
    drawable.onTabRemoved(tabSwitcher, index, tab, animation);
  }

  @Override
  public final void onAllTabsRemoved(@NonNull final TabSwitcher tabSwitcher,
                                     @NonNull final Tab[] tabs,
                                     @NonNull final Animation animation) {
    drawable.onAllTabsRemoved(tabSwitcher, tabs, animation);
  }

}
