package com.example.jjcamera;

import static org.bytedeco.javacv.ProjectiveDevice.normalize;

import org.opencv.calib3d.StereoSGBM;

import static org.opencv.imgproc.Imgproc.COLOR_GRAY2RGB;
import static org.opencv.imgproc.Imgproc.cvtColor;

import org.opencv.core.Core;
import org.opencv.core.CvType;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;


import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.Image;
import android.os.Build;
import android.os.Bundle;

import android.util.Log;
import android.util.Size;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.common.util.concurrent.ListenableFuture;

import org.bytedeco.javacv.AndroidFrameConverter;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.opencv.android.OpenCVLoader;

import java.io.IOException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.BitSet;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


import com.example.jjcamera.databinding.ActivityMainBinding;

import org.opencv.android.Utils;
import org.opencv.calib3d.StereoSGBM;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;


public class MainActivity extends AppCompatActivity {

    /*网络摄像头ImageView*/ ImageView video_ip;
    private ActivityMainBinding viewBinding;
    private ExecutorService cameraExecutor;

    private String webStream = null;

    private int frameRatio;

    private Bitmap pic1 = null;
    private Bitmap pic2 = null;
    private int wid=640;
    private int hit=360;

    ImageView resView;

    /**
     * Mat数组打印
     */
    public static void printMat(Mat mat) {
        System.out.println("Mat对象内容:");
        System.out.println(mat.dump());
    }

    /**
     * Bitmap 转 Mat
     */
    public static Mat bitmapToMap(Bitmap bitmap) {
        Mat mat = new Mat();
        Utils.bitmapToMat(bitmap, mat);
        return mat;
    }

