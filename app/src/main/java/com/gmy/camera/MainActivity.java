package com.gmy.camera;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Range;
import android.util.Rational;
import android.util.Size;
import android.util.SizeF;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.WindowManager;
import android.widget.TextView;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements TextureView.SurfaceTextureListener{
    private static final String TAG = "MainActivity";

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }
    // Handler for Camera Thread
    private Handler handler;
    private HandlerThread threadHandler;
    // camera2 API Camera
    private CameraDevice camera;
    // Back cam, 1 would be the front facing one
    private String cameraID = "0";
    // Texture View to display the camera picture, or the vins output
    private TextureView textureView;
    private CaptureRequest.Builder previewBuilder;
    private ImageReader imageReader;
    private Surface mSurface;
    /**
     * Adjustment to auto-exposure (AE) target image brightness in EV
     */
    private final int aeCompensation = 0;
    private final int framesPerSecond = 30;

    // Cam parameters
    private final int imageWidth = 640;
    private final int imageHeight = 360;
    private float virtualCamDistance = 2;

    private static native boolean onImageAvailableNative(int width, int height, int rowStrideY, ByteBuffer bufferY,
                                                   int rowStrideUV, ByteBuffer bufferU, ByteBuffer bufferV,
                                                   Surface surface, long timeStamp, boolean isScreenRotated,
                                                   float virtualCamDistance);
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        textureView = findViewById(R.id.texture_view);
        textureView.setSurfaceTextureListener(this);
        
    }

    /**
     * Starting separate thread to handle camera input
     */
    private void initLooper() {
        threadHandler = new HandlerThread("Camera2Thread");
        threadHandler.start();
        handler = new Handler(threadHandler.getLooper());
    }

    @Override
    protected void onResume() {
        super.onResume();
        PermissionsUtil.doRequestPermissions(this);

    }

    @Override
    protected void onStop() {
        super.onStop();

    }

    /**
     * shutting down onPause
     */
    protected void onPause() {
        if (null != camera) {
            camera.close();
            camera = null;
        }
        if (null != imageReader) {
            imageReader.close();
            imageReader = null;
        }

        super.onPause();
    }
    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
        try {
            CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            // check permissions
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            // start up Camera (not the recording)
            cameraManager.openCamera(cameraID, cameraDeviceStateCallback, handler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

    }

    private CameraDevice.StateCallback cameraDeviceStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(CameraDevice cameraDevice) {
            try {
                camera = cameraDevice;

                startCameraView(camera);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
        }

        @Override
        public void onError(CameraDevice camera, int error) {
        }
    };

    /**
     * starts CameraView
     */
    private void startCameraView(CameraDevice camera) throws CameraAccessException {
        SurfaceTexture texture = textureView.getSurfaceTexture();
        // to set CameraView size
        texture.setDefaultBufferSize(textureView.getWidth(), textureView.getHeight());
        Log.d(TAG, "texture width: " + textureView.getWidth() + " height: " + textureView.getHeight());
        mSurface = new Surface(texture);
        try {
            // to set request for CameraView
            previewBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        // to set the format of captured images and the maximum number of images that can be accessed in mImageReader
        imageReader = ImageReader.newInstance(imageWidth, imageHeight, ImageFormat.YUV_420_888, 1);
        imageReader.setOnImageAvailableListener(onImageAvailableListener, handler);
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraID);
        // get the StepSize of the auto exposure compensation
        Rational aeCompStepSize = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP);
        if (aeCompStepSize == null) {
            Log.e(TAG, "Camera doesn't support setting Auto-Exposure Compensation");
            finish();
        }
        Log.d(TAG, "AE Compensation StepSize: " + aeCompStepSize);
        int aeCompensationInSteps = aeCompensation * aeCompStepSize.getDenominator() / aeCompStepSize.getNumerator();
        Log.d(TAG, "aeCompensationInSteps: " + aeCompensationInSteps);
        previewBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, aeCompensationInSteps);
        // set the camera output frequency to 60Hz
        previewBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, new Range<Integer>(framesPerSecond, framesPerSecond));

        // the first added target surface is for CameraView display
        // the second added target mImageReader.getSurface()
        // is for ImageReader Callback where it can be access EACH frame
//        previewBuilder.addTarget(surface);
        previewBuilder.addTarget(imageReader.getSurface());

        //============================================================
        //设置固定焦距
        previewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF);
        //============================================================
        //output Surface
        List<Surface> outputSurfaces = new ArrayList<>();
//        outputSurfaces.add(surface);
        outputSurfaces.add(imageReader.getSurface());
        camera.createCaptureSession(outputSurfaces, sessionStateCallback, handler);
        cameraTimestampsShiftWrtSensors = ImageUtils.getCameraTimestampsShiftWrtSensors(characteristics);
    }
    private CameraCaptureSession.StateCallback sessionStateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(CameraCaptureSession session) {
            try {
                updateCameraView(session);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
        @Override
        public void onConfigureFailed(CameraCaptureSession session) {

        }
    };
    /**
     * Starts the RepeatingRequest for
     */
    private void updateCameraView(CameraCaptureSession session)
            throws CameraAccessException {
        // 不自动对焦
//        previewBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, 0.0f);
        // 不自动对焦
//        previewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF);
        //自动对焦
        previewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
        // 不自动白平衡
//        previewBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_OFF);
        session.setRepeatingRequest(previewBuilder.build(), null, handler);
    }
    private long cameraTimestampsShiftWrtSensors = -1;
    /**
     * At last the actual function with access to the image
     */
    private ImageReader.OnImageAvailableListener onImageAvailableListener = new ImageReader.OnImageAvailableListener() {

        /*
         *  The following method will be called every time an image is ready
         *  be sure to use method acquireNextImage() and then close(), otherwise, the display may STOP
         */
        @Override
        public void onImageAvailable(ImageReader reader) {
            // get the newest frame
            Image image = reader.acquireNextImage();
            if (image == null) {
                return;
            }
            if (image.getFormat() != ImageFormat.YUV_420_888) {
                Log.e(TAG, "camera image is in wrong format");
            }
            //RGBA output
            Image.Plane Y_plane = image.getPlanes()[0];   //Y
            int Y_rowStride = Y_plane.getRowStride();
            Image.Plane U_plane = image.getPlanes()[1];   //U
            int UV_rowStride = U_plane.getRowStride();
            Image.Plane V_plane = image.getPlanes()[2];   //V

            // pass the current device's screen orientation to the c++ part
            int currentRotation = getWindowManager().getDefaultDisplay().getRotation();
            boolean isScreenRotated = currentRotation != Surface.ROTATION_90;

            long imageTimestamp = image.getTimestamp() + cameraTimestampsShiftWrtSensors;
            int imageWidth = image.getWidth();
            int imageHeight = image.getHeight();
            Log.i(TAG, "image width: " + imageWidth + " height: " + imageHeight);
            // pass image to c++ part
            onImageAvailableNative(imageWidth,
                    imageHeight,
                    Y_rowStride,
                    Y_plane.getBuffer(),
                    UV_rowStride,
                    U_plane.getBuffer(),
                    V_plane.getBuffer(),
                    mSurface,
                    imageTimestamp,
                    isScreenRotated,
                    virtualCamDistance);

            image.close();
        }
    };
}
