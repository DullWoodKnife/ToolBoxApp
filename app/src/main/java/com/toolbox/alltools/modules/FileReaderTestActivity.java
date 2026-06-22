package com.toolbox.alltools.modules;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.toolbox.alltools.R;

import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;

/**
 * 文件阅读器测试Activity
 * 扫描指定目录中的所有 txt/word/pdf/epub/mobi/azw3 文件，逐个测试打开并记录日志
 */
public class FileReaderTestActivity extends Activity {

    private static final String TAG = "FileReaderTest";
    private static final String TEST_DIR = "/sdcard/Movies/";
    private static final String LOG_FILE = "/sdcard/Movies/reader_test_log.txt";

    private TextView tvLog;
    private ScrollView scrollLog;
    private ProgressBar progressBar;
    private Button btnStartTest;
    private TextView tvProgressText;

    private final List<TestResult> results = new ArrayList<>();
    private final StringBuilder logBuilder = new StringBuilder();

    private static class TestResult {
        String fileName;
        String format;
        long fileSize;
        boolean success;
        String error;
        long durationMs;
        boolean hasNav;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_reader_test);

        tvLog = findViewById(R.id.tv_log);
        scrollLog = findViewById(R.id.scroll_log);
        progressBar = findViewById(R.id.progress_bar);
        btnStartTest = findViewById(R.id.btn_start_test);
        tvProgressText = findViewById(R.id.tv_progress_text);

        btnStartTest.setOnClickListener(v -> startTest());

