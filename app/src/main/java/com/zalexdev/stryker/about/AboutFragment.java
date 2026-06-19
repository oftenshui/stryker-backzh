package com.zalexdev.stryker.about;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.zalexdev.stryker.BuildConfig;
import com.zalexdev.stryker.R;
import com.zalexdev.stryker.utils.LicenseActivity;

import java.util.ArrayList;
import java.util.List;

public class AboutFragment extends Fragment {

    private static final String SITE_URL = "https://zalexdev.com";
    private static final String GITHUB_URL = "https://github.com/zalexdev/strykerapp";

    private float entranceTranslation;

    private final List<Animator> runningAnimators = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_about, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        entranceTranslation = 36f * getResources().getDisplayMetrics().density;

        setupFeatureStack(view);

        TextView version = view.findViewById(R.id.about_version);
        if (version != null) {
            version.setText("v" + BuildConfig.VERSION_NAME);
        }

        view.findViewById(R.id.about_link_site).setOnClickListener(v -> openUrl(SITE_URL));
        view.findViewById(R.id.about_link_github).setOnClickListener(v -> openUrl(GITHUB_URL));
        view.findViewById(R.id.about_oss_licenses).setOnClickListener(v -> {
            if (getContext() == null) return;
            startActivity(new Intent(getContext(), LicenseActivity.class));
        });

        playEntranceAnimation(view);
        startLogoAmbientAnimation(view);
    }

    private void setupFeatureStack(@NonNull View root) {
        FeatureCardStack stack = root.findViewById(R.id.about_feature_stack);
        if (stack == null) return;

        View heroCard = LayoutInflater.from(stack.getContext())
                .inflate(R.layout.view_about_hero_card, stack, false);

        List<FeatureCardStack.Feature> features = new ArrayList<>();
        features.add(new FeatureCardStack.Feature(
                R.string.feat_wifi_title, R.string.feat_wifi_desc, R.drawable.wifi,
                Color.parseColor("#1E88E5"), Color.parseColor("#0D47A1")));
        features.add(new FeatureCardStack.Feature(
                R.string.feat_lan_title, R.string.feat_lan_desc, R.drawable.lan,
                Color.parseColor("#00ACC1"), Color.parseColor("#00695C")));
        features.add(new FeatureCardStack.Feature(
                R.string.feat_msf_title, R.string.feat_msf_desc, R.drawable.shield,
                Color.parseColor("#E53935"), Color.parseColor("#B71C1C")));
        features.add(new FeatureCardStack.Feature(
                R.string.feat_nuclei_title, R.string.feat_nuclei_desc, R.drawable.webscan,
                Color.parseColor("#8E24AA"), Color.parseColor("#4A148C")));
        features.add(new FeatureCardStack.Feature(
                R.string.feat_searchsploit_title, R.string.feat_searchsploit_desc, R.drawable.search,
                Color.parseColor("#FB8C00"), Color.parseColor("#E65100")));
        features.add(new FeatureCardStack.Feature(
                R.string.feat_router_title, R.string.feat_router_desc, R.drawable.router,
                Color.parseColor("#43A047"), Color.parseColor("#1B5E20")));
        features.add(new FeatureCardStack.Feature(
                R.string.feat_hid_title, R.string.feat_hid_desc, R.drawable.keyboard,
                Color.parseColor("#5E35B1"), Color.parseColor("#311B92")));

        stack.setContent(heroCard, features);
        stack.hintSwipe();
    }

    private void playEntranceAnimation(@NonNull View root) {
        int[] ids = {
                R.id.about_feature_stack,
                R.id.about_label_links,
                R.id.about_link_site,
                R.id.about_link_github,
                R.id.about_label_legal,
                R.id.about_oss_licenses,
                R.id.about_footer
        };

        long delay = 60L;
        for (int id : ids) {
            View block = root.findViewById(id);
            if (block == null) continue;

            block.setAlpha(0f);
            block.setTranslationY(entranceTranslation);
            block.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(delay)
                    .setDuration(480L)
                    .setInterpolator(new DecelerateInterpolator(1.6f))
                    .start();
            delay += 80L;
        }
    }

    private void startLogoAmbientAnimation(@NonNull View root) {
        View icon = root.findViewById(R.id.about_logo_icon);
        View disc = root.findViewById(R.id.about_logo_disc);
        View glow = root.findViewById(R.id.about_logo_glow);

        if (disc != null) {
            AnimatorSet pulse = new AnimatorSet();
            pulse.playTogether(
                    loopingScale(disc, View.SCALE_X),
                    loopingScale(disc, View.SCALE_Y)
            );
            if (icon != null) {
                pulse.playTogether(
                        loopingScale(icon, View.SCALE_X),
                        loopingScale(icon, View.SCALE_Y)
                );
            }
            pulse.start();
            runningAnimators.add(pulse);
        }

        if (glow != null) {
            ObjectAnimator glowAlpha = ObjectAnimator.ofFloat(glow, View.ALPHA, 0.55f, 1f);
            glowAlpha.setDuration(2200L);
            glowAlpha.setRepeatCount(ValueAnimator.INFINITE);
            glowAlpha.setRepeatMode(ValueAnimator.REVERSE);
            glowAlpha.setInterpolator(new AccelerateDecelerateInterpolator());
            glowAlpha.start();
            runningAnimators.add(glowAlpha);
        }
    }

    private ObjectAnimator loopingScale(@NonNull View target, @NonNull android.util.Property<View, Float> axis) {
        ObjectAnimator animator = ObjectAnimator.ofFloat(target, axis, 1f, 1.06f);
        animator.setDuration(2200L);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.REVERSE);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        return animator;
    }

    private void openUrl(String url) {
        if (getContext() == null) return;
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    @Override
    public void onDestroyView() {
        for (Animator animator : runningAnimators) {
            animator.cancel();
        }
        runningAnimators.clear();
        super.onDestroyView();
    }
}
