package com.zalexdev.stryker;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.os.StrictMode;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.zalexdev.stryker.about.AboutFragment;
import com.zalexdev.stryker.appintro.AppIntroActivity;
import com.zalexdev.stryker.coremanger.CoreManager;
import com.zalexdev.stryker.custom.Device;
import com.zalexdev.stryker.custom.WiFINetwork;
import com.zalexdev.stryker.dashboard.Dashboard;
import com.zalexdev.stryker.arsenal.ArsenalFragment;
import com.zalexdev.stryker.geomac.GeoMac;
import com.zalexdev.stryker.handshakes.HandshakeStorage;
import com.zalexdev.stryker.localnetwork.LocalMain;
import com.zalexdev.stryker.macchanger.MACChangerFragment;
import com.zalexdev.stryker.metasploit.InstallMetasploit;
import com.zalexdev.stryker.metasploit.utils.MetasploitUtils;
import com.zalexdev.stryker.nmap.NmapScanner;
import com.zalexdev.stryker.nuclei.InstallNuclei;
import com.zalexdev.stryker.ota.UpdateManager;
import com.zalexdev.stryker.routerscan.RouterScanMain;
import com.zalexdev.stryker.settings.SettingsNew;
import com.zalexdev.stryker.utils.Core;
import com.zalexdev.stryker.utils.PromoDialogs;
import com.zalexdev.stryker.hid.ui.HidFragment;
import com.zalexdev.stryker.usbarsenal.ui.UsbArsenalFragment;
import com.zalexdev.stryker.vnc.VNCFragment;
import com.zalexdev.stryker.wifi.Wifi;
import com.zalexdev.stryker.wpair.WpairFragment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity {

    public static final String EXTRA_OPEN_UPDATE = "open_update";

    private static TextView logo;
    private static ImageView menu;
    private static ImageView settings;
    private static int lastSelectedItemId = 0;
    private Core core;
    private static FragmentManager fragmentManager;
    private TempFragment tempFragment;
    private static final HashMap<Integer, View> drawerRows = new HashMap<>();
    private static final java.util.LinkedHashMap<Integer, DrawerSpec> DRAWER_SPECS = buildDrawerSpecs();
    private MetasploitUtils metasploitUtils;
    private ArrayList<WiFINetwork> networks;
    private ArrayList<Device> devices = new ArrayList<>();
    private boolean usbState = false;
    private BottomSheetDialog usbDialog;
    private final Receiver receiver = new Receiver();

    @SuppressLint({"NonConstantResourceId", "UseCompatLoadingForDrawables", "SetTextI18n", "SdCardPath"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        core = new Core(this);
        setContentView(R.layout.activity_main);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            String NOTIFICATION_PERMISSION = Manifest.permission.POST_NOTIFICATIONS;
            if (checkSelfPermission(NOTIFICATION_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{NOTIFICATION_PERMISSION}, 1337);
            }
        }
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
        fragmentManager = getSupportFragmentManager();
        fragmentManager.beginTransaction().replace(R.id.flContent, new Initializing()).commit();
        settings = findViewById(R.id.settings_icon);
        settings.setOnClickListener(view -> {
            Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.flContent);
            if (currentFragment instanceof SettingsNew) {
                closeSettings();
            } else {
                openSettings();
            }
        });

        new Thread(this::copyAssets).start();
        if (core.getBoolean("clean_after_update")) {
            new Thread(() -> core.deleteFile("/data/data/com.zalexdev.stryker/files/Stryker.apk")).start();
            core.remove("clean_after_update");
        }

        if (core != null) {
            if (!core.getBoolean("first_open") || !core.checkFile("/data/local/stryker/release/4.0")) {
                core.putString("username", "User");
                Intent install = new Intent(this, AppIntroActivity.class);
                startActivity(install);
            } else {
                new Thread(() -> {
                    if (!core.checkFolder("/data/local/stryker/release/usr")) {
                        Intent install = new Intent(this, AppIntroActivity.class);
                        install.putExtra("update", false);
                        startActivity(install);
                    } else {
                        if (core.checkFolder("/data/local/stryker/release/sdcard/Stryker")) {
                            fragmentManager.beginTransaction().replace(R.id.flContent, new Dashboard()).commit();
                            schedulePromo();
                            checkForUsb();
                            if (!isConnected()) {
                                new Thread(() -> core.getInterfacesList()).start();
                            }
                            new Thread(() -> {
                                if (core != null && core.getBoolean("msf")) {
                                    metasploitUtils = new MetasploitUtils(MainActivity.this, MainActivity.this);
                                }
                                assert core != null;
                                core.chmodFolder("/data/data/com.zalexdev.stryker/files");
                            }).start();
                        } else {
                            if (!core.mountCore()) {
                                fragmentManager.beginTransaction().replace(R.id.flContent, new Error()).commit();
                            } else {
                                fragmentManager.beginTransaction().replace(R.id.flContent, new Dashboard()).commit();
                                schedulePromo();
                                checkForUsb();
                                new Thread(() -> {
                                    if (core != null && core.getBoolean("msf")) {
                                        metasploitUtils = new MetasploitUtils(MainActivity.this, MainActivity.this);
                                    }
                                }).start();
                                if (!isConnected()) {
                                    new Thread(() -> core.getInterfacesList()).start();
                                }
                            }
                        }
                    }
                }).start();
            }
        }

        DrawerLayout drawer = findViewById(R.id.drawerLayout);

        logo = findViewById(R.id.stryker_main_logo);
        menu = findViewById(R.id.menu_img);
        menu.setOnClickListener(view -> {
            if (core != null) {
                if (core.getBoolean("nav_type")) {
                    drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
                } else {
                    drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
                    if (drawer.isDrawerOpen(GravityCompat.START)) {
                        drawer.closeDrawer(GravityCompat.START);
                    } else {
                        drawer.openDrawer(GravityCompat.START);
                    }
                }
            }
        });

        View navView = findViewById(R.id.nav_view);
        wireDrawerRows(navView, drawer);

        View menuCloseDrawer = navView.findViewById(R.id.menu_close_drawer);
        menuCloseDrawer.setOnClickListener(v -> {
            if (drawer.isDrawerOpen(GravityCompat.START)) drawer.closeDrawers();
        });

        TextView arch = navView.findViewById(R.id.drawer_arch_badge);
        if (arch != null) arch.setText(core != null && core.is64Bit() ? "arm64" : "arm32");
        TextView chrootStatus = navView.findViewById(R.id.drawer_chroot_status);
        new Thread(() -> {
            boolean mounted = core != null
                    && core.checkFolder("/data/local/stryker/release/sdcard/Stryker");
            runOnUiThread(() -> {
                if (chrootStatus != null) {
                    chrootStatus.setText(mounted ? "Chroot mounted" : "Chroot detached");
                }
            });
        }).start();

        TextView versionTv = navView.findViewById(R.id.drawer_version);
        if (versionTv != null) versionTv.setText("v" + BuildConfig.VERSION_NAME);

        if (lastSelectedItemId == 0) {
            receiver.changeFragmentQuiet(R.id.dasboard_item);
        }

        boolean openUpdate = getIntent() != null
                && getIntent().getBooleanExtra(EXTRA_OPEN_UPDATE, false);
        UpdateManager.checkAndPrompt(this, openUpdate);
    }

    private void wireDrawerRows(View navView, DrawerLayout drawer) {
        drawerRows.clear();
        for (java.util.Map.Entry<Integer, DrawerSpec> e : DRAWER_SPECS.entrySet()) {
            int rowId = e.getKey();
            DrawerSpec spec = e.getValue();
            View row = navView.findViewById(rowId);
            if (row == null) continue;
            TextView title = row.findViewById(R.id.row_title);
            ImageView icon = row.findViewById(R.id.row_icon);
            if (title != null) title.setText(spec.title);
            if (icon != null) {
                icon.setImageResource(spec.iconRes);
                icon.setColorFilter(ContextCompat.getColor(this, R.color.grey));
            }
            drawerRows.put(rowId, row);
            row.setOnClickListener(v -> {
                if (drawer.isDrawerOpen(GravityCompat.START)) drawer.closeDrawers();
                settings.setImageDrawable(ContextCompat.getDrawable(getApplicationContext(), R.drawable.settings));
                receiver.changeFragment(rowId);
            });
        }
    }

    public MetasploitUtils getMetasploitUtils() {
        return metasploitUtils;
    }

    public void setMetasploitUtils(MetasploitUtils metasploitUtils) {
        this.metasploitUtils = metasploitUtils;
    }

    public ArrayList<Device> getDevices() {
        return devices;
    }

    public void setDevices(ArrayList<Device> devices) {
        this.devices = devices;
    }

    public ArrayList<WiFINetwork> getNetworks() {
        return networks;
    }

    public void setNetworks(ArrayList<WiFINetwork> networks) {
        this.networks = networks;
    }

    private void usbDialog() {
        if (usbDialog != null && usbDialog.isShowing()) {
            return;
        }
        usbDialog = new BottomSheetDialog(this, R.style.ThemeOverlay_Stryker_BottomSheetDialog);
        usbDialog.setDismissWithAnimation(true);
        usbDialog.setContentView(R.layout.usb_dialog);

        com.zalexdev.stryker.netdetect.UsbDialogRenderer renderer =
                new com.zalexdev.stryker.netdetect.UsbDialogRenderer(usbDialog);
        renderer.renderEmpty();

        View changeListen = usbDialog.findViewById(R.id.change_listen);
        View changeDeauth = usbDialog.findViewById(R.id.change_deauth);
        View refresh      = usbDialog.findViewById(R.id.usb_refresh_btn);
        if (changeListen != null) changeListen.setOnClickListener(v -> changeInterface(true));
        if (changeDeauth != null) changeDeauth.setOnClickListener(v -> changeInterface(false));
        if (refresh != null) refresh.setOnClickListener(v -> scanUsb(renderer));
        usbDialog.show();

        scanUsb(renderer);
    }

    private void scanUsb(com.zalexdev.stryker.netdetect.UsbDialogRenderer renderer) {
        new Thread(() -> {
            java.util.List<com.zalexdev.stryker.netdetect.UsbDeviceReport> devs =
                    com.zalexdev.stryker.netdetect.NetDetector.listNetworkUsbDevices(this);
            com.zalexdev.stryker.netdetect.UsbDeviceReport pick = devs.isEmpty() ? null : devs.get(0);
            runOnUiThread(() -> {
                if (pick == null) renderer.renderEmpty();
                else renderer.render(pick);
            });
        }).start();
    }

    private void changeInterface(boolean isScan) {
        ArrayList<String> w = null;
        try {
            w = getInterfaces();
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }
        assert w != null;
        String[] w2 = new String[w.size() + 1];
        for (int i = 0; i < w.size(); i++) {
            w2[i] = w.get(i);
        }
        w2[w2.length - 1] = getResources().getString(R.string.customvalue);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.pick)
                .setItems(w2, (dialogInterface, i) -> {
                    if (i != w2.length - 1) {
                        if (isScan) {
                            core.putString("wlan_scan", w2[i]);
                        } else {
                            core.putString("wlan_deauth", w2[i]);
                        }
                    } else {
                        new Thread(() -> {
                            final String[] temp = {""};
                            runOnUiThread(() -> {
                                final Dialog valuedialog = new Dialog(this);
                                valuedialog.setContentView(R.layout.input_dialog);
                                valuedialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                                valuedialog.getWindow().setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                                TextView title = valuedialog.findViewById(R.id.title);
                                TextInputEditText value = valuedialog.findViewById(R.id.value);
                                MaterialButton ok = valuedialog.findViewById(R.id.ok);
                                MaterialButton cancel = valuedialog.findViewById(R.id.cancel);
                                cancel.setOnClickListener(view12 -> valuedialog.dismiss());
                                title.setText(getResources().getString(R.string.customvalue));
                                ok.setOnClickListener(view1 -> {
                                    temp[0] = Objects.requireNonNull(value.getText()).toString();
                                    valuedialog.dismiss();
                                });
                                valuedialog.show();
                            });
                            while (temp[0].equals("")) {
                                try { Thread.sleep(50); } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    return;
                                }
                            }
                            if (isScan) {
                                core.putString("wlan_scan", temp[0]);
                            } else {
                                core.putString("wlan_deauth", temp[0]);
                            }
                        }).start();
                    }
                })
                .show();
    }

    private boolean isConnected() {
        return getConnectedUSBDevices().size() > 0;
    }

    private void schedulePromo() {
        new android.os.Handler(getMainLooper()).postDelayed(
                () -> PromoDialogs.maybeShow(MainActivity.this), 1500);
    }

    @SuppressLint("SdCardPath")
    private void copyAssets() {
        AssetManager assetManager = getAssets();
        String[] files = null;
        try {
            files = assetManager.list("");
        } catch (IOException e) {
        }
        if (files != null) for (String filename : files) {
            if (filename.equals("busybox32") || filename.equals("busybox64")) continue;
            InputStream in = null;
            OutputStream out = null;
            try {
                in = assetManager.open(filename);
                File outFile = new File("/data/data/com.zalexdev.stryker/files/", filename);
                out = new FileOutputStream(outFile);
                copyFile(in, out);
            } catch (IOException ignored) {
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException ignored) {
                    }
                }
                if (out != null) {
                    try {
                        out.close();
                    } catch (IOException ignored) {
                    }
                }
            }
        }
        Core.extractBusybox(this);
        copyAssets2();
    }

    @SuppressLint("SdCardPath")
    private void copyAssets2() {
        AssetManager assetManager = getAssets();
        String[] files = null;
        try {
            files = assetManager.list("imgs_adapters");
        } catch (IOException e) {
        }
        if (files != null) for (String filename : files) {
            InputStream in = null;
            OutputStream out = null;
            try {
                in = assetManager.open(filename);
                File outFile = new File("/data/data/com.zalexdev.stryker/files/imgs_adapters", filename);
                out = new FileOutputStream(outFile);
                copyFile(in, out);
            } catch (IOException ignored) {
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException ignored) {
                    }
                }
                if (out != null) {
                    try {
                        out.close();
                    } catch (IOException ignored) {
                    }
                }
            }
        }
    }

    private void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }

    private ArrayList<String> getInterfaces() throws ExecutionException, InterruptedException {
        return core.getInterfacesList();
    }

    private void checkForUsb() {
        Timer usb = new Timer();
        usb.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                boolean temp = usbState;
                usbState = isConnected();
                if (temp != usbState && usbState) {
                    runOnUiThread(() -> usbDialog());
                } else if (!usbState) {
                    runOnUiThread(() -> {
                        if (usbDialog != null) {
                            usbDialog.dismiss();
                        }
                    });
                }
            }
        }, 0, 300);
    }

    private ArrayList<UsbDevice> getConnectedUSBDevices() {
        ArrayList<UsbDevice> devices = new ArrayList<>();
        UsbManager manager = (UsbManager) MainActivity.this.getSystemService(Context.USB_SERVICE);
        HashMap<String, UsbDevice> connectedDevices = manager.getDeviceList();
        for (String deviceName : connectedDevices.keySet()) {
            devices.add(connectedDevices.get(deviceName));
        }
        return devices;
    }

    private void openSettings() {
        settings.setImageDrawable(getDrawable(R.drawable.close));
        tempFragment = new TempFragment(getSupportFragmentManager().findFragmentById(R.id.flContent),
                lastSelectedItemId != 0 ? lastSelectedItemId : R.id.dasboard_item);
        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(R.anim.settings_open_enter, R.anim.settings_open_exit)
                .replace(R.id.flContent, new SettingsNew())
                .commit();
        receiver.changeFragmentQuiet(R.id.dasboard_item);
    }

    private void closeSettings() {
        settings.setImageDrawable(getDrawable(R.drawable.settings));
        receiver.changeFragment(tempFragment != null ? tempFragment.getItemId() : R.id.dasboard_item,
                R.anim.settings_close_enter, R.anim.settings_close_exit);
    }

    @Override
    public void onBackPressed() {
        FragmentManager fm = getSupportFragmentManager();
        Fragment current = fm.findFragmentById(R.id.flContent);
        if (current instanceof SettingsNew) {
            if (((SettingsNew) current).popChild()) return;
            closeSettings();
            return;
        }
        if (fm.getBackStackEntryCount() > 0) {
            fm.popBackStack();
        } else {
            if (core != null && !core.getBoolean("nav_type")) {
                DrawerLayout drawer = findViewById(R.id.drawerLayout);
                if (drawer.isDrawerOpen(GravityCompat.START)) {
                    drawer.closeDrawer(GravityCompat.START);
                } else {
                    drawer.openDrawer(GravityCompat.START);
                }
            }
        }
    }

    private static class TempFragment {

        private final Fragment fragment;
        private final int itemId;

        public TempFragment(Fragment fragment, int itemId) {
            this.fragment = fragment;
            this.itemId = itemId;
        }

        public Fragment getFragment() {
            return fragment;
        }

        public int getItemId() {
            return itemId;
        }
    }

    private interface MainActivityReceiver {
        void setTitle(String title);
        void restoreTitle();
        String getTitle();
        void setToolBarMenuEnabled(boolean bool);
        boolean isToolBarMenuEnabled();
        void changeFragmentQuiet(int itemId);
        void changeFragment(int itemId);
        void changeFragment(int motherItemId, Fragment fragment);
        void changeFragment(int motherItemId, Fragment fragment, String backstackName);
    }

    public static class Receiver implements MainActivityReceiver {

        @Override
        public void setTitle(String title) {
            logo.setText(title);
        }

        @SuppressLint("SetTextI18n")
        @Override
        public void restoreTitle() {
            logo.setText("StrykerOSS");
        }

        @Override
        public String getTitle() {
            return logo.getText().toString();
        }

        @Override
        public void setToolBarMenuEnabled(boolean bool) {
            menu.setEnabled(bool);
            menu.setAlpha(bool ? 1f : .75f);
            settings.setEnabled(bool);
            settings.setAlpha(bool ? 1f : .75f);
        }

        @Override
        public boolean isToolBarMenuEnabled() {
            return (menu.isEnabled() && settings.isEnabled());
        }

        @Override
        public void changeFragmentQuiet(int itemId) {
            View prev = drawerRows.get(lastSelectedItemId);
            if (prev != null) styleDrawerRow(prev, false, lastSelectedItemId);
            View next = drawerRows.get(itemId);
            if (next != null) styleDrawerRow(next, true, itemId);
            lastSelectedItemId = itemId;
        }

        private void styleDrawerRow(View row, boolean active, int itemId) {
            row.setActivated(active);
            Context ctx = row.getContext();
            TextView title = row.findViewById(R.id.row_title);
            ImageView icon = row.findViewById(R.id.row_icon);
            DrawerSpec spec = DRAWER_SPECS.get(itemId);
            if (active) {
                if (title != null) {
                    title.setTextColor(ContextCompat.getColor(ctx, R.color.stryker_accent));
                    title.setTypeface(null, android.graphics.Typeface.BOLD);
                }
                if (icon != null && spec != null) {
                    icon.setColorFilter(spec.activeTint);
                }
            } else {
                if (title != null) {
                    title.setTextColor(ContextCompat.getColor(ctx, R.color.night_contrast));
                    title.setTypeface(null, android.graphics.Typeface.NORMAL);
                }
                if (icon != null) {
                    icon.setColorFilter(ContextCompat.getColor(ctx, R.color.grey));
                }
            }
        }

        @Override
        public void changeFragment(int itemId) {
            changeFragment(itemId, R.anim.nav_fade_enter, R.anim.nav_fade_exit);
        }

        public void changeFragment(int itemId, int enterAnim, int exitAnim) {
            if (settings != null) {
                settings.setImageDrawable(settings.getContext().getDrawable(R.drawable.settings));
            }
            changeFragmentQuiet(itemId);
            if (itemId == R.id.terminal_item) {
                Intent terminal = new Intent(settings.getContext(),
                        com.stryker.terminal.ui.term.NeoTermActivity.class);
                terminal.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                settings.getContext().startActivity(terminal);
                return;
            }
            Fragment target = fragmentFor(itemId);
            if (target != null) {
                if (fragmentManager.getBackStackEntryCount() > 0) {
                    fragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
                }
                fragmentManager.beginTransaction()
                        .setCustomAnimations(enterAnim, exitAnim)
                        .replace(R.id.flContent, target)
                        .commit();
            }
        }

        private static Fragment fragmentFor(int itemId) {
            if (itemId == R.id.wifi_item) return new Wifi();
            if (itemId == R.id.lan_item) return new LocalMain();
            if (itemId == R.id.macchanger_item) return new MACChangerFragment();
            if (itemId == R.id.dasboard_item) return new Dashboard();
            if (itemId == R.id.arsenal_item) return new ArsenalFragment();
            if (itemId == R.id.manager_item) return new CoreManager();
            if (itemId == R.id.nuclei_item) return new InstallNuclei();
            if (itemId == R.id.hs_item) return new HandshakeStorage();
            if (itemId == R.id.metasploit_item) return new InstallMetasploit();
            if (itemId == R.id.geomac_item) return new GeoMac();
            if (itemId == R.id.nmap_item) return new NmapScanner();
            if (itemId == R.id.router_scan_item) return new RouterScanMain();
            if (itemId == R.id.wpair_item) return new WpairFragment();
            if (itemId == R.id.about_item) return new AboutFragment();
            if (itemId == R.id.vnc_item) return new VNCFragment();
            if (itemId == R.id.hid_item) return new HidFragment();
            if (itemId == R.id.usb_arsenal_item) return new UsbArsenalFragment();
            return null;
        }

        @Override
        public void changeFragment(int motherItemId, Fragment fragment) {
            changeFragment(motherItemId, fragment, null);
        }

        @Override
        public void changeFragment(int motherItemId, Fragment fragment, String backstackName) {
            changeFragmentQuiet(motherItemId);
            FragmentTransaction transaction = fragmentManager.beginTransaction()
                    .setCustomAnimations(R.anim.nav_fade_enter, R.anim.nav_fade_exit,
                            R.anim.nav_fade_enter, R.anim.nav_fade_exit)
                    .replace(R.id.flContent, fragment);
            if (backstackName != null && !backstackName.isEmpty()) {
                transaction.addToBackStack(backstackName);
            }
            transaction.commit();
        }
    }

    private static final class DrawerSpec {
        final String title;
        final int iconRes;
        final int activeTint;

        DrawerSpec(String title, int iconRes, int activeTint) {
            this.title = title;
            this.iconRes = iconRes;
            this.activeTint = activeTint;
        }
    }

    private static java.util.LinkedHashMap<Integer, DrawerSpec> buildDrawerSpecs() {
        java.util.LinkedHashMap<Integer, DrawerSpec> m = new java.util.LinkedHashMap<>();
        m.put(R.id.dasboard_item,     new DrawerSpec("Dashboard",        R.drawable.home,        0xFF1565C0));
        m.put(R.id.terminal_item,     new DrawerSpec("Terminal",         R.drawable.terminal,    0xFF1565C0));
        m.put(R.id.wifi_item,         new DrawerSpec("WiFi networks",    R.drawable.wifi,        0xFF1565C0));
        m.put(R.id.hs_item,           new DrawerSpec("Handshakes",       R.drawable.storage,     0xFF00897B));
        m.put(R.id.macchanger_item,   new DrawerSpec("MAC changer",      R.drawable.password,    0xFF1565C0));
        m.put(R.id.router_scan_item,  new DrawerSpec("Router scan",      R.drawable.router,      0xFF2E7D32));
        m.put(R.id.wpair_item,        new DrawerSpec("WhisperPair (BLE)", R.drawable.wpair,      0xFF3949AB));
        m.put(R.id.lan_item,          new DrawerSpec("Local network",    R.drawable.lan,         0xFFAB47BC));
        m.put(R.id.nmap_item,         new DrawerSpec("Nmap",             R.drawable.scanner,     0xFFAB47BC));
        m.put(R.id.nuclei_item,       new DrawerSpec("Web scanner",      R.drawable.webscan,     0xFF3949AB));
        m.put(R.id.arsenal_item,      new DrawerSpec("Arsenal",          R.drawable.motion_blur, 0xFFEF6C00));
        m.put(R.id.hid_item,          new DrawerSpec("HID Attacks",      R.drawable.keyboard,    0xFFC62828));
        m.put(R.id.metasploit_item,   new DrawerSpec("Metasploit",       R.drawable.shield,      0xFFC62828));
        m.put(R.id.geomac_item,       new DrawerSpec("GeoMac",           R.drawable.map,         0xFF00838F));
        m.put(R.id.vnc_item,          new DrawerSpec("VNC desktop",      R.drawable.vnc,         0xFF5E35B1));
        m.put(R.id.usb_arsenal_item,  new DrawerSpec("USB Arsenal",      R.drawable.usb,         0xFF1565C0));
        m.put(R.id.manager_item,      new DrawerSpec("Core manager",     R.drawable.tune,        0xFF5E35B1));
        m.put(R.id.about_item,        new DrawerSpec("About",            R.drawable.info_outlined, 0xFF1565C0));
        return m;
    }
}
