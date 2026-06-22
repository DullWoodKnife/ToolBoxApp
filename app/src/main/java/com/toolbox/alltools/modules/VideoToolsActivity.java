package com.toolbox.alltools.modules;

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

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 视频工具Activity
 * 支持视频格式转换、视频信息查看等视频相关功能
 */
public class VideoToolsActivity extends BaseToolActivity {

    private static final int REQUEST_SELECT_FILE = 3001;

    private static final String[] VIDEO_FORMATS = {
            "MP4", "AVI", "MKV", "MOV", "WebM", "FLV", "WMV", "VDAT"
    };

    private MaterialButton btnSelectFile;
    private MaterialButton btnConvert;
    private TextView tvFileName;
    private TextView tvFileSize;
    private TextView tvFileFormat;
    private TextView tvVideoInfo;
    private Spinner spinnerTargetFormat;
    private ProgressBar progressBar;
    private TextView tvOutputPath;

    private Uri selectedFileUri;
    private String selectedFileName;
    private long selectedFileSize;

    private final AtomicBoolean isAlive = new AtomicBoolean(true);

    @Override
    protected int getLayoutResId() {
        return R.layout.activity_video_tools;
    }

    @Override
    protected String getToolTitle() {
        return getString(R.string.title_video_tools);
    }

    @Override
    protected void initViews() {
        btnSelectFile = findViewById(R.id.btn_select_file);
        btnConvert = findViewById(R.id.btn_convert);
        tvFileName = findViewById(R.id.tv_file_name);
        tvFileSize = findViewById(R.id.tv_file_size);
        tvFileFormat = findViewById(R.id.tv_file_format);
        tvVideoInfo = findViewById(R.id.tv_video_info);
        spinnerTargetFormat = findViewById(R.id.spinner_target_format);
        progressBar = findViewById(R.id.progress_bar);
        tvOutputPath = findViewById(R.id.tv_output_path);

        ArrayAdapter<String> formatAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, VIDEO_FORMATS);
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
        intent.setType("video/*");
        String[] mimeTypes = {
                "video/mp4", "video/avi", "video/x-matroska",
                "video/quicktime", "video/webm", "video/x-flv", "video/x-ms-wmv",
                "application/octet-stream"
        };
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        startActivityForResult(intent, REQUEST_SELECT_FILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
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
            tvVideoInfo.setText("分辨率: 待检测 | 时长: 待检测 | 码率: 待检测");

            String outputPath = "/sdcard/Download/converted_video_" +
                    System.currentTimeMillis() + "." +
                    extension.toLowerCase();
            tvOutputPath.setText(getString(R.string.label_output_path, outputPath));

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
                for (int progress = 0; progress <= 100; progress += 2) {
                    Thread.sleep(200);
                    if (!isAlive.get()) return;
                    final int currentProgress = progress;
                    runOnUiThread(() -> {
                        if (!isAlive.get()) return;
                        progressBar.setProgress(currentProgress);
                    });
                }

                runOnUiThread(() -> {
                    if (!isAlive.get()) return;
                    String outputPath = "/sdcard/Download/converted_video_" +
                            System.currentTimeMillis() + "." +
                            targetFormat.toLowerCase();
                    tvOutputPath.setText(
                            getString(R.string.label_output_path, outputPath));
                    progressBar.setVisibility(View.GONE);
                    btnConvert.setEnabled(true);
                    Toast.makeText(VideoToolsActivity.this,
                            "视频转换完成（Demo模式）",
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
