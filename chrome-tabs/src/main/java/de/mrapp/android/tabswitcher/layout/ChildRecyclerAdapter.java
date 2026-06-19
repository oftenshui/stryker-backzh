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
package de.mrapp.android.tabswitcher.layout;

import android.content.Context;
import android.os.Bundle;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import de.mrapp.android.tabswitcher.Tab;
import de.mrapp.android.tabswitcher.TabSwitcher;
import de.mrapp.android.tabswitcher.TabSwitcherDecorator;
import de.mrapp.android.tabswitcher.model.Restorable;
import de.mrapp.android.util.view.AbstractViewRecycler;

import static de.mrapp.android.util.Condition.ensureNotNull;

public class ChildRecyclerAdapter extends AbstractViewRecycler.Adapter<Tab, Void>
  implements Restorable {

  private static final String SAVED_INSTANCE_STATES_EXTRA =
    ChildRecyclerAdapter.class.getName() + "::SavedInstanceStates";

  private final TabSwitcher tabSwitcher;

  private final TabSwitcherDecorator decorator;

  private SparseArray<Bundle> savedInstanceStates;

  public ChildRecyclerAdapter(@NonNull final TabSwitcher tabSwitcher,
                              @NonNull final TabSwitcherDecorator decorator) {
    ensureNotNull(tabSwitcher, "The tab switcher may not be null");
    ensureNotNull(decorator, "The decorator may not be null");
    this.tabSwitcher = tabSwitcher;
    this.decorator = decorator;
    this.savedInstanceStates = new SparseArray<>();
  }

  @NonNull
  @Override
  public final View onInflateView(@NonNull final LayoutInflater inflater,
                                  @Nullable final ViewGroup parent, @NonNull final Tab item,
                                  final int viewType, @NonNull final Void... params) {
    int index = tabSwitcher.indexOf(item);
    return decorator.inflateView(inflater, parent, item, index);
  }

  @Override
  public final void onShowView(@NonNull final Context context, @NonNull final View view,
                               @NonNull final Tab item, final boolean inflated,
                               @NonNull final Void... params) {
    int index = tabSwitcher.indexOf(item);
    Bundle savedInstanceState = savedInstanceStates.get(item.hashCode());
    decorator.applyDecorator(context, tabSwitcher, view, item, index, savedInstanceState);
  }

  @Override
  public final void onRemoveView(@NonNull final View view, @NonNull final Tab item) {
    int index = tabSwitcher.indexOf(item);
    Bundle outState = decorator.saveInstanceState(view, item, index);
    savedInstanceStates.put(item.hashCode(), outState);
  }

  @Override
  public final int getViewTypeCount() {
    return decorator.getViewTypeCount();
  }

  @Override
  public final int getViewType(@NonNull final Tab item) {
    int index = tabSwitcher.indexOf(item);
    return decorator.getViewType(item, index);
  }

  @Override
  public final void saveInstanceState(@NonNull final Bundle outState) {
    outState.putSparseParcelableArray(SAVED_INSTANCE_STATES_EXTRA, savedInstanceStates);
  }

  @Override
  public final void restoreInstanceState(@Nullable final Bundle savedInstanceState) {
    if (savedInstanceState != null) {
      savedInstanceStates =
        savedInstanceState.getSparseParcelableArray(SAVED_INSTANCE_STATES_EXTRA);
    }
  }

}
