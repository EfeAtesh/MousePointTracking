package com.efeates.mpointtracking;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.content.Intent;
import android.provider.Settings;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class Main extends AppCompatActivity {
    public static ArrayList<ArrayList<Float>> points = new ArrayList<>();
    
    private boolean isRecording = false;
    private TextView textView;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.onlyscreen);

        textView = findViewById(R.id.programmeSituation);

        Button start = findViewById(R.id.start);
        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isRecording) {
                    stopTracking();
                    start.setText("Start Tracking");
                } else {
                    startTracking();
                    start.setText("Stop Tracking");
                }
            }
        });
        
        textView.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
            Toast.makeText(this, "Please enable 'MousePointTracer' service", Toast.LENGTH_LONG).show();
        });

        View root = findViewById(android.R.id.content);

        root.setOnTouchListener((v, event) -> {
            if (!isRecording) return false;

            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                float x = event.getRawX();
                float y = event.getRawY();
                Log.d("TRACKER", "In-App Click: x=" + x + ", y=" + y);
                
                ArrayList<Float> point = new ArrayList<>();
                point.add(x);
                point.add(y);
                points.add(point);
                return true;
            }
            return false;
        });
    }

    private void startTracking() {
        isRecording = true;
        MyTrackerAccessibilityService.isRecording = true;
        
        points.clear();
        textView.setText("Recording (Tap here to open Settings if needed)");
        textView.setTextColor(ContextCompat.getColor(this, R.color.red));
    }

    private void stopTracking() {
        isRecording = false;
        MyTrackerAccessibilityService.isRecording = false;
        
        textView.setText("Is not Recording");
        textView.setTextColor(ContextCompat.getColor(this, R.color.black));
        save2CSV();
        Log.d("TRACKER", "Total points captured: " + points.size());
    }

    protected void save2CSV() {
        Context currentContext = this;
        if (currentContext == null) {
            return;
        }

        if (points == null || points.isEmpty()) {
            new MaterialAlertDialogBuilder(currentContext)
                    .setTitle("No Data")
                    .setMessage("There is no data available to save.")
                    .setPositiveButton("OK", null)
                    .show();
            Log.w("SaveCSV", "expData is null or empty. Nothing to save.");
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
        String timestamp = sdf.format(new Date());
        String fileName = "Run_" +  timestamp + ".csv";
        String mimeType = "application/octet-stream";

        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);

        Uri collectionUri;
        String finalSaveLocationDescription;
        String customSubFolder = "MPointTracking";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            String relativePathWithSubfolder = Environment.DIRECTORY_DOCUMENTS + File.separator + customSubFolder;
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, relativePathWithSubfolder);
            collectionUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            finalSaveLocationDescription = relativePathWithSubfolder + File.separator + fileName;
            Log.d("SaveSTFY_Q_Plus", "Attempting to save: " + fileName + " with MIME: " + mimeType + " to path: " + relativePathWithSubfolder);

        } else {
            File baseStorageDir = currentContext.getExternalFilesDir(null);
            if (baseStorageDir == null) {
                new MaterialAlertDialogBuilder(currentContext)
                        .setTitle("Storage Error")
                        .setMessage("App-specific external storage is not available. Cannot save the file.")
                        .setPositiveButton("OK", null)
                        .show();
                return;
            }

            File sensitifyAppSpecificDir = new File(baseStorageDir, customSubFolder);
            if (!sensitifyAppSpecificDir.exists()) {
                if (!sensitifyAppSpecificDir.mkdirs()) {
                    new MaterialAlertDialogBuilder(currentContext)
                            .setTitle("Storage Error")
                            .setMessage("Could not create directory: " + sensitifyAppSpecificDir.getAbsolutePath())
                            .setPositiveButton("OK", null)
                            .show();
                    return;
                }
            }

            File csvFile = new File(sensitifyAppSpecificDir, fileName);
            Log.d("Savestfy", "Attempting to save CSV (pre-Q) to: " + csvFile.getAbsolutePath());
            finalSaveLocationDescription = csvFile.getAbsolutePath();

            try (FileOutputStream fos = new FileOutputStream(csvFile);
                 OutputStreamWriter osw = new OutputStreamWriter(fos)) {
                writeCsvData(osw);
                osw.flush();

                new MaterialAlertDialogBuilder(currentContext)
                        .setTitle("Save Successful ")
                        .setMessage("Data saved as:\n" + fileName + "\n\nLocation:\n" + finalSaveLocationDescription)
                        .setPositiveButton("OK", null)
                        .show();

                Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                Uri contentUri = Uri.fromFile(csvFile);
                mediaScanIntent.setData(contentUri);
                currentContext.sendBroadcast(mediaScanIntent);
                return;

            } catch (IOException e) {
                Log.e("SaveCSV", "Error writing CSV file (pre-Q)", e);
                new MaterialAlertDialogBuilder(currentContext)
                        .setTitle("Save Error (App Specific)")
                        .setMessage("Could not save the file.\nError: " + e.getMessage())
                        .setPositiveButton("OK", null)
                        .show();
                return;
            }
        }

        ContentResolver resolver = currentContext.getContentResolver();
        Uri uri = null;
        OutputStream outputStream = null;

        try {
            uri = resolver.insert(collectionUri, values);
            if (uri == null) {
                throw new IOException("Failed to create new MediaStore record for CSV in " + customSubFolder);
            }
            outputStream = resolver.openOutputStream(uri);
            if (outputStream == null) {
                throw new IOException("Failed to get output stream for CSV in " + customSubFolder);
            }

            try (OutputStreamWriter osw = new OutputStreamWriter(outputStream)) {
                writeCsvData(osw);
                osw.flush();
            }

            Log.i("SaveCSV", "CSV file saved successfully via MediaStore to: " + finalSaveLocationDescription);
            Toast.makeText(this, "CSV file saved successfully via MediaStore to:" + finalSaveLocationDescription, Toast.LENGTH_LONG).show();
            new MaterialAlertDialogBuilder(currentContext)
                    .setTitle("Save Successful")
                    .setMessage("Data saved to:\n" + fileName + "\n\nLocation:\n" + Environment.DIRECTORY_DOCUMENTS + File.separator + customSubFolder + File.separator + fileName)
                    .setPositiveButton("OK", null)
                    .show();

        } catch (IOException e) {
            Log.e("SaveCSV", "Error writing CSV file via MediaStore to " + customSubFolder, e);
            if (uri != null) {
                resolver.delete(uri, null, null);
            }
            new MaterialAlertDialogBuilder(currentContext)
                    .setTitle("Save Error")
                    .setMessage("Could not save the file to " + Environment.DIRECTORY_DOCUMENTS + File.separator + customSubFolder + ".\nError: " + e.getMessage())
                    .setPositiveButton("OK", null)
                    .show();
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    Log.e("SaveCSV", "Error closing output stream for CSV to " + customSubFolder, e);
                }
            }
        }
    }
    private void writeCsvData(@NonNull OutputStreamWriter osw) throws IOException {
        osw.append("MpointTracker App \n");
        osw.append("Date");

        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
        String timestamp = sdf.format(new Date());
        osw.append(timestamp + "\n");
        osw.append("\n");
        osw.append("\n");
        osw.append("\n");

        int currentSeries = 1;
        for (ArrayList<Float> row : points) {
            StringBuilder rowString = new StringBuilder();

            rowString.append(currentSeries);

            for (Float value : row) {
                rowString.append(",");
                rowString.append(String.format(Locale.US, "%.6f", value));
            }

            rowString.append("\n");
            osw.append(rowString.toString());
        }
    }
}
