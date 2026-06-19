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

import android.content.Context;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import de.mrapp.android.util.view.AbstractViewHolderAdapter;

public abstract class TabSwitcherDecorator extends AbstractViewHolderAdapter {

  private static final String VIEW_HIERARCHY_STATE_EXTRA =
    TabSwitcherDecorator.class.getName() + "::ViewHierarchyState";

  @NonNull
  public abstract View onInflateView(@NonNull final LayoutInflater inflater,
                                     @Nullable final ViewGroup parent, final int viewType);

  public abstract void onShowTab(@NonNull final Context context,
                                 @NonNull final TabSwitcher tabSwitcher, @NonNull final View view,
                                 @NonNull final Tab tab, final int index, final int viewType,
                                 @Nullable final Bundle savedInstanceState);

  public void onSaveInstanceState(@NonNull final View view, @NonNull final Tab tab,
                                  final int index, final int viewType,
                                  @NonNull final Bundle outState) {

  }

  public int getViewType(@NonNull final Tab tab, final int index) {
    return 0;
  }

  public int getViewTypeCount() {
    return 1;
  }

  @NonNull
  public final View inflateView(@NonNull final LayoutInflater inflater,
                                @Nullable final ViewGroup parent, @NonNull final Tab tab,
                                final int index) {
    int viewType = getViewType(tab, index);
    return onInflateView(inflater, parent, viewType);
  }

  public final void applyDecorator(@NonNull final Context context,
                                   @NonNull final TabSwitcher tabSwitcher,
                                   @NonNull final View view, @NonNull final Tab tab,
                                   final int index, @Nullable final Bundle savedInstanceState) {
    setCurrentParentView(view);
    int viewType = getViewType(tab, index);

    if (savedInstanceState != null) {
      SparseArray<Parcelable> viewStates =
        savedInstanceState.getSparseParcelableArray(VIEW_HIERARCHY_STATE_EXTRA);

      if (viewStates != null) {
        view.restoreHierarchyState(viewStates);
      }
    }

    onShowTab(context, tabSwitcher, view, tab, index, viewType, savedInstanceState);
  }

  @NonNull
  public final Bundle saveInstanceState(@NonNull final View view, @NonNull final Tab tab,
                                        final int index) {
    setCurrentParentView(view);
    int viewType = getViewType(tab, index);
    Bundle outState = new Bundle();
    SparseArray<Parcelable> viewStates = new SparseArray<>();
    view.saveHierarchyState(viewStates);
    outState.putSparseParcelableArray(VIEW_HIERARCHY_STATE_EXTRA, viewStates);
    onSaveInstanceState(view, tab, index, viewType, outState);
    return outState;
  }

}
