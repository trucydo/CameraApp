package com.example.android.camera2basic;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import java.util.List;

/**
 * Created by USER on 5/5/2017.
 */

public class FaceOverlayView extends View {
    private Bitmap mBitmap;
    private android.hardware.camera2.params.Face[] mFaces;

    public FaceOverlayView(Context context) {
        this(context, null);
    }

    public FaceOverlayView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FaceOverlayView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }
    private List<Rect> faceRect;
    public void setBitmap(android.hardware.camera2.params.Face[] mFacess, List<Rect> faceRects) {
        //mBitmap = bitmap;
        /*FaceDetector detector = new FaceDetector.Builder( getContext() )
                .setTrackingEnabled(true)
                .setLandmarkType(FaceDetector.ALL_LANDMARKS)
                .setMode(FaceDetector.ACCURATE_MODE)
                .build();

        if (!detector.isOperational()) {
            //Handle contingency
        } else {
            //Frame frame = new Frame.Builder().setId(0).build();
             //detector.detect(frame);

            detector.release();
        }*/
        //logFaceData();
        mFaces = mFacess;
        faceRect = faceRects;
        /*activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
            invalidate();
            }
        });*/

    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if ((mBitmap != null) && (mFaces != null)) {
            double scale = drawBitmap(canvas);
            //drawFaceLandmarks(canvas, scale);

        }
        if(mFaces != null) {
            //drawFaceBox(canvas, 0.4);
            //drawTriangle(canvas);
            drawCircle(canvas);
        }
    }
    private void drawCircle(Canvas canvas){
        Paint red = new Paint();

        red.setColor(android.graphics.Color.RED);
        red.setStyle(Paint.Style.FILL);
        for( int i = 0; i < mFaces.length; i++ ) {

        }
        if(mFaces.length > 0) {
            Log.e("Point :", String.valueOf(mFaces[0].getBounds().centerX()));
            //canvas.drawCircle(mFaces[0].getBounds().centerX()/ 10, mFaces[0].getBounds().centerY()/10, 50, red);
            //canvas.drawCircle(300, 300, 50, red);
        }
    }
    private void drawTriangle(Canvas canvas){
        Paint red = new Paint();

        red.setColor(android.graphics.Color.RED);
        red.setStyle(Paint.Style.FILL);
        red.setAntiAlias(true);
        Path path = new Path();
        for( int i = 0; i < mFaces.length & (mFaces[i].getRightEyePosition()!= null); i++ ) {
            path.moveTo(mFaces[i].getRightEyePosition().x, mFaces[i].getRightEyePosition().y);
            path.lineTo(mFaces[i].getLeftEyePosition().x, mFaces[i].getLeftEyePosition().y);
            path.moveTo(mFaces[i].getLeftEyePosition().x, mFaces[i].getLeftEyePosition().y);
            path.lineTo(mFaces[i].getMouthPosition().x, mFaces[i].getMouthPosition().y);
            path.moveTo(mFaces[i].getMouthPosition().x, mFaces[i].getMouthPosition().y);
            path.lineTo(mFaces[i].getRightEyePosition().x, mFaces[i].getRightEyePosition().y);
        }
        path.close();
        canvas.drawPath(path, red);
    }
    private double drawBitmap(Canvas canvas) {
        double viewWidth = canvas.getWidth();
        double viewHeight = canvas.getHeight();
        double imageWidth = mBitmap.getWidth();
        double imageHeight = mBitmap.getHeight();
        double scale = Math.min(viewWidth / imageWidth, viewHeight / imageHeight);

        Rect destBounds = new Rect(0, 0, (int)(imageWidth * scale), (int)(imageHeight * scale));
        canvas.drawBitmap(mBitmap, null, destBounds, null);
        return scale;
    }

    private void drawFaceBox(Canvas canvas, double scale) {
        //This should be defined as a member variable rather than
        //being created on each onDraw request, but left here for
        //emphasis.
        Paint paint = new Paint();
        paint.setColor(Color.GREEN);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(5);

        float left = 0;
        float top = 0;
        float right = 0;
        float bottom = 0;

        for( int i = 0; i < mFaces.length; i++ ) {
            android.hardware.camera2.params.Face face = mFaces[i];
             left = (float) (face.getBounds().left* scale);
             right = (float) (face.getBounds().right* scale);
             top = (float) (face.getBounds().top* scale);
             bottom = (float) (face.getBounds().bottom* scale);
            /*left = (float) ( face.getPosition().x * scale );
            top = (float) ( face.getPosition().y * scale );
            right = (float) scale * ( face.getPosition().x + face.getWidth() );
            bottom = (float) scale * ( face.getPosition().y + face.getHeight() );*/

            canvas.drawRect( left, top, right, bottom, paint );
            //canvas.drawRect(faceRect.get(i), paint );
        }
    }

    /*private void drawFaceLandmarks( Canvas canvas, double scale ) {
        Paint paint = new Paint();
        paint.setColor( Color.GREEN );
        paint.setStyle( Paint.Style.STROKE );
        paint.setStrokeWidth( 5 );

        for( int i = 0; i < mFaces.size(); i++ ) {
            Face face = mFaces.valueAt(i);

            for ( Landmark landmark : face.getLandmarks() ) {
                int cx = (int) ( landmark.getPosition().x * scale );
                int cy = (int) ( landmark.getPosition().y * scale );
                canvas.drawCircle( cx, cy, 10, paint );
            }

        }
    }

    private void logFaceData() {
        float smilingProbability;
        float leftEyeOpenProbability;
        float rightEyeOpenProbability;
        float eulerY;
        float eulerZ;
        for( int i = 0; i < mFaces.size(); i++ ) {
            Face face = mFaces.valueAt(i);

            smilingProbability = face.getIsSmilingProbability();
            leftEyeOpenProbability = face.getIsLeftEyeOpenProbability();
            rightEyeOpenProbability = face.getIsRightEyeOpenProbability();
            eulerY = face.getEulerY();
            eulerZ = face.getEulerZ();

            Log.e( "Tuts+ Face Detection", "Smiling: " + smilingProbability );
            Log.e( "Tuts+ Face Detection", "Left eye open: " + leftEyeOpenProbability );
            Log.e( "Tuts+ Face Detection", "Right eye open: " + rightEyeOpenProbability );
            Log.e( "Tuts+ Face Detection", "Euler Y: " + eulerY );
            Log.e( "Tuts+ Face Detection", "Euler Z: " + eulerZ );
        }
    }*/
}
