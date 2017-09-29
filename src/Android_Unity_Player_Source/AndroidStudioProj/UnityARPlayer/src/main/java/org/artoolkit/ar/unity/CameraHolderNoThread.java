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

    protected final static String TAG = "CameraHolderNoThread";

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
    private Camera mCamera = null;
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
            Log.i(TAG, "Open Camera Success");
        }
        catch (Exception e) {
            Log.e(TAG, "Error opening camera with message: " + e.getMessage());
        }

        mHolderTexture = new SurfaceTexture(49);
        try {
            mCamera.setPreviewTexture(mHolderTexture);
            mCamera.setPreviewCallback(mPreviewCallback);

            Log.i(TAG, "Set Camera Preview Callback");
        } catch (IOException e) {
            e.printStackTrace();
        }

        mState = CameraHolderState.Idle;
    }

    public void CloseCamera()
    {
        if(mCamera != null) {

            try {
                mHolderTexture.release();
                mHolderTexture = null;
            }
            catch(Exception e) {
                Log.e(TAG, "Could not release holder texture with message: " + e.getMessage());
            }

            try {
                mCamera.release();
                mCamera = null;
            }
            catch(Exception e) {
                Log.e(TAG, "Could not release camera with message: " + e.getMessage());
            }

            mState = CameraHolderState.Closed;
            Log.i(TAG, "Close Camera Success");
        }
    }

    public void StartCapture()
    {
        if(mCamera != null) {
            ConfigCamera();
            mCamera.startPreview();

            mState = CameraHolderState.Capturing;
            Log.i(TAG, "Start Capture Success");
        }
    }

    public void StopCapture()
    {
        if(mCamera != null) {
            try {
                mCamera.stopPreview();
                mCamera.setPreviewCallback(null); // <-- NEVER FORGET TO DO THIS AFTER STOP PREVIEW

                mState = CameraHolderState.Idle;
                Log.i(TAG, "Stop Capture Success");
            }
            catch(Exception e) {
                Log.e(TAG, "Could not stop capture with message: " + e.getMessage());
            }
        }
    }

    public void DestroyCamera() {
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
        Log.i(TAG, "Set Config Camera");
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
            Log.i(TAG, "No camera");
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
}
