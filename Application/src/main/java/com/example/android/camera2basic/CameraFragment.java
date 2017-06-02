/*
 * Copyright 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.camera2basic;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.Face;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.AudioManager;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.PowerManager;
import android.support.annotation.NonNull;
import android.support.v13.app.FragmentCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.squareup.picasso.Picasso;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class CameraFragment extends Fragment
        implements View.OnClickListener, FragmentCompat.OnRequestPermissionsResultCallback {

    /**
     * Conversion from screen rotation to JPEG orientation.
     */
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    private static final int REQUEST_CAMERA_PERMISSION = 1;
    private static final String FRAGMENT_DIALOG = "dialog";

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    /**
     * Tag for the {@link Log}.
     */
    private static final String TAG = "Camera2BasicFragment";

    /**
     * Camera state: Showing camera preview.
     */
    private static final int STATE_PREVIEW = 0;

    /**
     * Camera state: Waiting for the focus to be locked.
     */
    private static final int STATE_WAITING_LOCK = 1;

    /**
     * Camera state: Waiting for the exposure to be precapture state.
     */
    private static final int STATE_WAITING_PRECAPTURE = 2;

    /**
     * Camera state: Waiting for the exposure state to be something other than precapture.
     */
    private static final int STATE_WAITING_NON_PRECAPTURE = 3;

    /**
     * Camera state: Picture was taken.
     */
    private static final int STATE_PICTURE_TAKEN = 4;

    /**
     * Max preview width that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_WIDTH = 1920;

    /**
     * Max preview height that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_HEIGHT = 1080;



    /**
     * {@link TextureView.SurfaceTextureListener} handles several lifecycle events on a
     * {@link TextureView}.
     */
    private final TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            mCameraId = "0";
            try {
                getImageSizes();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
            openCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
            configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        }
    };

    /**
     * ID of the current {@link CameraDevice}.
     */
    private String mCameraId;

    /**
     * An {@link AutoFitTextureView} for camera preview.
     */
    private AutoFitTextureView mTextureView;

    /**
     * A {@link CameraCaptureSession } for camera preview.
     */
    private CameraCaptureSession mCaptureSession;

    /**
     * A reference to the opened {@link CameraDevice}.
     */
    private CameraDevice mCameraDevice;

    /**
     * The {@link android.util.Size} of camera preview.
     */
    private Size mPreviewSize;

    /**
     * {@link CameraDevice.StateCallback} is called when {@link CameraDevice} changes its state.
     */
    private ImageButton civRecentImage;
    private ImageButton btnFlash;

    ArrayList<Size> biggestSizesFront = new ArrayList<>();
    ArrayList<Size> biggestSizesRear = new ArrayList<>();


    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            // This method is called when the camera is opened.  We start camera preview here.
            mCameraOpenCloseLock.release();
            mCameraDevice = cameraDevice;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
            Activity activity = getActivity();
            if (null != activity) {
                activity.finish();
            }
        }

    };

    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private HandlerThread mBackgroundThread;

    /**
     * A {@link Handler} for running tasks in the background.
     */
    private Handler mBackgroundHandler;

    /**
     * An {@link ImageReader} that handles still image capture.
     */
    private ImageReader mImageReader;

    /**
     * This is the output file for our picture.
     */
    private String GALLERY_LOCATION = "Boo360";
    private String mImageFileLocation = "";
    private File mFile;
    private void createImageGallery() {
        File storageDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        mFile = new File(storageDirectory, GALLERY_LOCATION);
        if(!mFile.exists()) {
            mFile.mkdirs();
        }

    }
    private File createImageFile() throws IOException {

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "IMAGE_" + timeStamp + "_";

        File image = new File(mFile, imageFileName + ".jpg");//File.createTempFile(imageFileName,".jpg", mFile);
        mImageFileLocation = image.getAbsolutePath();

        return image;

    }
    /**
     * This a callback object for the {@link ImageReader}. "onImageAvailable" will be called when a
     * still image is ready to be saved.
     */
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {
            mBackgroundHandler.post(new ImageSaver(reader.acquireNextImage(), mFile));
        }

    };

    /**
     * {@link CaptureRequest.Builder} for the camera preview
     */
    private CaptureRequest.Builder mPreviewRequestBuilder;

    /**
     * {@link CaptureRequest} generated by {@link #mPreviewRequestBuilder}
     */
    private CaptureRequest mPreviewRequest;

    /**
     * The current state of camera state for taking pictures.
     *
     * @see #mCaptureCallback
     */
    private int mState = STATE_PREVIEW;

    /**
     * A {@link Semaphore} to prevent the app from exiting before closing the camera.
     */
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);

    /**
     * Whether the current camera device supports Flash or not.
     */
    private int mFlashSupported;

    /**
     * Orientation of the camera sensor
     */
    private int mSensorOrientation;

    /**
     * A {@link CameraCaptureSession.CaptureCallback} that handles events related to JPEG capture.
     */
    private List<Rect> faceRects;
    private CameraCaptureSession.CaptureCallback mCaptureCallback
            = new CameraCaptureSession.CaptureCallback() {


        private void process(CaptureResult result) {
            switch (mState) {
                case STATE_PREVIEW: {
                    // We have nothing to do when the camera preview is working normally.
                    if(checktakepicture){
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if(mFile.exists()){
                                    Picasso.with(getView().getContext())
                                            .load(mFile)
                                            .resize(0,100)
                                            .skipMemoryCache()
                                            .noFade()
                                            .into(civRecentImage);
                                    checktakepicture = false;
                                }
                            }
                        });
                    }
                    Face faces[] =result.get(CaptureResult.STATISTICS_FACES);
                    if (faces.length>0){
                        Log.d(TAG, "face detected " + Integer.toString(faces.length));
                        /*faceRects = new ArrayList<Rect>();
                        //Log.e("Point: ",faces[0].getMouthPosition().toString());
                        for (int i=0; i<faces.length; i++) {
                            int left = faces[i].getBounds().left;
                            int right = faces[i].getBounds().right;
                            int top = faces[i].getBounds().top;
                            int bottom = faces[i].getBounds().bottom;
                            Log.e("XYZ: ", left +":" + right +":" +top+":"+bottom);
                            Rect uRect = new Rect(left, top, right, bottom);
                            faceRects.add(uRect);
                        }
                        mFaceOverlayView.setBitmap(faces, faceRects);
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                             mFaceOverlayView.invalidate();
                            }
                        });*/

                        //DrawRectDec dectection = new DrawRectDec(getActivity().getParent());
                        //dectection.invalidate();
                        //takePicture();
                    }

                    break;
                }
                case STATE_WAITING_LOCK: {
                    Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                    if (afState == null || afState == 0) {
                        captureStillPicture();mState=0;
                    } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
                            CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {
                        // CONTROL_AE_STATE can be null on some devices
                        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                        if (aeState == null ||
                                aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                            mState = STATE_PICTURE_TAKEN;
                            captureStillPicture();
                        } else {
                            runPrecaptureSequence();
                        }
                        mState=0;
                    }
                    break;
                }
                case STATE_WAITING_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null ||
                            aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                            aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                        mState = STATE_WAITING_NON_PRECAPTURE;
                    }
                    break;
                }
                case STATE_WAITING_NON_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        mState = STATE_PICTURE_TAKEN;
                        captureStillPicture();
                    }
                    break;
                }
            }
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                        @NonNull CaptureRequest request,
                                        @NonNull CaptureResult partialResult) {
            process(partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {
            process(result);
        }

    };

    /**
     * Shows a {@link Toast} on the UI thread.
     *
     * @param text The message to show
     */
    private void showToast(final String text) {
        final Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    /**
     * Given {@code choices} of {@code Size}s supported by a camera, choose the smallest one that
     * is at least as large as the respective texture view size, and that is at most as large as the
     * respective max size, and whose aspect ratio matches with the specified value. If such size
     * doesn't exist, choose the largest one that is at most as large as the respective max size,
     * and whose aspect ratio matches with the specified value.
     *
     * @param choices           The list of sizes that the camera supports for the intended output
     *                          class
     * @param textureViewWidth  The width of the texture view relative to sensor coordinate
     * @param textureViewHeight The height of the texture view relative to sensor coordinate
     * @param maxWidth          The maximum width that can be chosen
     * @param maxHeight         The maximum height that can be chosen
     * @param aspectRatio       The aspect ratio
     * @return The optimal {@code Size}, or an arbitrary one if none were big enough
     */
    private static Size chooseOptimalSize(Size[] choices, int textureViewWidth,
                                          int textureViewHeight, int maxWidth, int maxHeight, Size aspectRatio) {

        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        // Collect the supported resolutions that are smaller than the preview Surface
        List<Size> notBigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            Log.e("OPTION",option.getHeight() + "  "+option.getWidth());
            if (option.getWidth() <= maxWidth && option.getHeight() <= maxHeight &&
                    (option.getHeight() == option.getWidth() * h / w)) {
                if (option.getWidth() >= textureViewWidth &&
                        option.getHeight() >= textureViewHeight) {
                    bigEnough.add(option);
                    //Log.e("BIG",option.getHeight() + "  "+option.getWidth());
                } else {
                    notBigEnough.add(option);
                    //Log.e("NOT BIG",option.getHeight() + "  "+option.getWidth());
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }
    //Zoom Camera2
    public float finger_spacing = 0;
    public int zoom_level = 1;


    //Determine the space between the first two fingers
    @SuppressWarnings("deprecation")
    private float getFingerSpacing(MotionEvent event) {

        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(x * x + y * y);
    }
    //
    private PowerManager.WakeLock lock;
    public static CameraFragment newInstance() {
        return new CameraFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        createImageGallery();
        return inflater.inflate(R.layout.fragment_camera, container, false);

    }
    private int mAspectRatio = 1;
    private boolean flagzoom;
    private  Rect zoom;
    private FaceOverlayView mFaceOverlayView;
    private Context context;
    private ImageButton btn_mono;
    private ImageButton btn_sepia;
    private ImageButton btn_negative;
    private ImageButton btn_nof;
    private LinearLayout layout_menu_filter;
    private LinearLayout layout_menu_sub;
    private ImageButton btn_timer;
    private ImageButton btn_brightness;
    private ImageButton btn_white_balance;
    private ImageButton btn_sound;
    private ImageButton btn_grid;
    private LinearLayout white_balance_menu_1;
    private LinearLayout white_balance_menu_2;
    private ImageButton btn_wb_auto;
    private ImageButton btn_wb_daylight;
    private ImageButton btn_wb_cloudy;
    private ImageButton btn_wb_twillight;
    private ImageButton btn_wb_fluorescent;
    private ImageButton btn_wb_shade;
    private ImageButton btn_more;
    //private ImageView mGridlines;
    private Gridlines mGridlines;
    private ImageView mActual;
    private ImageView mCover;

    private boolean hasGrid = false;

    private ImageButton btn_screen_size;
    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        //PowerManager pwr = (PowerManager) getActivity().getSystemService(POWER_SERVICE);
        //lock = pwr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"my lock");
        civRecentImage = (ImageButton) view.findViewById(R.id.civ_recent_image);
        view.findViewById(R.id.btn_video).setOnClickListener(this);
        btnFlash = (ImageButton)view.findViewById(R.id.flash);
        File imgFile = new  File(getRecentImage());
        if(imgFile.exists()){
            Picasso.with(view.getContext())
                    .load(imgFile)
                    .resize(0,100)
                    .skipMemoryCache()
                    .noFade()
                    .into(civRecentImage);
        }
        context = view.getContext();
        view.findViewById(R.id.btn_take_picture).setOnClickListener(this);
        view.findViewById(R.id.btn_rotate).setOnClickListener(this);
        view.findViewById(R.id.flash).setOnClickListener(this);
        view.findViewById(R.id.btn_filter).setOnClickListener(this);
        btn_more = (ImageButton)view.findViewById(R.id.btn_more);
        view.findViewById(R.id.civ_recent_image).setOnClickListener(this);
        btn_more.setOnClickListener(this);
        btn_screen_size = (ImageButton)view.findViewById(R.id.btn_screen_size);
        btn_screen_size.setOnClickListener(this);
        btn_mono = (ImageButton)view.findViewById(R.id.btn_mono);
        btn_mono.setOnClickListener(this);
        btn_sepia = (ImageButton)view.findViewById(R.id.btn_sepia);
        btn_sepia.setOnClickListener(this);
        btn_negative = (ImageButton)view.findViewById(R.id.btn_negative);
        btn_negative.setOnClickListener(this);
        btn_nof = (ImageButton)view.findViewById(R.id.btn_effect_off);
        btn_nof.setOnClickListener(this);

        btn_timer = (ImageButton)view.findViewById(R.id.btn_timer);
        btn_timer.setOnClickListener(this);
        btn_brightness = (ImageButton)view.findViewById(R.id.btn_brightness);
        btn_brightness.setOnClickListener(this);

        btn_white_balance = (ImageButton)view.findViewById(R.id.btn_white_balance);
        btn_white_balance.setOnClickListener(this);
        btn_sound = (ImageButton)view.findViewById(R.id.btn_sound);
        btn_sound.setOnClickListener(this);
        btn_grid = (ImageButton)view.findViewById(R.id.btn_grid);
        btn_grid.setOnClickListener(this);

        btn_wb_auto = (ImageButton)view.findViewById(R.id.btn_wb_auto);
        btn_wb_auto.setOnClickListener(this);
        btn_wb_daylight = (ImageButton)view.findViewById(R.id.btn_wb_daylight);
        btn_wb_daylight.setOnClickListener(this);
        btn_wb_cloudy = (ImageButton)view.findViewById(R.id.btn_wb_cloudy);
        btn_wb_cloudy.setOnClickListener(this);
        btn_wb_twillight = (ImageButton)view.findViewById(R.id.btn_wb_twillight);
        btn_wb_twillight.setOnClickListener(this);
        btn_wb_fluorescent = (ImageButton)view.findViewById(R.id.btn_wb_fluorescent);
        btn_wb_fluorescent.setOnClickListener(this);
        btn_wb_shade = (ImageButton)view.findViewById(R.id.btn_wb_shade);
        btn_wb_shade.setOnClickListener(this);

        mTextureView = (AutoFitTextureView) view.findViewById(R.id.texture);
        mFaceOverlayView = (FaceOverlayView) view.findViewById( R.id.face_overlay );
        //mGridlines = (ImageView) view.findViewById(R.id.gridlines);
        mGridlines = (Gridlines) view.findViewById(R.id.gridlines);
        mActual = (ImageView) view.findViewById(R.id.actual_view);
        mCover = (ImageView) view.findViewById(R.id.cover_view);


        layout_menu_filter = (LinearLayout) view.findViewById(R.id.filter_menu);
        layout_menu_sub = (LinearLayout)view.findViewById(R.id.sub_menu);
        white_balance_menu_1 = (LinearLayout) view.findViewById(R.id.white_balance_menu_1);
        white_balance_menu_2 = (LinearLayout)view.findViewById(R.id.white_balance_menu_2);


        for (int i = 0; i < white_balance_menu_1.getChildCount(); i++) {
            View child = white_balance_menu_1.getChildAt(i);
            child.setVisibility(View.GONE);
        }
        for (int i = 0; i < white_balance_menu_2.getChildCount(); i++) {
            View child = white_balance_menu_2.getChildAt(i);
            child.setVisibility(View.GONE);
        }
        for (int i = 0; i < layout_menu_sub.getChildCount(); i++) {
            View child = layout_menu_sub.getChildAt(i);
            child.setVisibility(View.GONE);
        }
        for (int i = 0; i < layout_menu_filter.getChildCount(); i++) {
            View child = layout_menu_filter.getChildAt(i);
            child.setVisibility(View.GONE);
        }
        Picasso.with(getView().getContext())
                .load(R.drawable.img_blackwhite)
                .skipMemoryCache()
                .noFade()
                .into(btn_mono);
        Picasso.with(getView().getContext())
                .load(R.drawable.img_sepia)
                .skipMemoryCache()
                .noFade()
                .into(btn_sepia);
        Picasso.with(getView().getContext())
                .load(R.drawable.img_negative)
                .skipMemoryCache()
                .noFade()
                .into(btn_negative);
        Picasso.with(getView().getContext())
                .load(R.drawable.img_nofilter)
                .skipMemoryCache()
                .noFade()
                .into(btn_nof);
        mTextureView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                try {
                    flagzoom =true;
                    Activity activity = getActivity();
                    CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
                    CameraCharacteristics characteristics = manager.getCameraCharacteristics(mCameraId);
                    float maxzoom = (characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM))*10;

                    Rect m = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
                    int action = event.getAction();
                    float current_finger_spacing;

                    if (event.getPointerCount() > 1) {
                        // Multi touch logic
                        current_finger_spacing = getFingerSpacing(event);

                        if(finger_spacing != 0){
                            if(current_finger_spacing > finger_spacing && maxzoom > zoom_level){
                                zoom_level++;

                            }
                            else if (current_finger_spacing < finger_spacing && zoom_level > 1){
                                zoom_level--;

                            }
                            int minW = (int) (m.width() / maxzoom);
                            int minH = (int) (m.height() / maxzoom);
                            int difW = m.width() - minW;
                            int difH = m.height() - minH;
                            int cropW = difW /100 *(int)zoom_level;
                            int cropH = difH /100 *(int)zoom_level;
                            cropW -= cropW & 3;
                            cropH -= cropH & 3;
                            zoom = new Rect(cropW, cropH, m.width() - cropW, m.height() - cropH);
                            mPreviewRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoom);
                            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE ,effectMode);
                            //mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, awbMode);
                            //mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
                            //mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, Boolean.TRUE);
                            //mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION , 0);

                        }
                        finger_spacing = current_finger_spacing;
                    }
                    else{
                        if (action == MotionEvent.ACTION_UP) {
                            //single touch logic
                        }
                    }

                    try {
                        mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mCaptureCallback,
                                null);
                    }
                    catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                    catch (NullPointerException ex)
                    {
                        ex.printStackTrace();
                    }
                }
                catch (CameraAccessException e)
                {
                    throw new RuntimeException("can not access camera.", e);
                }

                return true;
            }
        });

    }
    private boolean hasSoundEfect = false;
    private void capturePictureZoom() {
        try {
            final Activity activity = getActivity();
            if (null == activity || null == mCameraDevice) {
                return;
            }
            // This is the CaptureRequest.Builder that we use to take a picture.
            final CaptureRequest.Builder captureBuilder =
                    mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(mImageReader.getSurface());

            // Use the same AE and AF modes as the preview.
            captureBuilder.set(CaptureRequest.SCALER_CROP_REGION,zoom);
            //CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            setAutoFlash(captureBuilder);

            // Orientation
            int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation(rotation));
            //captureBuilder.set(CaptureRequest.CONTROL_AWB_MODE, awbMode);
            //mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, Boolean.TRUE);
            captureBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE ,effectMode);
            //mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, 0);
            CameraCaptureSession.CaptureCallback CaptureCallback
                    = new CameraCaptureSession.CaptureCallback() {

                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                               @NonNull CaptureRequest request,
                                               @NonNull TotalCaptureResult result) {
                    //if(hasSoundEfect){
                        AudioManager mgr = (AudioManager) getActivity().getSystemService(Context.AUDIO_SERVICE);
                        mgr.playSoundEffect(SoundEffectConstants.CLICK,20f);
                    //}

                    showToast("Saved: " + mFile);
                    Log.d(TAG, mFile.toString());
                    unlockFocus();
                }
            };

            mCaptureSession.stopRepeating();
            mCaptureSession.capture(captureBuilder.build(), CaptureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        //todo: Change image in preview album
        if(mFile.exists()){
            Picasso.with(getView().getContext())
                    .load(mFile)
                    .resize(0,100)
                    .skipMemoryCache()
                    .noFade()
                    .into(civRecentImage);
        }
    }
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        //mFile = new File(getActivity().getExternalFilesDir(null), "pic.jpg");

        try{
            mFile = createImageFile();
        }catch (IOException e){
            e.printStackTrace();
        }

    }

    @Override
    public void onResume() {
        super.onResume();
        //lock.acquire();
        startBackgroundThread();

        // When the screen is turned off and turned back on, the SurfaceTexture is already
        // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
        // a camera and start preview from here (otherwise, we wait until the surface is ready in
        // the SurfaceTextureListener).
        if (mTextureView.isAvailable()) {
            openCamera(mTextureView.getWidth(), mTextureView.getHeight());
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    @Override
    public void onPause() {
        //lock.release();
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    private void requestCameraPermission() {
        if (FragmentCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
            new ConfirmationDialog().show(getChildFragmentManager(), FRAGMENT_DIALOG);
        } else {
            FragmentCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA},
                    REQUEST_CAMERA_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                ErrorDialog.newInstance(getString(R.string.request_permission))
                        .show(getChildFragmentManager(), FRAGMENT_DIALOG);
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    /**
     * Sets up member variables related to camera.
     *
     */
    /*
    public Size getBestSize(CameraCharacteristics characteristics) {
        // Find a good size for output - largest 16:9 aspect ratio that's less than 720p
        final int MAX_WIDTH = 1280;
        final float TARGET_ASPECT = 1.f / 1.f;
        final float ASPECT_TOLERANCE = 0.1f;


        StreamConfigurationMap configs =
                characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

        Size[] outputSizes = configs.getOutputSizes(SurfaceTexture.class);

        Size outputSize = outputSizes[0];
        float outputAspect = (float) outputSize.getWidth() / outputSize.getHeight();
        for (Size candidateSize : outputSizes) {
            if (candidateSize.getWidth() > MAX_WIDTH) continue;
            float candidateAspect = (float) candidateSize.getWidth() / candidateSize.getHeight();
            boolean goodCandidateAspect =
                    Math.abs(candidateAspect - TARGET_ASPECT) < ASPECT_TOLERANCE;
            boolean goodOutputAspect =
                    Math.abs(outputAspect - TARGET_ASPECT) < ASPECT_TOLERANCE;
            if ((goodCandidateAspect && !goodOutputAspect) ||
                    candidateSize.getWidth() > outputSize.getWidth()) {
                outputSize = candidateSize;
                outputAspect = candidateAspect;
            }
        }
        return outputSize;
    }
    */
    private String IDCameraFront;
    private String IDCameraRear;
    private Boolean mFaceDetectSupported = false;
    private int mFaceDetectMode ;
    private void setUpCameraOutputs(int width, int height) {
        Activity activity = getActivity();
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            IDCameraRear = manager.getCameraIdList()[0];
            IDCameraFront = manager.getCameraIdList()[1];
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        try {
            //for (String cameraId : manager.getCameraIdList()) {
            CameraCharacteristics characteristics
                    = manager.getCameraCharacteristics(mCameraId);
            //minexprosure = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE).getLower();
            //maxexprosure = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE).getUpper();
            //int iso = ((20 * (maxexprosure - minexprosure)) / 100 + minexprosure);

            int[] FD =characteristics.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES);
            int maxFD=characteristics.get(CameraCharacteristics.STATISTICS_INFO_MAX_FACE_COUNT);

            if (FD.length>0) {
                List<Integer> fdList = new ArrayList<>();
                for (int FaceD : FD
                        ) {
                    fdList.add(FaceD);
                    Log.d(TAG, "setUpCameraOutputs: FD type:" + Integer.toString(FaceD));
                }
                Log.d(TAG, "setUpCameraOutputs: FD count" + Integer.toString(maxFD));

                if (maxFD > 0) {
                    mFaceDetectSupported = true;
                    mFaceDetectMode = Collections.max(fdList);
                }
            }

            /*
            // We don't use a front facing camera in this sample.
            Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
            if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                return;
            }
            */

            StreamConfigurationMap map = characteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map == null) {
                //continue;
            }

            // For still image captures, we use the largest available size.
            //Size largest = new Size(2576,1932);
            /*
            if(checkScreenSize==1) {
                //mPreviewSize = map.getOutputSizes(SurfaceTexture.class)[1];
                //largest = new Size(3264,2448);
                largest = new Size(2048,1536);
                //largest = new Size(2576,1932);
            }
            else if(checkScreenSize==0){
                // largest = new Size(2048,2048);
                largest = new Size(2448,2448);
                //largest = new Size(2576,2576);
                //mPreviewSize = map.getOutputSizes(SurfaceTexture.class)[1];
            }
            else if(checkScreenSize==-1){
                //largest = new Size(3264,1836);
                largest = new Size(2048,1152);
                //largest = new Size(2576,1449);
                //mPreviewSize = map.getOutputSizes(SurfaceTexture.class)[1];
            }

            List<Size> temp = Arrays.asList(map.getOutputSizes(ImageFormat.JPEG));
            for (int i = 0; i < temp.size(); ++i){
                if ((16/9)==(temp.get(i).getWidth()/temp.get(i).getHeight())){
                    Log.e("size",temp.get(i).getWidth()+ ""+ temp.get(i).getHeight());
                }
            }
               */
            Size largest = null;
            switch(mAspectRatio) {
                case 1:
                    largest = biggestSizesRear.get(0);
                    break;
                case 2:
                    largest = biggestSizesRear.get(1);
                    break;
                case 3:
                    largest = biggestSizesRear.get(2);
                    break;
                case 4:
                    largest = biggestSizesRear.get(3);
                    break;
                case 5:
                    largest = biggestSizesFront.get(0);
                    break;
                case 6:
                    largest = biggestSizesFront.get(1);
                    break;
                case 7:
                    largest = biggestSizesFront.get(2);
                    break;
                case 8:
                    largest = biggestSizesFront.get(3);
                    break;

            }
            /*
                Size largest = Collections.max(
                        Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
                        new CompareSizesByArea());*/
            Log.e("MKAD: " , largest.getHeight() + ":" + largest.getWidth());
            mImageReader = ImageReader.newInstance(largest.getWidth(), largest.getHeight(),
                    ImageFormat.JPEG, /*maxImages*/2);
            mImageReader.setOnImageAvailableListener(
                    mOnImageAvailableListener, mBackgroundHandler);

            // Find out if we need to swap dimension to get the preview size relative to sensor
            // coordinate.
            int displayRotation = activity.getWindowManager().getDefaultDisplay().getRotation();
            //noinspection ConstantConditions
            mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            boolean swappedDimensions = false;
            switch (displayRotation) {
                case Surface.ROTATION_0:
                case Surface.ROTATION_180:
                    if (mSensorOrientation == 90 || mSensorOrientation == 270) {
                        swappedDimensions = true;
                    }
                    break;
                case Surface.ROTATION_90:
                case Surface.ROTATION_270:
                    if (mSensorOrientation == 0 || mSensorOrientation == 180) {
                        swappedDimensions = true;
                    }
                    break;
                default:
                    Log.e(TAG, "Display rotation is invalid: " + displayRotation);
            }

            Point displaySize = new Point();
            activity.getWindowManager().getDefaultDisplay().getSize(displaySize);
            int rotatedPreviewWidth = width;
            int rotatedPreviewHeight = height;
            int maxPreviewWidth = displaySize.x;
            int maxPreviewHeight = displaySize.y;

            if (swappedDimensions) {
                rotatedPreviewWidth = height;
                rotatedPreviewHeight = width;
                maxPreviewWidth = displaySize.y;
                maxPreviewHeight = displaySize.x;
            }

            if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
                maxPreviewWidth = MAX_PREVIEW_WIDTH;
            }

            if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
                maxPreviewHeight = MAX_PREVIEW_HEIGHT;
            }

            // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
            // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
            // garbage capture data.
            
            mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                    rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth,
                    maxPreviewHeight, largest);
            


            //mPreviewSize = getBestSize(characteristics);
            Log.e("hi",rotatedPreviewWidth + "  " + rotatedPreviewHeight + "  " + maxPreviewWidth + "  " + maxPreviewHeight + "  ");

            // We fit the aspect ratio of TextureView to the size of preview we picked.
            int orientation = getResources().getConfiguration().orientation;
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                mTextureView.setAspectRatio(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            } else {
                mTextureView.setAspectRatio(mPreviewSize.getHeight(), mPreviewSize.getWidth());

            }

            // Check if the flash is supported.
            Boolean available = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
            mFlashSupported = available == null ? 0 : 1;

            //mCameraId = cameraId;
            //return;
            // }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            ErrorDialog.newInstance(getString(R.string.camera_error))
                    .show(getChildFragmentManager(), FRAGMENT_DIALOG);
        }
    }

    /**
     * Opens the camera specified by {@link CameraFragment#mCameraId}.
     */
    private void openCamera(int width, int height) {
        if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission();
            return;
        }

        setUpCameraOutputs(width, height);
        configureTransform(width, height);
        Activity activity = getActivity();
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            manager.openCamera(mCameraId, mStateCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }

    /**
     * Closes the current {@link CameraDevice}.
     */
    private void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();
            if (null != mCaptureSession) {
                mCaptureSession.close();
                mCaptureSession = null;
            }
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if (null != mImageReader) {
                mImageReader.close();
                mImageReader = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Creates a new {@link CameraCaptureSession} for camera preview.
     */
    private void setFaceDetect(CaptureRequest.Builder requestBuilder , int faceDetectMode){
        if (mFaceDetectSupported){
            requestBuilder.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE,faceDetectMode);
        }

    }
    private void createCameraPreviewSession() {
        try {
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;

            // We configure the size of default buffer to be the size of camera preview we want.
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

            // This is the output Surface we need to start preview.
            Surface surface = new Surface(texture);

            // We set up a CaptureRequest.Builder with the output Surface.
            mPreviewRequestBuilder
                    = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(surface);

            // Here, we create a CameraCaptureSession for camera preview.
            mCameraDevice.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            // The camera is already closed
                            if (null == mCameraDevice) {
                                return;
                            }

                            // When the session is ready, we start displaying the preview.
                            mCaptureSession = cameraCaptureSession;
                            try {
                                // Auto focus should be continuous for camera preview.
                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

                                //mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
                                //mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, Boolean.TRUE);
                                //mPreviewRequestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE ,CaptureRequest.CONTROL_EFFECT_MODE_MONO);

                                // Flash is automatically enabled when necessary.
                                setAutoFlash(mPreviewRequestBuilder);
                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, awbMode);
                                setFaceDetect(mPreviewRequestBuilder,mFaceDetectMode);

                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE ,effectMode);
                                // Finally, we start displaying the camera preview.
                                mPreviewRequest = mPreviewRequestBuilder.build();
                                mCaptureSession.setRepeatingRequest(mPreviewRequest,
                                        mCaptureCallback, mBackgroundHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(
                                @NonNull CameraCaptureSession cameraCaptureSession) {
                            showToast("Failed");
                        }
                    }, null
            );
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Configures the necessary {@link android.graphics.Matrix} transformation to `mTextureView`.
     * This method should be called after the camera preview size is determined in
     * setUpCameraOutputs and also the size of `mTextureView` is fixed.
     *
     * @param viewWidth  The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    private void configureTransform(int viewWidth, int viewHeight) {
        Activity activity = getActivity();
        if (null == mTextureView || null == mPreviewSize || null == activity) {
            return;
        }
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        mTextureView.setTransform(matrix);
    }

    /**
     * Initiate a still image capture.
     */
    private void takePicture() {
        try {
            mFile = null;
            createImageGallery();
            mFile = createImageFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if(!flagzoom){
            lockFocus();
            //capturePictureZoom();
        }
        else{
            //mState = STATE_WAITING_LOCK;
            flagzoom = false;
            capturePictureZoom();
        }
        if(mFile.exists()){
            Picasso.with(getView().getContext())
                    .load(mFile)
                    .resize(0,100)
                    .skipMemoryCache()
                    .noFade()
                    .into(civRecentImage);
        }

    }

    /**
     * Lock the focus as the first step for a still image capture.
     */
    private void lockFocus() {
        try {
            // This is how to tell the camera to lock focus.
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_START);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE ,effectMode);
            //setwhiteballane
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, awbMode);
            //mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
            //mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, Boolean.TRUE);
            //mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION , 0);
            // Tell #mCaptureCallback to wait for the lock.
            mState = STATE_WAITING_LOCK;
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Run the precapture sequence for capturing a still image. This method should be called when
     * we get a response in {@link #mCaptureCallback} from {@link #lockFocus()}.
     */
    private void runPrecaptureSequence() {
        try {
            // This is how to tell the camera to trigger.
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE ,effectMode);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, awbMode);
            //mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
            //mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, Boolean.TRUE);
            //mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION , 0);
            // Tell #mCaptureCallback to wait for the precapture sequence to be set.
            mState = STATE_WAITING_PRECAPTURE;
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Capture a still picture. This method should be called when we get a response in
     * {@link #mCaptureCallback} from both {@link #lockFocus()}.
     */
    private int awbMode = CaptureRequest.CONTROL_AWB_MODE_OFF;
    private int effectMode = CaptureRequest.CONTROL_EFFECT_MODE_OFF;
    private void captureStillPicture() {
        try {
            final Activity activity = getActivity();
            if (null == activity || null == mCameraDevice) {
                return;
            }
            // This is the CaptureRequest.Builder that we use to take a picture.
            final CaptureRequest.Builder captureBuilder =
                    mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(mImageReader.getSurface());

            // Use the same AE and AF modes as the preview.
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            setAutoFlash(captureBuilder);

            // Orientation
            int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation(rotation));
            captureBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE ,effectMode);
            captureBuilder.set(CaptureRequest.CONTROL_AWB_MODE, awbMode);
            //mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, Boolean.TRUE);
            //mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, 0);
            //captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
            //captureBuilder.set(CaptureRequest., minexprosure);
            //captureBuilder.set(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX);
            //captureBuilder.set(CaptureRequest.COLOR_CORRECTION_GAINS, colorTemperature(Integer.parseInt(awbMode)));
            CameraCaptureSession.CaptureCallback CaptureCallback
                    = new CameraCaptureSession.CaptureCallback() {

                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                               @NonNull CaptureRequest request,
                                               @NonNull TotalCaptureResult result) {
                    showToast("Saved: " + mFile);
                    Log.d(TAG, mFile.toString());
                    unlockFocus();
                }
            };

            mCaptureSession.stopRepeating();
            mCaptureSession.capture(captureBuilder.build(), CaptureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Retrieves the JPEG orientation from the specified screen rotation.
     *
     * @param rotation The screen rotation.
     * @return The JPEG orientation (one of 0, 90, 270, and 360)
     */
    private int getOrientation(int rotation) {
        // Sensor orientation is 90 for most devices, or 270 for some devices (eg. Nexus 5X)
        // We have to take that into account and rotate JPEG properly.
        // For devices with orientation of 90, we simply return our mapping from ORIENTATIONS.
        // For devices with orientation of 270, we need to rotate the JPEG 180 degrees.
        return (ORIENTATIONS.get(rotation) + mSensorOrientation + 270) % 360;
    }

    /**
     * Unlock the focus. This method should be called when still image capture sequence is
     * finished.
     */
    private void unlockFocus() {
        try {
            // Reset the auto-focus trigger
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            //setwhiteballane
            //
            //mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, Boolean.TRUE);

            //mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, 0);
            setAutoFlash(mPreviewRequestBuilder);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE ,effectMode);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, awbMode);
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);
            // After this, the camera will go back to the normal state of preview.
            mState = STATE_PREVIEW;
            mPreviewRequest = mPreviewRequestBuilder.build();
            mCaptureSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    /*
    private void setScreenSize(){
        closeCamera();
        stopBackgroundThread();

        startBackgroundThread();
        if (mTextureView.isAvailable()) {
            openCamera(mTextureView.getWidth(), mTextureView.getHeight());
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }*/
    private void setWB(){
        /*closeCamera();
        stopBackgroundThread();
        startBackgroundThread();
        if (mTextureView.isAvailable()) {
            openCamera(mTextureView.getWidth(), mTextureView.getHeight());
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }*/
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, awbMode);
        try {
            mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mCaptureCallback,
                    null);
        }
        catch (CameraAccessException e) {
            e.printStackTrace();
        }
        catch (NullPointerException ex)
        {
            ex.printStackTrace();
        }
    }
    private void setEffect(){
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE ,effectMode);
        try {
            mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mCaptureCallback,
                    null);
        }
        catch (CameraAccessException e) {
            e.printStackTrace();
        }
        catch (NullPointerException ex)
        {
            ex.printStackTrace();
        }
    }
    private boolean checkMenuEffect = true;
    public void SetEnabledMenuFilter(int value){
        for (int i = 0; i < layout_menu_filter.getChildCount(); i++) {
            View child = layout_menu_filter.getChildAt(i);
            child.setVisibility(value);

        }
    }
    public void SetEnabledSubMenu(int value){
        for (int i = 0; i < layout_menu_sub.getChildCount(); i++) {
            View child = layout_menu_sub.getChildAt(i);
            child.setVisibility(value);

        }
    }
    public void SetEnabledWhiteBalanceMenu(int value){
        for (int i = 0; i < white_balance_menu_1.getChildCount(); i++) {
            View child = white_balance_menu_1.getChildAt(i);
            child.setVisibility(value);

        }
        for (int i = 0; i < white_balance_menu_2.getChildCount(); i++) {
            View child = white_balance_menu_2.getChildAt(i);
            child.setVisibility(value);

        }
    }
    private boolean checkSubMenu = true;
    private boolean checkWBMenu = true;
    private boolean checktakepicture = true;
    private int checkScreenSize = 1;
    private boolean is43 = true;
    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_video:{
                closeCamera();
                //stopBackgroundThread();

                getFragmentManager().beginTransaction()
                        .replace(R.id.container, VideoFragment.newInstance())
                        .commit();
                break;
            }
            case R.id.btn_take_picture: {
                takePicture();
                checktakepicture = true;
                SetEnabledMenuFilter(View.GONE);
                checkMenuEffect = true;
                checkSubMenu = true;
                checkWBMenu = true;
                break;
            }
            case R.id.civ_recent_image:{
                //closeCamera();
                //stopBackgroundThread();
                /*Intent it3 = new Intent(getActivity(),ImageActivity.class);
                it3.putExtra("Link",mFile.getAbsolutePath());
                startActivity(it3);*/
                break;
            }
            case R.id.btn_white_balance:{
                if(checkWBMenu){
                    SetEnabledWhiteBalanceMenu(View.VISIBLE);
                    checkWBMenu = false;
                }else{
                    SetEnabledWhiteBalanceMenu(View.GONE);
                    checkWBMenu = true;
                }
                break;
            }
            case R.id.btn_more:{
                if(checkSubMenu){
                    SetEnabledSubMenu(View.VISIBLE);
                    SetEnabledMenuFilter(View.GONE);
                    checkMenuEffect = true;
                    checkSubMenu = false;
                }else{
                    SetEnabledSubMenu(View.GONE);
                    SetEnabledWhiteBalanceMenu(View.GONE);
                    checkWBMenu = true;
                    checkSubMenu = true;
                }

                break;
            }
            case R.id.btn_filter:{
                if(checkMenuEffect) {
                    SetEnabledMenuFilter(View.VISIBLE);
                    checkMenuEffect = false;
                    SetEnabledSubMenu(View.GONE);
                    SetEnabledWhiteBalanceMenu(View.GONE);
                    checkWBMenu = true;
                    checkSubMenu = true;
                }
                else{
                    SetEnabledMenuFilter(View.GONE);
                    checkMenuEffect = true;
                }
                break;
            }
            case R.id.btn_mono:{
                effectMode = CaptureRequest.CONTROL_EFFECT_MODE_MONO;
                awbMode = CaptureRequest.CONTROL_AWB_MODE_OFF;
                setEffect();
                break;
            }
            case R.id.btn_negative:{
                effectMode = CaptureRequest.CONTROL_EFFECT_MODE_NEGATIVE;
                awbMode = CaptureRequest.CONTROL_AWB_MODE_OFF;
                setEffect();
                break;
            }
            case R.id.btn_sepia:{
                effectMode = CaptureRequest.CONTROL_EFFECT_MODE_SEPIA;
                awbMode = CaptureRequest.CONTROL_AWB_MODE_OFF;

                setEffect();
                break;
            }
            case R.id.btn_effect_off:{
                effectMode = CaptureRequest.CONTROL_EFFECT_MODE_OFF;
                awbMode = CaptureRequest.CONTROL_AWB_MODE_OFF;
                setEffect();
                break;
            }
            case R.id.btn_wb_auto :{
                awbMode = CaptureRequest.CONTROL_AWB_MODE_AUTO;
                effectMode = CaptureRequest.CONTROL_EFFECT_MODE_OFF;
                setWB();
                break;
            }
            case R.id.btn_wb_cloudy :{
                awbMode = CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT;
                effectMode = CaptureRequest.CONTROL_EFFECT_MODE_OFF;
                setWB();
                break;
            }
            case R.id.btn_wb_daylight :{
                awbMode = CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT;
                effectMode = CaptureRequest.CONTROL_EFFECT_MODE_OFF;
                setWB();
                break;
            }
            case R.id.btn_wb_fluorescent:{
                awbMode = CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT;
                effectMode = CaptureRequest.CONTROL_EFFECT_MODE_OFF;
                setWB();
                break;
            }
            case R.id.btn_wb_shade:{
                awbMode = CaptureRequest.CONTROL_AWB_MODE_SHADE;
                effectMode = CaptureRequest.CONTROL_EFFECT_MODE_OFF;
                setWB();
                break;
            }
            case R.id.btn_wb_twillight:{
                awbMode = CaptureRequest.CONTROL_AWB_MODE_TWILIGHT;
                effectMode = CaptureRequest.CONTROL_EFFECT_MODE_OFF;
                setWB();
                break;
            }
            case R.id.btn_screen_size:{
                switch(mAspectRatio){
                    case 1:
                        /*
                        checkScreenSize = 1;
                        btn_screen_size.setImageResource(R.drawable.ic_1_1);
                        btn_screen_size.setScaleX(0.7f);
                        btn_screen_size.setScaleY(0.7f);
                        if (mTextureView.getActualHeight() > mTextureView.getActualWidth()){
                            mActual.getLayoutParams().height = mTextureView.getActualWidth();
                            mActual.requestLayout();
                            mCover.getLayoutParams().height = mTextureView.getActualHeight() - mTextureView.getActualWidth();
                            mCover.getLayoutParams().width = mTextureView.getActualWidth();
                        }else{
                            mActual.getLayoutParams().width = mTextureView.getActualHeight();
                            mActual.requestLayout();
                            mCover.getLayoutParams().height = mTextureView.getActualHeight();
                            mCover.getLayoutParams().width = mTextureView.getActualWidth() - mTextureView.getActualHeight();
                        }
                        mCover.requestLayout();
                        */

                        break;
                    case 2:
                        /*
                        checkScreenSize = 0;
                        btn_screen_size.setImageResource(R.drawable.ic_4_3);
                        btn_screen_size.setScaleX(0.7f);
                        btn_screen_size.setScaleY(0.7f);
                        */
                        break;
                    case 3:
                        break;
                    case 4:
                        break;
                    case 5:
                        break;
                    case 6:
                        break;
                    case 7:
                        break;
                    case 8:
                        break;
                }

                mAspectRatio++;
                if (mAspectRatio == 5){
                    mAspectRatio = 1;
                }else if(mAspectRatio == 9){
                    mAspectRatio = 5;
                }
                closeCamera();
                stopBackgroundThread();
                startBackgroundThread();
                if (mTextureView.isAvailable()) {
                    openCamera(mTextureView.getWidth(), mTextureView.getHeight());
                } else {
                    mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
                }
                //setUpCameraOutputs(mTextureView.getWidth(), mTextureView.getHeight());
                //configureTransform(mTextureView.getWidth(), mTextureView.getHeight());
                /*
                if(checkScreenSize==1) {
                    checkScreenSize = 0;
                    btn_screen_size.setImageResource(R.drawable.ic_1_1);
                    btn_screen_size.setScaleX(0.7f);
                    btn_screen_size.setScaleY(0.7f);
                    //setScreenSize();
                    //setUpCameraOutputs(mTextureView.getWidth(),mTextureView.getHeight());
                }
                else if(checkScreenSize==0) {
                    checkScreenSize = -1;
                    btn_screen_size.setImageResource(R.drawable.ic_16_9);
                    btn_screen_size.setScaleX(0.7f);
                    btn_screen_size.setScaleY(0.7f);
                    //setUpCameraOutputs(mTextureView.getWidth(),mTextureView.getHeight());
                }
                else if(checkScreenSize == -1){
                    checkScreenSize = 1;
                    btn_screen_size.setImageResource(R.drawable.ic_4_3);
                    btn_screen_size.setScaleX(0.7f);
                    btn_screen_size.setScaleY(0.7f);
                    //setScreenSize();
                    //setUpCameraOutputs(mTextureView.getWidth(),mTextureView.getHeight());

                }
                */
                break;
            }
            case R.id.btn_sound : {
                if(hasSoundEfect){
                    hasSoundEfect = false;
                    Toast.makeText(getActivity(),"Sound off",Toast.LENGTH_SHORT).show();
                    btn_sound.setImageResource(R.drawable.ic_volume_off_white_48dp);
                }
                else{
                    hasSoundEfect = true;
                    Toast.makeText(getActivity(),"Sound on",Toast.LENGTH_SHORT).show();
                    btn_sound.setImageResource(R.drawable.ic_volume_up_white_48dp);
                }
                break;
            }
            case R.id.flash: {
                if(mFlashSupported==1) {
                    mFlashSupported = 0;
                    btnFlash.setImageResource(R.drawable.ic_flash_auto_black_48dp);
                    btnFlash.setScaleX(0.7f);
                    btnFlash.setScaleY(0.7f);
                }
                else if(mFlashSupported==0) {
                    mFlashSupported = -1;
                    btnFlash.setImageResource(R.drawable.ic_flash_off_black_48dp);
                    btnFlash.setScaleX(0.7f);
                    btnFlash.setScaleY(0.7f);
                }
                else if(mFlashSupported == -1){
                    mFlashSupported = 1;

                    btnFlash.setImageResource(R.drawable.ic_flash_on_black_48dp);
                    btnFlash.setScaleX(0.7f);
                    btnFlash.setScaleY(0.7f);

                }
                break;
            }
            case R.id.btn_rotate: {
                /*Activity activity = getActivity();
                if (null != activity) {
                    new AlertDialog.Builder(activity)
                            .setMessage(R.string.intro_message)
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                }*/
                closeCamera();
                stopBackgroundThread();
                if(mCameraId.equals(IDCameraRear)) {
                    mCameraId = IDCameraFront;
                    mAspectRatio = 5;
                    Log.e(TAG, "Display rotation is invalid: " + IDCameraFront);
                }else{
                    mCameraId = IDCameraRear;
                    mAspectRatio = 1;
                    Log.e(TAG, "Display rotation is invalid: " + IDCameraRear);
                }
                startBackgroundThread();
                if (mTextureView.isAvailable()) {
                    openCamera(mTextureView.getWidth(), mTextureView.getHeight());
                } else {
                    mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
                }
                break;
            }
            case R.id.btn_grid:
                hasGrid = !hasGrid;
                if (hasGrid){
                    btn_grid.setImageResource(R.drawable.ic_grid_on_white_48dp);
                    mGridlines.getLayoutParams().height = mTextureView.getActualHeight();
                    mGridlines.getLayoutParams().width = mTextureView.getActualWidth();
                    mGridlines.requestLayout();
                    mGridlines.setRect(new Rect(0,0,
                            mTextureView.getActualWidth(),
                            mTextureView.getActualHeight()));
                    mGridlines.setVisibility(View.VISIBLE);
                }else{
                    btn_grid.setImageResource(R.drawable.ic_grid_off_white_48dp);
                    mGridlines.setVisibility(View.GONE);
                }

                break;
        }

    }

    private void setAutoFlash(CaptureRequest.Builder requestBuilder) {
        if (mFlashSupported == 0) {
            requestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
        }else if (mFlashSupported == 1){
            requestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH);
        }
        else if (mFlashSupported == -1){
            requestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.FLASH_MODE_OFF);
        }
    }

    /**
     * Saves a JPEG {@link Image} into the specified {@link File}.
     */
    private static class ImageSaver implements Runnable {

        /**
         * The JPEG image
         */
        private final Image mImage;
        /**
         * The file we save the image into.
         */
        private final File mFile;

        public ImageSaver(Image image, File file) {
            mImage = image;
            mFile = file;
        }

        @Override
        public void run() {
            ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            FileOutputStream output = null;
            try {
                output = new FileOutputStream(mFile);
                output.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                mImage.close();
                if (null != output) {
                    try {
                        output.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

    }

    /**
     * Compares two {@code Size}s based on their areas.
     */
    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }

    /**
     * Shows an error message dialog.
     */
    public static class ErrorDialog extends DialogFragment {

        private static final String ARG_MESSAGE = "message";

        public static ErrorDialog newInstance(String message) {
            ErrorDialog dialog = new ErrorDialog();
            Bundle args = new Bundle();
            args.putString(ARG_MESSAGE, message);
            dialog.setArguments(args);
            return dialog;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Activity activity = getActivity();
            return new AlertDialog.Builder(activity)
                    .setMessage(getArguments().getString(ARG_MESSAGE))
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            activity.finish();
                        }
                    })
                    .create();
        }

    }

    /**
     * Shows OK/Cancel confirmation dialog about camera permission.
     */
    public static class ConfirmationDialog extends DialogFragment {

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Fragment parent = getParentFragment();
            return new AlertDialog.Builder(getActivity())
                    .setMessage(R.string.request_permission)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            FragmentCompat.requestPermissions(parent,
                                    new String[]{Manifest.permission.CAMERA},
                                    REQUEST_CAMERA_PERMISSION);
                        }
                    })
                    .setNegativeButton(android.R.string.cancel,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Activity activity = parent.getActivity();
                                    if (activity != null) {
                                        activity.finish();
                                    }
                                }
                            })
                    .create();
        }
    }

    public class DrawRectDec extends View {
        Paint paint = new Paint();

        public DrawRectDec(Context context) {
            super(context);
        }

        @Override
        public void onDraw(Canvas canvas) {
            paint.setColor(Color.YELLOW);
            paint.setStrokeWidth(3);
            for(int i = 0;i< faceRects.size();i++){
                canvas.drawRect(faceRects.get(i),paint);
            }

        }
    }


    private String getRecentImage(){
        String path = "";
        File storageDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        File file = new File(storageDirectory, GALLERY_LOCATION);
        File[] list = file.listFiles();
        for(File f:list){
            if(f.getAbsolutePath().endsWith(".jpg")){
                path = f.getAbsolutePath();
            }
        }
        return path;
    }
    //TODO: Get sizes
    private void getImageSizes() throws CameraAccessException {
        int count = 0;

        Activity activity = getActivity();
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        // TODO: Need to check whether the camera has front camera
        IDCameraRear = manager.getCameraIdList()[0];
        IDCameraFront = manager.getCameraIdList()[1];

        // Get Sizes of Back Camera
        CameraCharacteristics characteristics
                = manager.getCameraCharacteristics(IDCameraRear);

        StreamConfigurationMap map = characteristics.get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (map == null) {
            Log.e("ConfigurationMap","Cant get map of back camera");
        }

        // TODO: fix count
        List<Size> tempSizesRear = Arrays.asList(map.getOutputSizes(ImageFormat.JPEG));
        for (int i = 0; i< tempSizesRear.size(); ++i){
            if ((float)tempSizesRear.get(i).getWidth()/(float)tempSizesRear.get(i).getHeight() == ((float)16/9)||
                    (float)tempSizesRear.get(i).getWidth()/(float)tempSizesRear.get(i).getHeight() == ((float)4/3)||
                    (float)tempSizesRear.get(i).getWidth()/(float)tempSizesRear.get(i).getHeight() == (1)){
                biggestSizesRear.add(tempSizesRear.get(i));

                Log.e("Size Rear","Aspect Ratio: "+ Float.toString((float)biggestSizesRear.get(count).getWidth()/(float)biggestSizesRear.get(count).getHeight()) +
                        " Size: "+ biggestSizesRear.get(count).getWidth() + " : "+ biggestSizesRear.get(count).getHeight());
                count++;
            }
            if (count == 4)
                break;
        }

        characteristics
                = manager.getCameraCharacteristics(IDCameraFront);
        map = characteristics.get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (map == null) {
            Log.e("ConfigurationMap","Cant get map of front camera");
        }

        // TODO: fix 4
        count = 0;
        List<Size> tempSizesFront = Arrays.asList(map.getOutputSizes(ImageFormat.JPEG));
        for (int i = 0; i< tempSizesFront.size(); ++i){
            if ((float)tempSizesFront.get(i).getWidth()/(float)tempSizesFront.get(i).getHeight() == ((float)16/9)||
                    (float)tempSizesFront.get(i).getWidth()/(float)tempSizesFront.get(i).getHeight() == ((float)4/3)||
                    (float)tempSizesFront.get(i).getWidth()/(float)tempSizesFront.get(i).getHeight() == ((float)1)){
                biggestSizesFront.add(tempSizesFront.get(i));

                Log.e("Size Front","Aspect Ratio: "+ Float.toString((float)biggestSizesFront.get(count).getWidth()/(float)biggestSizesFront.get(count).getHeight()) +
                        " Size: "+ biggestSizesFront.get(count).getWidth() + " : "+ biggestSizesFront.get(count).getHeight());
                count++;
            }

            if (count == 4)
                break;
        }
    }
}
