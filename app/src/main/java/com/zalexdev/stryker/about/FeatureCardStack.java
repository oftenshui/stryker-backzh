package com.zalexdev.stryker.about;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.textview.MaterialTextView;
import com.zalexdev.stryker.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FeatureCardStack extends FrameLayout {

    public static class Feature {
        final int titleRes;
        final int descRes;
        final int iconRes;
        final int colorStart;
        final int colorEnd;

        public Feature(int titleRes, int descRes, int iconRes, int colorStart, int colorEnd) {
            this.titleRes = titleRes;
            this.descRes = descRes;
            this.iconRes = iconRes;
            this.colorStart = colorStart;
            this.colorEnd = colorEnd;
        }
    }

    private static final int MAX_VISIBLE = 3;

    private final List<View> deck = new ArrayList<>();
    private int totalCards;

    private float peekX;
    private float peekY;
    private float scaleStep;
    private float cornerRadius;

    private int touchSlop;
    private int flingVelocity;

    private float downX;
    private float downY;
    private float frontBaseX;
    private boolean dragging;
    private boolean settling;
    private VelocityTracker velocityTracker;

    public FeatureCardStack(@NonNull Context context) {
        super(context);
        init();
    }

    public FeatureCardStack(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public FeatureCardStack(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        float density = getResources().getDisplayMetrics().density;
        peekX = 0f;
        peekY = 18f * density;
        scaleStep = 0.05f;
        cornerRadius = 22f * density;

        ViewConfiguration vc = ViewConfiguration.get(getContext());
        touchSlop = vc.getScaledTouchSlop();
        flingVelocity = vc.getScaledMinimumFlingVelocity() * 8;

        setClipChildren(false);
        setClipToPadding(false);
        setChildrenDrawingOrderEnabled(true);
    }

    @Override
    protected int getChildDrawingOrder(int childCount, int drawingPosition) {
        if (deck.size() != childCount) return drawingPosition;
        View card = deck.get(childCount - 1 - drawingPosition);
        int index = indexOfChild(card);
        return index < 0 ? drawingPosition : index;
    }

    public void setContent(@Nullable View heroCard, @NonNull List<Feature> features) {
        removeAllViews();
        deck.clear();

        if (heroCard != null) {
            addView(heroCard);
            deck.add(heroCard);
        }

        LayoutInflater inflater = LayoutInflater.from(getContext());
        for (int i = 0; i < features.size(); i++) {
            View card = inflater.inflate(R.layout.view_feature_card, this, false);
            bind(card, features.get(i), i, features.size());
            addView(card);
            deck.add(card);
        }

        totalCards = deck.size();
        applyPositions(false);
    }

    private void bind(@NonNull View card, @NonNull Feature feature, int index, int count) {
        View bg = card.findViewById(R.id.feature_bg);
        GradientDrawable gradient = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{feature.colorStart, feature.colorEnd});
        gradient.setCornerRadius(cornerRadius);
        bg.setBackground(gradient);

        ((ImageView) card.findViewById(R.id.feature_icon)).setImageResource(feature.iconRes);
        ((MaterialTextView) card.findViewById(R.id.feature_title)).setText(feature.titleRes);
        ((MaterialTextView) card.findViewById(R.id.feature_desc)).setText(feature.descRes);
        ((MaterialTextView) card.findViewById(R.id.feature_index))
                .setText(String.format(Locale.US, "%02d / %02d", index + 1, count));
    }

    private void applyPositions(boolean animate) {
        for (int i = 0; i < deck.size(); i++) {
            View card = deck.get(i);
            int depth = Math.min(i, MAX_VISIBLE - 1);
            boolean visible = i < MAX_VISIBLE;

            float targetX = peekX * depth;
            float targetY = peekY * depth;
            float targetScale = 1f - scaleStep * depth;
            float targetAlpha = visible ? 1f : 0f;

            View hint = card.findViewById(R.id.feature_hint);
            if (hint != null) {
                hint.setVisibility(i == 0 ? VISIBLE : INVISIBLE);
            }

            if (animate) {
                card.animate()
                        .translationX(targetX)
                        .translationY(targetY)
                        .scaleX(targetScale)
                        .scaleY(targetScale)
                        .rotation(0f)
                        .alpha(targetAlpha)
                        .setDuration(320L)
                        .setInterpolator(new DecelerateInterpolator(1.5f))
                        .start();
            } else {
                card.setTranslationX(targetX);
                card.setTranslationY(targetY);
                card.setScaleX(targetScale);
                card.setScaleY(targetScale);
                card.setRotation(0f);
                card.setAlpha(targetAlpha);
            }
        }
        invalidate();
    }

    @Nullable
    private View front() {
        return deck.isEmpty() ? null : deck.get(0);
    }

    public void hintSwipe() {
        View front = front();
        if (front == null || deck.size() < 2) return;
        front.postDelayed(() -> {
            if (dragging || settling) return;
            front.animate()
                    .translationX(peekX * 4f)
                    .rotation(4f)
                    .setDuration(260L)
                    .setInterpolator(new DecelerateInterpolator())
                    .withEndAction(() -> front.animate()
                            .translationX(0f)
                            .rotation(0f)
                            .setDuration(420L)
                            .setInterpolator(new OvershootInterpolator(2.2f))
                            .start())
                    .start();
        }, 700L);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (settling || deck.size() < 2) return false;

        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = ev.getX();
                downY = ev.getY();
                View front = front();
                frontBaseX = front == null ? 0f : front.getTranslationX();
                dragging = false;
                obtainTracker(ev);
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = ev.getX() - downX;
                float dy = ev.getY() - downY;
                if (!dragging && Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy)) {
                    dragging = true;
                    requestDisallowParentIntercept();
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                releaseTracker();
                break;
            default:
                break;
        }
        return dragging;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        View front = front();
        if (front == null || settling || deck.size() < 2) return false;

        if (velocityTracker != null) {
            velocityTracker.addMovement(event);
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                frontBaseX = front.getTranslationX();
                obtainTracker(event);
                requestDisallowParentIntercept();
                return true;

            case MotionEvent.ACTION_MOVE: {
                float dx = event.getX() - downX;
                if (!dragging && Math.abs(dx) > touchSlop) {
                    dragging = true;
                    requestDisallowParentIntercept();
                }
                if (dragging) {
                    float width = Math.max(getWidth(), 1);
                    float progress = Math.min(Math.abs(dx) / width, 1f);
                    front.setTranslationX(frontBaseX + dx);
                    front.setRotation((dx / width) * 14f);

                    if (deck.size() > 1) {
                        View next = deck.get(1);
                        next.setScaleX(1f - scaleStep + scaleStep * progress);
                        next.setScaleY(1f - scaleStep + scaleStep * progress);
                        next.setTranslationY(peekY - peekY * progress);
                        next.setTranslationX(peekX - peekX * progress);
                    }
                }
                return true;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                float dx = event.getX() - downX;
                float vx = 0f;
                if (velocityTracker != null) {
                    velocityTracker.computeCurrentVelocity(1000);
                    vx = velocityTracker.getXVelocity();
                }
                releaseTracker();

                boolean wasDragging = dragging;
                dragging = false;

                float width = Math.max(getWidth(), 1);
                boolean dismiss = Math.abs(dx) > width * 0.32f || Math.abs(vx) > flingVelocity;
                if (wasDragging && dismiss) {
                    dismissFront(Math.signum(dx == 0 ? vx : dx) >= 0 ? 1 : -1);
                } else if (wasDragging) {
                    springBack();
                }
                return true;
            }
            default:
                return super.onTouchEvent(event);
        }
    }

    private void dismissFront(int direction) {
        final View front = front();
        if (front == null) {
            settling = false;
            return;
        }
        settling = true;
        float width = Math.max(getWidth(), getResources().getDisplayMetrics().widthPixels);
        front.animate()
                .translationX(direction * width * 1.3f)
                .translationY(front.getTranslationY() + peekY)
                .rotation(direction * 22f)
                .alpha(0f)
                .setDuration(300L)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    View recycled = deck.remove(0);
                    deck.add(recycled);
                    int backDepth = Math.min(deck.size() - 1, MAX_VISIBLE - 1);
                    recycled.setRotation(0f);
                    recycled.setAlpha(0f);
                    recycled.setTranslationX(peekX * backDepth);
                    recycled.setTranslationY(peekY * backDepth);
                    recycled.setScaleX(1f - scaleStep * backDepth);
                    recycled.setScaleY(1f - scaleStep * backDepth);
                    settling = false;
                    applyPositions(true);
                })
                .start();
    }

    private void springBack() {
        View front = front();
        if (front != null) {
            front.animate()
                    .translationX(0f)
                    .rotation(0f)
                    .setDuration(280L)
                    .setInterpolator(new OvershootInterpolator(1.6f))
                    .start();
        }
        if (deck.size() > 1) {
            View next = deck.get(1);
            next.animate()
                    .scaleX(1f - scaleStep)
                    .scaleY(1f - scaleStep)
                    .translationX(peekX)
                    .translationY(peekY)
                    .setDuration(280L)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }
    }

    private void requestDisallowParentIntercept() {
        ViewGroup parent = (ViewGroup) getParent();
        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(true);
        }
    }

    private void obtainTracker(MotionEvent ev) {
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain();
        } else {
            velocityTracker.clear();
        }
        velocityTracker.addMovement(ev);
    }

    private void releaseTracker() {
        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        releaseTracker();
        super.onDetachedFromWindow();
    }
}
