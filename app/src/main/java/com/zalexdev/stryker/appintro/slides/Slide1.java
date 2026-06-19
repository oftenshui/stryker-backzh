package com.zalexdev.stryker.appintro.slides;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.race604.drawable.wave.WaveDrawable;
import com.zalexdev.stryker.R;
import com.zalexdev.stryker.appintro.AppIntroActivity;
import com.zalexdev.stryker.utils.Core;

public class Slide1 extends Fragment {

    private Activity activity;
    private Core core;
    private ViewPager2 mPager;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.new_slide1, container, false);
        activity = getActivity();
        Context context = getContext();
        core = new Core(context);
        mPager = activity.findViewById(R.id.view_pager);

        MaterialButton button = view.findViewById(R.id.login);
        MaterialCheckBox checkBox = view.findViewById(R.id.slide_checkbox);
        MaterialCheckBox checkBox2 = view.findViewById(R.id.slide_checkbox2);
        MaterialCheckBox checkBox3 = view.findViewById(R.id.slide_checkbox3);
        LinearLayout row1 = view.findViewById(R.id.consent_row_1);
        LinearLayout row2 = view.findViewById(R.id.consent_row_2);
        LinearLayout row3 = view.findViewById(R.id.consent_row_3);

        Runnable sync = () -> button.setEnabled(
                checkBox.isChecked() && checkBox2.isChecked() && checkBox3.isChecked());

        row1.setOnClickListener(v -> { checkBox.setChecked(!checkBox.isChecked()); sync.run(); });
        row2.setOnClickListener(v -> { checkBox2.setChecked(!checkBox2.isChecked()); sync.run(); });
        row3.setOnClickListener(v -> { checkBox3.setChecked(!checkBox3.isChecked()); sync.run(); });

        button.setOnClickListener(view1 -> {
            WaveDrawable mWaveDrawable = ((AppIntroActivity) activity).getWaveDrawable();
            core.setSmoothLevel(mWaveDrawable, 3000);
            core.moveNext(mPager);
        });
        return view;
    }
}
