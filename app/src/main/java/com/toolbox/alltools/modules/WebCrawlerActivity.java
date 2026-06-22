package com.toolbox.alltools.modules;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.toolbox.alltools.R;
import com.toolbox.alltools.base.BaseToolActivity;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 爬虫工具箱Activity
 * 使用OkHttp发送HTTP请求，使用Jsoup解析HTML
 */
public class WebCrawlerActivity extends BaseToolActivity {

    private static final int REQUEST_SAVE_RESULT = 3001;

    /** 响应体最大读取大小（2MB），防止OOM */
    private static final long MAX_RESPONSE_SIZE = 2 * 1024 * 1024;

    private static final String[] REQUEST_METHODS = {"GET", "POST"};

    private EditText etUrl;
    private Spinner spinnerMethod;
    private EditText etHeaders;
    private EditText etBody;
    private MaterialButton btnSend;
    private TextView tvStatusCode;
    private TextView tvResponse;
    private MaterialButton btnSaveResult;

    private View tvBodyLabel;

    private OkHttpClient httpClient;

    /** 使用WeakReference-safe的Handler，在onDestroy中清理回调 */
    private Handler uiHandler;

    @Override
    protected int getLayoutResId() {
        return R.layout.activity_web_crawler;
    }

    @Override
    protected String getToolTitle() {
        return getString(R.string.title_web_crawler);
    }

    @Override
    protected void initViews() {
        etUrl = findViewById(R.id.et_url);
        spinnerMethod = findViewById(R.id.spinner_method);
        etHeaders = findViewById(R.id.et_headers);
        etBody = findViewById(R.id.et_body);
        btnSend = findViewById(R.id.btn_send);
        tvStatusCode = findViewById(R.id.tv_status_code);
        tvResponse = findViewById(R.id.tv_response);
        btnSaveResult = findViewById(R.id.btn_save_result);
        tvBodyLabel = findViewById(R.id.tv_body_label);

        // 设置请求方法Spinner
        ArrayAdapter<String> methodAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, REQUEST_METHODS);
        methodAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        spinnerMethod.setAdapter(methodAdapter);

        // 初始化OkHttp客户端
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .build();

        // 初始化Handler
        uiHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    protected void initListeners() {
        // 监听请求方法变化，POST时显示请求体编辑区域
        spinnerMethod.setOnItemSelectedListener(
                new android.widget.AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(android.widget.AdapterView<?> parent,
                            View view, int position, long id) {
                        String method = (String) parent.getItemAtPosition(position);
                        if ("POST".equals(method)) {
                            tvBodyLabel.setVisibility(View.VISIBLE);
                            etBody.setVisibility(View.VISIBLE);
                        } else {
                            tvBodyLabel.setVisibility(View.GONE);
                            etBody.setVisibility(View.GONE);
                        }
                    }

                    @Override
                    public void onNothingSelected(android.widget.AdapterView<?> parent) {
                    }
                });

        // 发送请求按钮
        btnSend.setOnClickListener(v -> sendRequest());

