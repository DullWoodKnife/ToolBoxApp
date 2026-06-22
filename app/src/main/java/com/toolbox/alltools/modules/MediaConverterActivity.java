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

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 音视频格式转换器Activity
 * Demo阶段：展示UI和文件信息，转换功能预留接口
 */
public class MediaConverterActivity extends BaseToolActivity {

    private static final int REQUEST_SELECT_FILE = 2001;

    private static final String[] ALL_FORMATS = {
            "MP3", "AAC", "WAV", "FLAC", "OGG",
            "MP4", "AVI", "MKV", "MOV", "WebM"
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

    /** 标记Activity是否存活，防止线程在Activity销毁后操作UI */
    private final AtomicBoolean isAlive = new AtomicBoolean(true);

    @Override
    protected int getLayoutResId() {
        return R.layout.activity_media_converter;
    }

    @Override
    protected String getToolTitle() {
        return getString(R.string.title_media_converter);
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

        // 设置目标格式Spinner
        ArrayAdapter<String> formatAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, ALL_FORMATS);
        formatAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        spinnerTargetFormat.setAdapter(formatAdapter);
    }

    @Override
    protected void initListeners() {
        // 选择文件按钮
        btnSelectFile.setOnClickListener(v -> selectFile());

        // 转换按钮
        btnConvert.setOnClickListener(v -> startConvert());
    }

    @Override
    protected void initData() {
        // 无需额外初始化
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isAlive.set(false);
    }

    /**
     * 打开文件选择器
     */
    private void selectFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        String[] mimeTypes = {
                "audio/*", "video/*",
                "audio/mpeg", "audio/aac", "audio/wav", "audio/flac", "audio/ogg",
                "video/mp4", "video/avi", "video/x-matroska",
                "video/quicktime", "video/webm"
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

    /**
     * 显示文件信息
     */
    private void displayFileInfo(Uri uri) {
        try {
            // 获取文件名
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

            // 获取文件扩展名
            String extension = "未知";
            int dotIndex = displayName.lastIndexOf('.');
            if (dotIndex > 0 && dotIndex < displayName.length() - 1) {
                extension = displayName.substring(dotIndex + 1).toUpperCase();
            }

            // 更新UI
            tvFileName.setText(getString(R.string.label_file_name, displayName));
            tvFileSize.setText(getString(R.string.label_file_size,
                    formatFileSize(selectedFileSize)));
            tvFileFormat.setText(getString(R.string.label_file_format, extension));

            // 设置默认输出路径
            String outputPath = "/sdcard/Download/converted_" +
                    System.currentTimeMillis() + "." +
                    extension.toLowerCase();
            tvOutputPath.setText(getString(R.string.label_output_path, outputPath));

        } catch (Exception e) {
            Toast.makeText(this, "获取文件信息失败: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 格式化文件大小（修复size=0时log10(0)=NaN的崩溃问题）
     */
    private String formatFileSize(long size) {
        if (size <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10((double) size) / Math.log10(1024.0));
        digitGroups = Math.min(digitGroups, units.length - 1); // 防止数组越界
        return String.format("%.2f %s",
                size / Math.pow(1024.0, digitGroups), units[digitGroups]);
    }

    /**
     * 开始转换（Demo阶段模拟）
     */
    private void startConvert() {
        if (selectedFileUri == null) {
            Toast.makeText(this, R.string.msg_select_file_first,
                    Toast.LENGTH_SHORT).show();
            return;
        }

        String targetFormat = (String) spinnerTargetFormat.getSelectedItem();

        // 显示进度条
        progressBar.setVisibility(View.VISIBLE);
        progressBar.setProgress(0);
        btnConvert.setEnabled(false);

        // 模拟转换进度
        simulateConversion(targetFormat);
    }

    /**
     * 模拟转换过程（Demo阶段）
     * 使用isAlive标记防止Activity销毁后操作UI导致崩溃
     */
    private void simulateConversion(final String targetFormat) {
        isAlive.set(true);
        new Thread(() -> {
            try {
                for (int progress = 0; progress <= 100; progress += 5) {
                    Thread.sleep(100);
                    if (!isAlive.get()) return; // Activity已销毁，停止线程
                    final int currentProgress = progress;
                    runOnUiThread(() -> {
                        if (!isAlive.get()) return;
                        progressBar.setProgress(currentProgress);
                    });
                }

                runOnUiThread(() -> {
                    if (!isAlive.get()) return;
                    String outputPath = "/sdcard/Download/converted_" +
                            System.currentTimeMillis() + "." +
                            targetFormat.toLowerCase();
                    tvOutputPath.setText(
                            getString(R.string.label_output_path, outputPath));
                    progressBar.setVisibility(View.GONE);
                    btnConvert.setEnabled(true);
                    Toast.makeText(MediaConverterActivity.this,
                            "转换完成（Demo模式，实际转换功能待实现）",
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
