package com.zalexdev.stryker.usbarsenal.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.chip.Chip;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textview.MaterialTextView;
import com.zalexdev.stryker.R;
import com.zalexdev.stryker.hid.configfs.GadgetFunction;
import com.zalexdev.stryker.hid.configfs.GadgetProfile;
import com.zalexdev.stryker.hid.configfs.TargetOs;
import com.zalexdev.stryker.utils.Core;

import java.util.EnumSet;
import java.util.regex.Pattern;

public final class GadgetProfileEditorSheet {

    public interface Listener {
        void onSaved(@NonNull GadgetProfile profile);
    }

    private static final Pattern VID_PID = Pattern.compile("0x[0-9a-fA-F]{4}");

    private final Fragment host;
    private final BottomSheetDialog dialog;
    @Nullable private final GadgetProfile editing;
    private final Listener listener;

    private String massImagePath;
    private ActivityResultLauncher<String[]> imagePicker;

    public GadgetProfileEditorSheet(@NonNull Fragment host,
                                    @Nullable GadgetProfile editing,
                                    @NonNull Listener listener) {
        this.host = host;
        this.editing = editing;
        this.listener = listener;
        Context ctx = host.requireContext();
        dialog = new BottomSheetDialog(ctx, R.style.ThemeOverlay_Stryker_BottomSheetDialog);
        dialog.setContentView(R.layout.bs_gadget_profile_editor);
        wire();
    }

    public void registerImagePicker(@NonNull ActivityResultLauncher<String[]> picker) {
        this.imagePicker = picker;
    }

    public void onImagePicked(@Nullable Uri uri) {
        if (uri == null) return;
        Context ctx = host.requireContext();
        String resolved = MassStorageImageStore.copyToWorkingDir(ctx, uri);
        if (resolved == null) {
            new Core(ctx).toaster("Could not copy image into Stryker storage");
            return;
        }
        massImagePath = resolved;
        MaterialTextView label = dialog.findViewById(R.id.bs_gp_ms_path);
        if (label != null) label.setText(resolved);
    }

    public void show() {
        dialog.show();
    }

