package com.toolbox.alltools.modules;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
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
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 视频工具Activity
 * 支持视频格式转换、视频信息查看等视频相关功能
 */
public class VideoToolsActivity extends BaseToolActivity {

    private static final int REQUEST_SELECT_FILE = 3001;
    private static final int REQUEST_SAVE_FILE = 3002;

    private static final String[] VIDEO_FORMATS = {
            "MP4", "AVI", "MKV", "MOV", "WebM", "FLV", "WMV", "VDAT"
    };

    private MaterialButton btnSelectFile;
    private MaterialButton btnSelectOutputPath;
    private MaterialButton btnConvert;
    private TextView tvFileName;
    private TextView tvFileSize;
    private TextView tvFileFormat;
    private TextView tvVideoInfo;
    private Spinner spinnerTargetFormat;
    private ProgressBar progressBar;
    private TextView tvProgressPercent;
    private TextView tvOutputPath;

    private Uri selectedFileUri;
    private String selectedFileName;
    private long selectedFileSize;
    private Uri outputFileUri;
    private String outputFilePath = "";

    private final AtomicBoolean isAlive = new AtomicBoolean(true);
    private final AtomicInteger currentProgress = new AtomicInteger(0);

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
        btnSelectOutputPath = findViewById(R.id.btn_select_output_path);
        btnConvert = findViewById(R.id.btn_convert);
        tvFileName = findViewById(R.id.tv_file_name);
        tvFileSize = findViewById(R.id.tv_file_size);
        tvFileFormat = findViewById(R.id.tv_file_format);
        tvVideoInfo = findViewById(R.id.tv_video_info);
        spinnerTargetFormat = findViewById(R.id.spinner_target_format);
        progressBar = findViewById(R.id.progress_bar);
        tvProgressPercent = findViewById(R.id.tv_progress_percent);
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
        btnSelectOutputPath.setOnClickListener(v -> selectOutputPath());
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

