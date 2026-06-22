package com.toolbox.alltools.modules;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.toolbox.alltools.R;
import com.toolbox.alltools.base.BaseToolActivity;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * 文本阅读编辑器Activity
 * 支持打开、编辑、保存文本文件，自动检测文件编码，实时统计字数和行数
 */
public class TextEditorActivity extends BaseToolActivity {

    private static final int REQUEST_OPEN_FILE = 1001;
    private static final int REQUEST_SAVE_FILE = 1002;

    /** 可打开文件的最大大小（5MB），防止OOM */
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;

    /** 支持的编码列表，按优先级排序 */
    private static final List<String> SUPPORTED_ENCODINGS = Arrays.asList(
            "UTF-8",
            "GB18030",
            "GBK",
            "GB2312",
            "UTF-16",
            "UTF-16BE",
            "UTF-16LE",
            "ISO-8859-1",
            "windows-1252"
    );

    private EditText etEditor;
    private TextView tvWordCount;
    private TextView tvLineCount;
    private ImageButton btnOpenFile;
    private ImageButton btnNewFile;
    private ImageButton btnSearch;
    private ImageButton btnSave;
    private Spinner spinnerEncoding;

    private Uri currentFileUri;
    /** 当前文件检测到的编码 */
    private String detectedEncoding = "UTF-8";

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
        spinnerEncoding = findViewById(R.id.spinner_encoding);

