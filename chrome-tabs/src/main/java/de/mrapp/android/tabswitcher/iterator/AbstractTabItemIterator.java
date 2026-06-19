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
import de.mrapp.android.tabswitcher.model.TabItem;

import static de.mrapp.android.util.Condition.ensureAtLeast;

public abstract class AbstractTabItemIterator implements java.util.Iterator<TabItem> {

  public static abstract class AbstractBuilder<BuilderType extends AbstractBuilder<?, ProductType>, ProductType extends AbstractTabItemIterator> {

    protected boolean reverse;

    protected int start;

    @SuppressWarnings("unchecked")
    private BuilderType self() {
      return (BuilderType) this;
    }

    protected AbstractBuilder() {
      reverse(false);
      start(-1);
    }

    @NonNull
    public abstract ProductType create();

    @NonNull
    public BuilderType reverse(final boolean reverse) {
      this.reverse = reverse;
      return self();
    }

    @NonNull
    public BuilderType start(final int start) {
      ensureAtLeast(start, -1, "The start must be at least -1");
      this.start = start;
      return self();
    }

  }

  private boolean reverse;

  private int index;

  private TabItem current;

  private TabItem previous;

  private TabItem first;

  public abstract int getCount();

  @NonNull
  public abstract TabItem getItem(final int index);

  protected final void initialize(final boolean reverse, final int start) {
    ensureAtLeast(start, -1, "The start must be at least -1");
    this.reverse = reverse;
    this.previous = null;
    this.index = start != -1 ? start : (reverse ? getCount() - 1 : 0);
    int previousIndex = reverse ? this.index + 1 : this.index - 1;

    if (previousIndex >= 0 && previousIndex < getCount()) {
      this.current = getItem(previousIndex);
    } else {
      this.current = null;
    }
  }

  public final TabItem first() {
    return first;
  }

  public final TabItem previous() {
    return previous;
  }

  public final TabItem peek() {
    return index >= 0 && index < getCount() ? getItem(index) : null;
  }

  @Override
  public final boolean hasNext() {
    if (reverse) {
      return index >= 0;
    } else {
      return getCount() - index >= 1;
    }
  }

  @Override
  public final TabItem next() {
    if (hasNext()) {
      previous = current;

      if (first == null) {
        first = current;
      }

      current = getItem(index);
      index += reverse ? -1 : 1;
      return current;
    }

    return null;
  }

}
