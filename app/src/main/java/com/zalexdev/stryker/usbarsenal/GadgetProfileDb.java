package com.zalexdev.stryker.usbarsenal;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zalexdev.stryker.hid.configfs.GadgetFunction;
import com.zalexdev.stryker.hid.configfs.GadgetProfile;
import com.zalexdev.stryker.hid.configfs.TargetOs;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

public final class GadgetProfileDb {

    private static final String DB_NAME = "stryker_arsenal.db";
    private static final int DB_VERSION = 1;
    private static final String TABLE = "gadget_profile";

    private final Helper helper;

    public GadgetProfileDb(@NonNull Context context) {
        this.helper = new Helper(context.getApplicationContext());
        ensureDefaultsPresent();
    }

    public void ensureDefaultsPresent() {
        SQLiteDatabase db = helper.getReadableDatabase();
        java.util.Set<String> existing = new java.util.HashSet<>();
        try (Cursor c = db.query(TABLE, new String[]{"name"}, null, null, null, null, null)) {
            while (c.moveToNext()) {
                String n = c.getString(0);
                if (n != null) existing.add(n);
            }
        }
        SQLiteDatabase wdb = helper.getWritableDatabase();
        for (GadgetProfile p : DefaultProfiles.list()) {
            if (!existing.contains(p.name)) {
                wdb.insert(TABLE, null, toValues(p));
            }
        }
    }

    @NonNull
    public List<GadgetProfile> listAll() {
        List<GadgetProfile> out = new ArrayList<>();
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor c = db.query(TABLE, null, null, null, null, null, "name COLLATE NOCASE ASC")) {
            while (c.moveToNext()) out.add(fromCursor(c));
        }
        return out;
    }

    @Nullable
    public GadgetProfile get(long id) {
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor c = db.query(TABLE, null, "id=?",
                new String[]{Long.toString(id)}, null, null, null)) {
            if (c.moveToFirst()) return fromCursor(c);
        }
        return null;
    }

    public long upsert(@NonNull GadgetProfile p) {
        SQLiteDatabase db = helper.getWritableDatabase();
        ContentValues v = toValues(p);
        if (p.id > 0) {
            int rows = db.update(TABLE, v, "id=?", new String[]{Long.toString(p.id)});
            if (rows > 0) return p.id;
        }
        return db.insert(TABLE, null, v);
    }

    public boolean delete(long id) {
        SQLiteDatabase db = helper.getWritableDatabase();
        return db.delete(TABLE, "id=?", new String[]{Long.toString(id)}) > 0;
    }

    public int count() {
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + TABLE, null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    private ContentValues toValues(GadgetProfile p) {
        ContentValues v = new ContentValues();
        v.put("name", p.name);
        v.put("target_os", p.targetOs.name());
        v.put("functions", encodeFunctions(p.functions));
        v.put("id_vendor", p.idVendor);
        v.put("id_product", p.idProduct);
        v.put("manufacturer", p.manufacturer);
        v.put("product_name", p.productName);
        v.put("serial_number", p.serialNumber);
        v.put("config_label", p.configurationLabel);
        v.put("mass_storage_image", p.massStorageImage == null ? "" : p.massStorageImage);
        v.put("mass_storage_ro", p.massStorageReadOnly ? 1 : 0);
        v.put("mass_storage_cdrom", p.massStorageCdrom ? 1 : 0);
        v.put("inquiry_string", p.inquiryString);
        return v;
    }

    private GadgetProfile fromCursor(Cursor c) {
        long id = c.getLong(c.getColumnIndexOrThrow("id"));
        String name = c.getString(c.getColumnIndexOrThrow("name"));
        TargetOs os = TargetOs.valueOf(c.getString(c.getColumnIndexOrThrow("target_os")));
        EnumSet<GadgetFunction> fns = decodeFunctions(c.getString(c.getColumnIndexOrThrow("functions")));
        String image = c.getString(c.getColumnIndexOrThrow("mass_storage_image"));
        return new GadgetProfile(
                id, name, os, fns,
                c.getString(c.getColumnIndexOrThrow("id_vendor")),
                c.getString(c.getColumnIndexOrThrow("id_product")),
                c.getString(c.getColumnIndexOrThrow("manufacturer")),
                c.getString(c.getColumnIndexOrThrow("product_name")),
                c.getString(c.getColumnIndexOrThrow("serial_number")),
                c.getString(c.getColumnIndexOrThrow("config_label")),
                image == null || image.isEmpty() ? null : image,
                c.getInt(c.getColumnIndexOrThrow("mass_storage_ro")) == 1,
                c.getInt(c.getColumnIndexOrThrow("mass_storage_cdrom")) == 1,
                c.getString(c.getColumnIndexOrThrow("inquiry_string"))
        );
    }

    static String encodeFunctions(java.util.Set<GadgetFunction> set) {
        StringBuilder sb = new StringBuilder();
        for (GadgetFunction f : set) {
            if (sb.length() > 0) sb.append(',');
            sb.append(f.name());
        }
        return sb.toString();
    }

    static EnumSet<GadgetFunction> decodeFunctions(String csv) {
        EnumSet<GadgetFunction> set = EnumSet.noneOf(GadgetFunction.class);
        if (csv == null || csv.isEmpty()) return set;
        for (String part : csv.split(",")) {
            String t = part.trim();
            if (t.isEmpty()) continue;
            try { set.add(GadgetFunction.valueOf(t)); }
            catch (IllegalArgumentException ignored) {}
        }
        return set;
    }

    private final class Helper extends SQLiteOpenHelper {
        Helper(Context ctx) { super(ctx, DB_NAME, null, DB_VERSION); }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + TABLE + " ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "name TEXT NOT NULL, "
                    + "target_os TEXT NOT NULL, "
                    + "functions TEXT NOT NULL, "
                    + "id_vendor TEXT NOT NULL, "
                    + "id_product TEXT NOT NULL, "
                    + "manufacturer TEXT NOT NULL, "
                    + "product_name TEXT NOT NULL, "
                    + "serial_number TEXT NOT NULL, "
                    + "config_label TEXT NOT NULL, "
                    + "mass_storage_image TEXT NOT NULL DEFAULT '', "
                    + "mass_storage_ro INTEGER NOT NULL DEFAULT 0, "
                    + "mass_storage_cdrom INTEGER NOT NULL DEFAULT 0, "
                    + "inquiry_string TEXT NOT NULL DEFAULT ''"
                    + ")");
            for (GadgetProfile p : DefaultProfiles.list()) {
                ContentValues v = toValues(p);
                db.insert(TABLE, null, v);
            }
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        }
    }

    public void restoreDefaults() {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(TABLE, null, null);
            for (GadgetProfile p : DefaultProfiles.list()) {
                db.insert(TABLE, null, toValues(p));
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    @SuppressWarnings("unused")
    private static String dump(EnumSet<GadgetFunction> set) {
        StringBuilder sb = new StringBuilder("[");
        for (GadgetFunction f : set) sb.append(f.key).append(' ');
        return sb.append(']').toString().toLowerCase(Locale.ROOT);
    }
}
