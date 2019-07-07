package com.bytedance.camera.demo;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.Window;
import android.view.WindowManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Parameter;
import java.security.Policy;
import java.util.List;

import static com.bytedance.camera.demo.utils.Utils.MEDIA_TYPE_IMAGE;
import static com.bytedance.camera.demo.utils.Utils.MEDIA_TYPE_VIDEO;
import static com.bytedance.camera.demo.utils.Utils.getOutputMediaFile;
import android.os.Build;
import android.widget.Toast;

public class CustomCameraActivity extends AppCompatActivity {

    private static final String TAG = "CustomCameraActivity";
    private SurfaceView mSurfaceView;
    private Camera mCamera;

    private int CAMERA_TYPE = Camera.CameraInfo.CAMERA_FACING_BACK;

    private boolean isRecording = false;

    private int rotationDegree = 0;
    private static final int REQUEST_CODE=99;
    private int progress=0;
    private SurfaceHolder surfaceHolder;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_custom_camera);

        mCamera =getCamera(CAMERA_TYPE);
        mSurfaceView = findViewById(R.id.img);

        //todo 给SurfaceHolder添加Callback
        surfaceHolder = mSurfaceView.getHolder();
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        surfaceHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                try{
                    startPreview(holder);
                    AutoFocus();
                }catch (Exception e){
                    e.printStackTrace();
                }
            }
            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) { }
            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                    mCamera.stopPreview();
                    mCamera.release();
                    mCamera = null;
            }
        });

        findViewById(R.id.btn_picture).setOnClickListener(v -> {
            //todo 拍一张照片
            mCamera.takePicture(null,null,mPicture);
        });

        findViewById(R.id.btn_record).setOnClickListener(v -> {
            //todo 录制，第一次点击是start，第二次点击是stop
            if (isRecording) {
                //todo 停止录制
                mMediaRecorder.stop();
                mMediaRecorder.reset();
                mMediaRecorder.release();
                mMediaRecorder =null;
                mCamera.lock();
                isRecording = false;
                Toast.makeText(this, "录制结束", Toast.LENGTH_LONG).show();
            } else {
                //todo 录制
                Toast.makeText(this, "录制开始", Toast.LENGTH_LONG).show();
                isRecording =true;
                mMediaRecorder = new MediaRecorder();
                mCamera.unlock();
                mMediaRecorder.setCamera(mCamera);
                mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
                mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
                mMediaRecorder.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH));

                mMediaRecorder.setOutputFile(getOutputMediaFile(MEDIA_TYPE_VIDEO).toString());
                Log.i(TAG, "onCreate: "+getOutputMediaFile(MEDIA_TYPE_VIDEO).toString());
                mMediaRecorder.setPreviewDisplay(mSurfaceView.getHolder().getSurface());
                mMediaRecorder.setOrientationHint(rotationDegree);
                try {
                    mMediaRecorder.prepare();
                    mMediaRecorder.start();
                }catch (Exception e){
                    releaseMediaRecorder();
                    e.printStackTrace();
                }
            }
        });

        findViewById(R.id.btn_facing).setOnClickListener(v -> {
            //todo 切换前后摄像头
            progress=0;
            mCamera.cancelAutoFocus();
            mCamera.stopPreview();
            mCamera.release();
            if(CAMERA_TYPE == Camera.CameraInfo.CAMERA_FACING_BACK){
                mCamera = getCamera(Camera.CameraInfo.CAMERA_FACING_FRONT);
                Toast.makeText(this, "后置摄像头", Toast.LENGTH_LONG).show();
            }else{
                mCamera = getCamera(Camera.CameraInfo.CAMERA_FACING_BACK);
                Toast.makeText(this, "前置摄像头", Toast.LENGTH_LONG).show();
            }
            startPreview(surfaceHolder);
            if(CAMERA_TYPE == Camera.CameraInfo.CAMERA_FACING_BACK){
                AutoFocus();
            }
        });

        findViewById(R.id.btn_zoom).setOnClickListener(v -> {
            //todo 调焦，需要判断手机是否支持
            progress = progress> 80?1:progress+5;
            mCamera.stopPreview();
 //           mCamera.release();
            Camera.Parameters p =mCamera.getParameters();
            p.setZoom(progress);
            mCamera.setParameters(p);
            mCamera.startPreview();
            if(CAMERA_TYPE == Camera.CameraInfo.CAMERA_FACING_BACK){
                AutoFocus();
            }

        });

    }

    public void AutoFocus(){

        mCamera.autoFocus(new Camera.AutoFocusCallback() {
            @Override
            public void onAutoFocus(boolean success, Camera camera) {
                if(success){
                    mCamera.cancelAutoFocus();
                    Camera.Parameters parameters = mCamera.getParameters();
               //     parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                    parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
                    camera.setParameters(parameters);
                }
            }
        });

    }


    public Camera getCamera(int position) {
        CAMERA_TYPE = position;
        if (mCamera != null) {
            releaseCameraAndPreview();
        }
        Camera cam = Camera.open(position);
        //todo 摄像头添加属性，例是否自动对焦，设置旋转方向等
        rotationDegree = getCameraDisplayOrientation(position);
        cam.setDisplayOrientation(rotationDegree);

        Camera.Parameters parameters = cam.getParameters();

        cam.setParameters(parameters);

        return cam;
    }



    private static final int DEGREE_90 = 90;
    private static final int DEGREE_180 = 180;
    private static final int DEGREE_270 = 270;
    private static final int DEGREE_360 = 360;

    private int getCameraDisplayOrientation(int cameraId) {
        android.hardware.Camera.CameraInfo info =
                new android.hardware.Camera.CameraInfo();
        android.hardware.Camera.getCameraInfo(cameraId, info);
        int rotation = getWindowManager().getDefaultDisplay()
                .getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = DEGREE_90;
                break;
            case Surface.ROTATION_180:
                degrees = DEGREE_180;
                break;
            case Surface.ROTATION_270:
                degrees = DEGREE_270;
                break;
            default:
                break;
        }

        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % DEGREE_360;
            result = (DEGREE_360 - result) % DEGREE_360;  // compensate the mirror
        } else {  // back-facing
            result = (info.orientation - degrees + DEGREE_360) % DEGREE_360;
        }
        return result;
    }


    private void releaseCameraAndPreview() {
        //todo 释放camera资源
        mCamera.release();
    }

    Camera.Size size;

    private void startPreview(SurfaceHolder holder) {
        //todo 开始预览
        try{
            mCamera.setPreviewDisplay(holder);
        }catch (Exception e){
            e.printStackTrace();
        }
        mCamera.startPreview();
    }


    private MediaRecorder mMediaRecorder;

    private boolean prepareVideoRecorder() {
        //todo 准备MediaRecorder
        try {
            mMediaRecorder.prepare();
            return true;
        }catch (Exception e){
            e.printStackTrace();
            return false;
        }

    }


    private void releaseMediaRecorder() {
        //todo 释放MediaRecorder
        mMediaRecorder.stop();
        mMediaRecorder.reset();
        mMediaRecorder.release();
    }


    private Camera.PictureCallback mPicture = (data, camera) -> {
        File pictureFile = getOutputMediaFile(MEDIA_TYPE_IMAGE);
        if (pictureFile == null) {
            return;
        }
        try {
            FileOutputStream fos = new FileOutputStream(pictureFile);
            fos.write(data);
            fos.close();
        } catch (IOException e) {
            Log.d("mPicture", "Error accessing file: " + e.getMessage());
        }

        mCamera.startPreview();
        Toast.makeText(this, "拍照完毕", Toast.LENGTH_LONG).show();
        AutoFocus();
    };


    private Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes, int w, int h) {
        final double ASPECT_TOLERANCE = 0.1;
        double targetRatio = (double) h / w;

        if (sizes == null) return null;

        Camera.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        int targetHeight = Math.min(w, h);

        for (Camera.Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
            if (Math.abs(size.height - targetHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
            }
        }

        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Camera.Size size : sizes) {
                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
        }
        return optimalSize;
    }




}
