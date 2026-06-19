package com.zalexdev.stryker.hid.configfs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public final class GadgetProfile {

    public final long id;
    public final String name;
    public final TargetOs targetOs;
    public final Set<GadgetFunction> functions;
    public final String idVendor;
    public final String idProduct;
    public final String manufacturer;
    public final String productName;
    public final String serialNumber;
    public final String configurationLabel;
    public final String massStorageImage;
    public final boolean massStorageReadOnly;
    public final boolean massStorageCdrom;
    public final String inquiryString;

    public GadgetProfile(long id,
                         @NonNull String name,
                         @NonNull TargetOs targetOs,
                         @NonNull Set<GadgetFunction> functions,
                         @NonNull String idVendor,
                         @NonNull String idProduct,
                         @NonNull String manufacturer,
                         @NonNull String productName,
                         @NonNull String serialNumber,
                         @NonNull String configurationLabel,
                         @Nullable String massStorageImage,
                         boolean massStorageReadOnly,
                         boolean massStorageCdrom,
                         @NonNull String inquiryString) {
        this.id = id;
        this.name = name;
        this.targetOs = targetOs;
        this.functions = Collections.unmodifiableSet(EnumSet.copyOf(functions));
        this.idVendor = idVendor;
        this.idProduct = idProduct;
        this.manufacturer = manufacturer;
        this.productName = productName;
        this.serialNumber = serialNumber;
        this.configurationLabel = configurationLabel;
        this.massStorageImage = massStorageImage;
        this.massStorageReadOnly = massStorageReadOnly;
        this.massStorageCdrom = massStorageCdrom;
        this.inquiryString = inquiryString;
    }
}
