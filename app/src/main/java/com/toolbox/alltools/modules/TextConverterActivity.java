package com.toolbox.alltools.modules;

import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.toolbox.alltools.R;
import com.toolbox.alltools.base.BaseToolActivity;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文本格式转换器Activity
 * 支持JSON/XML/CSV/YAML/Base64/URL编解码等格式转换
 */
public class TextConverterActivity extends BaseToolActivity {

    /** 输入文本最大长度限制（1MB），防止OOM */
    private static final int MAX_INPUT_LENGTH = 1024 * 1024;

    /** 缩进最大深度限制，防止恶意输入导致无限缩进 */
    private static final int MAX_INDENT_DEPTH = 100;

    private Spinner spinnerConvertType;
    private EditText etInput;
    private EditText etOutput;
    private MaterialButton btnConvert;
    private MaterialButton btnCopy;

    private static final String[] CONVERT_TYPES = {
            "JSON 格式化",
            "JSON 压缩",
            "XML 格式化",
            "CSV 转 JSON",
            "Base64 编码",
            "Base64 解码",
            "URL 编码",
            "URL 解码",
            "JSON 转 CSV",
            "YAML 转 JSON"
    };

    @Override
    protected int getLayoutResId() {
        return R.layout.activity_text_converter;
    }

    @Override
    protected String getToolTitle() {
        return getString(R.string.title_text_converter);
    }

