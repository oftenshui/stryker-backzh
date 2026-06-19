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

import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import de.mrapp.android.tabswitcher.R;
import de.mrapp.android.tabswitcher.Tab;
import de.mrapp.android.tabswitcher.TabSwitcher;
import de.mrapp.android.tabswitcher.layout.phone.PhoneTabViewHolder;
import de.mrapp.android.util.view.AttachedViewRecycler;

import static de.mrapp.android.util.Condition.ensureAtLeast;
import static de.mrapp.android.util.Condition.ensureNotNull;

public class TabItem {

  public static class Comparator implements java.util.Comparator<TabItem> {

    private final TabSwitcher tabSwitcher;

    public Comparator(@NonNull final TabSwitcher tabSwitcher) {
      ensureNotNull(tabSwitcher, "The tab switcher may not be null");
      this.tabSwitcher = tabSwitcher;
    }

    @Override
    public int compare(final TabItem o1, final TabItem o2) {
      Tab tab1 = o1.getTab();
      Tab tab2 = o2.getTab();
      int index1 = tabSwitcher.indexOf(tab1);
      int index2 = tabSwitcher.indexOf(tab2);

      if (index2 == -1) {
        index2 = o2.getIndex();
      }

      if (index1 == -1 || index2 == -1) {
        throw new RuntimeException("Tab not contained by tab switcher");
      }

      return index1 < index2 ? -1 : 1;
    }

  }

  private final int index;

  private final Tab tab;

  private View view;

  private PhoneTabViewHolder viewHolder;

  private Tag tag;

  public TabItem(final int index, @NonNull final Tab tab) {
    ensureAtLeast(index, 0, "The index must be at least 0");
    ensureNotNull(tab, "The tab may not be null");
    this.index = index;
    this.tab = tab;
    this.view = null;
    this.viewHolder = null;
    this.tag = new Tag();
  }

  @NonNull
  public static TabItem create(@NonNull final Model model,
                               @NonNull final AttachedViewRecycler<TabItem, ?> viewRecycler,
                               final int index) {
    Tab tab = model.getTab(index);
    return create(viewRecycler, index, tab);
  }

  @NonNull
  public static TabItem create(@NonNull final AttachedViewRecycler<TabItem, ?> viewRecycler,
                               final int index, @NonNull final Tab tab) {
    TabItem tabItem = new TabItem(index, tab);
    View view = viewRecycler.getView(tabItem);

    if (view != null) {
      tabItem.setView(view);
      tabItem.setViewHolder((PhoneTabViewHolder) view.getTag(R.id.tag_view_holder));
      Tag tag = (Tag) view.getTag(R.id.tag_properties);

      if (tag != null) {
        tabItem.setTag(tag);
      }
    }

    return tabItem;
  }

  public final int getIndex() {
    return index;
  }

  @NonNull
  public final Tab getTab() {
    return tab;
  }

  public final View getView() {
    return view;
  }

  public final void setView(@Nullable final View view) {
    this.view = view;
  }

  public final PhoneTabViewHolder getViewHolder() {
    return viewHolder;
  }

  public final void setViewHolder(@Nullable final PhoneTabViewHolder viewHolder) {
    this.viewHolder = viewHolder;
  }

  @NonNull
  public final Tag getTag() {
    return tag;
  }

  public final void setTag(@NonNull final Tag tag) {
    ensureNotNull(tag, "The tag may not be null");
    this.tag = tag;
  }

  public final boolean isInflated() {
    return view != null && viewHolder != null;
  }

  public final boolean isVisible() {
    return tag.getState() != State.HIDDEN || tag.isClosing();
  }

  @Override
  public final String toString() {
    return "TabItem [index = " + index + "]";
  }

  @Override
  public final int hashCode() {
    return tab.hashCode();
  }

  @Override
  public final boolean equals(final Object obj) {
    if (obj == null)
      return false;
    if (obj.getClass() != getClass())
      return false;
    TabItem other = (TabItem) obj;
    return tab.equals(other.tab);
  }

}
