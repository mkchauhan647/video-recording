package com.example.vidocapture;

import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.graphics.drawable.Drawable;
import android.widget.EditText;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import androidx.camera.video.Recorder;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoRecordEvent;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Quality;
import androidx.camera.video.FileOutputOptions;

import android.hardware.camera2.CameraManager;
import android.widget.TextView;

import org.tensorflow.lite.Interpreter;


import android.graphics.Color;


import android.content.res.AssetFileDescriptor;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import org.opencv.android.OpenCVLoader;


public class MainActivity extends AppCompatActivity {
    public static final int STABILIZATION_MODE_ON = 1;
    public static final int STABILIZATION_MODE_OFF = 0;


    private ActivityResultLauncher<String> requestPermissionLauncher;
    private boolean isRecording = false;
    private VideoCapture<Recorder> videoCapture;
    private Recording recording;
    private CameraSelector cameraSelector;
    private boolean isBackCamera = true;
    private boolean isFlashlightOn = false;
    private boolean isStabilizationOn = false;
    private CameraManager cameraManager;
    private String cameraId;
    private Camera camera;
    private SeekBar zoomSeekBar;
    private SeekBar exposureSeekBar;
    private Spinner videoQualitySpinner;
    private Quality selectedQuality = Quality.HIGHEST;
    private CircularProgressIndicator recordingProgressBar;
    private TextView recordingTime;
    private Handler handler;
    private Runnable updateRecordingTimeRunnable;
    private long startTime;

    private Bitmap currentFrame;
    private ImageView imageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (!OpenCVLoader.initDebug()) {
            Log.e("OpenCV", "Failed to initialize OpenCV");
        } else {
            Log.d("OpenCV", "OpenCV initialized successfully");
        }

        // Initialize UI components
        Button toggleStabilizationButton = findViewById(R.id.toggle_stabilization);

