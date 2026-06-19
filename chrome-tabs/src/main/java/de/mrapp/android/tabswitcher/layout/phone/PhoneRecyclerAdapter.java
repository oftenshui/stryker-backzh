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

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.FrameLayout.LayoutParams;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.MenuRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar.OnMenuItemClickListener;
import androidx.core.content.ContextCompat;
import androidx.core.util.Pair;
import de.mrapp.android.tabswitcher.*;
import de.mrapp.android.tabswitcher.iterator.AbstractTabItemIterator;
import de.mrapp.android.tabswitcher.iterator.TabItemIterator;
import de.mrapp.android.tabswitcher.model.Model;
import de.mrapp.android.tabswitcher.model.TabItem;
import de.mrapp.android.tabswitcher.model.TabSwitcherModel;
import de.mrapp.android.util.ViewUtil;
import de.mrapp.android.util.logging.LogLevel;
import de.mrapp.android.util.multithreading.AbstractDataBinder;
import de.mrapp.android.util.view.AbstractViewRecycler;
import de.mrapp.android.util.view.AttachedViewRecycler;
import de.mrapp.android.util.view.ViewRecycler;

import static de.mrapp.android.util.Condition.ensureNotNull;

public class PhoneRecyclerAdapter extends AbstractViewRecycler.Adapter<TabItem, Integer>
  implements Tab.Callback, Model.Listener,
  AbstractDataBinder.Listener<Bitmap, Tab, ImageView, TabItem> {

  private final TabSwitcher tabSwitcher;

  private final TabSwitcherModel model;

  private final ViewRecycler<Tab, Void> childViewRecycler;

  private final AbstractDataBinder<Bitmap, Tab, ImageView, TabItem> dataBinder;

  private final int tabInset;

  private final int tabBorderWidth;

  private final int tabTitleContainerHeight;

  private final int tabBackgroundColor;

  private final int tabTitleTextColor;

  private AttachedViewRecycler<TabItem, Integer> viewRecycler;

  private void addChildView(@NonNull final TabItem tabItem) {
    PhoneTabViewHolder viewHolder = tabItem.getViewHolder();
    View view = viewHolder.child;
    Tab tab = tabItem.getTab();

    if (view == null) {
      ViewGroup parent = viewHolder.childContainer;
      Pair<View, ?> pair = childViewRecycler.inflate(tab, parent);
      view = pair.first;
      LayoutParams layoutParams =
        new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
      layoutParams.setMargins(model.getPaddingLeft(), model.getPaddingTop(),
        model.getPaddingRight(), model.getPaddingBottom());
      parent.addView(view, 0, layoutParams);
      viewHolder.child = view;
    } else {
      childViewRecycler.getAdapter().onShowView(model.getContext(), view, tab, false);
    }

    viewHolder.previewImageView.setVisibility(View.GONE);
    viewHolder.previewImageView.setImageBitmap(null);
    viewHolder.borderView.setVisibility(View.GONE);
  }

  private void renderChildView(@NonNull final TabItem tabItem) {
    Tab tab = tabItem.getTab();
    PhoneTabViewHolder viewHolder = tabItem.getViewHolder();
    viewHolder.borderView.setVisibility(View.VISIBLE);

    if (viewHolder.child != null) {
      childViewRecycler.getAdapter().onRemoveView(viewHolder.child, tab);
      dataBinder.load(tab, viewHolder.previewImageView, false, tabItem);
      removeChildView(viewHolder, tab);
    } else {
      dataBinder.load(tab, viewHolder.previewImageView, tabItem);
    }
  }

  private void removeChildView(@NonNull final PhoneTabViewHolder viewHolder,
                               @NonNull final Tab tab) {
    if (viewHolder.childContainer.getChildCount() > 2) {
      viewHolder.childContainer.removeViewAt(0);
    }

    viewHolder.child = null;
    childViewRecycler.remove(tab);
  }

  private void adaptLogLevel() {
    dataBinder.setLogLevel(model.getLogLevel());
  }

  private void adaptTitle(@NonNull final PhoneTabViewHolder viewHolder, @NonNull final Tab tab) {
    viewHolder.titleTextView.setText(tab.getTitle());
  }

  private void adaptIcon(@NonNull final PhoneTabViewHolder viewHolder, @NonNull final Tab tab) {
    Drawable icon = tab.getIcon(model.getContext());
    viewHolder.titleTextView
      .setCompoundDrawablesWithIntrinsicBounds(icon != null ? icon : model.getTabIcon(),
        null, null, null);
  }

  private void adaptCloseButton(@NonNull final PhoneTabViewHolder viewHolder,
                                @NonNull final Tab tab) {
    viewHolder.closeButton.setVisibility(tab.isCloseable() ? View.VISIBLE : View.GONE);
    viewHolder.closeButton.setOnClickListener(
      tab.isCloseable() ? createCloseButtonClickListener(viewHolder.closeButton, tab) :
        null);
  }

  private void adaptCloseButtonIcon(@NonNull final PhoneTabViewHolder viewHolder,
                                    @NonNull final Tab tab) {
    Drawable icon = tab.getCloseButtonIcon(model.getContext());

    if (icon == null) {
      icon = model.getTabCloseButtonIcon();
    }

    if (icon != null) {
      viewHolder.closeButton.setImageDrawable(icon);
    } else {
      viewHolder.closeButton.setImageResource(R.drawable.ic_close_tab_18dp);
    }
  }

  @NonNull
  private OnClickListener createCloseButtonClickListener(@NonNull final ImageButton closeButton,
                                                         @NonNull final Tab tab) {
    return new OnClickListener() {

      @Override
      public void onClick(final View v) {
        if (notifyOnCloseTab(tab)) {
          closeButton.setOnClickListener(null);
          tabSwitcher.removeTab(tab);
        }
      }

    };
  }

  private boolean notifyOnCloseTab(@NonNull final Tab tab) {
    boolean result = true;

    for (TabCloseListener listener : model.getTabCloseListeners()) {
      result &= listener.onCloseTab(tabSwitcher, tab);
    }

    return result;
  }

  private void adaptBackgroundColor(@NonNull final View view,
                                    @NonNull final PhoneTabViewHolder viewHolder,
                                    @NonNull final Tab tab) {
    ColorStateList colorStateList =
      tab.getBackgroundColor() != null ? tab.getBackgroundColor() :
        model.getTabBackgroundColor();
    int color = tabBackgroundColor;

    if (colorStateList != null) {
      int[] stateSet =
        model.getSelectedTab() == tab ? new int[]{android.R.attr.state_selected} :
          new int[]{};
      color = colorStateList.getColorForState(stateSet, colorStateList.getDefaultColor());
    }

    Drawable background = view.getBackground();
    background.setColorFilter(color, PorterDuff.Mode.MULTIPLY);
    Drawable border = viewHolder.borderView.getBackground();
    border.setColorFilter(color, PorterDuff.Mode.MULTIPLY);
  }

  private void adaptTitleTextColor(@NonNull final PhoneTabViewHolder viewHolder,
                                   @NonNull final Tab tab) {
    ColorStateList colorStateList = tab.getTitleTextColor() != null ? tab.getTitleTextColor() :
      model.getTabTitleTextColor();

    if (colorStateList != null) {
      viewHolder.titleTextView.setTextColor(colorStateList);
    } else {
      viewHolder.titleTextView.setTextColor(tabTitleTextColor);
    }
  }

  private void adaptSelectionState(@NonNull final PhoneTabViewHolder viewHolder,
                                   @NonNull final Tab tab) {
    boolean selected = model.getSelectedTab() == tab;
    viewHolder.titleTextView.setSelected(selected);
    viewHolder.closeButton.setSelected(selected);
  }

  private void adaptAllSelectionStates() {
    AbstractTabItemIterator iterator =
      new TabItemIterator.Builder(model, viewRecycler).create();
    TabItem tabItem;

    while ((tabItem = iterator.next()) != null) {
      if (tabItem.isInflated()) {
        Tab tab = tabItem.getTab();
        PhoneTabViewHolder viewHolder = tabItem.getViewHolder();
        adaptSelectionState(viewHolder, tab);
        adaptBackgroundColor(tabItem.getView(), viewHolder, tab);
      }
    }
  }

  private void adaptPadding(@NonNull final PhoneTabViewHolder viewHolder) {
    if (viewHolder.child != null) {
      LayoutParams childLayoutParams = (LayoutParams) viewHolder.child.getLayoutParams();
      childLayoutParams.setMargins(model.getPaddingLeft(), model.getPaddingTop(),
        model.getPaddingRight(), model.getPaddingBottom());
    }

    LayoutParams previewLayoutParams =
      (LayoutParams) viewHolder.previewImageView.getLayoutParams();
    previewLayoutParams
      .setMargins(model.getPaddingLeft(), model.getPaddingTop(), model.getPaddingRight(),
        model.getPaddingBottom());
  }

  @Nullable
  private TabItem getTabItem(@NonNull final Tab tab) {
    ensureNotNull(viewRecycler, "No view recycler has been set", IllegalStateException.class);
    int index = model.indexOf(tab);

    if (index != -1) {
      TabItem tabItem = TabItem.create(model, viewRecycler, index);

      if (tabItem.isInflated()) {
        return tabItem;
      }
    }

    return null;
  }

  public PhoneRecyclerAdapter(@NonNull final TabSwitcher tabSwitcher,
                              @NonNull final TabSwitcherModel model,
                              @NonNull final ViewRecycler<Tab, Void> childViewRecycler) {
    ensureNotNull(tabSwitcher, "The tab switcher may not be null");
    ensureNotNull(model, "The model may not be null");
    ensureNotNull(childViewRecycler, "The child view recycler may not be null");
    this.tabSwitcher = tabSwitcher;
    this.model = model;
    this.childViewRecycler = childViewRecycler;
    this.dataBinder = new PreviewDataBinder(tabSwitcher, childViewRecycler);
    this.dataBinder.addListener(this);
    Resources resources = tabSwitcher.getResources();
    this.tabInset = resources.getDimensionPixelSize(R.dimen.tab_inset);
    this.tabBorderWidth = resources.getDimensionPixelSize(R.dimen.tab_border_width);
    this.tabTitleContainerHeight =
      resources.getDimensionPixelSize(R.dimen.tab_title_container_height);
    this.tabBackgroundColor =
      ContextCompat.getColor(tabSwitcher.getContext(), R.color.tab_background_color);
    this.tabTitleTextColor =
      ContextCompat.getColor(tabSwitcher.getContext(), R.color.tab_title_text_color);
    this.viewRecycler = null;
    adaptLogLevel();
  }

  public final void setViewRecycler(
    @NonNull final AttachedViewRecycler<TabItem, Integer> viewRecycler) {
    ensureNotNull(viewRecycler, "The view recycler may not be null");
    this.viewRecycler = viewRecycler;
  }

  public final void clearCachedPreviews() {
    dataBinder.clearCache();
  }

  @NonNull
  @Override
  public final View onInflateView(@NonNull final LayoutInflater inflater,
                                  @Nullable final ViewGroup parent,
                                  @NonNull final TabItem tabItem, final int viewType,
                                  @NonNull final Integer... params) {
    PhoneTabViewHolder viewHolder = new PhoneTabViewHolder();
    View view = inflater.inflate(R.layout.phone_tab, tabSwitcher.getTabContainer(), false);
    Drawable backgroundDrawable =
      ContextCompat.getDrawable(model.getContext(), R.drawable.phone_tab_background);
    ViewUtil.setBackground(view, backgroundDrawable);
    int padding = tabInset + tabBorderWidth;
    view.setPadding(padding, tabInset, padding, padding);
    viewHolder.titleContainer = (ViewGroup) view.findViewById(R.id.tab_title_container);
    viewHolder.titleTextView = (TextView) view.findViewById(R.id.tab_title_text_view);
    viewHolder.closeButton = (ImageButton) view.findViewById(R.id.close_tab_button);
    viewHolder.childContainer = (ViewGroup) view.findViewById(R.id.child_container);
    viewHolder.previewImageView = (ImageView) view.findViewById(R.id.preview_image_view);
    adaptPadding(viewHolder);
    viewHolder.borderView = view.findViewById(R.id.border_view);
    Drawable borderDrawable =
      ContextCompat.getDrawable(model.getContext(), R.drawable.phone_tab_border);
    ViewUtil.setBackground(viewHolder.borderView, borderDrawable);
    view.setTag(R.id.tag_view_holder, viewHolder);
    tabItem.setView(view);
    tabItem.setViewHolder(viewHolder);
    view.setTag(R.id.tag_properties, tabItem.getTag());
    return view;
  }

  @Override
  public final void onShowView(@NonNull final Context context, @NonNull final View view,
                               @NonNull final TabItem tabItem, final boolean inflated,
                               @NonNull final Integer... params) {
    PhoneTabViewHolder viewHolder = (PhoneTabViewHolder) view.getTag(R.id.tag_view_holder);

    if (!tabItem.isInflated()) {
      tabItem.setView(view);
      tabItem.setViewHolder(viewHolder);
      view.setTag(R.id.tag_properties, tabItem.getTag());
    }

    LayoutParams layoutParams =
      new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
    int borderMargin = -(tabInset + tabBorderWidth);
    int bottomMargin = params.length > 0 && params[0] != -1 ? params[0] : borderMargin;
    layoutParams.leftMargin = borderMargin;
    layoutParams.topMargin = -(tabInset + tabTitleContainerHeight);
    layoutParams.rightMargin = borderMargin;
    layoutParams.bottomMargin = bottomMargin;
    view.setLayoutParams(layoutParams);
    Tab tab = tabItem.getTab();
    tab.addCallback(this);
    adaptTitle(viewHolder, tab);
    adaptIcon(viewHolder, tab);
    adaptCloseButton(viewHolder, tab);
    adaptCloseButtonIcon(viewHolder, tab);
    adaptBackgroundColor(view, viewHolder, tab);
    adaptTitleTextColor(viewHolder, tab);
    adaptSelectionState(viewHolder, tab);

    if (!model.isSwitcherShown()) {
      if (tab == model.getSelectedTab()) {
        addChildView(tabItem);
      }
    } else {
      renderChildView(tabItem);
    }
  }

  @Override
  public final void onRemoveView(@NonNull final View view, @NonNull final TabItem tabItem) {
    PhoneTabViewHolder viewHolder = (PhoneTabViewHolder) view.getTag(R.id.tag_view_holder);
    Tab tab = tabItem.getTab();
    tab.removeCallback(this);
    removeChildView(viewHolder, tab);

    if (!dataBinder.isCached(tab)) {
      Drawable drawable = viewHolder.previewImageView.getDrawable();
      viewHolder.previewImageView.setImageBitmap(null);

      if (drawable instanceof BitmapDrawable) {
        Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();

        if (bitmap != null && !bitmap.isRecycled()) {
          bitmap.recycle();
        }
      }
    } else {
      viewHolder.previewImageView.setImageBitmap(null);
    }

    view.setTag(R.id.tag_properties, null);
  }

  @Override
  public final void onTitleChanged(@NonNull final Tab tab) {
    TabItem tabItem = getTabItem(tab);

    if (tabItem != null) {
      adaptTitle(tabItem.getViewHolder(), tabItem.getTab());
    }
  }

  @Override
  public final void onIconChanged(@NonNull final Tab tab) {
    TabItem tabItem = getTabItem(tab);

    if (tabItem != null) {
      adaptIcon(tabItem.getViewHolder(), tabItem.getTab());
    }
  }

  @Override
  public final void onCloseableChanged(@NonNull final Tab tab) {
    TabItem tabItem = getTabItem(tab);

    if (tabItem != null) {
      adaptCloseButton(tabItem.getViewHolder(), tabItem.getTab());
    }
  }

  @Override
  public final void onCloseButtonIconChanged(@NonNull final Tab tab) {
    TabItem tabItem = getTabItem(tab);

    if (tabItem != null) {
      adaptCloseButtonIcon(tabItem.getViewHolder(), tabItem.getTab());
    }
  }

  @Override
  public final void onBackgroundColorChanged(@NonNull final Tab tab) {
    TabItem tabItem = getTabItem(tab);

    if (tabItem != null) {
      adaptBackgroundColor(tabItem.getView(), tabItem.getViewHolder(), tabItem.getTab());
    }
  }

  @Override
  public final void onTitleTextColorChanged(@NonNull final Tab tab) {
    TabItem tabItem = getTabItem(tab);

    if (tabItem != null) {
      adaptTitleTextColor(tabItem.getViewHolder(), tabItem.getTab());
    }
  }

  @Override
  public final void onLogLevelChanged(@NonNull final LogLevel logLevel) {
    adaptLogLevel();
  }

  @Override
  public final void onDecoratorChanged(@NonNull final TabSwitcherDecorator decorator) {

  }

  @Override
  public final void onSwitcherShown() {

  }

  @Override
  public final void onSwitcherHidden() {

  }

  @Override
  public final void onSelectionChanged(final int previousIndex, final int index,
                                       @Nullable final Tab selectedTab,
                                       final boolean switcherHidden) {
    adaptAllSelectionStates();
  }

  @Override
  public final void onTabAdded(final int index, @NonNull final Tab tab,
                               final int previousSelectedTabIndex, final int selectedTabIndex,
                               final boolean switcherVisibilityChanged,
                               @NonNull final Animation animation) {
    if (previousSelectedTabIndex != selectedTabIndex) {
      adaptAllSelectionStates();
    }
  }

  @Override
  public final void onAllTabsAdded(final int index, @NonNull final Tab[] tabs,
                                   final int previousSelectedTabIndex, final int selectedTabIndex,
                                   @NonNull final Animation animation) {
    if (previousSelectedTabIndex != selectedTabIndex) {
      adaptAllSelectionStates();
    }
  }

  @Override
  public final void onTabRemoved(final int index, @NonNull final Tab tab,
                                 final int previousSelectedTabIndex, final int selectedTabIndex,
                                 @NonNull final Animation animation) {
    if (previousSelectedTabIndex != selectedTabIndex) {
      adaptAllSelectionStates();
    }
  }

  @Override
  public final void onAllTabsRemoved(@NonNull final Tab[] tabs,
                                     @NonNull final Animation animation) {

  }

  @Override
  public final void onPaddingChanged(final int left, final int top, final int right,
                                     final int bottom) {
    TabItemIterator iterator = new TabItemIterator.Builder(model, viewRecycler).create();
    TabItem tabItem;

    while ((tabItem = iterator.next()) != null) {
      if (tabItem.isInflated()) {
        adaptPadding(tabItem.getViewHolder());
      }
    }
  }

  @Override
  public final void onTabIconChanged(@Nullable final Drawable icon) {
    TabItemIterator iterator = new TabItemIterator.Builder(model, viewRecycler).create();
    TabItem tabItem;

    while ((tabItem = iterator.next()) != null) {
      if (tabItem.isInflated()) {
        adaptIcon(tabItem.getViewHolder(), tabItem.getTab());
      }
    }
  }

  @Override
  public final void onTabBackgroundColorChanged(@Nullable final ColorStateList colorStateList) {
    TabItemIterator iterator = new TabItemIterator.Builder(model, viewRecycler).create();
    TabItem tabItem;

    while ((tabItem = iterator.next()) != null) {
      if (tabItem.isInflated()) {
        adaptBackgroundColor(tabItem.getView(), tabItem.getViewHolder(), tabItem.getTab());
      }
    }
  }

  @Override
  public final void onTabTitleColorChanged(@Nullable final ColorStateList colorStateList) {
    TabItemIterator iterator = new TabItemIterator.Builder(model, viewRecycler).create();
    TabItem tabItem;

    while ((tabItem = iterator.next()) != null) {
      if (tabItem.isInflated()) {
        adaptTitleTextColor(tabItem.getViewHolder(), tabItem.getTab());
      }
    }
  }

  @Override
  public final void onTabCloseButtonIconChanged(@Nullable final Drawable icon) {
    TabItemIterator iterator = new TabItemIterator.Builder(model, viewRecycler).create();
    TabItem tabItem;

    while ((tabItem = iterator.next()) != null) {
      if (tabItem.isInflated()) {
        adaptCloseButtonIcon(tabItem.getViewHolder(), tabItem.getTab());
      }
    }
  }

  @Override
  public final void onToolbarVisibilityChanged(final boolean visible) {

  }

  @Override
  public final void onToolbarTitleChanged(@Nullable final CharSequence title) {

  }

  @Override
  public final void onToolbarNavigationIconChanged(@Nullable final Drawable icon,
                                                   @Nullable final OnClickListener listener) {

  }

  @Override
  public final void onToolbarMenuInflated(@MenuRes final int resourceId,
                                          @Nullable final OnMenuItemClickListener listener) {

  }

  @Override
  public final boolean onLoadData(
    @NonNull final AbstractDataBinder<Bitmap, Tab, ImageView, TabItem> dataBinder,
    @NonNull final Tab key, @NonNull final TabItem... params) {
    boolean result = true;

    for (TabPreviewListener listener : model.getTabPreviewListeners()) {
      result &= listener.onLoadTabPreview(tabSwitcher, key);
    }

    return result;
  }

  @Override
  public final void onFinished(
    @NonNull final AbstractDataBinder<Bitmap, Tab, ImageView, TabItem> dataBinder,
    @NonNull final Tab key, @Nullable final Bitmap data, @NonNull final ImageView view,
    @NonNull final TabItem... params) {

  }

  @Override
  public final void onCanceled(
    @NonNull final AbstractDataBinder<Bitmap, Tab, ImageView, TabItem> dataBinder) {

  }

}
