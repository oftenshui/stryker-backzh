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
package de.mrapp.android.tabswitcher.iterator;

import androidx.annotation.NonNull;
import de.mrapp.android.tabswitcher.TabSwitcher;
import de.mrapp.android.tabswitcher.model.Model;
import de.mrapp.android.tabswitcher.model.TabItem;
import de.mrapp.android.util.view.AttachedViewRecycler;

import static de.mrapp.android.util.Condition.ensureNotNull;

public class TabItemIterator extends AbstractTabItemIterator {

  public static class Builder extends AbstractBuilder<Builder, TabItemIterator> {

    private final Model model;

    private final AttachedViewRecycler<TabItem, ?> viewRecycler;

    public Builder(@NonNull final Model model,
                   @NonNull final AttachedViewRecycler<TabItem, ?> viewRecycler) {
      ensureNotNull(model, "The model may not be null");
      ensureNotNull(viewRecycler, "The view recycler may not be null");
      this.model = model;
      this.viewRecycler = viewRecycler;
    }

    @NonNull
    @Override
    public TabItemIterator create() {
      return new TabItemIterator(model, viewRecycler, reverse, start);
    }

  }

  private final Model model;

  private final AttachedViewRecycler<TabItem, ?> viewRecycler;

  private TabItemIterator(@NonNull final Model model,
                          @NonNull final AttachedViewRecycler<TabItem, ?> viewRecycler,
                          final boolean reverse, final int start) {
    ensureNotNull(model, "The model may not be null");
    ensureNotNull(viewRecycler, "The view recycler may not be null");
    this.model = model;
    this.viewRecycler = viewRecycler;
    initialize(reverse, start);
  }

  @Override
  public final int getCount() {
    return model.getCount();
  }

  @NonNull
  @Override
  public final TabItem getItem(final int index) {
    return TabItem.create(model, viewRecycler, index);
  }

}