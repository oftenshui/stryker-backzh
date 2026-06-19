package com.zalexdev.stryker.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.zalexdev.stryker.R;

public class SettingsNew extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.settings_host, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        if (getChildFragmentManager().findFragmentById(R.id.settings_nav_host) == null) {
            getChildFragmentManager().beginTransaction()
                    .replace(R.id.settings_nav_host, new SettingsHomeFragment())
                    .commit();
        }
    }

    public void openChild(Fragment fragment, String tag) {
        getChildFragmentManager().beginTransaction()
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                .replace(R.id.settings_nav_host, fragment, tag)
                .addToBackStack(tag)
                .commit();
    }

    public boolean popChild() {
        if (getChildFragmentManager().getBackStackEntryCount() > 0) {
            getChildFragmentManager().popBackStack();
            return true;
        }
        return false;
    }
}
