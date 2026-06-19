package com.zalexdev.stryker.appintro;

import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.airbnb.lottie.LottieAnimationView;
import com.race604.drawable.wave.WaveDrawable;
import com.zalexdev.stryker.R;
import com.zalexdev.stryker.appintro.slides.Slide1;
import com.zalexdev.stryker.appintro.slides.Slide2;
import com.zalexdev.stryker.appintro.slides.Slide3;
import com.zalexdev.stryker.appintro.slides.Slide6Final;
import com.zalexdev.stryker.appintro.slides.SlidePCheck;

public class AppIntroActivity extends FragmentActivity {

    private static final int NUM_PAGES = 5;
    private LottieAnimationView animationView;
    private WaveDrawable mWaveDrawable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_intro);
        animationView = findViewById(R.id.lottie_view);
        ViewPager2 mPager = findViewById(R.id.view_pager);
        mPager.setUserInputEnabled(false);
        ScreenPagerAdapter pagerAdapter = new ScreenPagerAdapter(this);
        mPager.setAdapter(pagerAdapter);
        ImageView logo = findViewById(R.id.logo);

        boolean isDarkThemeOn = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        if (isDarkThemeOn) {
            logo.setImageResource(R.drawable.ic_white);
        } else {
            logo.setImageResource(R.drawable.ic_blue);
        }

        mWaveDrawable = new WaveDrawable(logo.getDrawable());
        logo.setImageDrawable(mWaveDrawable);
        mWaveDrawable.setLevel(10000);
        mWaveDrawable.setWaveSpeed(15);
    }

    @Override
    public void onBackPressed() {

    }

    public WaveDrawable getWaveDrawable() {
        return mWaveDrawable;
    }

    public void setAnimationView(boolean type) {
        int frame = animationView.getFrame();
        if (!type) {
            animationView.setAnimation(R.raw.hello);
        } else {
            animationView.setAnimation(R.raw.hello_red);
        }
        animationView.playAnimation();
        animationView.setFrame(frame + 1);
    }

    public void setSpeed(float speed) {
        animationView.setSpeed(speed);
    }

    private static class ScreenPagerAdapter extends FragmentStateAdapter {

        public ScreenPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
            super(fragmentActivity);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            if (position == 0) {
                return new Slide1();
            } else if (position == 1) {
                return new Slide2();
            } else if (position == 2) {
                return new SlidePCheck();
            } else if (position == 3) {
                return new Slide3();
            } else if (position == 4) {
                return new Slide6Final();
            } else {
                return new Slide6Final();
            }
        }

        @Override
        public int getItemCount() {
            return NUM_PAGES;
        }
    }
}