        // 设置编码选择Spinner
        ArrayAdapter<String> encodingAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, SUPPORTED_ENCODINGS);
        encodingAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        spinnerEncoding.setAdapter(encodingAdapter);

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
        btnOpenFile.setOnClickListener(v -> openFile());
        btnNewFile.setOnClickListener(v -> newFile());
        btnSearch.setOnClickListener(v -> searchInEditor());
        btnSave.setOnClickListener(v -> saveFile());
    }

    @Override
    protected void initData() {
        updateStats();
    }

    private void updateStats() {
        String text = etEditor.getText().toString();
        int wordCount = text.length();
        tvWordCount.setText(getString(R.string.label_word_count, wordCount));
        int lineCount = text.isEmpty() ? 0 : text.split("\n", -1).length;
        tvLineCount.setText(getString(R.string.label_line_count, lineCount));
    }

    private void openFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "text/plain", "text/html", "text/css", "text/javascript",
                "application/json", "application/xml", "text/xml"
        });
        startActivityForResult(intent, REQUEST_OPEN_FILE);
    }

    private void newFile() {
        etEditor.setText("");
        currentFileUri = null;
        detectedEncoding = "UTF-8";
        spinnerEncoding.setSelection(0);
        Toast.makeText(this, "已新建文件", Toast.LENGTH_SHORT).show();
        updateStats();
    }

    private void searchInEditor() {
        String text = etEditor.getText().toString();
        if (text.isEmpty()) {
            Toast.makeText(this, "没有可搜索的内容", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "搜索功能（可扩展）", Toast.LENGTH_SHORT).show();
    }

    private void saveFile() {
        if (currentFileUri != null) {
            saveToFile(currentFileUri);
        } else {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TITLE, "untitled.txt");
            startActivityForResult(intent, REQUEST_SAVE_FILE);
        }
    }

    /**
     * 保存文件，使用用户选择的编码或默认UTF-8
     */
    private void saveToFile(Uri uri) {
        String encoding = (String) spinnerEncoding.getSelectedItem();
        if (encoding == null) {
            encoding = "UTF-8";
        }
        try (OutputStream outputStream = getContentResolver().openOutputStream(uri)) {
            if (outputStream != null) {
                OutputStreamWriter writer = new OutputStreamWriter(
                        outputStream, Charset.forName(encoding));
                writer.write(etEditor.getText().toString());
                writer.flush();
                Toast.makeText(this,
                        getString(R.string.msg_file_saved) + " (" + encoding + ")",
                        Toast.LENGTH_SHORT).show();
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
     * 读取文件内容，自动检测编码
     */
    private void readFileContent(Uri uri) {
        try {
            long fileSize = getFileSize(uri);
            if (fileSize > MAX_FILE_SIZE) {
                Toast.makeText(this, "文件过大（超过5MB），请选择较小的文件",
                        Toast.LENGTH_LONG).show();
                return;
            }

            // 第一步：读取文件原始字节用于编码检测
            byte[] fileBytes = readFileBytes(uri, (int) Math.min(fileSize, 64 * 1024));
            if (fileBytes == null || fileBytes.length == 0) {
                Toast.makeText(this, "文件为空或无法读取", Toast.LENGTH_SHORT).show();
                return;
            }

            // 第二步：自动检测编码
            detectedEncoding = detectEncoding(fileBytes);

            // 第三步：使用检测到的编码读取文件内容
            String content = readWithEncoding(uri, detectedEncoding);

            // 第四步：更新UI
            etEditor.setText(content);
            updateEncodingSpinner(detectedEncoding);
            updateStats();
            Toast.makeText(this,
                    "文件已打开 (编码: " + detectedEncoding + ")",
                    Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Toast.makeText(this, "打开文件失败: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 读取文件原始字节（用于编码检测）
     */
    private byte[] readFileBytes(Uri uri, int maxBytes) {
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            if (is == null) return null;
            BufferedInputStream bis = new BufferedInputStream(is);
            byte[] buffer = new byte[maxBytes];
            int read = bis.read(buffer);
            if (read <= 0) return new byte[0];
            return Arrays.copyOf(buffer, read);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 自动检测文件编码
     * 检测顺序：BOM -> UTF-8有效性 -> GB18030有效性 -> 默认UTF-8
     */
    private String detectEncoding(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "UTF-8";
        }

        // 1. 检测BOM (Byte Order Mark)
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF) {
            return "UTF-8";
        }
        if (bytes.length >= 2
                && (bytes[0] & 0xFF) == 0xFE
                && (bytes[1] & 0xFF) == 0xFF) {
            return "UTF-16BE";
        }
        if (bytes.length >= 2
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xFE) {
            return "UTF-16LE";
        }

        // 2. 尝试UTF-8解码验证
        if (isValidUtf8(bytes)) {
            return "UTF-8";
        }

        // 3. 尝试GB18030解码验证（兼容GBK和GB2312）
        if (isValidEncoding(bytes, "GB18030")) {
            return "GB18030";
        }

        // 4. 尝试GBK
        if (isValidEncoding(bytes, "GBK")) {
            return "GBK";
        }

        // 5. 尝试GB2312
        if (isValidEncoding(bytes, "GB2312")) {
            return "GB2312";
        }

        // 6. 回退到UTF-8（即使可能乱码，也是最佳尝试）
        return "UTF-8";
    }

    /**
     * 验证字节数组是否为有效的UTF-8编码
     */
    private boolean isValidUtf8(byte[] bytes) {
        int i = 0;
        while (i < bytes.length) {
            int b = bytes[i] & 0xFF;

            // ASCII (0xxxxxxx)
            if (b < 0x80) {
                i++;
                continue;
            }

            // 2字节序列 (110xxxxx 10xxxxxx)
            if ((b & 0xE0) == 0xC0) {
                if (i + 1 >= bytes.length) return false;
                if ((bytes[i + 1] & 0xC0) != 0x80) return false;
                i += 2;
                continue;
            }

            // 3字节序列 (1110xxxx 10xxxxxx 10xxxxxx)
            if ((b & 0xF0) == 0xE0) {
                if (i + 2 >= bytes.length) return false;
                if ((bytes[i + 1] & 0xC0) != 0x80) return false;
                if ((bytes[i + 2] & 0xC0) != 0x80) return false;
                i += 3;
                continue;
            }

            // 4字节序列 (11110xxx 10xxxxxx 10xxxxxx 10xxxxxx)
            if ((b & 0xF8) == 0xF0) {
                if (i + 3 >= bytes.length) return false;
                if ((bytes[i + 1] & 0xC0) != 0x80) return false;
                if ((bytes[i + 2] & 0xC0) != 0x80) return false;
                if ((bytes[i + 3] & 0xC0) != 0x80) return false;
                i += 4;
                continue;
            }

            // 无效的UTF-8起始字节
            return false;
        }
        return true;
    }

    /**
     * 验证字节数组是否可以用指定编码正确解码
     */
    private boolean isValidEncoding(byte[] bytes, String encoding) {
        try {
            String decoded = new String(bytes, Charset.forName(encoding));
            byte[] reEncoded = decoded.getBytes(Charset.forName(encoding));
            // 如果重新编码后与原始字节一致，说明编码正确
            return Arrays.equals(bytes, reEncoded);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 使用指定编码读取文件完整内容
     */
    private String readWithEncoding(Uri uri, String encoding) {
        StringBuilder sb = new StringBuilder();
        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
            if (inputStream != null) {
                // 跳过BOM（如果存在）
                if ("UTF-8".equals(encoding)) {
                    inputStream.mark(3);
                    byte[] bom = new byte[3];
                    int read = inputStream.read(bom);
                    if (read < 3 || bom[0] != (byte) 0xEF
                            || bom[1] != (byte) 0xBB
                            || bom[2] != (byte) 0xBF) {
                        inputStream.reset();
                    }
                }

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(inputStream, Charset.forName(encoding)));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (sb.length() > 0) {
                        sb.append("\n");
                    }
                    sb.append(line);
                    if (sb.length() > MAX_FILE_SIZE) {
                        break;
                    }
                }
            }
        } catch (Exception e) {
            return "";
        }
        return sb.toString();
    }

    /**
     * 更新编码Spinner的选中项
     */
    private void updateEncodingSpinner(String encoding) {
        int index = SUPPORTED_ENCODINGS.indexOf(encoding);
        if (index >= 0) {
            spinnerEncoding.setSelection(index);
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
        return -1;
    }
}
