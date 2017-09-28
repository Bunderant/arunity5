package org.artoolkit.ar.unity;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Message;
import android.util.Log;

import org.artoolkit.ar.base.NativeInterface;

import java.io.IOException;

/**
 * Created by Nam Nguyen on 12/28/2016.
 */


public class CameraHolderNoThread {
    static {
        NativeInterface.loadNativeLibrary();
    }

    private enum CameraHolderState
    {
        Closed,
        Idle,
        Capturing
    }

    // provide static for call function from unity3d
    public static CameraHolderNoThread Instance = null;
    // --------------------------------------------------
    // Message ID
    // --------------------------------------------------
    private final static String FaceBack = "Back";
    private final static String FaceFront = "Front";
    // --------------------------------------------------
    // camera variable
    // --------------------------------------------------
    public SurfaceTexture mHolderTexture;
    private Camera mCamera;
    private int mWidth = 0;
    private int mHeight = 0;
    private boolean mCameraIsFrontFacing = false;
    private int mCameraIndex= 0;

    private int mConfigW = 640;
    private int mConfigH = 480;

    private CameraHolderState mState = CameraHolderState.Closed;
    private boolean mReminderCapturing = false;

    // --------------------------------------------------
    // Camera Holder Functions
    // --------------------------------------------------

    public CameraHolderNoThread()
    {
        // remove last surface and set instance to this ?
        if(CameraHolderNoThread.Instance != null) {
            CameraHolderNoThread.Instance.CloseCamera();
            CameraHolderNoThread.Instance.DestroyCamera();
            CameraHolderNoThread.Instance = null;
        }
        CameraHolderNoThread.Instance = this;
    }

    public void OpenCamera() {
        if(mCamera != null) {
            return;
        }

        // get camera ID
        mCameraIndex = findFacingCameraId(FaceBack);
        try {
            mCamera = Camera.open(mCameraIndex); // attempt to get a Camera instance
            Log.i("CameraHolder", "Open Camera Success");
        }
        catch (Exception e){
            Log.e("CameraHolder", "ERROR OPENING CAMERA");
        }

        mHolderTexture = new SurfaceTexture(49);
        try {
            mCamera.setPreviewTexture(mHolderTexture);
            mCamera.setPreviewCallback(mPreviewCallback);

            Log.i("CameraHolder", "Set Camera Preview Callback");
        } catch (IOException e) {
            e.printStackTrace();
        }

        mState = CameraHolderState.Idle;
    }

    public void CloseCamera()
    {
        if(mCamera != null) {
            StopCapture();
            mHolderTexture.release();
            mHolderTexture = null;
            mCamera.release();
            mCamera = null;
            mState = CameraHolderState.Closed;
            Log.i("CameraHolder", "Close Camera Success");
        }
    }

    public void StartCapture()
    {
        if(mCamera != null) {
            ConfigCamera();
            mCamera.startPreview();

            mState = CameraHolderState.Capturing;
            Log.i("CameraHolder", "Start Capture Success");
        }
    }

    public void StopCapture()
    {
        if(mCamera != null) {
            mCamera.stopPreview();

            mState = CameraHolderState.Idle;
            Log.i("CameraHolder", "Stop Capture Success");
        }
    }

    public void DestroyCamera()
    {
        mState = CameraHolderState.Closed;
    }

    public void ConfigCamera()
    {
        Camera.Parameters params = mCamera.getParameters();
        params.setPreviewFormat(ImageFormat.NV21);
        // 640x480
        // 480x320
        params.setPreviewSize(mConfigW, mConfigH);
        // -- finish set parameter
        mCamera.setParameters(params);
        // gather some ARToolkit require values
        params = mCamera.getParameters();
        mWidth = params.getPreviewSize().width;;
        mHeight = params.getPreviewSize().height;;
        mCameraIsFrontFacing = false;
        Log.i("CameraHolder", "Set Config Camera");
    }

    // --------------------------------------------------
    // Camera Holder Callback
    // --------------------------------------------------
    private Camera.PreviewCallback mPreviewCallback = new Camera.PreviewCallback() {
        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            NativeInterface.arwAcceptVideoImage(data, mWidth, mHeight, mCameraIndex, mCameraIsFrontFacing);
        }
    };
    // --------------------------------------------------
    // Camera Holder Ultities
    // --------------------------------------------------
    /** Check if this device has a camera */
    public boolean checkCameraHardware(Context context) {
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
            // this device has a camera
            return true;
        } else {
            // no camera on this device
            return false;
        }
    }

    private int findFacingCameraId(String face) {
        int camera_id = -1;
        // Search for the front facing camera
        int numberOfCameras = Camera.getNumberOfCameras();
        for (int i = 0; i < numberOfCameras; i++) {
            Camera.CameraInfo info = new Camera.CameraInfo();
            Camera.getCameraInfo(i, info);
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT && face == "Front") {
                camera_id = i;
                break;
            }
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK && face == "Back") {
                camera_id = i;
                break;
            }
        }
        return camera_id;
    }

    // --------------------------------------------------
    // Camera Holder Interactives
    // --------------------------------------------------
    public static void CommandOpenCamera() {
        if (CameraHolderNoThread.Instance != null) {
            CameraHolderNoThread.Instance.OpenCamera();
        }
    }

    public static void CommandCloseCamera() {
        if (CameraHolderNoThread.Instance != null) {
            CameraHolderNoThread.Instance.CloseCamera();
        }
    }

    public static void CommandStartCapture() {
        if (CameraHolderNoThread.Instance != null) {
            CameraHolderNoThread.Instance.StartCapture();
        }
    }

    public static void CommandStopCapture() {
        if (CameraHolderNoThread.Instance != null) {
            CameraHolderNoThread.Instance.StopCapture();
        }
    }

    public static void ConfigCameraResoulution(int w, int h)
    {
        if (CameraHolderNoThread.Instance != null) {
            CameraHolderNoThread.Instance.mConfigW = w;
            CameraHolderNoThread.Instance.mConfigH = h;
            Log.i("CameraHolder", "Set Camera Resolution : " + w + " : " + h);
        }
    }

    public static void ApplyConfig()
    {
        if (CameraHolderNoThread.Instance != null) {
            CommandCloseCamera();
            CommandOpenCamera();
        }
    }

    public static Boolean IsCameraOpened()
    {
        if (CameraHolderNoThread.Instance != null) {
            return CameraHolderNoThread.Instance.mState != CameraHolderState.Closed;
        }
        return false;
    }

    public static Boolean IsCameraCapturing()
    {
        if (CameraHolderNoThread.Instance != null) {
            return CameraHolderNoThread.Instance.mState == CameraHolderState.Capturing;
        }
        return false;
    }

    public static void ReminderCapturing(Boolean v)
    {
        if (CameraHolderNoThread.Instance != null) {
            CameraHolderNoThread.Instance.mReminderCapturing = v;
        }
    }

    public static Boolean IsReminderCapturing(){
        if (CameraHolderNoThread.Instance != null) {
            return CameraHolderNoThread.Instance.mReminderCapturing;
        }
        return false;
    }
}
