package com.zalexdev.stryker.utils;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.zalexdev.stryker.R;
import com.zalexdev.stryker.custom.License;
import com.zalexdev.stryker.license.LicenseAdapter;

import java.util.ArrayList;

public class LicenseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_license);

        MaterialToolbar toolbar = findViewById(R.id.license_toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        RecyclerView recyclerView = findViewById(R.id.license_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(new LicenseAdapter(this, this, buildLicenses()));
    }

    private ArrayList<License> buildLicenses() {
        ArrayList<License> l = new ArrayList<>();

        l.add(License.section("Android & Java libraries"));
        l.add(License.of("AndroidX AppCompat", "Apache License 2.0", "The Android Open Source Project",
                "https://developer.android.com/jetpack/androidx/releases/appcompat"));
        l.add(License.of("Material Components for Android", "Apache License 2.0", "Google LLC",
                "https://github.com/material-components/material-components-android"));
        l.add(License.of("AndroidX ConstraintLayout", "Apache License 2.0", "The Android Open Source Project",
                "https://github.com/androidx/constraintlayout"));
        l.add(License.of("AndroidX Legacy Support v4", "Apache License 2.0", "The Android Open Source Project",
                "https://developer.android.com/jetpack/androidx/releases/legacy"));
        l.add(License.of("AndroidX RecyclerView", "Apache License 2.0", "The Android Open Source Project",
                "https://developer.android.com/jetpack/androidx/releases/recyclerview"));
        l.add(License.of("AndroidX Security Crypto", "Apache License 2.0", "The Android Open Source Project",
                "https://developer.android.com/jetpack/androidx/releases/security"));
        l.add(License.of("AndroidX Lifecycle", "Apache License 2.0", "The Android Open Source Project",
                "https://developer.android.com/jetpack/androidx/releases/lifecycle"));
        l.add(License.of("AndroidX Annotation", "Apache License 2.0", "The Android Open Source Project",
                "https://developer.android.com/jetpack/androidx/releases/annotation"));
        l.add(License.of("AndroidX CardView", "Apache License 2.0", "The Android Open Source Project",
                "https://developer.android.com/jetpack/androidx/releases/cardview"));
        l.add(License.of("AndroidX Preference", "Apache License 2.0", "The Android Open Source Project",
                "https://developer.android.com/jetpack/androidx/releases/preference"));
        l.add(License.of("OkHttp", "Apache License 2.0", "Square, Inc.",
                "https://square.github.io/okhttp/"));
        l.add(License.of("ExpandableLayout", "Apache License 2.0", "Copyright 2016 Daniel Cachapa",
                "https://github.com/cachapa/ExpandableLayout"));
        l.add(License.of("Lottie", "Apache License 2.0", "Airbnb, Inc.",
                "https://github.com/airbnb/lottie-android"));
        l.add(License.of("IPAddress", "Apache License 2.0", "Sean C. Foley",
                "https://github.com/seancfoley/IPAddress"));
        l.add(License.of("jsoup", "MIT License", "Copyright (c) 2009-2024 Jonathan Hedley",
                "https://jsoup.org/license"));
        l.add(License.of("Shimmer for Android", "BSD 3-Clause License", "Meta Platforms, Inc. and affiliates",
                "https://github.com/facebook/shimmer-android"));
        l.add(License.of("jCIFS", "GNU LGPL v2.1", "The jCIFS Project — Michael B. Allen",
                "https://jcifs.samba.org/"));
        l.add(License.of("Apache Commons Net", "Apache License 2.0", "The Apache Software Foundation",
                "https://commons.apache.org/proper/commons-net/"));
        l.add(License.of("osmdroid", "Apache License 2.0", "The osmdroid contributors",
                "https://github.com/osmdroid/osmdroid"));
        l.add(License.of("Bouncy Castle (bcpkix)", "Bouncy Castle Licence (MIT-style)",
                "The Legion of the Bouncy Castle Inc.", "https://www.bouncycastle.org/licence.html"));
        l.add(License.of("Apache Commons Codec", "Apache License 2.0", "The Apache Software Foundation",
                "https://commons.apache.org/proper/commons-codec/"));
        l.add(License.of("TapTargetView", "Apache License 2.0", "Copyright 2016 Keepsafe Software Inc.",
                "https://github.com/KeepSafe/TapTargetView"));
        l.add(License.of("ExpandableFab", "MIT License", "Copyright (c) 2020 Kelvin Abumere, The Nambi Company",
                "https://github.com/nambicompany/expandable-fab"));

        l.add(License.section("Bundled terminal"));
        l.add(License.of("NeoTerm", "GNU GPL v3.0", "NeoTerm authors — basis of the bundled terminal",
                "https://github.com/NeoTerrm/NeoTerm"));
        l.add(License.of("Termux terminal-emulator / terminal-view", "GNU GPL v3.0 (Apache-2.0 upstream code)",
                "Termux contributors; Android Terminal Emulator (Jack Palevich, AOSP)",
                "https://github.com/termux/termux-app/blob/master/LICENSE.md"));
        l.add(License.of("EventBus", "Apache License 2.0", "Markus Junginger, greenrobot",
                "https://github.com/greenrobot/EventBus"));
        l.add(License.of("RecyclerView-FastScroll", "Apache License 2.0", "Tim Malseed; The Android Open Source Project",
                "https://github.com/timusus/RecyclerView-FastScroll"));
        l.add(License.of("Color-O-Matic", "GNU GPL v3.0 or later", "Copyright (C) 2016 GrenderG",
                "https://github.com/GrenderG/Color-O-Matic"));
        l.add(License.of("AndroidRetroFile", "GNU GPL v2.0 with Classpath Exception",
                "Hai Zhang; OpenJDK (Oracle and/or its affiliates)",
                "https://github.com/zhanghai/AndroidRetroFile"));
        l.add(License.of("libsu", "Apache License 2.0", "John \"topjohnwu\" Wu",
                "https://github.com/topjohnwu/libsu"));
        l.add(License.of("AndroidUtil", "Apache License 2.0", "Michael Rapp",
                "https://github.com/michael-rapp/AndroidUtil"));

        l.add(License.section("Bundled binaries"));
        l.add(License.of("BusyBox", "GNU GPL v2.0 only", "Erik Andersen, Rob Landley, Denys Vlasenko et al.",
                "https://busybox.net/license.html"));
        l.add(License.of("SQLite", "Public Domain", "Dedicated to the public domain by its authors (Hwaci)",
                "https://www.sqlite.org/copyright.html"));

        l.add(License.section("Chroot environment & security tools"));
        l.add(License.of("Alpine Linux (apk-tools, musl)", "Mixed — apk-tools GPL-2.0, musl MIT",
                "Alpine Linux team; musl: Rich Felker and contributors",
                "https://alpinelinux.org/"));
        l.add(License.of("Metasploit Framework", "BSD 3-Clause License", "Copyright (c) 2006-2024 Rapid7, Inc.",
                "https://github.com/rapid7/metasploit-framework/blob/master/LICENSE"));
        l.add(License.of("MSFPC", "MIT License", "Copyright (c) 2015 g0tmi1k",
                "https://github.com/g0tmi1k/msfpc"));
        l.add(License.of("Nuclei", "MIT License", "ProjectDiscovery, Inc.",
                "https://github.com/projectdiscovery/nuclei/blob/main/LICENSE.md"));
        l.add(License.of("Nmap", "Nmap Public Source License (NPSL)", "Nmap Software LLC (Gordon Lyon)",
                "https://nmap.org/npsl/"));
        l.add(License.of("THC-Hydra", "GNU AGPL v3.0 (with OpenSSL exception)", "van Hauser / THC; David Maciejak",
                "https://github.com/vanhauser-thc/thc-hydra"));
        l.add(License.of("aircrack-ng", "GNU GPL v2.0 or later", "Thomas d'Otreppe; Christophe Devine",
                "https://github.com/aircrack-ng/aircrack-ng/blob/master/LICENSE"));
        l.add(License.of("mdk4", "GNU GPL v3.0 or later", "ASPj, E7mer and the aircrack-ng community",
                "https://github.com/aircrack-ng/mdk4"));
        l.add(License.of("iw", "ISC License", "Johannes Berg and contributors",
                "https://wireless.wiki.kernel.org/en/users/documentation/iw"));
        l.add(License.of("Exploit-DB / SearchSploit", "GNU GPL v2.0 or later", "OffSec Services Limited",
                "https://gitlab.com/exploit-database/exploitdb/-/blob/main/LICENSE.md"));
        l.add(License.of("sudo", "ISC-style (sudo licence)", "Copyright (c) Todd C. Miller",
                "https://www.sudo.ws/about/license/"));
        l.add(License.of("x11vnc", "GNU GPL v2.0 or later (OpenSSL exception)", "Copyright (c) 2002-2010 Karl J. Runge",
                "https://github.com/LibVNC/x11vnc/blob/master/COPYING"));
        l.add(License.of("Xfce desktop", "GNU GPL v2.0", "Xfce Development Team",
                "https://www.xfce.org/"));
        l.add(License.of("X.Org (Xvfb, xset, xrdb)", "MIT / X11 License", "The X.Org Foundation and contributors",
                "https://www.x.org/"));
        l.add(License.of("D-Bus", "GPL-2.0-or-later OR AFL-2.1", "Red Hat, Inc. and D-Bus contributors",
                "https://gitlab.freedesktop.org/dbus/dbus/-/blob/master/COPYING"));
        l.add(License.of("Mesa 3D", "MIT License", "Copyright (c) 1999-2007 Brian Paul and contributors",
                "https://docs.mesa3d.org/license.html"));
        l.add(License.of("cURL", "curl License (MIT-style)", "Copyright (c) 1996-2024 Daniel Stenberg et al.",
                "https://curl.se/docs/copyright.html"));
        l.add(License.of("OpenSSL", "Apache License 2.0", "The OpenSSL Project Authors",
                "https://openssl-library.org/source/license/"));
        l.add(License.of("ca-certificates (Mozilla CA bundle)", "MPL-2.0 AND MIT", "Mozilla Foundation; Alpine team",
                "https://www.mozilla.org/MPL/2.0/"));
        l.add(License.of("DejaVu fonts", "Bitstream Vera Fonts License (parts public domain)",
                "Bitstream, Inc.; Tavmjong Bah; DejaVu authors",
                "https://dejavu-fonts.github.io/License.html"));

        return l;
    }

    @Override
    public void onBackPressed() {
        finish();
    }
}
