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

import androidx.annotation.NonNull;
import de.mrapp.android.tabswitcher.TabSwitcher;

import static de.mrapp.android.util.Condition.ensureNotNull;

public class Tag implements Cloneable {

  private float position;

  private State state;

  private boolean closing;

  public Tag() {
    setPosition(Float.NaN);
    setState(State.HIDDEN);
    setClosing(false);
  }

  public final float getPosition() {
    return position;
  }

  public final void setPosition(final float position) {
    this.position = position;
  }

  @NonNull
  public final State getState() {
    return state;
  }

  public final void setState(@NonNull final State state) {
    ensureNotNull(state, "The state may not be null");
    this.state = state;
  }

  public final boolean isClosing() {
    return closing;
  }

  public final void setClosing(final boolean closing) {
    this.closing = closing;
  }

  @Override
  public final Tag clone() {
    Tag clone;

    try {
      clone = (Tag) super.clone();
    } catch (ClassCastException | CloneNotSupportedException e) {
      clone = new Tag();
    }

    clone.position = position;
    clone.state = state;
    clone.closing = closing;
    return clone;
  }

}
