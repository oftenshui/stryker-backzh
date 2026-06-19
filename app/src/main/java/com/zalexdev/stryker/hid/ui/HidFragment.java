package com.zalexdev.stryker.hid.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textview.MaterialTextView;
import com.zalexdev.stryker.MainActivity;
import com.zalexdev.stryker.R;
import com.zalexdev.stryker.hid.capability.HidCapabilities;
import com.zalexdev.stryker.hid.capability.HidCapabilityProbe;
import com.zalexdev.stryker.hid.payload.Payload;
import com.zalexdev.stryker.hid.payload.PayloadLibrary;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class HidFragment extends Fragment implements PayloadCardAdapter.OnPayloadClicked {

    private static final String PREF_FILTER = "hid_files_filter";

    private PayloadLibrary library;
    private PayloadCardAdapter adapter;
    private final List<Payload> allPayloads = new ArrayList<>();

    private final AtomicBoolean capProbeAlive = new AtomicBoolean(false);

    private MaterialTextView stateText;
    private MaterialTextView statePill;
    private MaterialTextView stateDetail;
    private MaterialButton arsenalBtn;
    private MaterialTextView emptyView;
    private TextInputEditText filterInput;

    private ActivityResultLauncher<Intent> ideLauncher;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_hid, container, false);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ideLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result == null) return;
                    if (result.getResultCode() == android.app.Activity.RESULT_OK
                            && result.getData() != null
                            && result.getData().getBooleanExtra(HidIdeActivity.EXTRA_REQUEST_ARSENAL, false)) {
                        jumpToArsenal();
                    }
                    refreshPayloads();
                });
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        library = new PayloadLibrary(requireContext());

        stateText   = v.findViewById(R.id.hid_state_text);
        statePill   = v.findViewById(R.id.hid_state_pill);
        stateDetail = v.findViewById(R.id.hid_state_detail);
        arsenalBtn  = v.findViewById(R.id.hid_arsenal_btn);
        emptyView   = v.findViewById(R.id.hid_files_empty);
        filterInput = v.findViewById(R.id.hid_files_filter);

        RecyclerView list = v.findViewById(R.id.hid_files_list);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new PayloadCardAdapter(this);
        list.setAdapter(adapter);

        arsenalBtn.setOnClickListener(b -> jumpToArsenal());

        ExtendedFloatingActionButton fab = v.findViewById(R.id.hid_files_fab);
        fab.setOnClickListener(b -> openIde(null));

        String savedFilter = new com.zalexdev.stryker.utils.Core(requireContext())
                .getString(PREF_FILTER);
        if (savedFilter != null && !savedFilter.isEmpty()) {
            filterInput.setText(savedFilter);
            filterInput.setSelection(savedFilter.length());
        }

        filterInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                String text = s == null ? "" : s.toString();
                new com.zalexdev.stryker.utils.Core(requireContext()).putString(PREF_FILTER, text);
                applyFilter(text);
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        refreshCapability();
        refreshPayloads();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshPayloads();
    }

    @Override
    public void onDestroyView() {
        capProbeAlive.set(false);
        super.onDestroyView();
    }

    @Override
    public void onClick(@NonNull Payload payload) {
        openIde(payload);
    }

    @Override
    public void onMore(@NonNull Payload payload, @NonNull View anchor) {
        String[] items = new String[]{
                getString(R.string.hid_payload_rename),
                getString(R.string.hid_payload_delete),
                getString(R.string.hid_payload_move_up),
                getString(R.string.hid_payload_move_down)
        };
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(payload.displayName)
                .setItems(items, (d, idx) -> {
                    switch (idx) {
                        case 0: promptRename(payload); break;
                        case 1: confirmDelete(payload); break;
                        case 2: applyMove(payload, -1); break;
                        case 3: applyMove(payload, +1); break;
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void promptRename(@NonNull Payload payload) {
        android.widget.EditText name = new android.widget.EditText(requireContext());
        name.setText(payload.name);
        com.google.android.material.textfield.TextInputLayout til =
                new com.google.android.material.textfield.TextInputLayout(requireContext());
        til.setHint(getString(R.string.hid_save_dialog_hint));
        til.addView(name);
        android.widget.LinearLayout wrap = new android.widget.LinearLayout(requireContext());
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        wrap.setPadding(pad, pad / 2, pad, 0);
        wrap.addView(til);
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.hid_payload_rename_title)
                .setView(wrap)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String newName = name.getText().toString().trim();
                    if (newName.isEmpty() || newName.equals(payload.name)) return;
                    if (library.rename(payload, newName)) {
                        new com.zalexdev.stryker.utils.Core(requireContext())
                                .toaster(getString(R.string.hid_payload_renamed, newName));
                        refreshPayloads();
                    } else {
                        new com.zalexdev.stryker.utils.Core(requireContext())
                                .toaster(getString(R.string.hid_payload_rename_failed));
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void confirmDelete(@NonNull Payload payload) {
        String msg = getString(R.string.hid_payload_delete_confirm, payload.displayName);
        if (payload.source == Payload.Source.ASSET) {
            msg += "\n\n" + getString(R.string.hid_payload_delete_hidden_note);
        }
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.hid_payload_delete)
                .setMessage(msg)
                .setPositiveButton(R.string.hid_payload_delete, (d, w) -> {
                    if (library.delete(payload)) {
                        new com.zalexdev.stryker.utils.Core(requireContext())
                                .toaster(getString(R.string.hid_payload_deleted));
                        refreshPayloads();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void applyMove(@NonNull Payload payload, int direction) {
        if (library.move(payload, direction)) {
            refreshPayloads();
        }
    }

    private void openIde(@Nullable Payload payload) {
        Intent i = new Intent(requireContext(), HidIdeActivity.class);
        if (payload != null) {
            i.putExtra(HidIdeActivity.EXTRA_NAME, payload.name + ".ducky");
            i.putExtra(HidIdeActivity.EXTRA_BODY, payload.body);
        }
        ideLauncher.launch(i);
    }

    private void jumpToArsenal() {
        if (getActivity() instanceof MainActivity) {
            new MainActivity.Receiver().changeFragment(R.id.usb_arsenal_item);
        }
    }

    private void refreshPayloads() {
        allPayloads.clear();
        allPayloads.addAll(library.listAll());
        applyFilter(filterInput == null || filterInput.getText() == null
                ? "" : filterInput.getText().toString());
    }

    private void applyFilter(String query) {
        String q = query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            adapter.submit(allPayloads);
        } else {
            List<Payload> filtered = new ArrayList<>(allPayloads.size());
            for (Payload p : allPayloads) {
                if (p.displayName.toLowerCase(Locale.ROOT).contains(q)
                        || p.body.toLowerCase(Locale.ROOT).contains(q)) {
                    filtered.add(p);
                }
            }
            adapter.submit(filtered);
        }
        if (emptyView != null) {
            emptyView.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
        }
    }

    private void refreshCapability() {
        statePill.setText("PROBING");
        statePill.setBackgroundResource(R.drawable.chip_pill_warn);
        statePill.setTextColor(0xFFEF6C00);
        stateText.setText(R.string.hid_state_unknown);
        capProbeAlive.set(true);
        new Thread(() -> {
            HidCapabilities caps = new HidCapabilityProbe(requireContext()).probe();
            if (!capProbeAlive.get()) return;
            androidx.fragment.app.FragmentActivity activity = getActivity();
            if (activity != null && isAdded()) {
                activity.runOnUiThread(() -> {
                    if (capProbeAlive.get() && isAdded()) bindCapability(caps);
                });
            }
        }, "hid-files-cap").start();
    }

    private void bindCapability(@NonNull HidCapabilities caps) {
        if (!isAdded()) return;
        int textResource;
        String pill;
        int pillBg;
        int pillColor;
        boolean showArsenalCta = false;
        switch (caps.verdict) {
            case READY:           textResource = R.string.hid_state_ready;        pill = "READY";   pillBg = R.drawable.chip_pill_ok;   pillColor = 0xFF2E7D32; break;
            case ROOT_DENIED:     textResource = R.string.hid_state_root_denied;  pill = "NO ROOT"; pillBg = R.drawable.chip_pill_err;  pillColor = 0xFFC62828; break;
            case KERNEL_TOO_OLD:  textResource = R.string.hid_state_kernel_old;   pill = "KERNEL";  pillBg = R.drawable.chip_pill_err;  pillColor = 0xFFC62828; break;
            case CONFIGFS_MISSING:textResource = R.string.hid_state_no_configfs;  pill = "CFGFS";   pillBg = R.drawable.chip_pill_err;  pillColor = 0xFFC62828; break;
            case UDC_MISSING:     textResource = R.string.hid_state_no_udc;       pill = "NO UDC";  pillBg = R.drawable.chip_pill_err;  pillColor = 0xFFC62828; break;
            case HID_NODE_MISSING:textResource = R.string.hid_state_no_node;      pill = "ARSENAL"; pillBg = R.drawable.chip_pill_warn; pillColor = 0xFFEF6C00; showArsenalCta = true; break;
            case SELINUX_DENIES:  textResource = R.string.hid_state_selinux;      pill = "SELINUX"; pillBg = R.drawable.chip_pill_err;  pillColor = 0xFFC62828; break;
            default:              textResource = R.string.hid_state_unknown;      pill = "?";       pillBg = R.drawable.chip_pill_warn; pillColor = 0xFFEF6C00;
        }
        stateText.setText(textResource);
        statePill.setText(pill);
        statePill.setBackgroundResource(pillBg);
        statePill.setTextColor(pillColor);
        stateDetail.setText(String.format(Locale.US,
                "kernel=%s · udc=%s · /dev/hidg0=%s · /dev/hidg1=%s · selinux=%s",
                caps.kernelRelease.isEmpty() ? "?" : caps.kernelRelease,
                caps.udcName == null ? "—" : caps.udcName,
                caps.keyboardNodePresent ? "✓" : "✗",
                caps.mouseNodePresent ? "✓" : "✗",
                caps.selinuxEnforcing ? "enforcing" : "permissive"));
        arsenalBtn.setVisibility(showArsenalCta ? View.VISIBLE : View.GONE);
    }
}
