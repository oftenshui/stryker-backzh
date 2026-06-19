package com.zalexdev.stryker.netdetect;

import android.app.Dialog;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.zalexdev.stryker.R;

public final class UsbDialogRenderer {

    private static final int COLOR_OK       = 0xFF2E7D32;
    private static final int COLOR_WARN     = 0xFFEF6C00;
    private static final int COLOR_ERR      = 0xFFC62828;
    private static final int COLOR_NEUTRAL  = 0xFF757575;

    private interface ViewLookup { View find(int id); }

    private final TextView title, subtitle, statusPill, busPill, speedPill, details;
    private final TextView capMonitor, capInject, capBand, warningsText;
    private final View emptyState, detectedState, capsCard, warningsCard;

    public UsbDialogRenderer(@NonNull View root) {
        this(root::findViewById);
    }

    public UsbDialogRenderer(@NonNull Dialog dialog) {
        this(dialog::findViewById);
    }

    private UsbDialogRenderer(@NonNull ViewLookup lookup) {
        emptyState    = lookup.find(R.id.usb_empty_state);
        detectedState = lookup.find(R.id.usb_detected_state);
        title         = (TextView) lookup.find(R.id.usb_title);
        subtitle      = (TextView) lookup.find(R.id.usb_subtitle);
        statusPill    = (TextView) lookup.find(R.id.usb_status_pill);
        busPill       = (TextView) lookup.find(R.id.usb_bus_pill);
        speedPill     = (TextView) lookup.find(R.id.usb_speed_pill);
        details       = (TextView) lookup.find(R.id.usb_details);
        capsCard      = lookup.find(R.id.usb_caps_card);
        capMonitor    = (TextView) lookup.find(R.id.cap_monitor);
        capInject     = (TextView) lookup.find(R.id.cap_inject);
        capBand       = (TextView) lookup.find(R.id.cap_band);
        warningsCard  = lookup.find(R.id.usb_warnings_card);
        warningsText  = (TextView) lookup.find(R.id.usb_warnings_text);
    }

    public void renderEmpty() {
        emptyState.setVisibility(View.VISIBLE);
        detectedState.setVisibility(View.GONE);
    }

    public void render(@NonNull UsbDeviceReport r) {
        emptyState.setVisibility(View.GONE);
        detectedState.setVisibility(View.VISIBLE);

        title.setText(r.displayName());
        subtitle.setText(r.vidPid + "  ·  bus " + r.port);

        bindStatusPill(r);
        bindBusPill(r);
        bindSpeedPill(r);
        bindCapabilities(r.chipset);
        bindWarnings(r);
        bindDetails(r);
    }

    private void bindStatusPill(UsbDeviceReport r) {
        switch (r.driverState()) {
            case BOUND_WITH_NETDEV:
                setPill(statusPill,
                        r.interfaces.netdevs.isEmpty()
                                ? "READY"
                                : "READY · " + r.interfaces.netdevs.get(0),
                        COLOR_OK);
                break;
            case BOUND_NO_NETDEV:
                setPill(statusPill, "BOUND · NO NETDEV", COLOR_WARN);
                break;
            case UNBOUND:
            default:
                setPill(statusPill, "NO DRIVER", COLOR_ERR);
        }
    }

    private void bindBusPill(UsbDeviceReport r) {
        if (r.chipset != null && r.chipset.kind != null
                && r.chipset.kind != ChipsetInfo.Kind.OTHER) {
            String label;
            switch (r.chipset.kind) {
                case WIFI:             label = "WIFI"; break;
                case ETHERNET:         label = "ETHERNET"; break;
                case MOBILE_BROADBAND: label = "TETHER"; break;
                default:               label = "USB"; break;
            }
            setPill(busPill, label, COLOR_NEUTRAL);
            busPill.setVisibility(View.VISIBLE);
        } else {
            busPill.setVisibility(View.GONE);
        }
    }

    private void bindSpeedPill(UsbDeviceReport r) {
        if (r.speedMbps == null || r.speedMbps.isEmpty()) {
            speedPill.setVisibility(View.GONE);
            return;
        }
        String label;
        try {
            int mbps = Integer.parseInt(r.speedMbps);
            if (mbps >= 5000)       label = "USB 3";
            else if (mbps >= 480)   label = "USB 2";
            else                    label = mbps + " Mbps";
        } catch (NumberFormatException e) {
            label = r.speedMbps;
        }
        setPill(speedPill, label, COLOR_NEUTRAL);
        speedPill.setVisibility(View.VISIBLE);
    }

    private void bindCapabilities(ChipsetInfo c) {
        if (c == null || c.kind == ChipsetInfo.Kind.OTHER
                || c.kind == ChipsetInfo.Kind.ETHERNET) {
            capsCard.setVisibility(View.GONE);
            return;
        }
        capsCard.setVisibility(View.VISIBLE);
        renderCap(capMonitor, c.monitor);
        renderCap(capInject,  c.injection);
        capBand.setText(c.bandLabel());
        capBand.setTextColor(c.band == ChipsetInfo.Band.DUAL ? COLOR_OK : COLOR_NEUTRAL);
    }

    private void renderCap(TextView tv, ChipsetInfo.Capability cap) {
        switch (cap) {
            case YES:         tv.setText("✓");        tv.setTextColor(COLOR_OK);      break;
            case PARTIAL:     tv.setText("~");        tv.setTextColor(COLOR_WARN);    break;
            case CONDITIONAL: tv.setText("!");        tv.setTextColor(COLOR_WARN);    break;
            case NO:          tv.setText("✗");        tv.setTextColor(COLOR_ERR);     break;
            default:          tv.setText("—");        tv.setTextColor(COLOR_NEUTRAL);
        }
    }

    private void bindWarnings(UsbDeviceReport r) {
        if (r.warnings.isEmpty()) {
            warningsCard.setVisibility(View.GONE);
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < r.warnings.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append("• ").append(r.warnings.get(i));
        }
        warningsText.setText(sb.toString());
        warningsCard.setVisibility(View.VISIBLE);
    }

    private void bindDetails(UsbDeviceReport r) {
        StringBuilder sb = new StringBuilder();
        if (r.chipset != null && r.chipset.driver != null) {
            sb.append("Driver:    ").append(r.chipset.driver);
        } else if (!r.interfaces.drivers.isEmpty()) {
            sb.append("Driver:    ").append(r.interfaces.drivers.get(0));
        } else {
            sb.append("Driver:    (none bound)");
        }
        sb.append('\n').append("Bus path:  ").append(r.sysPath);
        if (r.manufacturer != null) sb.append('\n').append("Vendor:    ").append(r.manufacturer);
        if (r.product != null)      sb.append('\n').append("Product:   ").append(r.product);
        if (r.chipset != null && r.chipset.firmware != null) {
            sb.append('\n').append("Firmware:  ").append(r.chipset.firmware);
        }
        if (r.chipset != null && r.chipset.kernelMin != null) {
            sb.append('\n').append("Kernel ≥:  ").append(r.chipset.kernelMin);
        }
        if (!r.interfaces.modaliases.isEmpty()) {
            sb.append('\n').append("Modalias:  ").append(r.interfaces.modaliases.get(0));
        }
        switch (r.chipsetSource) {
            case CURATED: sb.append('\n').append("DB:        curated"); break;
            case LEGACY:  sb.append('\n').append("DB:        legacy fallback"); break;
            case UNKNOWN: sb.append('\n').append("DB:        unknown (not in DB)"); break;
        }
        details.setText(sb.toString());
    }

    private static void setPill(TextView pill, String text, int color) {
        pill.setText(text);
        pill.setTextColor(color);
    }
}
