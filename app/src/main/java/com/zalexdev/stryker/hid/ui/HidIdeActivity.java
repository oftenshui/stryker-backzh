package com.zalexdev.stryker.hid.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textview.MaterialTextView;
import com.zalexdev.stryker.R;
import com.zalexdev.stryker.hid.backchannel.BackChannelMarker;
import com.zalexdev.stryker.hid.capability.HidCapabilities;
import com.zalexdev.stryker.hid.capability.HidCapabilityProbe;
import com.zalexdev.stryker.hid.configfs.GadgetFunction;
import com.zalexdev.stryker.hid.configfs.GadgetProfile;
import com.zalexdev.stryker.hid.configfs.UsbGadgetController;
import com.zalexdev.stryker.hid.ducky.Program;
import com.zalexdev.stryker.hid.ducky.Step;
import com.zalexdev.stryker.hid.exec.ExecutionResult;
import com.zalexdev.stryker.hid.exec.HidExecutor;
import com.zalexdev.stryker.hid.keymap.KeymapRegistry;
import com.zalexdev.stryker.hid.payload.PayloadLibrary;
import com.zalexdev.stryker.hid.ui.editor.DuckySyntaxHighlighter;
import com.zalexdev.stryker.hid.ui.editor.LinedEditText;
import com.zalexdev.stryker.hid.ui.editor.LiveSyntaxValidator;
import com.zalexdev.stryker.hid.ui.editor.PayloadFileIo;
import com.zalexdev.stryker.usbarsenal.GadgetProfileDb;
import com.zalexdev.stryker.utils.Core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HidIdeActivity extends AppCompatActivity implements LiveSyntaxValidator.Listener {

    public static final String EXTRA_NAME = "stryker.hid.ide.name";
    public static final String EXTRA_BODY = "stryker.hid.ide.body";
    public static final String EXTRA_REQUEST_ARSENAL = "stryker.hid.ide.request_arsenal";

    private static final String PREF_LAYOUT = "hid_layout";

    private Core core;
    private HidExecutor executor;
    private PayloadLibrary library;
    private KeymapRegistry keymaps;
    private LiveSyntaxValidator validator;

    private MaterialToolbar toolbar;
    private MaterialTextView fileName;
    private MaterialTextView layoutChip;
    private MaterialTextView statusPill;
    private MaterialTextView capsPill;
    private MaterialTextView meta;
    private MaterialTextView consoleText;
    private MaterialTextView capsVerdict;
    private MaterialTextView capsDetail;
    private MaterialCardView consolePanel;
    private MaterialCardView capsSheet;
    private MaterialButton runBtn;
    private MaterialButton stopBtn;
    private MaterialButton consoleBtn;
    private MaterialButton saveBtn;
    private MaterialButton capsArsenalBtn;
    private LinearProgressIndicator progress;
    private LinedEditText editor;

    private String activeLayoutCode;
    private String currentFileName;
    private String originalBody = "";
    private int parseErrorLine;
    private String parseErrorMessage;
    private boolean syntaxOk;
    private boolean capabilityOk;
    private HidCapabilities.Verdict lastVerdict;
    private boolean dirty;
    private boolean savedAtLeastOnce;

    private ActivityResultLauncher<String[]> importLauncher;
    private ActivityResultLauncher<String> exportLauncher;
    private String pendingExportBody;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        Window w = getWindow();
        w.setStatusBarColor(ContextCompat.getColor(this, R.color.light_contrast));
        new WindowInsetsControllerCompat(w, w.getDecorView())
                .setAppearanceLightStatusBars(true);
        setContentView(R.layout.activity_hid_ide);

        core = new Core(this);
        executor = new HidExecutor(this);
        library = new PayloadLibrary(this);
        keymaps = new KeymapRegistry(this);

        bindViews();
        wireToolbar();
        wireEditor();
        wireBottomBar();
        wireConsole();
        wireCapsSheet();
        wireChips();
        wireLaunchers();
        wireBackHandler();

        Intent intent = getIntent();
        String startName = intent != null ? intent.getStringExtra(EXTRA_NAME) : null;
        String startBody = intent != null ? intent.getStringExtra(EXTRA_BODY) : null;
        currentFileName = startName != null && !startName.isEmpty()
                ? startName
                : getString(R.string.ide_default_filename);
        fileName.setText(currentFileName);
        savedAtLeastOnce = startName != null && !startName.isEmpty();
        if (startBody != null) {
            originalBody = startBody;
            editor.setText(startBody);
        } else {
            originalBody = "";
        }

        activeLayoutCode = core.getString(PREF_LAYOUT);
        if (activeLayoutCode == null || activeLayoutCode.isEmpty()) {
            activeLayoutCode = keymaps.preferredCode(Locale.getDefault());
        }
        updateLayoutChip();

        validator.validateNow();
        refreshCapability();
    }

    private void bindViews() {
        toolbar        = findViewById(R.id.ide_toolbar);
        fileName       = findViewById(R.id.ide_file_name);
        layoutChip     = findViewById(R.id.ide_layout_chip);
        statusPill     = findViewById(R.id.ide_status_pill);
        capsPill       = findViewById(R.id.ide_caps_pill);
        meta           = findViewById(R.id.ide_meta);
        consoleText    = findViewById(R.id.ide_console_text);
        capsVerdict    = findViewById(R.id.ide_caps_verdict);
        capsDetail     = findViewById(R.id.ide_caps_detail);
        consolePanel   = findViewById(R.id.ide_console_panel);
        capsSheet      = findViewById(R.id.ide_caps_sheet);
        runBtn         = findViewById(R.id.ide_run_btn);
        stopBtn        = findViewById(R.id.ide_stop_btn);
        consoleBtn     = findViewById(R.id.ide_console_btn);
        saveBtn        = findViewById(R.id.ide_save_btn);
        capsArsenalBtn = findViewById(R.id.ide_caps_arsenal_btn);
        progress       = findViewById(R.id.ide_progress);
        editor         = findViewById(R.id.ide_editor);
    }

    private void wireToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        toolbar.setNavigationOnClickListener(v -> attemptClose());
        toolbar.inflateMenu(R.menu.menu_hid_ide);
        toolbar.setOnMenuItemClickListener(this::onMenuItem);
        fileName.setOnClickListener(v -> openRenameDialog());
        saveBtn.setOnClickListener(v -> smartSave());
    }

    private void wireEditor() {
        validator = new LiveSyntaxValidator(this);
        editor.addTextChangedListener(new DuckySyntaxHighlighter());
        editor.addTextChangedListener(validator);
        editor.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                editor.setErrorLine(-1);
                refreshMeta(s);
                String body = s == null ? "" : s.toString();
                dirty = !body.equals(originalBody);
            }
        });
    }

    private void wireBottomBar() {
        runBtn.setOnClickListener(v -> attemptRun());
        stopBtn.setOnClickListener(v -> executor.cancel());
    }

    private void wireConsole() {
        consoleBtn.setOnClickListener(v -> toggleConsole(consolePanel.getVisibility() != View.VISIBLE));
        findViewById(R.id.ide_console_close_btn).setOnClickListener(v -> toggleConsole(false));
        findViewById(R.id.ide_console_clear_btn).setOnClickListener(v -> consoleText.setText(""));
    }

    private void wireCapsSheet() {
        findViewById(R.id.ide_caps_close_btn).setOnClickListener(v -> toggleCapsSheet(false));
        findViewById(R.id.ide_caps_refresh_btn).setOnClickListener(v -> refreshCapability());
        capsArsenalBtn.setOnClickListener(v -> exitToArsenal());
    }

    private void wireChips() {
        layoutChip.setOnClickListener(v -> openLayoutPicker());
        capsPill.setOnClickListener(v -> toggleCapsSheet(capsSheet.getVisibility() != View.VISIBLE));
        statusPill.setOnClickListener(v -> {
            if (!syntaxOk && parseErrorLine > 0) {
                editor.scrollToLine(parseErrorLine);
                editor.requestFocus();
            } else {
                validator.validateNow();
            }
        });
    }

    private void wireLaunchers() {
        importLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(),
                this::onImported);
        exportLauncher = registerForActivityResult(new ActivityResultContracts.CreateDocument(PayloadFileIo.MIME),
                this::onExported);
    }

    private void wireBackHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                attemptClose();
            }
        });
    }

    private boolean onMenuItem(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_layout) {
            openLayoutPicker();
            return true;
        }
        if (id == R.id.action_capabilities) {
            toggleCapsSheet(capsSheet.getVisibility() != View.VISIBLE);
            return true;
        }
        if (id == R.id.action_save) {
            smartSave();
            return true;
        }
        if (id == R.id.action_import) {
            importLauncher.launch(new String[]{"*/*"});
            return true;
        }
        if (id == R.id.action_export) {
            exportToFile();
            return true;
        }
        if (id == R.id.action_rename) {
            openRenameDialog();
            return true;
        }
        if (id == R.id.action_clear) {
            confirmClear();
            return true;
        }
        return false;
    }

    @Override
    public void onValid(@NonNull Program program) {
        syntaxOk = true;
        parseErrorLine = 0;
        parseErrorMessage = null;
        int n = program.steps.size();
        statusPill.setText(getString(R.string.hid_editor_status_ready, n, n == 1 ? "" : "s"));
        statusPill.setBackgroundResource(R.drawable.chip_pill_ok);
        statusPill.setTextColor(0xFF2E7D32);
        editor.setErrorLine(-1);
        updateRunButtonAlpha();
    }

    @Override
    public void onEmpty() {
        syntaxOk = false;
        parseErrorLine = 0;
        parseErrorMessage = null;
        statusPill.setText(R.string.hid_editor_status_empty);
        statusPill.setBackgroundResource(R.drawable.chip_pill_warn);
        statusPill.setTextColor(0xFFEF6C00);
        editor.setErrorLine(-1);
        updateRunButtonAlpha();
    }

    @Override
    public void onError(int line, @NonNull String message) {
        syntaxOk = false;
        parseErrorLine = line;
        parseErrorMessage = message;
        statusPill.setText(getString(R.string.hid_editor_status_error, line));
        statusPill.setBackgroundResource(R.drawable.chip_pill_err);
        statusPill.setTextColor(0xFFC62828);
        editor.setErrorLine(line);
        updateRunButtonAlpha();
    }

    private void refreshMeta(Editable s) {
        if (meta == null) return;
        int lines = 1;
        int cursor = editor.getSelectionStart();
        int colStart = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '\n') {
                lines++;
                if (i < cursor) colStart = i + 1;
            }
        }
        int col = Math.max(1, (cursor < 0 ? 0 : cursor) - colStart + 1);
        meta.setText(getString(R.string.ide_meta, lines, col));
    }

    private void updateRunButtonAlpha() {
        boolean ready = capabilityOk && syntaxOk;
        runBtn.setAlpha(ready ? 1f : 0.7f);
    }

    private void refreshCapability() {
        capsVerdict.setText("PROBING…");
        capsVerdict.setBackgroundResource(R.drawable.chip_pill_warn);
        capsVerdict.setTextColor(0xFFEF6C00);
        capsPill.setText("…");
        capsPill.setBackgroundResource(R.drawable.chip_pill_warn);
        capsPill.setTextColor(0xFFEF6C00);
        new Thread(() -> {
            HidCapabilities caps = new HidCapabilityProbe(this).probe();
            runOnUiThread(() -> bindCapability(caps));
        }, "ide-cap-probe").start();
    }

    private void bindCapability(@NonNull HidCapabilities caps) {
        capabilityOk = caps.canInjectKeyboard();
        lastVerdict = caps.verdict;
        String fullText;
        String shortPill;
        int pillBg;
        int pillColor;
        boolean showArsenal = false;
        switch (caps.verdict) {
            case READY:           fullText = "READY · KEYBOARD BOUND"; shortPill = "READY";   pillBg = R.drawable.chip_pill_ok;   pillColor = 0xFF2E7D32; break;
            case ROOT_DENIED:     fullText = "NO ROOT";                shortPill = "NO ROOT"; pillBg = R.drawable.chip_pill_err;  pillColor = 0xFFC62828; break;
            case KERNEL_TOO_OLD:  fullText = "KERNEL TOO OLD";         shortPill = "KERNEL";  pillBg = R.drawable.chip_pill_err;  pillColor = 0xFFC62828; break;
            case CONFIGFS_MISSING:fullText = "CONFIGFS MISSING";       shortPill = "CFGFS";   pillBg = R.drawable.chip_pill_err;  pillColor = 0xFFC62828; break;
            case UDC_MISSING:     fullText = "NO UDC";                 shortPill = "NO UDC";  pillBg = R.drawable.chip_pill_err;  pillColor = 0xFFC62828; break;
            case HID_NODE_MISSING:fullText = "/dev/hidg0 MISSING";     shortPill = "ARSENAL"; pillBg = R.drawable.chip_pill_warn; pillColor = 0xFFEF6C00; showArsenal = true; break;
            case SELINUX_DENIES:  fullText = "SELINUX DENIES";         shortPill = "SELINUX"; pillBg = R.drawable.chip_pill_err;  pillColor = 0xFFC62828; break;
            default:              fullText = "?";                      shortPill = "?";       pillBg = R.drawable.chip_pill_warn; pillColor = 0xFFEF6C00;
        }
        capsVerdict.setText(fullText);
        capsVerdict.setBackgroundResource(pillBg);
        capsVerdict.setTextColor(pillColor);
        capsPill.setText(shortPill);
        capsPill.setBackgroundResource(pillBg);
        capsPill.setTextColor(pillColor);
        capsArsenalBtn.setVisibility(showArsenal ? View.VISIBLE : View.GONE);
        capsDetail.setText(String.format(Locale.US,
                "kernel        : %s\n"
              + "udc           : %s\n"
              + "configfs      : %s\n"
              + "/dev/hidg0    : %s\n"
              + "/dev/hidg1    : %s\n"
              + "mass storage  : %s\n"
              + "rndis         : %s\n"
              + "selinux       : %s",
                caps.kernelRelease.isEmpty() ? "?" : caps.kernelRelease,
                caps.udcName == null ? "—" : caps.udcName,
                caps.configFsMounted ? "✓ mounted" : "✗ unavailable",
                caps.keyboardNodePresent ? "✓ present" : "✗ missing",
                caps.mouseNodePresent ? "✓ present" : "✗ missing",
                caps.massStorageSupported ? "✓ supported" : "✗ unavailable",
                caps.rndisSupported ? "✓ supported" : "✗ unavailable",
                caps.selinuxEnforcing ? "enforcing" : "permissive"));
        updateRunButtonAlpha();
    }

    private void attemptRun() {
        String body = editorText();
        if (body.trim().isEmpty()) {
            core.toaster("Editor is empty");
            return;
        }
        validator.validateNow();
        if (!syntaxOk) {
            core.toaster("Fix syntax errors first — see line " + parseErrorLine);
            if (parseErrorLine > 0) editor.scrollToLine(parseErrorLine);
            toggleConsole(true);
            appendConsole("⛔ Cannot run: line " + parseErrorLine + " · "
                    + (parseErrorMessage == null ? "syntax error" : parseErrorMessage));
            return;
        }
        if (!capabilityOk) {
            if (lastVerdict == HidCapabilities.Verdict.HID_NODE_MISSING) {
                autoApplyAndRun(body);
                return;
            }
            String reason = lastVerdict == null ? "HID not ready" : lastVerdict.name();
            core.toaster("HID not ready — see capabilities (" + reason + ")");
            toggleConsole(true);
            appendConsole("⛔ Cannot run: " + capsVerdict.getText());
            toggleCapsSheet(true);
            return;
        }
        startRun(body);
    }

    private void autoApplyAndRun(@NonNull String body) {
        appendConsole("⚙ HID node missing — applying HID-to-Go gadget profile...");
        toggleConsole(true);
        runBtn.setEnabled(false);
        runBtn.setAlpha(0.45f);
        new Thread(() -> {
            GadgetProfile picked = findAutoProfile();
            if (picked == null) {
                runOnUiThread(() -> {
                    appendConsole("⛔ No HID-capable profile in USB Arsenal — open it and create one");
                    toggleCapsSheet(true);
                    runBtn.setEnabled(true);
                    runBtn.setAlpha(1f);
                });
                return;
            }
            UsbGadgetController ctl = new UsbGadgetController(HidIdeActivity.this);
            ctl.teardown();
            boolean ok = ctl.apply(picked);
            runOnUiThread(() -> {
                if (!ok) {
                    appendConsole("⛔ Failed to apply '" + picked.name + "' — check root + Arsenal");
                    toggleCapsSheet(true);
                    runBtn.setEnabled(true);
                    runBtn.setAlpha(1f);
                    return;
                }
                appendConsole("✓ Bound '" + picked.name + "'. Re-probing...");
                refreshCapability();
                new android.os.Handler(getMainLooper()).postDelayed(() -> {
                    HidCapabilities recheck = new HidCapabilityProbe(HidIdeActivity.this).probe();
                    bindCapability(recheck);
                    if (recheck.canInjectKeyboard()) {
                        appendConsole("▶ Starting run.");
                        startRun(body);
                    } else {
                        appendConsole("⚠ Bound but capability still " + recheck.verdict
                                + " — open USB Arsenal manually");
                        runBtn.setEnabled(true);
                        runBtn.setAlpha(1f);
                    }
                }, 1500);
            });
        }, "ide-auto-apply").start();
    }

    @Nullable
    private GadgetProfile findAutoProfile() {
        GadgetProfileDb db = new GadgetProfileDb(this);
        java.util.List<GadgetProfile> all = db.listAll();
        for (GadgetProfile p : all) {
            if (BackChannelMarker.HIDTOGO_PROFILE_NAME.equals(p.name)) return p;
        }
        for (GadgetProfile p : all) {
            if (p.functions.contains(GadgetFunction.HID_KEYBOARD)
             && p.functions.contains(GadgetFunction.ACM)) return p;
        }
        for (GadgetProfile p : all) {
            if (p.functions.contains(GadgetFunction.HID_KEYBOARD)) return p;
        }
        return null;
    }

    private void startRun(String body) {
        toggleConsole(true);
        progress.setProgress(0);
        consoleText.setText("");
        stopBtn.setEnabled(true);
        executor.run(body, activeLayoutCode, sink);
    }

    private final HidExecutor.ProgressSink sink = new HidExecutor.ProgressSink() {
        private int totalSteps;

        @Override
        public void onCapabilityChecked(@NonNull HidCapabilities caps) {
            bindCapability(caps);
        }

        @Override
        public void onStarted(int total) {
            totalSteps = total;
            progress.setMax(Math.max(total, 1));
            appendConsole("▶  Running " + total + " step" + (total == 1 ? "" : "s") + "…");
        }

        @Override
        public void onStep(@NonNull Step step, int index, int total) {
            progress.setProgress(index);
            appendConsole(getString(R.string.hid_progress_step,
                    step.sourceLine, total, step.getClass().getSimpleName()));
        }

        @Override
        public void onError(@NonNull Step step, @NonNull Throwable error) {
            appendConsole("⚠ line " + step.sourceLine + ": " + error.getMessage());
            editor.setErrorLine(step.sourceLine);
        }

        @Override
        public void onCaptured(@NonNull Step step, @Nullable String text) {
            if (text == null) {
                appendConsole(getString(R.string.ide_capture_timeout, step.sourceLine));
            } else {
                appendConsole(getString(R.string.ide_capture_received, step.sourceLine, text));
                new MaterialAlertDialogBuilder(HidIdeActivity.this)
                        .setTitle(R.string.ide_capture_title)
                        .setMessage(text)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            }
        }

        @Override
        public void onOpenViewer(@NonNull Step step) {
            appendConsole("line " + step.sourceLine + ": opening viewer");
            Intent i = new Intent(HidIdeActivity.this,
                    com.zalexdev.stryker.hid.backchannel.ScreenViewerActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP
                     | Intent.FLAG_ACTIVITY_CLEAR_TOP
                     | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(i);
        }

        @Override
        public void onFinished(@NonNull ExecutionResult result) {
            stopBtn.setEnabled(false);
            updateRunButtonAlpha();
            switch (result.kind) {
                case OK:
                    appendConsole(getString(R.string.hid_progress_done, totalSteps));
                    progress.setProgress(progress.getMax());
                    break;
                case CANCELLED:
                    appendConsole(getString(R.string.hid_progress_cancelled));
                    break;
                case PARSE_ERROR:
                    appendConsole(getString(R.string.hid_parse_error, result.errorLine, result.message));
                    editor.setErrorLine(result.errorLine);
                    if (result.errorLine > 0) editor.scrollToLine(result.errorLine);
                    break;
                case CAPABILITY_ERROR:
                    appendConsole(getString(R.string.hid_run_capability, result.message));
                    toggleCapsSheet(true);
                    break;
                case IO_ERROR:
                    appendConsole(getString(R.string.hid_progress_io_error, result.message));
                    break;
            }
        }
    };

    private void smartSave() {
        String body = editorText();
        if (body.trim().isEmpty()) {
            core.toaster("Editor is empty");
            return;
        }
        String defaultName = getString(R.string.ide_default_filename);
        if (savedAtLeastOnce && !currentFileName.equals(defaultName)) {
            String stem = stripExtension(currentFileName);
            if (library.save(stem, body)) {
                originalBody = body;
                dirty = false;
                core.toaster(getString(R.string.hid_save_ok));
                setResult(RESULT_OK);
            } else {
                core.toaster(getString(R.string.hid_save_failed));
            }
            return;
        }
        openSaveDialog(body);
    }

    private void openSaveDialog(String body) {
        TextInputLayout til = new TextInputLayout(this);
        til.setHint(getString(R.string.hid_save_dialog_hint));
        EditText name = new EditText(this);
        String suggested = stripExtension(currentFileName);
        if (suggested.equals(stripExtension(getString(R.string.ide_default_filename)))) suggested = "";
        name.setText(suggested);
        til.addView(name);
        LinearLayout wrapper = new LinearLayout(this);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        wrapper.setPadding(pad, pad / 2, pad, 0);
        wrapper.addView(til);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.hid_save_to_library)
                .setView(wrapper)
                .setPositiveButton(R.string.hid_save, (d, w) -> {
                    String n = name.getText().toString().trim();
                    if (n.isEmpty()) return;
                    if (library.save(n, body)) {
                        currentFileName = n + ".ducky";
                        fileName.setText(currentFileName);
                        originalBody = body;
                        dirty = false;
                        savedAtLeastOnce = true;
                        core.toaster(getString(R.string.hid_save_ok));
                        setResult(RESULT_OK);
                    } else {
                        core.toaster(getString(R.string.hid_save_failed));
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void openLayoutPicker() {
        Map<String, String> available = keymaps.available();
        if (available.isEmpty()) {
            core.toaster("No keymap layouts bundled");
            return;
        }
        List<String> codes = new ArrayList<>(available.keySet());
        String[] labels = new String[codes.size()];
        int selected = 0;
        for (int i = 0; i < codes.size(); i++) {
            String code = codes.get(i);
            labels[i] = available.get(code) + "  ·  " + code;
            if (code.equals(activeLayoutCode)) selected = i;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.hid_layout_pick)
                .setSingleChoiceItems(labels, selected, (d, idx) -> {
                    activeLayoutCode = codes.get(idx);
                    core.putString(PREF_LAYOUT, activeLayoutCode);
                    updateLayoutChip();
                    d.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void updateLayoutChip() {
        layoutChip.setText(activeLayoutCode.toUpperCase(Locale.ROOT));
    }

    private void openRenameDialog() {
        TextInputLayout til = new TextInputLayout(this);
        til.setHint(getString(R.string.hid_save_dialog_hint));
        EditText name = new EditText(this);
        name.setText(currentFileName);
        til.addView(name);
        LinearLayout wrapper = new LinearLayout(this);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        wrapper.setPadding(pad, pad / 2, pad, 0);
        wrapper.addView(til);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.ide_rename)
                .setView(wrapper)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String n = name.getText().toString().trim();
                    if (n.isEmpty()) return;
                    currentFileName = n;
                    fileName.setText(currentFileName);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void confirmClear() {
        if (editorText().trim().isEmpty()) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.hid_clear)
                .setMessage(R.string.ide_unsaved)
                .setPositiveButton(R.string.ide_discard, (d, w) -> editor.setText(""))
                .setNegativeButton(R.string.ide_keep_editing, null)
                .show();
    }

    private void exportToFile() {
        String body = editorText();
        if (body.trim().isEmpty()) {
            core.toaster("Editor is empty");
            return;
        }
        pendingExportBody = body;
        String base = stripExtension(currentFileName);
        if (base.isEmpty()) base = "stryker_" + System.currentTimeMillis();
        exportLauncher.launch(base + ".ducky");
    }

    private void onImported(@Nullable Uri uri) {
        if (uri == null) return;
        String body = PayloadFileIo.read(this, uri);
        if (body == null) {
            core.toaster(getString(R.string.hid_import_failed));
            return;
        }
        String name = uri.getLastPathSegment();
        if (name != null && name.contains("/")) {
            name = name.substring(name.lastIndexOf('/') + 1);
        }
        if (name != null && !name.isEmpty()) {
            currentFileName = name;
            fileName.setText(currentFileName);
        }
        originalBody = body;
        editor.setText(body);
        dirty = false;
    }

    private void onExported(@Nullable Uri uri) {
        if (uri == null || pendingExportBody == null) return;
        boolean ok = PayloadFileIo.write(this, uri, pendingExportBody);
        pendingExportBody = null;
        core.toaster(ok ? getString(R.string.hid_export_ok) : getString(R.string.hid_export_failed));
    }

    private void toggleConsole(boolean show) {
        consolePanel.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void toggleCapsSheet(boolean show) {
        capsSheet.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void appendConsole(String line) {
        if (consoleText == null) return;
        CharSequence cur = consoleText.getText();
        if (cur == null || cur.length() == 0
                || getString(R.string.hid_log_idle).contentEquals(cur)) {
            consoleText.setText(line);
        } else {
            consoleText.setText(cur + "\n" + line);
        }
    }

    private void attemptClose() {
        if (!dirty) {
            finish();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.ide_unsaved)
                .setPositiveButton(R.string.ide_discard, (d, w) -> finish())
                .setNegativeButton(R.string.ide_keep_editing, null)
                .show();
    }

    private void exitToArsenal() {
        Intent data = new Intent();
        data.putExtra(EXTRA_REQUEST_ARSENAL, true);
        setResult(RESULT_OK, data);
        finish();
    }

    private String editorText() {
        return editor.getText() == null ? "" : editor.getText().toString();
    }

    private static String stripExtension(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (validator != null) validator.dispose();
        if (executor != null && executor.isRunning()) executor.cancel();
    }
}