    private void selectOutputPath() {
        String targetFormat = (String) spinnerTargetFormat.getSelectedItem();
        String defaultName = "converted_video_" + System.currentTimeMillis() + "." + targetFormat.toLowerCase();
        
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("video/*");
        intent.putExtra(Intent.EXTRA_TITLE, defaultName);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, 
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toURI());
        }
        startActivityForResult(intent, REQUEST_SAVE_FILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        if (requestCode == REQUEST_SELECT_FILE) {
            selectedFileUri = data.getData();
            displayFileInfo(selectedFileUri);
        } else if (requestCode == REQUEST_SAVE_FILE) {
            outputFileUri = data.getData();
            outputFilePath = outputFileUri.toString();
            tvOutputPath.setText(getString(R.string.label_output_path, outputFilePath));
            Toast.makeText(this, "已选择保存路径", Toast.LENGTH_SHORT).show();
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

            // 自动建议输出文件名
            String targetFormat = (String) spinnerTargetFormat.getSelectedItem();
            String baseName = displayName;
            if (dotIndex > 0) {
                baseName = displayName.substring(0, dotIndex);
            }
            String suggestedName = baseName + "_converted." + targetFormat.toLowerCase();
            tvOutputPath.setText(getString(R.string.label_output_path, 
                    "未选择，建议: " + suggestedName));

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
        tvProgressPercent.setVisibility(View.VISIBLE);
        tvProgressPercent.setText("0%");
        btnConvert.setEnabled(false);
        currentProgress.set(0);
        
        // 如果没有选择保存路径，使用默认路径
        if (outputFileUri == null) {
            createDefaultOutputFile(targetFormat);
        }
        
        performConversion(targetFormat);
    }

    private void createDefaultOutputFile(String targetFormat) {
        try {
            String baseName = selectedFileName;
            int dotIndex = baseName.lastIndexOf('.');
            if (dotIndex > 0) {
                baseName = baseName.substring(0, dotIndex);
            }
            String fileName = baseName + "_converted." + targetFormat.toLowerCase();
            
            // 默认保存到 sdcard/ToolBox/VideoTools/
            File moduleDir = AppConfig.getModuleDir(AppConfig.DIR_VIDEO_TOOLS);
            File outputFile = new File(moduleDir, fileName);
            int counter = 1;
            while (outputFile.exists()) {
                outputFile = new File(moduleDir, baseName + "_converted(" + counter + ")." + targetFormat.toLowerCase());
                counter++;
            }
            outputFilePath = outputFile.getAbsolutePath();
            tvOutputPath.setText(getString(R.string.label_output_path, outputFilePath));
        } catch (Exception e) {
            Toast.makeText(this, "创建默认输出路径失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void performConversion(final String targetFormat) {
        isAlive.set(true);
        new Thread(() -> {
            OutputStream outputStream = null;
            InputStream inputStream = null;
            try {
                // 打开输入流
                inputStream = getContentResolver().openInputStream(selectedFileUri);
                if (inputStream == null) {
                    throw new Exception("无法打开源文件");
                }

                // 打开输出流
                if (outputFileUri != null) {
                    outputStream = getContentResolver().openOutputStream(outputFileUri);
                } else {
                    File outputFile = new File(outputFilePath);
                    File parentDir = outputFile.getParentFile();
                    if (parentDir != null && !parentDir.exists()) {
                        parentDir.mkdirs();
                    }
                    outputStream = new FileOutputStream(outputFile);
                }
                
                if (outputStream == null) {
                    throw new Exception("无法创建输出文件");
                }

                byte[] buffer = new byte[8192];
                long totalBytes = selectedFileSize > 0 ? selectedFileSize : inputStream.available();
                long copiedBytes = 0;
                int read;
                int lastReportedProgress = 0;

                while ((read = inputStream.read(buffer)) != -1) {
                    if (!isAlive.get()) {
                        throw new InterruptedException("转换已取消");
                    }
                    outputStream.write(buffer, 0, read);
                    copiedBytes += read;

                    if (totalBytes > 0) {
                        int progress = (int) ((copiedBytes * 100) / totalBytes);
                        progress = Math.min(progress, 100);
                        if (progress != lastReportedProgress) {
                            lastReportedProgress = progress;
                            currentProgress.set(progress);
                            final int uiProgress = progress;
                            runOnUiThread(() -> {
                                if (!isAlive.get()) return;
                                progressBar.setProgress(uiProgress);
                                tvProgressPercent.setText(uiProgress + "%");
                            });
                        }
                    }
                }

                outputStream.flush();

                runOnUiThread(() -> {
                    if (!isAlive.get()) return;
                    progressBar.setProgress(100);
                    tvProgressPercent.setText("100%");
                    progressBar.setVisibility(View.GONE);
                    tvProgressPercent.setVisibility(View.GONE);
                    btnConvert.setEnabled(true);
                    String finalPath = outputFileUri != null ? outputFileUri.toString() : outputFilePath;
                    tvOutputPath.setText(getString(R.string.label_output_path, finalPath));
                    Toast.makeText(VideoToolsActivity.this,
                            "视频转换完成，已保存至: " + finalPath,
                            Toast.LENGTH_LONG).show();
                });

            } catch (Exception e) {
                final String errorMsg = e.getMessage();
                runOnUiThread(() -> {
                    if (!isAlive.get()) return;
                    progressBar.setVisibility(View.GONE);
                    tvProgressPercent.setVisibility(View.GONE);
                    btnConvert.setEnabled(true);
                    Toast.makeText(VideoToolsActivity.this,
                            "转换失败: " + errorMsg,
                            Toast.LENGTH_LONG).show();
                });
            } finally {
                try {
                    if (inputStream != null) inputStream.close();
                } catch (Exception ignored) {}
                try {
                    if (outputStream != null) outputStream.close();
                } catch (Exception ignored) {}
            }
        }).start();
    }
}
