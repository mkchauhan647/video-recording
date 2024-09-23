package com.example.vidocapture;

import android.app.AlertDialog;
import android.widget.EditText;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
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
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.File;
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
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.content.Intent;

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
    private Intent notificationServiceIntent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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

        FloatingActionButton fabStartRecording = findViewById(R.id.fab_start_recording);
        FloatingActionButton fabStopRecording = findViewById(R.id.fab_stop_recording);
        FloatingActionButton showBottomSheetButton = findViewById(R.id.show_bottom_sheet_button);
        LinearLayout bottomSheet = findViewById(R.id.bottom_sheet);
        zoomSeekBar = findViewById(R.id.zoomSeekBar);
        exposureSeekBar = findViewById(R.id.exposure_control);
        videoQualitySpinner = findViewById(R.id.video_quality);

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

        fabStartRecording.setOnClickListener(v -> startRecording());
        fabStopRecording.setOnClickListener(v -> stopRecording());
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

        notificationServiceIntent = new Intent(this, NotificationService.class);
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

    private void startRecording() {
        showFileNameDialog();
        startService(notificationServiceIntent);
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
                                    toggleRecordingButtons();
                                    startService(notificationServiceIntent);
                                } else if (videoRecordEvent instanceof VideoRecordEvent.Finalize) {
                                    isRecording = false;
                                    toggleRecordingButtons();
                                    stopService(notificationServiceIntent);
                                    if (!((VideoRecordEvent.Finalize) videoRecordEvent).hasError()) {
                                        Toast.makeText(this, "Recording saved: " + videoFile.getAbsolutePath(), Toast.LENGTH_SHORT).show();
                                    } else {
                                        Log.e("CameraXApp", "Recording error: " + ((VideoRecordEvent.Finalize) videoRecordEvent).getError());
                                    }
                                }
                            });
                } catch (Exception e) {
                    Log.e("CameraXApp", "Error starting recording", e);
                    Toast.makeText(this, "Error starting recording: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "File name cannot be empty", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void stopRecording() {
        if (recording != null) {
            recording.stop();
            recording = null;
            stopService(notificationServiceIntent);
        }
    }

    private void toggleRecordingButtons() {
        FloatingActionButton fabStartRecording = findViewById(R.id.fab_start_recording);
        FloatingActionButton fabStopRecording = findViewById(R.id.fab_stop_recording);
        if (isRecording) {
            fabStartRecording.setVisibility(View.GONE);
            fabStopRecording.setVisibility(View.VISIBLE);
        } else {
            fabStartRecording.setVisibility(View.VISIBLE);
            fabStopRecording.setVisibility(View.GONE);
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
        stopService(notificationServiceIntent);
    }
}
