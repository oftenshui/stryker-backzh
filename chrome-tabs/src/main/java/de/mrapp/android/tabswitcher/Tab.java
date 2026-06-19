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
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import androidx.annotation.*;
import androidx.core.content.ContextCompat;

import java.util.LinkedHashSet;
import java.util.Set;

import static de.mrapp.android.util.Condition.ensureNotEmpty;
import static de.mrapp.android.util.Condition.ensureNotNull;

public class Tab implements Parcelable {

  public static final Creator<Tab> CREATOR = new Creator<Tab>() {

    @Override
    public Tab createFromParcel(final Parcel source) {
      return new Tab(source);
    }

    @Override
    public Tab[] newArray(final int size) {
      return new Tab[size];
    }

  };

  public interface Callback {

    void onTitleChanged(@NonNull Tab tab);

    void onIconChanged(@NonNull Tab tab);

    void onCloseableChanged(@NonNull Tab tab);

    void onCloseButtonIconChanged(@NonNull Tab tab);

    void onBackgroundColorChanged(@NonNull Tab tab);

    void onTitleTextColorChanged(@NonNull Tab tab);

  }

  private final Set<Callback> callbacks = new LinkedHashSet<>();

  private CharSequence title;

  private int iconId;

  private Bitmap iconBitmap;

  private boolean closeable;

  private int closeButtonIconId;

  private Bitmap closeButtonIconBitmap;

  private ColorStateList backgroundColor;

  private ColorStateList titleTextColor;

  private Bundle parameters;

  private void notifyOnTitleChanged() {
    for (Callback callback : callbacks) {
      callback.onTitleChanged(this);
    }
  }

  private void notifyOnIconChanged() {
    for (Callback callback : callbacks) {
      callback.onIconChanged(this);
    }
  }

  private void notifyOnCloseableChanged() {
    for (Callback callback : callbacks) {
      callback.onCloseableChanged(this);
    }
  }

  private void notifyOnCloseButtonIconChanged() {
    for (Callback callback : callbacks) {
      callback.onCloseButtonIconChanged(this);
    }
  }

  private void notifyOnBackgroundColorChanged() {
    for (Callback callback : callbacks) {
      callback.onBackgroundColorChanged(this);
    }
  }

  private void notifyOnTitleTextColorChanged() {
    for (Callback callback : callbacks) {
      callback.onTitleTextColorChanged(this);
    }
  }

  protected Tab(@NonNull final Parcel source) {
    this.title = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(source);
    this.iconId = source.readInt();
    this.iconBitmap = source.readParcelable(getClass().getClassLoader());
    this.closeable = source.readInt() > 0;
    this.closeButtonIconId = source.readInt();
    this.closeButtonIconBitmap = source.readParcelable(getClass().getClassLoader());
    this.backgroundColor = source.readParcelable(getClass().getClassLoader());
    this.titleTextColor = source.readParcelable(getClass().getClassLoader());
    this.parameters = source.readBundle(getClass().getClassLoader());
  }

  public Tab(@NonNull final CharSequence title) {
    setTitle(title);
    this.closeable = true;
    this.closeButtonIconId = -1;
    this.closeButtonIconBitmap = null;
    this.iconId = -1;
    this.iconBitmap = null;
    this.backgroundColor = null;
    this.titleTextColor = null;
    this.parameters = null;
  }

  public Tab(@NonNull final Context context, @StringRes final int resourceId) {
    this(context.getString(resourceId));
  }

  @NonNull
  public final CharSequence getTitle() {
    return title;
  }

  public final void setTitle(@NonNull final CharSequence title) {
    ensureNotNull(title, "The title may not be null");
    ensureNotEmpty(title, "The title may not be empty");
    this.title = title;
    notifyOnTitleChanged();
  }

  public final void setTitle(@NonNull final Context context, @StringRes final int resourceId) {
    setTitle(context.getText(resourceId));
  }

  @Nullable
  public final Drawable getIcon(@NonNull final Context context) {
    ensureNotNull(context, "The context may not be null");

    if (iconId != -1) {
      return ContextCompat.getDrawable(context, iconId);
    } else {
      return iconBitmap != null ? new BitmapDrawable(context.getResources(), iconBitmap) :
        null;
    }
  }

  public final void setIcon(@DrawableRes final int resourceId) {
    this.iconId = resourceId;
    this.iconBitmap = null;
    notifyOnIconChanged();
  }

  public final void setIcon(@Nullable final Bitmap icon) {
    this.iconId = -1;
    this.iconBitmap = icon;
    notifyOnIconChanged();
  }

  public final boolean isCloseable() {
    return closeable;
  }

  public final void setCloseable(final boolean closeable) {
    this.closeable = closeable;
    notifyOnCloseableChanged();
  }

  @Nullable
  public final Drawable getCloseButtonIcon(@NonNull final Context context) {
    ensureNotNull(context, "The context may not be null");

    if (closeButtonIconId != -1) {
      return ContextCompat.getDrawable(context, closeButtonIconId);
    } else {
      return closeButtonIconBitmap != null ?
        new BitmapDrawable(context.getResources(), closeButtonIconBitmap) : null;
    }
  }

  public final void setCloseButtonIcon(@DrawableRes final int resourceId) {
    this.closeButtonIconId = resourceId;
    this.closeButtonIconBitmap = null;
    notifyOnCloseButtonIconChanged();
  }

  public final void setCloseButtonIcon(@Nullable final Bitmap icon) {
    this.closeButtonIconId = -1;
    this.closeButtonIconBitmap = icon;
    notifyOnCloseButtonIconChanged();
  }

  @Nullable
  public final ColorStateList getBackgroundColor() {
    return backgroundColor;
  }

  public final void setBackgroundColor(@ColorInt final int color) {
    setBackgroundColor(color != -1 ? ColorStateList.valueOf(color) : null);
  }

  public final void setBackgroundColor(@Nullable final ColorStateList colorStateList) {
    this.backgroundColor = colorStateList;
    notifyOnBackgroundColorChanged();
  }

  @Nullable
  public final ColorStateList getTitleTextColor() {
    return titleTextColor;
  }

  public final void setTitleTextColor(@ColorInt final int color) {
    setTitleTextColor(color != -1 ? ColorStateList.valueOf(color) : null);
  }

  public final void setTitleTextColor(@Nullable final ColorStateList colorStateList) {
    this.titleTextColor = colorStateList;
    notifyOnTitleTextColorChanged();
  }

  @Nullable
  public final Bundle getParameters() {
    return parameters;
  }

  public final void setParameters(@Nullable final Bundle parameters) {
    this.parameters = parameters;
  }

  public final void addCallback(@NonNull final Callback callback) {
    ensureNotNull(callback, "The callback may not be null");
    this.callbacks.add(callback);
  }

  public final void removeCallback(@NonNull final Callback callback) {
    ensureNotNull(callback, "The callback may not be null");
    this.callbacks.remove(callback);
  }

  @Override
  public final int describeContents() {
    return 0;
  }

  @Override
  public final void writeToParcel(final Parcel parcel, final int flags) {
    TextUtils.writeToParcel(title, parcel, flags);
    parcel.writeInt(iconId);
    parcel.writeParcelable(iconBitmap, flags);
    parcel.writeInt(closeable ? 1 : 0);
    parcel.writeInt(closeButtonIconId);
    parcel.writeParcelable(closeButtonIconBitmap, flags);
    parcel.writeParcelable(backgroundColor, flags);
    parcel.writeParcelable(titleTextColor, flags);
    parcel.writeBundle(parameters);
  }

}
