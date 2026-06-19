package com.zalexdev.stryker.account;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.card.MaterialCardView;
import com.zalexdev.stryker.R;
import com.zalexdev.stryker.utils.Core;
import com.zalexdev.stryker.utils.LicenseActivity;

public class Account extends Fragment {

    private Core core;
    private Activity activity;
    private Context context;

    @SuppressLint("SetTextI18n")
    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.account_fragment, container, false);
        activity = getActivity();
        context = getContext();
        core = new Core(context);

        TextView user = root.findViewById(R.id.username);
        user.setText(core.getString("username"));

        TextView info = root.findViewById(R.id.device_info);
        MaterialCardView github = root.findViewById(R.id.github);
        TextView lic = root.findViewById(R.id.lic);

        github.setOnClickListener(view -> openLink("https://github.com/zalexdev/strykerapp"));
        info.setText(getDeviceName() + "\n" + context.getResources().getString(R.string.plata) + " " + Build.BOARD + "\n" + "Android SDK: " + Build.VERSION.SDK_INT);
        lic.setOnClickListener(v -> activity.startActivity(new Intent(activity, LicenseActivity.class)));
        return root;
    }

    private String getDeviceName() {
        String manufacturer = Build.MANUFACTURER;
        String model = Build.MODEL;
        if (model.startsWith(manufacturer)) {
            return capitalize(model);
        } else {
            return capitalize(manufacturer) + " " + model;
        }
    }

    private String capitalize(String s) {
        if (s == null || s.length() == 0) {
            return "";
        }
        char first = s.charAt(0);
        if (Character.isUpperCase(first)) {
            return s;
        } else {
            return Character.toUpperCase(first) + s.substring(1);
        }
    }

    private void openLink(String url) {
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(browserIntent);
    }
}
