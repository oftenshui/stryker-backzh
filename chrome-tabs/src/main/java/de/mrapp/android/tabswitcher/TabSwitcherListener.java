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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public interface TabSwitcherListener {

  void onSwitcherShown(@NonNull TabSwitcher tabSwitcher);

  void onSwitcherHidden(@NonNull TabSwitcher tabSwitcher);

  void onSelectionChanged(@NonNull TabSwitcher tabSwitcher, int selectedTabIndex,
                          @Nullable Tab selectedTab);

  void onTabAdded(@NonNull TabSwitcher tabSwitcher, int index, @NonNull Tab tab,
                  @NonNull Animation animation);

  void onTabRemoved(@NonNull TabSwitcher tabSwitcher, int index, @NonNull Tab tab,
                    @NonNull Animation animation);

  void onAllTabsRemoved(@NonNull TabSwitcher tabSwitcher, @NonNull Tab[] tabs,
                        @NonNull Animation animation);

}
