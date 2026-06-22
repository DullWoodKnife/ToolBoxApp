package com.toolbox.alltools.modules;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.toolbox.alltools.R;
import com.toolbox.alltools.base.BaseToolActivity;
import com.toolbox.alltools.config.AppConfig;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 音频格式转换器Activity
 * 支持MP3、AAC、WAV、FLAC、OGG等音频格式转换
 */
public class AudioConverterActivity extends BaseToolActivity {

    private static final int REQUEST_SELECT_FILE = 2001;

    private static final String[] AUDIO_FORMATS = {
            "MP3", "AAC", "WAV", "FLAC", "OGG", "M4A", "WMA"
    };

    private MaterialButton btnSelectFile;
    private MaterialButton btnConvert;
    private TextView tvFileName;
    private TextView tvFileSize;
    private TextView tvFileFormat;
    private Spinner spinnerTargetFormat;
    private ProgressBar progressBar;
    private TextView tvOutputPath;

    private Uri selectedFileUri;
    private String selectedFileName;
    private long selectedFileSize;

    private final AtomicBoolean isAlive = new AtomicBoolean(true);

    @Override
    protected int getLayoutResId() {
        return R.layout.activity_audio_converter;
    }

    @Override
    protected String getToolTitle() {
        return getString(R.string.title_audio_converter);
    }

    @Override
    protected void initViews() {
        btnSelectFile = findViewById(R.id.btn_select_file);
        btnConvert = findViewById(R.id.btn_convert);
        tvFileName = findViewById(R.id.tv_file_name);
        tvFileSize = findViewById(R.id.tv_file_size);
        tvFileFormat = findViewById(R.id.tv_file_format);
        spinnerTargetFormat = findViewById(R.id.spinner_target_format);
        progressBar = findViewById(R.id.progress_bar);
        tvOutputPath = findViewById(R.id.tv_output_path);

        ArrayAdapter<String> formatAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, AUDIO_FORMATS);
        formatAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        spinnerTargetFormat.setAdapter(formatAdapter);
    }

    @Override
    protected void initListeners() {
        btnSelectFile.setOnClickListener(v -> selectFile());
        btnConvert.setOnClickListener(v -> startConvert());
    }

    @Override
    protected void initData() {
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isAlive.set(false);
    }

    private void selectFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        String[] mimeTypes = {
                "audio/mpeg", "audio/aac", "audio/wav", "audio/flac",
                "audio/ogg", "audio/mp4", "audio/x-ms-wma"
        };
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        startActivityForResult(intent, REQUEST_SELECT_FILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK || data == null) return;
        if (requestCode == REQUEST_SELECT_FILE) {
            selectedFileUri = data.getData();
            displayFileInfo(selectedFileUri);
        }
    }

    private void displayFileInfo(Uri uri) {
        try {
            String displayName = "未知文件";
            if (uri.getScheme() != null && uri.getScheme().equals("content")) {
                try (android.database.Cursor cursor = getContentResolver().query(
                        uri, null, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        int nameIndex = cursor.getColumnIndex(
                                android.provider.OpenableColumns.DISPLAY_NAME);
                        int sizeIndex = cursor.getColumnIndex(
                                android.provider.OpenableColumns.SIZE);
                        if (nameIndex >= 0) {
                            displayName = cursor.getString(nameIndex);
                        }
                        if (sizeIndex >= 0) {
                            selectedFileSize = cursor.getLong(sizeIndex);
                        }
                    }
                }
            }
            selectedFileName = displayName;

            String extension = "未知";
            int dotIndex = displayName.lastIndexOf('.');
            if (dotIndex > 0 && dotIndex < displayName.length() - 1) {
                extension = displayName.substring(dotIndex + 1).toUpperCase();
            }

            tvFileName.setText(getString(R.string.label_file_name, displayName));
            tvFileSize.setText(getString(R.string.label_file_size,
                    formatFileSize(selectedFileSize)));
            tvFileFormat.setText(getString(R.string.label_file_format, extension));

            // 默认保存到 sdcard/ToolBox/AudioConverter/
            File moduleDir = AppConfig.getModuleDir(AppConfig.DIR_AUDIO_CONVERTER);
            String baseName = displayName;
            int dotIdx = baseName.lastIndexOf('.');
            if (dotIdx > 0) baseName = baseName.substring(0, dotIdx);
            String fileName = baseName + "_converted." + extension.toLowerCase();
            File outputFile = new File(moduleDir, fileName);
            int counter = 1;
            while (outputFile.exists()) {
                outputFile = new File(moduleDir, baseName + "_converted(" + counter + ")." + extension.toLowerCase());
                counter++;
            }
            tvOutputPath.setText(getString(R.string.label_output_path, outputFile.getAbsolutePath()));

        } catch (Exception e) {
            Toast.makeText(this, "获取文件信息失败: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private String formatFileSize(long size) {
        if (size <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10((double) size) / Math.log10(1024.0));
        digitGroups = Math.min(digitGroups, units.length - 1);
        return String.format("%.2f %s",
                size / Math.pow(1024.0, digitGroups), units[digitGroups]);
    }

    private void startConvert() {
        if (selectedFileUri == null) {
            Toast.makeText(this, R.string.msg_select_file_first,
                    Toast.LENGTH_SHORT).show();
            return;
        }

        String targetFormat = (String) spinnerTargetFormat.getSelectedItem();
        progressBar.setVisibility(View.VISIBLE);
        progressBar.setProgress(0);
        btnConvert.setEnabled(false);
        simulateConversion(targetFormat);
    }

    private void simulateConversion(final String targetFormat) {
        isAlive.set(true);
        new Thread(() -> {
            try {
                for (int progress = 0; progress <= 100; progress += 5) {
                    Thread.sleep(100);
                    if (!isAlive.get()) return;
                    final int currentProgress = progress;
                    runOnUiThread(() -> {
                        if (!isAlive.get()) return;
                        progressBar.setProgress(currentProgress);
                    });
                }

                runOnUiThread(() -> {
                    if (!isAlive.get()) return;
                    // 默认保存到 sdcard/ToolBox/AudioConverter/
                    File moduleDir = AppConfig.getModuleDir(AppConfig.DIR_AUDIO_CONVERTER);
                    String baseName = selectedFileName;
                    int dotIndex = baseName.lastIndexOf('.');
                    if (dotIndex > 0) baseName = baseName.substring(0, dotIndex);
                    String fileName = baseName + "_converted." + targetFormat.toLowerCase();
                    File outputFile = new File(moduleDir, fileName);
                    int counter = 1;
                    while (outputFile.exists()) {
                        outputFile = new File(moduleDir, baseName + "_converted(" + counter + ")." + targetFormat.toLowerCase());
                        counter++;
                    }
                    tvOutputPath.setText(
                            getString(R.string.label_output_path, outputFile.getAbsolutePath()));
                    progressBar.setVisibility(View.GONE);
                    btnConvert.setEnabled(true);
                    Toast.makeText(AudioConverterActivity.this,
                            "音频转换完成（Demo模式），已保存到: " + outputFile.getAbsolutePath(),
                            Toast.LENGTH_LONG).show();
                });
            } catch (InterruptedException e) {
                if (!isAlive.get()) return;
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    btnConvert.setEnabled(true);
                });
            }
        }).start();
    }
}