    /**
     * Mat 转 Bitmap
     */
    public static Bitmap matToBitmap(Mat inputFrame) {
        Bitmap bitmap = Bitmap.createBitmap(inputFrame.width(), inputFrame.height(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(inputFrame, bitmap);
        return bitmap;
    }

    /**
     * Mat CV.16S转Bitmap ARGB_8888
     */
    public static Bitmap mat1ToBitmap3(Mat inputFrame) {
        Mat nor = new Mat();
        Core.MinMaxLocResult result = Core.minMaxLoc(inputFrame);
        inputFrame.convertTo(nor, CvType.CV_8U, 255.0 / result.maxVal);

//        printMat(inputFrame);

        Mat three = new Mat();
        nor.convertTo(three, CvType.CV_8U);
        Mat res = new Mat();
        cvtColor(three, res, COLOR_GRAY2RGB);
        Bitmap bitmap = Bitmap.createBitmap(res.width(), res.height(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(res, bitmap);
        return bitmap;
    }

    public static Bitmap zoomImg(Bitmap bm, int newWidth, int newHeight) {
        // 获得图片的宽高
        int width = bm.getWidth();
        int height = bm.getHeight();
        // 计算缩放比例
        float scaleWidth = 1.0f * newWidth / width;
        float scaleHeight = 1.0f * newHeight / height;
        // 取得想要缩放的matrix参数
        Matrix matrix = new Matrix();
        matrix.postScale(scaleWidth, scaleHeight);
        // 得到新的图片
        Bitmap newbm = Bitmap.createBitmap(bm, 0, 0, width, height, matrix, true);
//        Bitmap newbm = Bitmap.createScaledBitmap(bm,newWidth,newHeight,true);
        return newbm;
    }

    Runnable SGBMThread = new Runnable() {

        @Override
        public void run() {
            while (true) {
                if (pic1 != null && pic2 != null) {
                    Bitmap bit1 = pic1.copy(Bitmap.Config.ARGB_8888, true);
                    Bitmap bit2 = pic2.copy(Bitmap.Config.ARGB_8888, true);


                    bit1 = zoomImg(pic1, wid, hit);
                    bit2 = zoomImg(pic2, wid, hit);


                    Mat mat1 = bitmapToMap(bit1);
                    Mat mat2 = bitmapToMap(bit2);

                    Mat leftGray = new Mat();
                    Mat rightGray = new Mat();

                    cvtColor(mat1, leftGray, Imgproc.COLOR_BGR2GRAY);
                    cvtColor(mat2, rightGray, Imgproc.COLOR_BGR2GRAY);

//                    runOnUiThread(() -> {
//                        v1.setImageBitmap(matToBitmap(leftGray));
//                        v2.setImageBitmap(matToBitmap(rightGray));
//                    });

                    Mat disparity = new Mat();

//                    you code here
                    StereoSGBM stereo = StereoSGBM.create(1, 64, 3, 216, 864, -1, 1, 10, 100, 100, StereoSGBM.MODE_HH);
                    stereo.compute(leftGray, rightGray, disparity);

                    Bitmap res = mat1ToBitmap3(disparity);

                    runOnUiThread(() -> {
                        resView.setImageBitmap(res);
                    });


                    Log.d("MainActivitypic1", String.valueOf((bit1 == null)));
                    Log.d("MainActivitypic2", String.valueOf((bit2 == null)));
                }

            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        viewBinding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(viewBinding.getRoot());

//  Resource路径图片测试结果
//        Context context = this.getApplicationContext();
//        b1 = BitmapFactory.decodeResource(context.getResources(), R.drawable.captured_image1);
//        b2 = BitmapFactory.decodeResource(context.getResources(), R.drawable.captured_image2);

        if (OpenCVLoader.initDebug()) {
            Log.d("OPENCV", "Opencv init");
        }

        video_ip = findViewById(R.id.video_ip);
//        video_local=findViewById(R.id.video_local);

        resView = findViewById(R.id.resView);
        Button button = findViewById(R.id.button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                EditText editIP = (EditText) findViewById(R.id.editIP);
                EditText editRat = (EditText) findViewById(R.id.editRat);

                frameRatio=Integer.parseInt(editRat.getText().toString());
                webStream = editIP.getText().toString();

                new Thread(webVideoThread).start();

//                //隐藏软键盘
//
//                InputMethodManager imm =(InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
//                imm.hideSoftInputFromWindow(view.getWindowToken(),0);

                System.out.println(" Thread(webVideoThread).start();");

                new Thread(SGBMThread).start();

            }
        });


//        new Thread(localVideoThread).start();
        // 请求相机权限
        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, Configuration.REQUIRED_PERMISSIONS, Configuration.REQUEST_CODE_PERMISSIONS);
        }

        cameraExecutor = Executors.newSingleThreadExecutor();
        System.out.println(" Thread(localVideoThread).start();");


    }


    Runnable webVideoThread = new Runnable() {
        @Override
        public void run() {
            try {
//                webStream = "http://192.168.0.246:8080/video";
                FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(new URL(webStream).openStream());
                grabber.setOption("fflags", "nobuffer");
                //grabber.setFormat("h264");
                grabber.setImageWidth(960);
                grabber.setImageHeight(544);
                //为了加快转bitmap这句一定要写
//                System.out.println("grabber start");
                grabber.start();
                AndroidFrameConverter converter = new AndroidFrameConverter();
                Bitmap bmp;
                Frame frame = null;
                int i = 0;
                while ((frame = grabber.grabImage()) != null) {
//                    System.out.println("FFmpeg grabber");
                    if(i%frameRatio==0)
                    {
                        bmp = converter.convert(frame);
                        pic1 = bmp;
                        runOnUiThread(() -> video_ip.setImageBitmap(pic1));
                    }

                    i++;
                }
//                System.out.println("out");

            } catch (FrameGrabber.Exception e) {
                throw new RuntimeException(e);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        }
    };

    private void startCamera() {
        // 将Camera的生命周期和Activity绑定在一起（设定生命周期所有者），这样就不用手动控制相机的启动和关闭。
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                // 将你的相机和当前生命周期的所有者绑定所需的对象
                ProcessCameraProvider processCameraProvider = cameraProviderFuture.get();

                // 创建一个Preview 实例，并设置该实例的 surface 提供者（provider）。
                PreviewView viewFinder = (PreviewView) findViewById(R.id.viewFinder);
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

                // 选择后置摄像头作为默认摄像头
                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                // 设置预览帧分析
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder().setTargetResolution(new Size(1280, 720)).setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888).build();
                imageAnalysis.setAnalyzer(cameraExecutor, new MyAnalyzer());

                // 重新绑定用例前先解绑
                processCameraProvider.unbindAll();

                // 绑定用例至相机
                processCameraProvider.bindToLifecycle(MainActivity.this, cameraSelector, preview, imageAnalysis);

            } catch (Exception e) {
                Log.e(Configuration.TAG, "用例绑定失败！" + e);
            }
        }, ContextCompat.getMainExecutor(this));

    }


    private boolean allPermissionsGranted() {
        for (String permission : Configuration.REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }


    static class Configuration {
        public static final String TAG = "CameraxBasic";
        public static final String FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS";
        public static final int REQUEST_CODE_PERMISSIONS = 10;
        public static final int REQUEST_AUDIO_CODE_PERMISSIONS = 12;
        public static final String[] REQUIRED_PERMISSIONS = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P ? new String[]{android.Manifest.permission.CAMERA, android.Manifest.permission.RECORD_AUDIO, android.Manifest.permission.WRITE_EXTERNAL_STORAGE} : new String[]{android.Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO};
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == Configuration.REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {// 申请权限通过
                startCamera();
            } else {// 申请权限失败
                Toast.makeText(this, "用户拒绝授予权限！", Toast.LENGTH_LONG).show();
                finish();
            }
        } else if (requestCode == Configuration.REQUEST_AUDIO_CODE_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, "Manifest.permission.RECORD_AUDIO") != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "未授权录制音频权限！", Toast.LENGTH_LONG).show();
            }
        }
    }

    private class MyAnalyzer implements ImageAnalysis.Analyzer {
        @SuppressLint("UnsafeOptInUsageError")
        @Override
        public void analyze(@NonNull ImageProxy image) {
//            System.out.println(image.getImage().getFormat());
            Bitmap bmp = image.toBitmap();
            pic2 = bmp;
//            Log.d(Configuration.TAG, "Image's stamp is " + Objects.requireNonNull(image.getImage()).getTimestamp());
            image.close();
        }
    }

}