        // 保存结果按钮
        btnSaveResult.setOnClickListener(v -> saveResult());
    }

    @Override
    protected void initData() {
        // 无需额外初始化
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 清除Handler中所有待执行的回调，防止Activity销毁后操作UI
        if (uiHandler != null) {
            uiHandler.removeCallbacksAndMessages(null);
        }
    }

    /**
     * 安全地在主线程执行Runnable，Activity销毁后不执行
     */
    private void runOnUiThreadSafe(Runnable runnable) {
        if (isFinishing() || isDestroyed()) return;
        if (uiHandler != null) {
            uiHandler.post(runnable);
        }
    }

    /**
     * 发送HTTP请求
     */
    private void sendRequest() {
        String url = etUrl.getText().toString().trim();
        if (TextUtils.isEmpty(url)) {
            Toast.makeText(this, "请输入URL", Toast.LENGTH_SHORT).show();
            return;
        }

        // 确保URL有协议前缀
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
            etUrl.setText(url);
        }

        String method = (String) spinnerMethod.getSelectedItem();
        String headersJson = etHeaders.getText().toString().trim();
        String body = etBody.getText().toString().trim();

        // 构建请求
        Request.Builder requestBuilder = new Request.Builder().url(url);

        // 添加请求头
        if (!TextUtils.isEmpty(headersJson)) {
            try {
                org.json.JSONObject headersObj = new org.json.JSONObject(headersJson);
                java.util.Iterator<String> keys = headersObj.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    String value = headersObj.getString(key);
                    requestBuilder.addHeader(key, value);
                }
            } catch (Exception e) {
                // 请求头格式错误时使用默认头
                requestBuilder.addHeader("User-Agent",
                        "Mozilla/5.0 (ToolBoxApp/1.0)");
            }
        } else {
            requestBuilder.addHeader("User-Agent",
                    "Mozilla/5.0 (ToolBoxApp/1.0)");
        }

        // 设置请求体（POST请求）
        if ("POST".equals(method) && !TextUtils.isEmpty(body)) {
            RequestBody requestBody = RequestBody.create(
                    body, MediaType.parse("application/json; charset=utf-8"));
            requestBuilder.post(requestBody);
        } else if ("POST".equals(method)) {
            RequestBody requestBody = RequestBody.create(
                    "", MediaType.parse("application/json; charset=utf-8"));
            requestBuilder.post(requestBody);
        }

        Request request = requestBuilder.build();

        // 更新UI状态
        btnSend.setEnabled(false);
        btnSend.setText(R.string.msg_requesting);
        tvStatusCode.setText("状态码: 请求中...");
        tvResponse.setText("");

        // 异步发送请求
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThreadSafe(() -> {
                    btnSend.setEnabled(true);
                    btnSend.setText(R.string.btn_send_request);
                    tvStatusCode.setText("状态码: 请求失败");
                    tvResponse.setText("请求失败: " + e.getMessage());
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                // 必须在finally中关闭response，防止连接泄漏
                try (ResponseBody responseBody = response.body()) {
                    String responseBodyString = "";
                    int code = response.code();

                    if (responseBody != null) {
                        // 限制读取大小，防止OOM
                        String contentType = responseBody.contentType() != null
                                ? responseBody.contentType().toString() : "";

                        if (contentType.contains("text")
                                || contentType.contains("json")
                                || contentType.contains("html")
                                || contentType.contains("xml")) {
                            // 文本类型：限制读取大小
                            responseBodyString = responseBody.string();
                            if (responseBodyString.length() > MAX_RESPONSE_SIZE) {
                                responseBodyString = responseBodyString.substring(
                                        0, (int) MAX_RESPONSE_SIZE)
                                        + "\n\n[... 响应内容过长，已截断 ...]";
                            }
                        } else {
                            // 二进制类型：不读取内容
                            long contentLength = responseBody.contentLength();
                            responseBodyString = "[二进制内容，大小: "
                                    + formatSize(contentLength) + "]";
                        }
                    }

                    final String displayBody = responseBodyString;
                    final int finalCode = code;

                    runOnUiThreadSafe(() -> {
                        btnSend.setEnabled(true);
                        btnSend.setText(R.string.btn_send_request);
                        tvStatusCode.setText(getString(R.string.label_status_code, finalCode));

                        // 尝试用Jsoup解析HTML并美化显示
                        String displayText = displayBody;
                        try {
                            Document doc = Jsoup.parse(displayBody);
                            StringBuilder sb = new StringBuilder();

                            String title = doc.title();
                            if (!TextUtils.isEmpty(title)) {
                                sb.append("=== 页面标题 ===\n");
                                sb.append(title).append("\n\n");
                            }

                            sb.append("=== 页面文本内容 ===\n");
                            sb.append(doc.body().text()).append("\n\n");

                            sb.append("=== 所有链接 ===\n");
                            Elements links = doc.select("a[href]");
                            // 限制链接数量，防止OOM
                            int linkCount = 0;
                            for (Element link : links) {
                                if (linkCount >= 500) {
                                    sb.append("\n[... 链接过多，已截断 ...]");
                                    break;
                                }
                                sb.append(link.text()).append(" -> ")
                                        .append(link.attr("href")).append("\n");
                                linkCount++;
                            }

                            sb.append("\n=== 所有图片 ===\n");
                            Elements images = doc.select("img[src]");
                            int imgCount = 0;
                            for (Element img : images) {
                                if (imgCount >= 200) {
                                    sb.append("\n[... 图片过多，已截断 ...]");
                                    break;
                                }
                                sb.append(img.attr("alt")).append(" -> ")
                                        .append(img.attr("src")).append("\n");
                                imgCount++;
                            }

                            displayText = sb.toString();
                        } catch (Exception e) {
                            // 非HTML内容，直接显示原始响应
                            displayText = displayBody;
                        }

                        tvResponse.setText(displayText);
                    });
                }
            }
        });
    }

    /**
     * 格式化大小
     */
    private String formatSize(long bytes) {
        if (bytes <= 0) return "未知";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }

    /**
     * 保存响应结果
     */
    private void saveResult() {
        String result = tvResponse.getText().toString();
        if (TextUtils.isEmpty(result)) {
            Toast.makeText(this, "没有可保存的结果", Toast.LENGTH_SHORT).show();
            return;
        }

        // 创建保存文件的Intent
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE,
                "crawl_result_" + System.currentTimeMillis() + ".txt");
        startActivityForResult(intent, REQUEST_SAVE_RESULT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
            android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SAVE_RESULT && resultCode == RESULT_OK && data != null) {
            try (java.io.OutputStream outputStream =
                    getContentResolver().openOutputStream(data.getData())) {
                if (outputStream != null) {
                    java.io.OutputStreamWriter writer =
                            new java.io.OutputStreamWriter(outputStream, "UTF-8");
                    writer.write(tvResponse.getText().toString());
                    writer.flush();
                    Toast.makeText(this, R.string.msg_result_saved,
                            Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Toast.makeText(this, "保存失败: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        }
    }
}