    private void wire() {
        Context ctx = dialog.getContext();
        MaterialTextView title = dialog.findViewById(R.id.bs_gp_title);
        TextInputEditText name = dialog.findViewById(R.id.bs_gp_name);
        TextInputEditText vid = dialog.findViewById(R.id.bs_gp_vid);
        TextInputEditText pid = dialog.findViewById(R.id.bs_gp_pid);
        TextInputEditText mfg = dialog.findViewById(R.id.bs_gp_manufacturer);
        TextInputEditText prod = dialog.findViewById(R.id.bs_gp_product);
        TextInputEditText serial = dialog.findViewById(R.id.bs_gp_serial);
        MaterialButtonToggleGroup osGroup = dialog.findViewById(R.id.bs_gp_os_group);
        Chip chipKbd = dialog.findViewById(R.id.bs_gp_fn_hid_kbd);
        Chip chipMouse = dialog.findViewById(R.id.bs_gp_fn_hid_mouse);
        Chip chipMass = dialog.findViewById(R.id.bs_gp_fn_mass);
        Chip chipRndis = dialog.findViewById(R.id.bs_gp_fn_rndis);
        Chip chipEcm = dialog.findViewById(R.id.bs_gp_fn_ecm);
        Chip chipAcm = dialog.findViewById(R.id.bs_gp_fn_acm);
        LinearLayout msSection = dialog.findViewById(R.id.bs_gp_ms_section);
        MaterialCheckBox ro = dialog.findViewById(R.id.bs_gp_ms_ro);
        MaterialCheckBox cdrom = dialog.findViewById(R.id.bs_gp_ms_cdrom);
        MaterialTextView msPath = dialog.findViewById(R.id.bs_gp_ms_path);
        MaterialButton msPickBtn = dialog.findViewById(R.id.bs_gp_ms_pick_btn);
        MaterialButton saveBtn = dialog.findViewById(R.id.bs_gp_save_btn);
        MaterialButton cancelBtn = dialog.findViewById(R.id.bs_gp_cancel_btn);

        if (editing != null) {
            title.setText(R.string.arsenal_edit_profile);
            name.setText(editing.name);
            vid.setText(editing.idVendor);
            pid.setText(editing.idProduct);
            mfg.setText(editing.manufacturer);
            prod.setText(editing.productName);
            serial.setText(editing.serialNumber);
            switch (editing.targetOs) {
                case WINDOWS: osGroup.check(R.id.bs_gp_os_win); break;
                case MACOS:   osGroup.check(R.id.bs_gp_os_mac); break;
                case LINUX:   osGroup.check(R.id.bs_gp_os_linux); break;
                default:      osGroup.check(R.id.bs_gp_os_generic);
            }
            chipKbd.setChecked(editing.functions.contains(GadgetFunction.HID_KEYBOARD));
            chipMouse.setChecked(editing.functions.contains(GadgetFunction.HID_MOUSE));
            chipMass.setChecked(editing.functions.contains(GadgetFunction.MASS_STORAGE));
            chipRndis.setChecked(editing.functions.contains(GadgetFunction.RNDIS));
            chipEcm.setChecked(editing.functions.contains(GadgetFunction.ECM));
            chipAcm.setChecked(editing.functions.contains(GadgetFunction.ACM));
            ro.setChecked(editing.massStorageReadOnly);
            cdrom.setChecked(editing.massStorageCdrom);
            if (editing.massStorageImage != null) {
                massImagePath = editing.massStorageImage;
                msPath.setText(editing.massStorageImage);
            }
        } else {
            osGroup.check(R.id.bs_gp_os_generic);
            vid.setText("0x046d");
            pid.setText("0xc31c");
            mfg.setText("Logitech");
            prod.setText("USB Keyboard");
            serial.setText("STRYKER000");
            chipKbd.setChecked(true);
        }

        chipMass.setOnCheckedChangeListener((b, checked) ->
                msSection.setVisibility(checked ? View.VISIBLE : View.GONE));
        msSection.setVisibility(chipMass.isChecked() ? View.VISIBLE : View.GONE);

        msPickBtn.setOnClickListener(v -> {
            if (imagePicker != null) {
                imagePicker.launch(new String[]{"*/*"});
            }
        });

        cancelBtn.setOnClickListener(v -> dialog.dismiss());

        saveBtn.setOnClickListener(v -> {
            Core core = new Core(ctx);
            String nameVal = textOf(name);
            if (nameVal.isEmpty()) { core.toaster(ctx.getString(R.string.arsenal_field_name)); return; }
            String vidVal = textOf(vid);
            String pidVal = textOf(pid);
            if (!VID_PID.matcher(vidVal).matches()) { core.toaster(ctx.getString(R.string.arsenal_vid_invalid)); return; }
            if (!VID_PID.matcher(pidVal).matches()) { core.toaster(ctx.getString(R.string.arsenal_pid_invalid)); return; }

            EnumSet<GadgetFunction> fns = EnumSet.noneOf(GadgetFunction.class);
            if (chipKbd.isChecked())   fns.add(GadgetFunction.HID_KEYBOARD);
            if (chipMouse.isChecked()) fns.add(GadgetFunction.HID_MOUSE);
            if (chipMass.isChecked())  fns.add(GadgetFunction.MASS_STORAGE);
            if (chipRndis.isChecked()) fns.add(GadgetFunction.RNDIS);
            if (chipEcm.isChecked())   fns.add(GadgetFunction.ECM);
            if (chipAcm.isChecked())   fns.add(GadgetFunction.ACM);
            if (fns.isEmpty()) { core.toaster(ctx.getString(R.string.arsenal_select_function)); return; }

            TargetOs os = TargetOs.GENERIC;
            int checked = osGroup.getCheckedButtonId();
            if      (checked == R.id.bs_gp_os_win)   os = TargetOs.WINDOWS;
            else if (checked == R.id.bs_gp_os_mac)   os = TargetOs.MACOS;
            else if (checked == R.id.bs_gp_os_linux) os = TargetOs.LINUX;

            GadgetProfile built = new GadgetProfile(
                    editing == null ? 0 : editing.id,
                    nameVal,
                    os,
                    fns,
                    vidVal, pidVal,
                    textOf(mfg).isEmpty() ? "Stryker" : textOf(mfg),
                    textOf(prod).isEmpty() ? "Composite" : textOf(prod),
                    textOf(serial).isEmpty() ? "STRYKER" : textOf(serial),
                    "Stryker Gadget",
                    chipMass.isChecked() ? massImagePath : null,
                    ro.isChecked(),
                    cdrom.isChecked(),
                    "");
            dialog.dismiss();
            listener.onSaved(built);
        });
    }

    private String textOf(TextInputEditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }
}
