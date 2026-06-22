package com.toolbox.alltools.modules;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.toolbox.alltools.R;
import com.toolbox.alltools.base.BaseToolActivity;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

/**
 * 文本阅读编辑器Activity
 * 支持打开、编辑、保存文本文件，实时统计字数和行数
 */
public class TextEditorActivity extends BaseToolActivity {

    private static final int REQUEST_OPEN_FILE = 1001;
    private static final int REQUEST_SAVE_FILE = 1002;

    /** 可打开文件的最大大小（5MB），防止OOM */
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;

    private EditText etEditor;
    private TextView tvWordCount;
    private TextView tvLineCount;
    private ImageButton btnOpenFile;
    private ImageButton btnNewFile;
    private ImageButton btnSearch;
    private ImageButton btnSave;

    private Uri currentFileUri;

    @Override
    protected int getLayoutResId() {
        return R.layout.activity_text_editor;
    }

    @Override
    protected String getToolTitle() {
        return getString(R.string.title_text_editor);
    }

    @Override
    protected void initViews() {
        etEditor = findViewById(R.id.et_editor);
        tvWordCount = findViewById(R.id.tv_word_count);
        tvLineCount = findViewById(R.id.tv_line_count);
        btnOpenFile = findViewById(R.id.btn_open_file);
        btnNewFile = findViewById(R.id.btn_new_file);
        btnSearch = findViewById(R.id.btn_search);
        btnSave = findViewById(R.id.btn_save);

        // 监听文本变化，实时更新字数和行数
        etEditor.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateStats();
            }
        });
    }

    @Override
    protected void initListeners() {
        // 打开文件按钮
        btnOpenFile.setOnClickListener(v -> openFile());

        // 新建文件按钮
        btnNewFile.setOnClickListener(v -> newFile());

        // 搜索按钮
        btnSearch.setOnClickListener(v -> searchInEditor());

        // 保存按钮
        btnSave.setOnClickListener(v -> saveFile());
    }

    @Override
    protected void initData() {
        updateStats();
    }

    /**
     * 更新字数和行数统计
     */
    private void updateStats() {
        String text = etEditor.getText().toString();

        // 字数统计
        int wordCount = text.length();
        tvWordCount.setText(getString(R.string.label_word_count, wordCount));

        // 行数统计
        int lineCount = text.isEmpty() ? 0 : text.split("\n", -1).length;
        tvLineCount.setText(getString(R.string.label_line_count, lineCount));
    }

    /**
     * 打开文件选择器
     */
    private void openFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "text/plain",
                "text/html",
                "text/css",
                "text/javascript",
                "application/json",
                "application/xml",
                "text/xml"
        });
        startActivityForResult(intent, REQUEST_OPEN_FILE);
    }

    /**
     * 新建文件
     */
    private void newFile() {
        etEditor.setText("");
        currentFileUri = null;
        Toast.makeText(this, "已新建文件", Toast.LENGTH_SHORT).show();
        updateStats();
    }

    /**
     * 搜索功能（简单实现：高亮搜索文本）
     */
    private void searchInEditor() {
        String text = etEditor.getText().toString();
        if (text.isEmpty()) {
            Toast.makeText(this, "没有可搜索的内容", Toast.LENGTH_SHORT).show();
            return;
        }
        // 简单搜索：将光标移动到第一个匹配位置
        // 完整搜索功能可使用SearchView或自定义对话框
        Toast.makeText(this, "搜索功能（可扩展）", Toast.LENGTH_SHORT).show();
    }

    /**
     * 保存文件
     */
    private void saveFile() {
        if (currentFileUri != null) {
            // 覆盖已有文件
            saveToFile(currentFileUri);
        } else {
            // 创建新文件
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TITLE, "untitled.txt");
            startActivityForResult(intent, REQUEST_SAVE_FILE);
        }
    }

    /**
     * 将内容写入文件（使用try-with-resources确保流关闭）
     */
    private void saveToFile(Uri uri) {
        try (OutputStream outputStream = getContentResolver().openOutputStream(uri)) {
            if (outputStream != null) {
                OutputStreamWriter writer = new OutputStreamWriter(outputStream, "UTF-8");
                writer.write(etEditor.getText().toString());
                writer.flush();
                Toast.makeText(this, R.string.msg_file_saved, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "保存失败: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != Activity.RESULT_OK || data == null) return;

        if (requestCode == REQUEST_OPEN_FILE) {
            currentFileUri = data.getData();
            readFileContent(currentFileUri);
        } else if (requestCode == REQUEST_SAVE_FILE) {
            currentFileUri = data.getData();
            saveToFile(currentFileUri);
        }
    }

    /**
     * 读取文件内容（带文件大小检查，使用try-with-resources确保流关闭）
     */
    private void readFileContent(Uri uri) {
        try {
            // 先检查文件大小
            long fileSize = getFileSize(uri);
            if (fileSize > MAX_FILE_SIZE) {
                Toast.makeText(this,
                        "文件过大（超过5MB），请选择较小的文件",
                        Toast.LENGTH_LONG).show();
                return;
            }

            try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
                if (inputStream != null) {
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(inputStream, "UTF-8"));
                    StringBuilder sb = new StringBuilder(
                            fileSize > 0 ? (int) fileSize + 256 : 4096);
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (sb.length() > 0) {
                            sb.append("\n");
                        }
                        sb.append(line);
                        // 二次安全阀：即使文件头报告大小正确，也限制读取量
                        if (sb.length() > MAX_FILE_SIZE) {
                            Toast.makeText(this, "文件内容过大，已截断读取",
                                    Toast.LENGTH_SHORT).show();
                            break;
                        }
                    }

                    etEditor.setText(sb.toString());
                    updateStats();
                    Toast.makeText(this, "文件已打开", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "打开文件失败: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 获取文件大小
     */
    private long getFileSize(Uri uri) {
        try (android.database.Cursor cursor = getContentResolver().query(
                uri, new String[]{android.provider.OpenableColumns.SIZE},
                null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int sizeIndex = cursor.getColumnIndex(
                        android.provider.OpenableColumns.SIZE);
                if (sizeIndex >= 0) {
                    return cursor.getLong(sizeIndex);
                }
            }
        } catch (Exception e) {
            // 忽略查询异常
        }
        return -1; // 未知大小
    }
}
