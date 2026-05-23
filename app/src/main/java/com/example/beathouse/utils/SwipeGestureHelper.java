package com.example.beathouse.utils;

import android.view.GestureDetector;
import android.view.MotionEvent;

public class SwipeGestureHelper extends GestureDetector.SimpleOnGestureListener {
    private static final int SWIPE_THRESHOLD = 100;
    private static final int SWIPE_VELOCITY_THRESHOLD = 100;
    private final OnSwipeListener listener;

    public interface OnSwipeListener {
        void onSwipeLeft();
        void onSwipeRight();
    }

    public SwipeGestureHelper(OnSwipeListener listener) {
        this.listener = listener;
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        if (e1 == null || e2 == null) return false;

        float diffY = e2.getY() - e1.getY();
        float diffX = e2.getX() - e1.getX();

        if (Math.abs(diffX) > Math.abs(diffY)) {
            if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                if (diffX < 0) {
                    if (listener != null) listener.onSwipeLeft();
                    return true;
                } else {
                    if (listener != null) listener.onSwipeRight();
                    return true;
                }
            }
        }
        return false;
    }
}