    @Override
    protected void initViews() {
        spinnerConvertType = findViewById(R.id.spinner_convert_type);
        etInput = findViewById(R.id.et_input);
        etOutput = findViewById(R.id.et_output);
        btnConvert = findViewById(R.id.btn_convert);
        btnCopy = findViewById(R.id.btn_copy);

        // 设置Spinner适配器
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, CONVERT_TYPES);
        spinnerAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        spinnerConvertType.setAdapter(spinnerAdapter);
    }

    @Override
    protected void initListeners() {
        // 转换按钮点击事件
        btnConvert.setOnClickListener(v -> convertText());

        // 复制按钮点击事件
        btnCopy.setOnClickListener(v -> copyResult());
    }

    @Override
    protected void initData() {
        // 无需额外初始化
    }

    /**
     * 执行文本转换
     */
    private void convertText() {
        String input = etInput.getText().toString().trim();
        if (TextUtils.isEmpty(input)) {
            Toast.makeText(this, R.string.msg_empty_input, Toast.LENGTH_SHORT).show();
            return;
        }

        // 防止超大输入导致OOM
        if (input.length() > MAX_INPUT_LENGTH) {
            Toast.makeText(this, "输入文本过长，请限制在1MB以内",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        String type = (String) spinnerConvertType.getSelectedItem();
        // 防御性空检查
        if (type == null) {
            type = CONVERT_TYPES[0];
        }
        String result;

        try {
            switch (type) {
                case "JSON 格式化":
                    result = formatJson(input);
                    break;
                case "JSON 压缩":
                    result = compressJson(input);
                    break;
                case "XML 格式化":
                    result = formatXml(input);
                    break;
                case "CSV 转 JSON":
                    result = csvToJson(input);
                    break;
                case "Base64 编码":
                    result = base64Encode(input);
                    break;
                case "Base64 解码":
                    result = base64Decode(input);
                    break;
                case "URL 编码":
                    result = urlEncode(input);
                    break;
                case "URL 解码":
                    result = urlDecode(input);
                    break;
                case "JSON 转 CSV":
                    result = jsonToCsv(input);
                    break;
                case "YAML 转 JSON":
                    result = yamlToJson(input);
                    break;
                default:
                    result = "不支持的转换类型";
            }
        } catch (Exception e) {
            result = "转换失败: " + e.getMessage();
        }

        etOutput.setText(result);
    }

    /**
     * JSON格式化（简单实现，添加缩进）
     * indent使用Math.max(0, ...)防止下溢导致负数缩进
     */
    private String formatJson(String input) {
        StringBuilder sb = new StringBuilder(input.length() + 256);
        int indent = 0;
        boolean inString = false;
        char prevChar = 0;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (c == '"' && prevChar != '\\') {
                inString = !inString;
            }

            if (!inString) {
                if (c == '{' || c == '[') {
                    sb.append(c).append('\n');
                    indent = Math.min(indent + 1, MAX_INDENT_DEPTH);
                    sb.append(getIndent(indent));
                } else if (c == '}' || c == ']') {
                    sb.append('\n');
                    indent = Math.max(0, indent - 1);
                    sb.append(getIndent(indent)).append(c);
                } else if (c == ',') {
                    sb.append(c).append('\n');
                    sb.append(getIndent(indent));
                } else if (c == ':') {
                    sb.append(c).append(' ');
                } else {
                    sb.append(c);
                }
            } else {
                sb.append(c);
            }
            prevChar = c;
        }
        return sb.toString();
    }

    /**
     * JSON压缩（移除多余空白）
     */
    private String compressJson(String input) {
        boolean inString = false;
        StringBuilder sb = new StringBuilder(input.length());
        char prevChar = 0;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (c == '"' && prevChar != '\\') {
                inString = !inString;
            }

            if (!inString && Character.isWhitespace(c)) {
                continue;
            }
            sb.append(c);
            prevChar = c;
        }
        return sb.toString();
    }

    /**
     * XML格式化（简单实现）
     * indent使用Math.max(0, ...)防止下溢
     */
    private String formatXml(String input) {
        StringBuilder sb = new StringBuilder(input.length() + 256);
        int indent = 0;
        String[] lines = input.replaceAll("><", ">\n<").split("\n");

        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("</")) {
                indent = Math.max(0, indent - 1);
            }
            sb.append(getIndent(indent)).append(line).append('\n');
            if (line.startsWith("<") && !line.startsWith("</")
                    && !line.startsWith("<?") && !line.endsWith("/>")
                    && !line.contains("</")) {
                indent = Math.min(indent + 1, MAX_INDENT_DEPTH);
            }
        }
        return sb.toString().trim();
    }

    /**
     * CSV转JSON（简单实现）
     */
    private String csvToJson(String input) {
        String[] lines = input.split("\n");
        if (lines.length < 2) {
            return "CSV数据至少需要包含标题行和一行数据";
        }

        String[] headers = lines[0].split(",");
        StringBuilder sb = new StringBuilder("[\n");

        for (int i = 1; i < lines.length; i++) {
            if (TextUtils.isEmpty(lines[i].trim())) continue;
            String[] values = lines[i].split(",");
            sb.append("  {\n");
            for (int j = 0; j < headers.length && j < values.length; j++) {
                sb.append("    \"").append(headers[j].trim()).append("\": \"")
                        .append(values[j].trim()).append("\"");
                if (j < headers.length - 1 && j < values.length - 1) {
                    sb.append(",");
                }
                sb.append("\n");
            }
            sb.append("  }");
            if (i < lines.length - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Base64编码
     */
    private String base64Encode(String input) {
        return android.util.Base64.encodeToString(
                input.getBytes(StandardCharsets.UTF_8),
                android.util.Base64.NO_WRAP);
    }

    /**
     * Base64解码
     */
    private String base64Decode(String input) {
        byte[] decoded = android.util.Base64.decode(input, android.util.Base64.DEFAULT);
        return new String(decoded, StandardCharsets.UTF_8);
    }

    /**
     * URL编码
     */
    private String urlEncode(String input) {
        try {
            return URLEncoder.encode(input, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            return "编码失败: " + e.getMessage();
        }
    }

    /**
     * URL解码
     */
    private String urlDecode(String input) {
        try {
            return URLDecoder.decode(input, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            return "解码失败: " + e.getMessage();
        }
    }

    /**
     * JSON转CSV（简单实现）
     */
    private String jsonToCsv(String input) {
        StringBuilder sb = new StringBuilder();
        Pattern keyPattern = Pattern.compile("\"([^\"]+)\":\\s*\"([^\"]+)\"");
        Pattern arrayPattern = Pattern.compile("\\{([^}]+)\\}");

        Matcher arrayMatcher = arrayPattern.matcher(input);
        boolean isFirst = true;
        while (arrayMatcher.find()) {
            String obj = arrayMatcher.group(1);
            Matcher keyMatcher = keyPattern.matcher(obj);
            while (keyMatcher.find()) {
                if (isFirst) {
                    sb.append(keyMatcher.group(1)).append(",");
                    isFirst = false;
                }
                sb.append(keyMatcher.group(2)).append(",");
            }
            sb.append("\n");
            isFirst = true;
        }
        return sb.toString().replaceAll(",\n", "\n").replaceAll(",$", "");
    }

    /**
     * YAML转JSON（简单实现，处理基本键值对）
     */
    private String yamlToJson(String input) {
        StringBuilder sb = new StringBuilder("{\n");
        String[] lines = input.split("\n");
        boolean first = true;

        for (String line : lines) {
            line = line.trim();
            if (TextUtils.isEmpty(line) || line.startsWith("#")) continue;

            int colonIndex = line.indexOf(':');
            if (colonIndex > 0) {
                String key = line.substring(0, colonIndex).trim();
                String value = line.substring(colonIndex + 1).trim();

                if (!first) {
                    sb.append(",\n");
                }
                sb.append("  \"").append(key).append("\": ");

                // 判断值类型
                if (value.equals("true") || value.equals("false")) {
                    sb.append(value);
                } else if (value.matches("-?\\d+(\\.\\d+)?")) {
                    sb.append(value);
                } else if (value.startsWith("[") || value.startsWith("{")) {
                    sb.append(value);
                } else {
                    sb.append("\"").append(value).append("\"");
                }
                first = false;
            }
        }
        sb.append("\n}");
        return sb.toString();
    }

    /**
     * 生成缩进字符串，level使用Math.max(0, ...)防止负数
     */
    private String getIndent(int level) {
        level = Math.max(0, Math.min(level, MAX_INDENT_DEPTH));
        StringBuilder sb = new StringBuilder(level * 2);
        for (int i = 0; i < level; i++) {
            sb.append("  ");
        }
        return sb.toString();
    }

    /**
     * 复制转换结果到剪贴板
     */
    private void copyResult() {
        String result = etOutput.getText().toString();
        if (TextUtils.isEmpty(result)) {
            Toast.makeText(this, R.string.msg_empty_input, Toast.LENGTH_SHORT).show();
            return;
        }

        android.content.ClipboardManager clipboard =
                (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        android.content.ClipData clip = android.content.ClipData.newPlainText(
                "转换结果", result);
        clipboard.setPrimaryClip(clip);

        Toast.makeText(this, R.string.msg_copied, Toast.LENGTH_SHORT).show();
    }
}
