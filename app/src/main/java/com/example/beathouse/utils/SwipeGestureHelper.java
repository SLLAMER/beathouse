package com.example.beathouse.utils;

import android.view.GestureDetector;
import android.view.MotionEvent;

public class SwipeGestureHelper extends GestureDetector.SimpleOnGestureListener {
    private static final int SWIPE_THRESHOLD = 100;
    private static final int SWIPE_VELOCITY_THRESHOLD = 100;

    public interface OnSwipeListener {
        void onSwipeRight();
        void onSwipeLeft();
    }

    private final OnSwipeListener listener;

    public SwipeGestureHelper(OnSwipeListener listener) {
        this.listener = listener;
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        if (e1 == null || e2 == null) return false;
        float diffX = e2.getX() - e1.getX();
        float diffY = e2.getY() - e1.getY();
        if (Math.abs(diffX) > Math.abs(diffY)) {
            if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                if (diffX > 0) {
                    listener.onSwipeRight();
                } else {
                    listener.onSwipeLeft();
                }
                return true;
            }
        }
        return false;
    }
}
