package com.zchu.heartrate;

import android.Manifest;
import android.content.Context;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.PowerManager;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.IOException;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicBoolean;

import permissions.dispatcher.NeedsPermission;
import permissions.dispatcher.OnNeverAskAgain;
import permissions.dispatcher.OnPermissionDenied;
import permissions.dispatcher.OnShowRationale;
import permissions.dispatcher.PermissionRequest;
import permissions.dispatcher.RuntimePermissions;

/**
 * 作者: zchu on 2016/9/13 0013.
 */
@RuntimePermissions
public class HeartRateActivity extends AppCompatActivity implements View.OnClickListener {

    private AtomicBoolean processing = new AtomicBoolean(true);
    private LinkedList<Integer> averageData = new LinkedList<>();
    private long endTime = 0;

    private SurfaceHolder mPreviewHolder;
    private Camera mCamera = null;
    private PowerManager.WakeLock mWakeLock = null;
    private Camera.Parameters parameters;
    private boolean canNeedsPermission = true;

    private SurfaceView mPreview;
    private HeartRateChart heartRateChart;
    private Button btnActionStart;
    private Snackbar mSnackbar;

    private Camera.PreviewCallback mPreviewCallback = new Camera.PreviewCallback() {
        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            if (!processing.compareAndSet(false, true)) {
                return;
            }
            Camera.Size size = camera.getParameters().getPreviewSize();
            int width = size.width;
            int height = size.height;
            int imgAvg = ImageProcessing.decodeYUV420SPtoRedAvg(data.clone(), height, width); //获取红色的平均数量。
            Log.e("HeartRateActivity","onPreviewFrame");
            heartRateChart.lineTo(imgAvg);
            if (imgAvg == 0 || imgAvg == 255 || imgAvg < 150) {
                ToastUtil.showToast(HeartRateActivity.this,"请用你的手指盖住摄像头");
                reStart();
                //processing.set(false);
                return;
            }
            if (averageData.peekLast() == null || averageData.peekLast() != imgAvg) {
                averageData.add(imgAvg);

            }
            if (endTime == 0) {
                endTime = System.currentTimeMillis() + 10000;
            } else if (System.currentTimeMillis() >= endTime) {
                ToastUtil.showToast(HeartRateActivity.this,"心脏跳动" + processData(averageData) + "次" + "，心率：" + processData(averageData) * 6);
                return;
            }
            processing.set(false);
        }
    };


    private int processData(LinkedList<Integer> averageData) {
        int dInt = 0;
        int count = 0;
        boolean isRise = false;
        for (Integer integer : averageData) {
            if (dInt == 0) {
                dInt = integer;
                continue;
            }
            if (integer > dInt) {
                if (!isRise) {
                    count++;
                    isRise = true;
                }
            } else {
                isRise = false;
            }
            dInt = integer;
        }
        return count;
    }

    private SurfaceHolder.Callback mSurfaceCallback = new SurfaceHolder.Callback() {

        @Override
        public void surfaceCreated(SurfaceHolder surfaceHolder) {
            try {
                mCamera.setPreviewDisplay(mPreviewHolder);
                mCamera.setPreviewCallback(mPreviewCallback);
            } catch (Exception e) {
                e.printStackTrace();
            }

        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            //   parameters = mCamera.getParameters();
            //   parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);//打开闪光灯

            Camera.Size size = getSmallestPreviewSize(width, height, parameters);
            if (size != null) {
                parameters.setPreviewSize(size.width, size.height);
            }
            mCamera.setParameters(parameters);
            mCamera.startPreview();
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder surfaceHolder) {

        }
    };

    private Camera.Size getSmallestPreviewSize(int width, int height, Camera.Parameters parameters) {
        Camera.Size result = null;

        for (Camera.Size size : parameters.getSupportedPreviewSizes()) {
            if (size.width <= width && size.height <= height) {
                if (result == null) {
                    result = size;
                } else {
                    int resultArea = result.width * result.height;
                    int newArea = size.width * size.height;
                    if (newArea < resultArea) result = size;
                }
            }
        }

        return result;
    }





    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable throwable) {
                Log.e("ZCHU",Log.getStackTraceString(throwable));
                System.exit(0);
            }
        });
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_heart_rate);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        heartRateChart = (HeartRateChart) findViewById(R.id.heart_rate_chart);
        btnActionStart = (Button) findViewById(R.id.btn_action_start);
        btnActionStart.setOnClickListener(this);
        setSupportActionBar(toolbar);
        final ActionBar ab = getSupportActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
            ab.setDisplayShowTitleEnabled(false);
        }
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });

    }

    @Override
    protected void onResume() {
        super.onResume();
        if (canNeedsPermission) {
            HeartRateActivityPermissionsDispatcher.showCameraWithCheck(this);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mWakeLock != null) {
            mWakeLock.release();
        }
        if (mCamera != null) {
            try {
                mCamera.setPreviewDisplay(null);
            } catch (IOException e) {
                e.printStackTrace();
            }
            mCamera.setPreviewCallback(null);
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
        canNeedsPermission = true;
        if (mSnackbar != null) {
            mSnackbar.dismiss();
        }
    }

    /**
     * 开启或重启心率检测
     */
    private void reStart() {
        endTime = 0;
        averageData.clear();
        heartRateChart.clear();
        processing.set(false); //开启检测
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()){
            case R.id.btn_action_start:
                reStart();
                break;
        }

    }

    @NeedsPermission(Manifest.permission.CAMERA)
    void showCamera() {
        mCamera = Camera.open();
        mCamera.setDisplayOrientation(90);
        if (parameters == null) {
            parameters = mCamera.getParameters();
            parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);//打开闪光灯
        }
        if (mPreview == null) {
            mPreview = (SurfaceView) findViewById(R.id.sv_preview);
            mPreviewHolder = mPreview.getHolder();
            mPreviewHolder.addCallback(mSurfaceCallback);
            mPreviewHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            mWakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK, "DoNotDimScreen");
        }
        mWakeLock.acquire();

        if (parameters != null) {
            try {
                mCamera.setPreviewDisplay(mPreviewHolder);
            } catch (IOException e) {
                e.printStackTrace();
            }
            mCamera.setPreviewCallback(mPreviewCallback);
            mCamera.setParameters(parameters);
            mCamera.startPreview();
        }
    }

    @OnPermissionDenied(Manifest.permission.CAMERA)
    void onCameraDenied() {
        ToastUtil.showToast(HeartRateActivity.this,R.string.permission_camera_denied_msg);
        finish();
    }

    @OnNeverAskAgain(Manifest.permission.CAMERA)
    void OnCameraNeverAskAgain() {
        canNeedsPermission = false;
        mSnackbar = Snackbar.make(findViewById(android.R.id.content), R.string.permission_camera_never_ask_again, Snackbar.LENGTH_INDEFINITE).setAction(R.string.setting, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                UIHelper.showAppDetailSetting(HeartRateActivity.this);
            }
        });
        mSnackbar.show();

    }

    @OnShowRationale(Manifest.permission.CAMERA)
    void showRationaleForCamera(final PermissionRequest request) {
        UIHelper.getRationaleDialog(this, R.string.permission_camera_dialog_title, R.string.permission_camera_dialog_msg_scanner, request).show();
    }

    //动态授权
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // NOTE: delegate the permission handling to generated method
        HeartRateActivityPermissionsDispatcher.onRequestPermissionsResult(this, requestCode, grantResults);
    }


}
