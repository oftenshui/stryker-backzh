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
import de.mrapp.android.tabswitcher.Tab;
import de.mrapp.android.tabswitcher.model.TabItem;
import de.mrapp.android.util.view.AttachedViewRecycler;

import static de.mrapp.android.util.Condition.ensureNotNull;

public class ArrayTabItemIterator extends AbstractTabItemIterator {

  public static class Builder extends AbstractBuilder<Builder, ArrayTabItemIterator> {

    private final AttachedViewRecycler<TabItem, ?> viewRecycler;

    private final Tab[] array;

    public Builder(@NonNull final AttachedViewRecycler<TabItem, ?> viewRecycler,
                   @NonNull final Tab[] array) {
      ensureNotNull(viewRecycler, "The view recycler may not be null");
      ensureNotNull(array, "The array may not be null");
      this.viewRecycler = viewRecycler;
      this.array = array;
    }

    @NonNull
    @Override
    public ArrayTabItemIterator create() {
      return new ArrayTabItemIterator(viewRecycler, array, reverse, start);
    }

  }

  private final AttachedViewRecycler<TabItem, ?> viewRecycler;

  private final Tab[] array;

  private ArrayTabItemIterator(@NonNull final AttachedViewRecycler<TabItem, ?> viewRecycler,
                               @NonNull final Tab[] array, final boolean reverse,
                               final int start) {
    ensureNotNull(viewRecycler, "The view recycler may not be null");
    ensureNotNull(array, "The array may not be null");
    this.viewRecycler = viewRecycler;
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
    return TabItem.create(viewRecycler, index, array[index]);
  }

}