        log("=== 文件阅读器测试工具 ===");
        log("测试目录: " + TEST_DIR);
        log("日志文件: " + LOG_FILE);
        log("点击「开始测试」按钮开始测试\n");
    }

    private void log(String msg) {
        String timestamp = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(new Date());
        String line = "[" + timestamp + "] " + msg;
        logBuilder.append(line).append("\n");
        tvLog.append(line + "\n");
        scrollLog.post(() -> scrollLog.fullScroll(View.FOCUS_DOWN));
        Log.i(TAG, msg);
    }

    private void startTest() {
        btnStartTest.setEnabled(false);
        results.clear();
        logBuilder.setLength(0);
        tvLog.setText("");

        File dir = new File(TEST_DIR);
        if (!dir.exists() || !dir.isDirectory()) {
            log("错误: 测试目录不存在: " + TEST_DIR);
            btnStartTest.setEnabled(true);
            return;
        }

        File[] files = dir.listFiles();
        if (files == null || files.length == 0) {
            log("警告: 测试目录为空");
            btnStartTest.setEnabled(true);
            return;
        }

        List<File> testFiles = new ArrayList<>();
        for (File f : files) {
            if (f.isFile()) {
                String ext = getExtension(f.getName());
                if (Arrays.asList("txt", "doc", "docx", "pdf", "epub", "mobi", "azw3").contains(ext)) {
                    testFiles.add(f);
                }
            }
        }

        if (testFiles.isEmpty()) {
            log("警告: 未找到测试文件（txt/doc/docx/pdf/epub/mobi/azw3）");
            btnStartTest.setEnabled(true);
            return;
        }

        log("找到 " + testFiles.size() + " 个测试文件\n");
        new TestTask(testFiles).execute();
    }

    private String getExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(dot + 1).toLowerCase() : "";
    }

    private class TestTask extends AsyncTask<Void, String, Void> {
        private final List<File> testFiles;
        private final int total;

        TestTask(List<File> files) {
            this.testFiles = files;
            this.total = files.size();
        }

        @Override
        protected void onPreExecute() {
            progressBar.setMax(total);
            progressBar.setProgress(0);
            progressBar.setVisibility(View.VISIBLE);
            tvProgressText.setVisibility(View.VISIBLE);
        }

        @Override
        protected void onProgressUpdate(String... values) {
            if (values.length > 0) log(values[0]);
            if (values.length > 1) {
                try {
                    int progress = Integer.parseInt(values[1]);
                    progressBar.setProgress(progress);
                    tvProgressText.setText(progress + "/" + total);
                } catch (NumberFormatException ignored) {}
            }
        }

        @Override
        protected Void doInBackground(Void... voids) {
            for (int i = 0; i < testFiles.size(); i++) {
                File file = testFiles.get(i);
                TestResult result = testFile(file);
                results.add(result);
                publishProgress(
                        "[" + (i + 1) + "/" + total + "] " + result.fileName +
                                " | " + (result.success ? "成功" : "失败") +
                                " | " + result.durationMs + "ms" +
                                (result.error != null ? " | 错误: " + result.error : ""),
                        String.valueOf(i + 1)
                );
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            progressBar.setVisibility(View.GONE);
            tvProgressText.setVisibility(View.GONE);
            generateReport();
            saveLog();
            btnStartTest.setEnabled(true);
        }
    }

    private TestResult testFile(File file) {
        TestResult result = new TestResult();
        result.fileName = file.getName();
        result.format = getExtension(file.getName());
        result.fileSize = file.length();
        result.success = false;
        result.error = null;
        result.hasNav = false;

        long startTime = System.currentTimeMillis();
        try {
            Uri uri = Uri.fromFile(file);
            switch (result.format) {
                case "txt":
                    result.success = testTxt(uri);
                    break;
                case "doc":
                    result.success = testDoc(uri);
                    break;
                case "docx":
                    result.success = testDocx(uri);
                    break;
                case "pdf":
                    result.success = testPdf(uri);
                    result.hasNav = true; // PDF 有页面导航
                    break;
                case "epub":
                    result.success = testEpub(uri);
                    result.hasNav = true; // EPUB 有章节目录
                    break;
                case "mobi":
                case "azw3":
                    result.success = testMobi(uri);
                    break;
            }
        } catch (OutOfMemoryError e) {
            result.error = "OOM: " + e.getMessage();
            result.success = false;
        } catch (Exception e) {
            result.error = e.getClass().getSimpleName() + ": " + e.getMessage();
            result.success = false;
        }
        result.durationMs = System.currentTimeMillis() - startTime;
        return result;
    }

    private boolean testTxt(Uri uri) throws Exception {
        try (InputStream is = getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            int lines = 0;
            while ((line = reader.readLine()) != null && lines < 100) {
                lines++;
            }
            return lines > 0;
        }
    }

    private boolean testDoc(Uri uri) throws Exception {
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            HWPFDocument doc = new HWPFDocument(new BufferedInputStream(is));
            WordExtractor extractor = new WordExtractor(doc);
            String text = extractor.getText();
            return text != null && !text.isEmpty();
        }
    }

    private boolean testDocx(Uri uri) throws Exception {
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            XWPFDocument doc = new XWPFDocument(is);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(doc.getParagraphs().size(), 100); i++) {
                sb.append(doc.getParagraphs().get(i).getText()).append("\n");
            }
            return sb.length() > 0;
        }
    }

    private boolean testPdf(Uri uri) throws Exception {
        File tempFile = File.createTempFile("test_pdf_", ".pdf", getCacheDir());
        try (InputStream is = getContentResolver().openInputStream(uri);
             FileOutputStream os = new FileOutputStream(tempFile)) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = is.read(buf)) != -1) os.write(buf, 0, len);
        }

        PDDocument doc = null;
        try {
            doc = PDDocument.load(tempFile);
            int pages = doc.getNumberOfPages();
            if (pages <= 0) return false;

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(Math.min(1, pages));
            String text = stripper.getText(doc);
            return text != null;
        } finally {
            if (doc != null) try { doc.close(); } catch (Exception ignored) {}
            tempFile.delete();
        }
    }

    private boolean testEpub(Uri uri) throws Exception {
        // 解析 container.xml 获取 OPF 路径
        String opfPath = "";
        try (InputStream is = getContentResolver().openInputStream(uri);
             ZipInputStream zis = new ZipInputStream(is)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if ("META-INF/container.xml".equals(entry.getName())) {
                    String xml = readZipEntry(zis, 64 * 1024);
                    int idx = xml.indexOf("full-path=\"");
                    if (idx >= 0) {
                        int end = xml.indexOf("\"", idx + 11);
                        if (end > 0) opfPath = xml.substring(idx + 11, end);
                    }
                    break;
                }
            }
        }

        if (opfPath.isEmpty()) return false;

        // 解析 OPF 获取 spine 顺序
        List<String> spineOrder = new ArrayList<>();
        try (InputStream is = getContentResolver().openInputStream(uri);
             ZipInputStream zis = new ZipInputStream(is)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals(opfPath)) {
                    String opfXml = readZipEntry(zis, 256 * 1024);
                    spineOrder = extractSpineOrder(opfXml);
                    break;
                }
            }
        }

        // 读取第一个内容文件
        if (!spineOrder.isEmpty()) {
            String baseDir = "";
            int slashIdx = opfPath.lastIndexOf('/');
            if (slashIdx > 0) baseDir = opfPath.substring(0, slashIdx + 1);
            String entryName = baseDir + spineOrder.get(0);
            if (entryName.startsWith("/")) entryName = entryName.substring(1);

            String html = readZipEntryByName(uri, entryName, 2 * 1024 * 1024);
            return html != null && !html.isEmpty();
        }
        return false;
    }

    private boolean testMobi(Uri uri) throws Exception {
        File tempFile = File.createTempFile("test_mobi_", ".bin", getCacheDir());
        try (InputStream is = getContentResolver().openInputStream(uri);
             FileOutputStream os = new FileOutputStream(tempFile)) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = is.read(buf)) != -1) os.write(buf, 0, len);
        }

        try {
            RandomAccessFile raf = new RandomAccessFile(tempFile, "r");
            byte[] header = new byte[78];
            raf.readFully(header);
            String palmType = new String(header, 60, 8, StandardCharsets.ISO_8859_1).trim();
            raf.close();

            // 搜索 HTML 内容
            raf = new RandomAccessFile(tempFile, "r");
            long limit = Math.min(tempFile.length(), 4 * 1024 * 1024);
            byte[] buf = new byte[(int) limit];
            raf.read(buf);
            raf.close();

            String content = new String(buf, StandardCharsets.ISO_8859_1).toLowerCase();
            return content.contains("<html") || content.contains("<body");
        } finally {
            tempFile.delete();
        }
    }

    private String readZipEntry(ZipInputStream zis, int maxBytes) throws Exception {
        byte[] buf = new byte[8192];
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int len;
        while ((len = zis.read(buf)) > 0) {
            baos.write(buf, 0, len);
            if (baos.size() >= maxBytes) break;
        }
        return baos.toString(StandardCharsets.UTF_8.name());
    }

    private String readZipEntryByName(Uri uri, String entryName, int maxBytes) {
        try (InputStream is = getContentResolver().openInputStream(uri);
             ZipInputStream zis = new ZipInputStream(is)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals(entryName)) {
                    return readZipEntry(zis, maxBytes);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "读取ZIP条目失败: " + entryName, e);
        }
        return null;
    }

    private List<String> extractSpineOrder(String opfXml) {
        List<String> hrefs = new ArrayList<>();
        java.util.Map<String, String> idToHref = new java.util.HashMap<>();

        int manifestIdx = opfXml.indexOf("<manifest");
        if (manifestIdx < 0) return hrefs;
        int manifestEnd = opfXml.indexOf("</manifest>", manifestIdx);
        if (manifestEnd < 0) return hrefs;
        String manifest = opfXml.substring(manifestIdx, manifestEnd);
        int pos = 0;
        while ((pos = manifest.indexOf("<item ", pos)) >= 0) {
            String id = extractAttr(manifest, pos, "id");
            String href = extractAttr(manifest, pos, "href");
            if (id != null && href != null) idToHref.put(id, href);
            pos++;
        }

        int spineIdx = opfXml.indexOf("<spine");
        if (spineIdx < 0) return hrefs;
        int spineEnd = opfXml.indexOf("</spine>", spineIdx);
        if (spineEnd < 0) return hrefs;
        String spine = opfXml.substring(spineIdx, spineEnd);
        pos = 0;
        while ((pos = spine.indexOf("<itemref ", pos)) >= 0) {
            String idref = extractAttr(spine, pos, "idref");
            if (idref != null && idToHref.containsKey(idref)) {
                hrefs.add(idToHref.get(idref));
            }
            pos++;
        }
        return hrefs;
    }

    private String extractAttr(String xml, int startPos, String attrName) {
        String search = attrName + "=\"";
        int idx = xml.indexOf(search, startPos);
        if (idx < 0) return null;
        int valueStart = idx + search.length();
        int valueEnd = xml.indexOf("\"", valueStart);
        if (valueEnd < 0) return null;
        return xml.substring(valueStart, valueEnd);
    }

    private void generateReport() {
        log("\n========== 测试报告 ==========");
        int total = results.size();
        int success = 0;
        int failed = 0;
        int hasNav = 0;

        for (TestResult r : results) {
            if (r.success) success++;
            else failed++;
            if (r.hasNav) hasNav++;
        }

        log("总计: " + total + " 个文件");
        log("成功: " + success + " 个");
        log("失败: " + failed + " 个");
        log("支持导航: " + hasNav + " 个");
        log("\n失败详情:");
        for (TestResult r : results) {
            if (!r.success) {
                log("  - " + r.fileName + " (" + r.format + "): " + r.error);
            }
        }
        log("==============================\n");
    }

    private void saveLog() {
        try {
            File logFile = new File(LOG_FILE);
            FileWriter writer = new FileWriter(logFile);
            writer.write("=== 文件阅读器测试日志 ===\n");
            writer.write("测试时间: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()) + "\n");
            writer.write("测试目录: " + TEST_DIR + "\n\n");
            writer.write(logBuilder.toString());
            writer.flush();
            writer.close();
            log("日志已保存到: " + LOG_FILE);
        } catch (Exception e) {
            log("保存日志失败: " + e.getMessage());
        }
    }
}