        // Set up the button click listener for the video stabilization button
        toggleStabilizationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleStabilization();
            }
        });

        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        startCamera();
                    } else {
                        Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA);
        }

        FloatingActionButton fabRecording = findViewById(R.id.fab_recording);
        FloatingActionButton showBottomSheetButton = findViewById(R.id.show_bottom_sheet_button);
        LinearLayout bottomSheet = findViewById(R.id.bottom_sheet);
        zoomSeekBar = findViewById(R.id.zoomSeekBar);
        exposureSeekBar = findViewById(R.id.exposure_control);
        videoQualitySpinner = findViewById(R.id.video_quality);
        recordingProgressBar = findViewById(R.id.recording_progress_bar);
        recordingTime = findViewById(R.id.recording_time);

        List<String> supportedQualities = getSupportedQualities();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, supportedQualities);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        videoQualitySpinner.setAdapter(adapter);

        videoQualitySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String quality = parent.getItemAtPosition(position).toString();
                switch (quality) {
                    case "Lowest":
                        selectedQuality = Quality.LOWEST;
                        break;
                    case "SD":
                        selectedQuality = Quality.SD;
                        break;
                    case "HD":
                        selectedQuality = Quality.HD;
                        break;
                    case "FHD":
                        selectedQuality = Quality.FHD;
                        break;
                    case "UHD":
                        selectedQuality = Quality.UHD;
                        break;
                    default:
                        selectedQuality = Quality.HIGHEST;
                        break;
                }
                startCamera();
            }







            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });



        Spinner spinner = findViewById(R.id.background_spinner);
        Button solidColorButton = findViewById(R.id.btn_solid_color);
        Button textileButton = findViewById(R.id.btn_textile);
        imageView = findViewById(R.id.imageView);

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                switch (position) {
                    case 0:
                        imageView.setImageResource(R.drawable.solid_color_background); // Blurred solid color
                        break;
                    case 1:
                        imageView.setImageResource(R.drawable.textile_background); // Textile background
                        break;
                    case 2:
                        imageView.setImageResource(R.drawable.abstract_background); // Abstract background
                        break;
                    // Add more cases as needed
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Handle case where nothing is selected, if necessary
            }
        });


        fabRecording.setOnClickListener(v -> {
            if (isRecording) {
                stopRecording();
            } else {
                startRecording();
            }
        });

        showBottomSheetButton.setOnClickListener(v -> {
            if (bottomSheet.getVisibility() == View.GONE) {
                bottomSheet.setVisibility(View.VISIBLE);
            } else {
                bottomSheet.setVisibility(View.GONE);
            }
        });

        Button rotateButton = findViewById(R.id.rotateButton);
        rotateButton.setOnClickListener(v -> {
            isBackCamera = !isBackCamera;
            startCamera();
        });

        Button toggleFlashlightButton = findViewById(R.id.toggle_flashlight);
        cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);

        toggleFlashlightButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleFlashlight();
            }
        });

        zoomSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (camera != null) {
                    float maxZoomRatio = camera.getCameraInfo().getZoomState().getValue().getMaxZoomRatio();
                    float zoomRatio = progress / 100.0f * (maxZoomRatio - 1) + 1;
                    camera.getCameraControl().setZoomRatio(zoomRatio);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        exposureSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (camera != null) {
                    float exposureCompensation = (progress - 50) / 50.0f; // Assuming the range is -1 to 1
                    camera.getCameraControl().setExposureCompensationIndex((int) (exposureCompensation * camera.getCameraInfo().getExposureState().getExposureCompensationRange().getUpper()));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        Button resetButton = findViewById(R.id.reset_button);
        resetButton.setOnClickListener(v -> resetExposure());

        Button resetZoomButton = findViewById(R.id.reset_zoom_button);
        resetZoomButton.setOnClickListener(v -> resetZoom());

        handler = new Handler(Looper.getMainLooper());
        updateRecordingTimeRunnable = new Runnable() {
            @Override
            public void run() {
                if (isRecording) {
                    long elapsedTime = System.currentTimeMillis() - startTime;
                    int seconds = (int) (elapsedTime / 1000) % 60;
                    int minutes = (int) (elapsedTime / (1000 * 60)) % 60;
                    int hours = (int) (elapsedTime / (1000 * 60 * 60)) % 24;
                    recordingTime.setText(String.format("%02d:%02d:%02d", hours, minutes, seconds));
                    recordingProgressBar.setProgress((int) (elapsedTime / 1000));
                    handler.postDelayed(this, 1000);
                }
            }
        };
    }

    private List<String> getSupportedQualities() {
        List<String> supportedQualities = new ArrayList<>();
        if (isQualitySupported(Quality.LOWEST)) supportedQualities.add("Lowest");
        if (isQualitySupported(Quality.SD)) supportedQualities.add("SD");
        if (isQualitySupported(Quality.HD)) supportedQualities.add("HD");
        if (isQualitySupported(Quality.FHD)) supportedQualities.add("FHD");
        if (isQualitySupported(Quality.UHD)) supportedQualities.add("UHD");
        if (isQualitySupported(Quality.HIGHEST)) supportedQualities.add("Highest");
        return supportedQualities;
    }



    private boolean isQualitySupported(Quality quality) {
        // Check if the selected quality is supported by the device
        // This is a placeholder implementation, you need to replace it with actual checks
        // based on your device's capabilities
        return true; // Replace with actual check
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (isRecording) {
            stopRecording();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isRecording) {
            stopRecording();
        }
    }

    private void toggleFlashlight() {
        try {
            if (camera != null) {
                camera.getCameraControl().enableTorch(!isFlashlightOn);
                isFlashlightOn = !isFlashlightOn;
            } else {
                Toast.makeText(this, "Camera is not initialized", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e("CameraXApp", "Error toggling flashlight", e);
            Toast.makeText(this, "Error toggling flashlight: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleStabilization() {
        isStabilizationOn = !isStabilizationOn;
        if (camera != null) {
            // Enable or disable stabilization for the Video
            Recorder recorder = new Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(selectedQuality))
                    .build();
            videoCapture = VideoCapture.withOutput(recorder);
            Toast.makeText(this, "Stabilization " + (isStabilizationOn ? "enabled" : "disabled"), Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Camera is not initialized", Toast.LENGTH_SHORT).show();
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                if (isQualitySupported(selectedQuality)) {
                    bindPreview(cameraProvider);
                } else {
                    Toast.makeText(this, "Selected video quality is not supported by this device", Toast.LENGTH_SHORT).show();
                }
            } catch (ExecutionException | InterruptedException e) {
                Log.e("CameraXApp", "Camera provider initialization failed.", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {
        PreviewView previewView = findViewById(R.id.previewView);
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(isBackCamera ? CameraSelector.LENS_FACING_BACK : CameraSelector.LENS_FACING_FRONT)
                .build();
        Recorder recorder = new Recorder.Builder()
                .setQualitySelector(QualitySelector.from(selectedQuality))
                .build();
        videoCapture = VideoCapture.withOutput(recorder);

        cameraProvider.unbindAll();
        camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, videoCapture);
    }

//    private void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {
//        PreviewView previewView = findViewById(R.id.previewView);
//
//        // Set up Preview use case
//        Preview preview = new Preview.Builder().build();
//        preview.setSurfaceProvider(previewView.getSurfaceProvider());
//
//        // Set up the CameraSelector to switch between back and front camera
//        cameraSelector = new CameraSelector.Builder()
//                .requireLensFacing(isBackCamera ? CameraSelector.LENS_FACING_BACK : CameraSelector.LENS_FACING_FRONT)
//                .build();
//
//        // Set up VideoCapture use case
//        Recorder recorder = new Recorder.Builder()
//                .setQualitySelector(QualitySelector.from(selectedQuality))
//                .build();
//        videoCapture = VideoCapture.withOutput(recorder);
//
//        // Unbind all use cases before rebinding (to avoid conflicts)
//        cameraProvider.unbindAll();
//
//        try {
//            if (!isRecording) {
//                // Set up ImageAnalysis only when not recording
//                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
//                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
//                        .build();
//
//                // Set the analyzer to process each frame
//                imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), new ImageAnalysis.Analyzer() {
//                    @Override
//                    public void analyze(@NonNull ImageProxy image) {
//                        Bitmap bitmap = imageToBitmap(image);  // Convert ImageProxy to Bitmap
//                        onCameraFrameCaptured(bitmap);         // Pass the frame for further processing
//                        image.close();                         // Close the image when done
//                    }
//                });
//
//                // Bind Preview, VideoCapture, and ImageAnalysis
//                camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, videoCapture, imageAnalysis);
//            } else {
//                // If recording, bind only Preview and VideoCapture
//                camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, videoCapture);
//            }
//        } catch (Exception e) {
//            Log.e("CameraXApp", "Failed to bind use cases", e);
//        }
//    }

    private Bitmap imageToBitmap(ImageProxy image) {
        ImageProxy.PlaneProxy[] planes = image.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        byte[] yBytes = new byte[yBuffer.remaining()];
        yBuffer.get(yBytes);

        ByteBuffer uBuffer = planes[1].getBuffer();
        byte[] uBytes = new byte[uBuffer.remaining()];
        uBuffer.get(uBytes);

        ByteBuffer vBuffer = planes[2].getBuffer();
        byte[] vBytes = new byte[vBuffer.remaining()];
        vBuffer.get(vBytes); // Read the V plane data

        int width = image.getWidth();
        int height = image.getHeight();

        YuvImage yuvImage = new YuvImage(yBytes, ImageFormat.NV21, width, height, null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, width, height), 100, out);
        byte[] imageBytes = out.toByteArray();

        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
    }


    private void startRecording() {
        showFileNameDialog();
    }

    private void showFileNameDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enter file name");

        final EditText input = new EditText(this);
        builder.setView(input);

        builder.setPositiveButton("OK", (dialog, which) -> {
            String fileName = input.getText().toString();
            if (!fileName.isEmpty()) {
                File videoFile = new File(getExternalFilesDir(null), fileName + ".mp4");
                try {
                    recording = videoCapture.getOutput()
                            .prepareRecording(this, new FileOutputOptions.Builder(videoFile).build())
                            .start(ContextCompat.getMainExecutor(this), videoRecordEvent -> {
                                if (videoRecordEvent instanceof VideoRecordEvent.Start) {
                                    isRecording = true;
                                    startTime = System.currentTimeMillis();
                                    handler.post(updateRecordingTimeRunnable);
                                    toggleRecordingButtons();
                                } else if (videoRecordEvent instanceof VideoRecordEvent.Finalize) {
                                    isRecording = false;
                                    handler.removeCallbacks(updateRecordingTimeRunnable);
                                    toggleRecordingButtons();
                                    if (!((VideoRecordEvent.Finalize) videoRecordEvent).hasError()) {
                                        Toast.makeText(this, "Recording saved: " + videoFile.getAbsolutePath(), Toast.LENGTH_SHORT).show();
                                    } else {
                                        Log.e("CameraXApp", "Recording error: " + ((VideoRecordEvent.Finalize) videoRecordEvent).getError());
                                        handleRecordingError(videoFile);
                                    }
                                }
                            });
                } catch (Exception e) {
                    Log.e("CameraXApp", "Error starting recording", e);
                    Toast.makeText(this, "Error starting recording: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    handleRecordingError(videoFile);
                }
            } else {
                Toast.makeText(this, "File name cannot be empty", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void handleRecordingError(File videoFile) {
        // Save the recording and reset the state
        if (recording != null) {
            recording.stop();
            recording = null;
        }
        Toast.makeText(this, "Recording saved with errors: " + videoFile.getAbsolutePath(), Toast.LENGTH_SHORT).show();
        resetToInitialState();
    }

    private void resetToInitialState() {
        // Reset UI components and state variables to initial state
        toggleRecordingButtons();
        resetExposure();
        resetZoom();
        recordingTime.setText("00:00:00");
        recordingProgressBar.setProgress(0);
        isRecording = false;
        isFlashlightOn = false;
        isStabilizationOn = false;
        startCamera();
    }

    private void stopRecording() {
        if (recording != null) {
            recording.stop();
            recording = null;
        }
    }

    private void toggleRecordingButtons() {
        FloatingActionButton fabRecording = findViewById(R.id.fab_recording);
        if (isRecording) {
            fabRecording.setImageResource(R.drawable.ic_stop); // Change icon to stop
        } else {
            fabRecording.setImageResource(R.drawable.ic_record); // Change icon to record
        }
    }

    private void resetExposure() {
        if (camera != null) {
            camera.getCameraControl().setExposureCompensationIndex(0);
            exposureSeekBar.setProgress(50); // Reset SeekBar to the middle position
        }
    }

    private void resetZoom() {
        if (camera != null) {
            camera.getCameraControl().setZoomRatio(1.0f); // Reset zoom to normal
            zoomSeekBar.setProgress(0); // Reset SeekBar to the initial position
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (recording != null) {
            recording.stop();
            recording = null;
        }
    }


    private void onCameraFrameCaptured(Bitmap frame) {
        currentFrame = frame;  // Save the captured frame for later processing
        processFrameForBackgroundChange(frame);
    }


    // Background extended featuress

    private void applyBackgroundChange(String option) {
        if (camera == null) {
            Toast.makeText(this, "Camera not initialized", Toast.LENGTH_SHORT).show();
            return;
        }

        switch (option) {
            case "Blur":
                applyBlurToBackground();
                break;
            case "Solid Color":
                applySolidColorBackground(Color.GREEN); // example with green color
                break;
            case "Textile":
                applyTextilePatternBackground();
                break;
            default:
                resetBackground();
                break;
        }
    }

    private void applyBlurToBackground() {
        if (currentFrame == null) {
            Toast.makeText(this, "No frame to blur", Toast.LENGTH_SHORT).show();
            return;
        }

        Mat frameMat = new Mat();
        Utils.bitmapToMat(currentFrame, frameMat);

        Mat blurredMat = new Mat();
        Imgproc.GaussianBlur(frameMat, blurredMat, new Size(15, 15), 0);

        Bitmap blurredBitmap = Bitmap.createBitmap(blurredMat.cols(), blurredMat.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(blurredMat, blurredBitmap);

        imageView.setImageBitmap(blurredBitmap);
    }

    private void applySolidColorBackground(int color) {

        View backgroundView = findViewById(R.id.background_view);
        if (backgroundView != null) {
            backgroundView.setBackgroundColor(color);
            Toast.makeText(this, "Solid color applied", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Background view not found", Toast.LENGTH_SHORT).show();
        }
    }

    private void applyTextilePatternBackground() {

        Drawable textilePattern = getResources().getDrawable(R.drawable.textile_background);

        View backgroundView = findViewById(R.id.background_view);
        if (backgroundView != null && textilePattern != null) {
            backgroundView.setBackground(textilePattern);
            Toast.makeText(this, "Textile pattern applied", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Background view or pattern not found", Toast.LENGTH_SHORT).show();
        }
    }

    private void resetBackground() {

        View backgroundView = findViewById(R.id.background_view);
        if (backgroundView != null) {
            backgroundView.setBackground(null);  // Reset background to transparent
            Toast.makeText(this, "Background reset", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Background view not found", Toast.LENGTH_SHORT).show();
        }
    }


    private Interpreter tflite;
    private void loadModelFile() {
        try {

            AssetFileDescriptor fileDescriptor = this.getAssets().openFd("1.tflite"); // deeplabv3 tflite model
            FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
            FileChannel fileChannel = inputStream.getChannel();
            long startOffset = fileDescriptor.getStartOffset();
            long declaredLength = fileDescriptor.getDeclaredLength();
            MappedByteBuffer buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
            tflite = new Interpreter(buffer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void processFrameForBackgroundChange(Bitmap frame) {
        if (tflite == null) {
            loadModelFile();
        }

        int[] inputShape = tflite.getInputTensor(0).shape();  // Assuming the first input is the image
        int width = inputShape[1];
        int height = inputShape[2];

        Bitmap resizedFrame = Bitmap.createScaledBitmap(frame, width, height, true);

        ByteBuffer inputBuffer = convertBitmapToByteBuffer(resizedFrame);

        int[] outputShape = tflite.getOutputTensor(0).shape();
        ByteBuffer outputBuffer = ByteBuffer.allocateDirect(outputShape[1] * outputShape[2] * 4);
        outputBuffer.order(ByteOrder.nativeOrder());

        tflite.run(inputBuffer, outputBuffer);

        Bitmap maskBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        outputBuffer.rewind();
        maskBitmap.copyPixelsFromBuffer(outputBuffer);

        applyBackgroundChange("Blur");
    }

    private ByteBuffer convertBitmapToByteBuffer(Bitmap bitmap) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(bitmap.getHeight() * bitmap.getWidth() * 4);
        buffer.order(ByteOrder.nativeOrder());

        int[] pixels = new int[bitmap.getWidth() * bitmap.getHeight()];
        bitmap.getPixels(pixels, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());

        for (int pixel : pixels) {
            buffer.putFloat(((pixel >> 16) & 0xFF) / 255.0f);  // Red
            buffer.putFloat(((pixel >> 8) & 0xFF) / 255.0f);   // Green
            buffer.putFloat((pixel & 0xFF) / 255.0f);          // Blue
        }

        return buffer;
    }





}


