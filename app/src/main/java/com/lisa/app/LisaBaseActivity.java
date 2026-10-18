package com.lisa.app;

import android.app.Activity;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MotionEvent;

public abstract class LisaBaseActivity extends Activity {
    private GestureDetector gestureDetector;
    private boolean isSwipe = false;
    private boolean isBackSwipe = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) { return true; }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;
                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();
                float soglia = 100 * getResources().getDisplayMetrics().density;
                if (Math.abs(diffX) > soglia && Math.abs(diffX) > Math.abs(diffY) * 2) {
                    isSwipe = true;
                    isBackSwipe = (diffX > 0);
                    return true;
                }
                return false;
            }
        });
    }

    protected boolean canSwipeForward() {
        return false;
    }

    protected void onSwipeForward() {
    }

    protected void onSwipeBack() {
        finish();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (gestureDetector != null) {
            gestureDetector.onTouchEvent(ev);
        }

        if (ev.getAction() == MotionEvent.ACTION_UP && isSwipe) {
            final boolean back = isBackSwipe;
            isSwipe = false;
            isBackSwipe = false;

            MotionEvent cancel = MotionEvent.obtain(ev);
            cancel.setAction(MotionEvent.ACTION_CANCEL);
            super.dispatchTouchEvent(cancel);
            cancel.recycle();

            getWindow().getDecorView().post(new Runnable() {
                @Override
                public void run() {
                    if (back) {
                        onSwipeBack();
                    } else if (canSwipeForward()) {
                        onSwipeForward();
                    }
                }
            });

            return true;
        }

        return super.dispatchTouchEvent(ev);
    }
}
