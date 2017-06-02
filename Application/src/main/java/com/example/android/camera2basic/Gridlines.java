package com.example.android.camera2basic;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.view.View;

/**
 * Created by WINDOWS 10 on 5/26/2017.
 */

public class Gridlines extends View {

    private Rect mDrawBounds;
    Paint mPaint = new Paint();

    public Gridlines(Context context, AttributeSet attrs) {
        super(context, attrs);
        mPaint.setStrokeWidth(3);
        mPaint.setColor(Color.WHITE);
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mDrawBounds != null) {
            float thirdWidth = mDrawBounds.width() / 3;
            float thirdHeight = mDrawBounds.height() / 3;
            for (int i = 1; i < 3; i++) {
                // Draw the vertical lines.
                final float x = thirdWidth * i;
                canvas.drawLine(mDrawBounds.left + x, mDrawBounds.top,
                        mDrawBounds.left + x, mDrawBounds.bottom, mPaint);
                // Draw the horizontal lines.
                final float y = thirdHeight * i;
                canvas.drawLine(mDrawBounds.left, mDrawBounds.top + y,
                        mDrawBounds.right, mDrawBounds.top + y, mPaint);
            }
        }
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        switch(visibility){
            case VISIBLE:
                setDrawBounds();
                break;
            case GONE:
                break;
        }
    }

    public void setRect(Rect rect){
        mDrawBounds = new Rect(rect);
    }

    private void setDrawBounds() {
        invalidate();
    }
}
