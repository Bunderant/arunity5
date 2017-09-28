package org.artoolkit.ar.unity;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.util.Log;

import org.artoolkit.ar.base.NativeInterface;

import java.io.IOException;
import java.lang.ref.WeakReference;

/**
 * Created by Nam Nguyen on 12/28/2016.
 */


public class CameraHolder {
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
    public static CameraHolder Instance = null;
    // --------------------------------------------------
    // Message ID
    // --------------------------------------------------
    private final static int OpenCamera = 0;
    private final static int CloseCamera = 1;
    private final static int StartCapture = 2;
    private final static int StopCapture = 3;

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

    private CameraHolderState mState = CameraHolderState.Closed;
    private boolean mReminderCapturing = false;
    // --------------------------------------------------
    // camera Thread variable
    // --------------------------------------------------
    private Looper mServiceLooper;
    public CameraServiceHandler mServiceHandler;

    // --------------------------------------------------
    // Camera Thread
    // --------------------------------------------------
    private final class CameraServiceHandler extends Handler {
        WeakReference<CameraHolder> mReference;

        public CameraServiceHandler(Looper looper, CameraHolder referent) {
            super(looper);
            mReference = new WeakReference<CameraHolder>(referent);
        }

        @Override
        public void handleMessage(Message msg) {
            CameraHolder cameraHolder = mReference.get();
            switch (msg.what) {
                case CameraHolder.OpenCamera:
                    cameraHolder.OpenCamera();
                    break;
                case CameraHolder.CloseCamera:
                    cameraHolder.CloseCamera();
                    break;
                case CameraHolder.StartCapture:
                    cameraHolder.StartCapture();
                    break;
                case CameraHolder.StopCapture:
                    cameraHolder.StopCapture();
                    break;
                default:
                    break;
            }
            super.handleMessage(msg);
        }
    }


    // --------------------------------------------------
    // Camera Holder Functions
    // --------------------------------------------------

    public CameraHolder()
    {
        // Start up the thread running the service. Note that we create a
        // separate thread because the service normally runs in the process's
        // main thread, which we don't want to block. We also make it
        // background priority so CPU-intensive work will not disrupt our UI.
        HandlerThread thread = new HandlerThread("camera_service_thread", Process.THREAD_PRIORITY_LOWEST);
        thread.start();
        // Get the HandlerThread's Looper and use it for our Handler
        mServiceLooper = thread.getLooper();
        mServiceHandler = new CameraServiceHandler(mServiceLooper, this);

        // remove last surface and set instance to this ?
        if(CameraHolder.Instance != null) {
            CameraHolder.Instance.CloseCamera();
            CameraHolder.Instance.DestroyCamera();
            CameraHolder.Instance = null;
        }
        CameraHolder.Instance = this;
    }

    public void OpenCamera()
    {
        if(mCamera != null)
        {
            // camera already open ?
            //TODO: do we need close and reopen camera here ?
            return;
        }
        //===============================================
        // get camera ID
        mCameraIndex = findFacingCameraId(FaceBack);
        try {
            mCamera = Camera.open(mCameraIndex); // attempt to get a Camera instance
            Log.i("CameraHolder", "Open Camera Success");
        }
        catch (Exception e){
            // trigger Open Camera Error here.
        }
        // -------------------------------------------------------
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
            mReminderCapturing = false;
        }
    }

    public void DestroyCamera()
    {
        try {
            mServiceLooper.getThread().join();
            mServiceLooper = null;
            mServiceHandler = null;

            Log.i("CameraHolder", "Destroy Camera");
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        mState = CameraHolderState.Closed;
    }

    public void ConfigCamera()
    {
        Camera.Parameters params = mCamera.getParameters();
        params.setPreviewFormat(ImageFormat.NV21);
        params.setPreviewSize(640, 480);
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
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)){
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
        if (CameraHolder.Instance != null) {
            Message msg = CameraHolder.Instance.mServiceHandler.obtainMessage();
            msg.what = CameraHolder.OpenCamera;
            CameraHolder.Instance.mServiceHandler.sendMessage(msg);
        }
    }

    public static void CommandCloseCamera() {
        if (CameraHolder.Instance != null) {
            Message msg = CameraHolder.Instance.mServiceHandler.obtainMessage();
            msg.what = CameraHolder.CloseCamera;
            CameraHolder.Instance.mServiceHandler.sendMessage(msg);
        }
    }

    public static void CommandStartCapture() {
        if (CameraHolder.Instance != null) {
            Message msg = CameraHolder.Instance.mServiceHandler.obtainMessage();
            msg.what = CameraHolder.StartCapture;
            CameraHolder.Instance.mServiceHandler.sendMessage(msg);
        }
    }

    public static void CommandStopCapture() {
        if (CameraHolder.Instance != null) {
            Message msg = CameraHolder.Instance.mServiceHandler.obtainMessage();
            msg.what = CameraHolder.StopCapture;
            CameraHolder.Instance.mServiceHandler.sendMessage(msg);
        }
    }

    public static Boolean IsCameraOpened()
    {
        if (CameraHolder.Instance != null) {
            return CameraHolder.Instance.mState != CameraHolderState.Closed;
        }
        return false;
    }

    public static Boolean IsCameraCapturing()
    {
        if (CameraHolder.Instance != null) {
            return CameraHolder.Instance.mState == CameraHolderState.Capturing;
        }
        return false;
    }

    public static void ReminderCapturing(Boolean v)
    {
        if (CameraHolder.Instance != null) {
            CameraHolder.Instance.mReminderCapturing = v;
        }
    }

    public static Boolean IsReminderCapturing(){
        if (CameraHolder.Instance != null) {
            return CameraHolder.Instance.mReminderCapturing;
        }
        return false;
    }
}
