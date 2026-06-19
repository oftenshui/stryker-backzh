package com.zalexdev.stryker.appintro.slides;


import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.zalexdev.stryker.R;
import com.zalexdev.stryker.utils.Core;

public class Slide6Final extends Fragment {

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.new_slide5, container, false);
        Core core = new Core(getContext());
        MaterialButton button = view.findViewById(R.id.login);
        button.setOnClickListener(view1 -> {
            core.threadCommand("am start -n com.zalexdev.stryker/.MainActivity");
            Activity activity = getActivity();
            if (activity != null && !activity.isFinishing()) {
                activity.finishAffinity();
            }
        });
        return view;
    }
}
