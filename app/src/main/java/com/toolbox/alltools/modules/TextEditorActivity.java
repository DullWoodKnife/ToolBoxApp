package com.toolbox.alltools.modules;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.toolbox.alltools.R;
import com.toolbox.alltools.base.BaseToolActivity;
import com.toolbox.alltools.config.AppConfig;

import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.hslf.usermodel.HSLFSlide;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.hslf.usermodel.HSLFTextParagraph;
import org.apache.poi.hslf.usermodel.HSLFTextRun;
import org.apache.poi.hslf.usermodel.HSLFTextShape;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFCell;

import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 文本阅读编辑器Activity
 * 支持多种文件格式的打开和文本提取显示。
 *
 * 大文件优化策略：
 * 1. PDF: 分页缓存，每页单独提取，内存中只保留当前页文本
 * 2. EPUB: 按章节加载，内存中只保留当前章节文本
 * 3. MOBI: 流式二进制解析，不加载全部文件到内存
 * 4. 所有格式解析都在后台线程执行
 */
public class TextEditorActivity extends BaseToolActivity {

    private static final int REQUEST_OPEN_FILE = 1001;
    private static final int REQUEST_SAVE_FILE = 1002;

    /** 文本缓冲区大小 */
    private static final int BUFFER_SIZE = 16384;

    /** 支持的编码列表 */
    private static final List<String> SUPPORTED_ENCODINGS = Arrays.asList(
            "UTF-8", "GB18030", "GBK", "GB2312", "UTF-16", "UTF-16BE", "UTF-16LE",
            "ISO-8859-1", "windows-1252"
    );

    private EditText etEditor;
    private TextView tvWordCount;
    private TextView tvLineCount;
    private TextView tvFileInfo;
    private ImageButton btnOpenFile;
    private ImageButton btnNewFile;
    private ImageButton btnSearch;
    private ImageButton btnSave;
    private Spinner spinnerEncoding;
    private ProgressBar progressBar;
    private TextView tvProgressText;
    private View llProgress;

    private Uri currentFileUri;
    private String currentFileName = "";
    private String detectedEncoding = "UTF-8";
    private String currentFormat = "txt";
    private boolean isReadOnlyFormat = false;

    // 后台线程池
    private ExecutorService executorService;

    // PDF 分页相关
    private File pdfCacheFile = null;
    private PDDocument pdfDocument = null;
    private int pdfTotalPages = 0;
    private int pdfCurrentPage = 1;
    /** PDF 页面文本缓存（当前页 + 预加载的下一页） */
    private final String[] pdfPageCache = new String[2]; // [0]=当前页, [1]=预加载页
    private int pdfPageCacheIndex = -1; // pdfPageCache[0] 对应的页码

    // EPUB 章节相关
    private List<EpubChapter> epubChapters = new ArrayList<>();
    private int epubCurrentChapter = 0;
    private String epubBaseDir = "";

    // MOBI 缓存
    private File mobiCacheFile = null;

    // 大文本分页相关
    private static final int TEXT_LINES_PER_PAGE = 5000; // 每页5000行
    private File txtCacheFile = null;
    private long txtTotalLines = 0;
    private int txtCurrentPage = 1;
    private int txtTotalPages = 1;
    private boolean txtIsPaged = false; // 是否为分页模式

    @Override
    protected void onDestroy() {
        super.onDestroy();
        closeAllCaches();
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
        }
    }

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
        tvFileInfo = findViewById(R.id.tv_file_info);
        btnOpenFile = findViewById(R.id.btn_open_file);
        btnNewFile = findViewById(R.id.btn_new_file);
        btnSearch = findViewById(R.id.btn_search);
        btnSave = findViewById(R.id.btn_save);
        spinnerEncoding = findViewById(R.id.spinner_encoding);
        progressBar = findViewById(R.id.progress_bar);
        tvProgressText = findViewById(R.id.tv_progress_text);
        llProgress = findViewById(R.id.ll_progress);

        ArrayAdapter<String> encodingAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, SUPPORTED_ENCODINGS);
        encodingAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerEncoding.setAdapter(encodingAdapter);

        etEditor.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { updateStats(); }
        });

        executorService = Executors.newSingleThreadExecutor();
    }

    @Override
    protected void initListeners() {
        btnOpenFile.setOnClickListener(v -> openFile());
        btnNewFile.setOnClickListener(v -> newFile());
        btnSearch.setOnClickListener(v -> searchInEditor());
        btnSave.setOnClickListener(v -> saveFile());
    }

    @Override protected void initData() { updateStats(); }

    private void updateStats() {
        String text = etEditor.getText().toString();
        tvWordCount.setText(getString(R.string.label_word_count, text.length()));
        tvLineCount.setText(getString(R.string.label_line_count, text.isEmpty() ? 0 : text.split("\n", -1).length));
    }

    private void openFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "text/plain", "text/markdown", "text/html", "application/xhtml+xml",
                "application/json", "application/xml", "text/xml",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.ms-excel",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.ms-powerpoint",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                "application/epub+zip", "application/pdf", "application/octet-stream"
        });
        startActivityForResult(intent, REQUEST_OPEN_FILE);
    }

    private void newFile() {
        closeAllCaches();
        etEditor.setText("");
        etEditor.setFocusableInTouchMode(true);
        etEditor.setFocusable(true);
        currentFileUri = null;
        currentFileName = "";
        currentFormat = "txt";
        isReadOnlyFormat = false;
        detectedEncoding = "UTF-8";
        spinnerEncoding.setSelection(0);
        tvFileInfo.setText("");
        tvFileInfo.setVisibility(View.GONE);
        hideEpubNav();
        btnSave.setEnabled(true);
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
        if (isReadOnlyFormat) {
            Toast.makeText(this, "当前格式为只读模式，请另存为txt文件", Toast.LENGTH_LONG).show();
        }
        if (currentFileUri != null) {
            saveToFile(currentFileUri);
        } else {
            saveToDefaultPath();
        }
    }

    private void saveToDefaultPath() {
        String encoding = (String) spinnerEncoding.getSelectedItem();
        if (encoding == null) encoding = "UTF-8";
        File moduleDir = AppConfig.getModuleDir(AppConfig.DIR_TEXT_EDITOR);
        String fileName = TextUtils.isEmpty(currentFileName) ? "untitled.txt" : currentFileName;
        File outputFile = new File(moduleDir, fileName);
        int counter = 1;
        String baseName = fileName;
        String ext = "";
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            baseName = fileName.substring(0, dotIndex);
            ext = fileName.substring(dotIndex);
        }
        while (outputFile.exists()) {
            outputFile = new File(moduleDir, baseName + "(" + counter + ")" + ext);
            counter++;
        }
        try (FileOutputStream fos = new FileOutputStream(outputFile);
             OutputStreamWriter writer = new OutputStreamWriter(fos, Charset.forName(encoding))) {
            writer.write(etEditor.getText().toString());
            writer.flush();
            Toast.makeText(this, "已保存到: " + outputFile.getAbsolutePath() + " (" + encoding + ")", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void saveToFile(Uri uri) {
        String encoding = (String) spinnerEncoding.getSelectedItem();
        if (encoding == null) encoding = "UTF-8";
        try (OutputStream outputStream = getContentResolver().openOutputStream(uri)) {
            if (outputStream != null) {
                OutputStreamWriter writer = new OutputStreamWriter(outputStream, Charset.forName(encoding));
                writer.write(etEditor.getText().toString());
                writer.flush();
                Toast.makeText(this, getString(R.string.msg_file_saved) + " (" + encoding + ")", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK || data == null) return;
        if (requestCode == REQUEST_OPEN_FILE) {
            currentFileUri = data.getData();
            currentFileName = getFileName(currentFileUri);
            currentFormat = getFileExtension(currentFileName);
            new FileLoadTask().execute(currentFileUri);
        } else if (requestCode == REQUEST_SAVE_FILE) {
            currentFileUri = data.getData();
            isReadOnlyFormat = false;
            saveToFile(currentFileUri);
        }
    }

    // ==================== 进度显示 ====================

    private void showProgress(String message) {
        llProgress.setVisibility(View.VISIBLE);
        progressBar.setIndeterminate(true);
        progressBar.setProgress(0);
        tvProgressText.setText(message);
    }

    private void updateProgress(int percent, String message) {
        progressBar.setIndeterminate(false);
        progressBar.setProgress(percent);
        tvProgressText.setText(message + " (" + percent + "%)");
    }

    private void hideProgress() {
        llProgress.setVisibility(View.GONE);
    }

    // ==================== 资源释放 ====================

    private void closeAllCaches() {
        closePdfCache();
        closeMobiCache();
        closeTxtCache();
        epubChapters.clear();
        epubCurrentChapter = 0;
        epubBaseDir = "";
    }

    private void closePdfCache() {
        if (pdfDocument != null) {
            try { pdfDocument.close(); } catch (Exception ignored) {}
            pdfDocument = null;
        }
        if (pdfCacheFile != null && pdfCacheFile.exists()) {
            pdfCacheFile.delete();
            pdfCacheFile = null;
        }
        pdfTotalPages = 0;
        pdfCurrentPage = 1;
        pdfPageCache[0] = null;
        pdfPageCache[1] = null;
        pdfPageCacheIndex = -1;
    }

    private void closeMobiCache() {
        if (mobiCacheFile != null && mobiCacheFile.exists()) {
            mobiCacheFile.delete();
            mobiCacheFile = null;
        }
    }

    // ==================== EPUB 导航 ====================

    private static class EpubChapter {
        String title;
        String entryName; // ZIP 条目路径
        EpubChapter(String title, String entryName) {
            this.title = title;
            this.entryName = entryName;
        }
    }

    private void showEpubNav() {
        if (epubChapters.isEmpty()) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("目录导航（共" + epubChapters.size() + "章，当前第" + (epubCurrentChapter + 1) + "章）");

        List<String> itemList = new ArrayList<>();
        if (epubCurrentChapter > 0) {
            itemList.add("上一章：" + epubChapters.get(epubCurrentChapter - 1).title);
        }
        if (epubCurrentChapter < epubChapters.size() - 1) {
            itemList.add("下一章：" + epubChapters.get(epubCurrentChapter + 1).title);
        }
        itemList.add("──────────");
        for (int i = 0; i < epubChapters.size(); i++) {
            itemList.add((i + 1) + ". " + epubChapters.get(i).title);
        }

        final String[] items = itemList.toArray(new String[0]);
        builder.setItems(items, (dialog, which) -> {
            String item = items[which];
            if (item.startsWith("上一章")) {
                epubCurrentChapter--;
                loadEpubChapterAsync(epubCurrentChapter);
            } else if (item.startsWith("下一章")) {
                epubCurrentChapter++;
                loadEpubChapterAsync(epubCurrentChapter);
            } else if (item.startsWith("────")) {
                return; // 分隔线，忽略
            } else {
                // 解析章节编号 "1. xxx" -> 0
                int dotIdx = item.indexOf(". ");
                if (dotIdx > 0) {
                    try {
                        int chapterNum = Integer.parseInt(item.substring(0, dotIdx)) - 1;
                        epubCurrentChapter = chapterNum;
                        loadEpubChapterAsync(chapterNum);
                    } catch (NumberFormatException ignored) {}
                }
            }
        });
        builder.setNegativeButton("关闭", null);
        builder.show();
    }

    private void hideEpubNav() {
        epubChapters.clear();
        epubCurrentChapter = 0;
        epubBaseDir = "";
    }

    // ==================== PDF 分页导航 ====================

    private void showPdfPageNav() {
        if (pdfTotalPages <= 0) return;

        // 快捷导航对话框：上一页/下一页/跳转
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("PDF 导航（共" + pdfTotalPages + "页，当前第" + pdfCurrentPage + "页）");

        String[] items;
        if (pdfCurrentPage > 1 && pdfCurrentPage < pdfTotalPages) {
            items = new String[]{"上一页（第" + (pdfCurrentPage - 1) + "页）", "下一页（第" + (pdfCurrentPage + 1) + "页）", "跳转到指定页..."};
        } else if (pdfCurrentPage > 1) {
            items = new String[]{"上一页（第" + (pdfCurrentPage - 1) + "页）", "跳转到指定页..."};
        } else if (pdfCurrentPage < pdfTotalPages) {
            items = new String[]{"下一页（第" + (pdfCurrentPage + 1) + "页）", "跳转到指定页..."};
        } else {
            items = new String[]{"跳转到指定页..."};
        }

        builder.setItems(items, (dialog, which) -> {
            String item = items[which];
            if (item.startsWith("上一页")) {
                loadPdfPageAsync(pdfCurrentPage - 1);
            } else if (item.startsWith("下一页")) {
                loadPdfPageAsync(pdfCurrentPage + 1);
            } else {
                showPdfPageJumpDialog();
            }
        });
        builder.setNegativeButton("关闭", null);
        builder.show();
    }

    private void showPdfPageJumpDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("跳转到页面（1-" + pdfTotalPages + "）");

        // 分段显示：每10页一组
        List<String> pageItems = new ArrayList<>();
        for (int i = 1; i <= pdfTotalPages; i++) {
            pageItems.add("第 " + i + " 页");
        }

        // 如果页数太多，分段显示
        if (pdfTotalPages > 50) {
            // 显示范围选择
            String[] ranges = new String[(pdfTotalPages + 49) / 50];
            for (int i = 0; i < ranges.length; i++) {
                int start = i * 50 + 1;
                int end = Math.min((i + 1) * 50, pdfTotalPages);
                ranges[i] = "第 " + start + "-" + end + " 页";
            }
            builder.setItems(ranges, (dialog, which) -> {
                int startPage = which * 50 + 1;
                showPdfPageJumpDialog(startPage, Math.min(startPage + 49, pdfTotalPages));
            });
        } else {
            final String[] pageArray = pageItems.toArray(new String[0]);
            builder.setItems(pageArray, (dialog, which) -> {
                loadPdfPageAsync(which + 1);
            });
        }
        builder.setNegativeButton("关闭", null);
        builder.show();
    }

    private void showPdfPageJumpDialog(int fromPage, int toPage) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("第 " + fromPage + "-" + toPage + " 页");
        String[] items = new String[toPage - fromPage + 1];
        for (int i = 0; i < items.length; i++) {
            items[i] = "第 " + (fromPage + i) + " 页";
        }
        builder.setItems(items, (dialog, which) -> {
            loadPdfPageAsync(fromPage + which);
        });
        builder.setNegativeButton("关闭", null);
        builder.show();
    }

    private void loadPdfPageAsync(int page) {
        showProgress("正在读取第 " + page + " 页...");
        pdfCurrentPage = page;

        // 检查缓存
        if (pdfPageCacheIndex == page && pdfPageCache[0] != null) {
            displayPdfPage(page, pdfPageCache[0]);
            preloadPdfPage(page + 1);
            return;
        }
        if (pdfPageCacheIndex == page - 1 && pdfPageCache[1] != null) {
            // 下一页已被预加载
            pdfPageCache[0] = pdfPageCache[1];
            pdfPageCache[1] = null;
            pdfPageCacheIndex = page;
            displayPdfPage(page, pdfPageCache[0]);
            preloadPdfPage(page + 1);
            return;
        }

        executorService.execute(() -> {
            try {
                if (pdfDocument == null || pdfCacheFile == null || !pdfCacheFile.exists()) {
                    runOnUiThread(() -> {
                        hideProgress();
                        Toast.makeText(this, "PDF 缓存已失效", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(pdfDocument);

                final String displayText = (text != null && !text.trim().isEmpty()) ? text :
                        "第 " + page + " 页无文本内容（可能是扫描版或图片页）";

                // 更新缓存
                pdfPageCache[0] = displayText;
                pdfPageCache[1] = null;
                pdfPageCacheIndex = page;

                runOnUiThread(() -> {
                    displayPdfPage(page, displayText);
                    // 预加载下一页
                    preloadPdfPage(page + 1);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    hideProgress();
                    Toast.makeText(this, "读取第 " + page + " 页失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void displayPdfPage(int page, String text) {
        etEditor.setText("━━━ 第 " + page + " / " + pdfTotalPages + " 页 ━━━\n\n" + text);
        updateStats();
        tvFileInfo.setText(currentFileName + " | PDF | 第 " + page + "/" + pdfTotalPages + " 页 | 点击跳转");
        tvFileInfo.setOnClickListener(v -> showPdfPageNav());
        hideProgress();
    }

    /**
     * 预加载下一页到缓存
     */
    private void preloadPdfPage(int nextPage) {
        if (nextPage > pdfTotalPages || pdfDocument == null) return;
        executorService.execute(() -> {
            try {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setStartPage(nextPage);
                stripper.setEndPage(nextPage);
                String text = stripper.getText(pdfDocument);
                pdfPageCache[1] = (text != null && !text.trim().isEmpty()) ? text : null;
            } catch (Exception ignored) {
                pdfPageCache[1] = null;
            }
        });
    }

    // ==================== EPUB 章节异步加载 ====================

    private void loadEpubChapterAsync(int chapterIndex) {
        if (chapterIndex < 0 || chapterIndex >= epubChapters.size()) return;
        showProgress("正在读取: " + epubChapters.get(chapterIndex).title);
        final EpubChapter chapter = epubChapters.get(chapterIndex);
        executorService.execute(() -> {
            try {
                String html = readZipEntryByName(currentFileUri, chapter.entryName, 2 * 1024 * 1024);
                if (html == null) {
                    runOnUiThread(() -> {
                        hideProgress();
                        Toast.makeText(this, "无法读取章节: " + chapter.title, Toast.LENGTH_SHORT).show();
                    });
                    return;
                }
                String text = stripHtmlTags(html);
                final String displayText = "━━━ " + chapter.title + " ━━━\n\n" +
                        (text.trim().isEmpty() ? "（本章无文本内容）" : text);

                runOnUiThread(() -> {
                    etEditor.setText(displayText);
                    updateStats();
                    tvFileInfo.setText(currentFileName + " | EPUB | 第 " + (chapterIndex + 1) + "/" +
                            epubChapters.size() + " 章 | 点击跳转目录");
                    tvFileInfo.setOnClickListener(v -> showEpubNav());
                    hideProgress();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    hideProgress();
                    Toast.makeText(this, "读取章节失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    // ==================== 后台文件加载 ====================

    private class FileLoadTask extends AsyncTask<Uri, String, String> {
        private String displayInfo;
        private boolean readOnly;
        private long fileSize;

        @Override
        protected void onPreExecute() {
            showProgress("正在读取 " + currentFileName + "...");
            etEditor.setText("");
            closeAllCaches();
        }

        @Override
        protected void onProgressUpdate(String... values) {
            if (values != null && values.length > 0) {
                tvProgressText.setText(values[0]);
            }
        }

        @Override
        protected String doInBackground(Uri... uris) {
            try {
                Uri uri = uris[0];
                fileSize = getFileSize(uri);
                displayInfo = currentFileName + " | " + formatFileSize(fileSize) + " | " + currentFormat.toUpperCase();

                switch (currentFormat.toLowerCase()) {
                    case "docx": readOnly = true; return readDocx(uri);
                    case "doc": readOnly = true; return readDoc(uri);
                    case "xlsx": readOnly = true; return readXlsx(uri);
                    case "xls": readOnly = true; return readXls(uri);
                    case "pptx": readOnly = true; return readPptx(uri);
                    case "ppt": readOnly = true; return readPpt(uri);
                    case "epub": readOnly = true; return initEpub(uri);
                    case "pdf": readOnly = true; return initPdf(uri);
                    case "azw3":
                    case "mobi": readOnly = true; return initMobi(uri);
                    case "json": readOnly = false; return readTextFile(uri, true);
                    case "html":
                    case "htm":
                    case "xml":
                    case "md":
                    case "txt":
                    default: readOnly = false; return readTextFile(uri, false);
                }
            } catch (OutOfMemoryError e) {
                return "ERROR:内存不足，文件过大无法加载";
            } catch (Exception e) {
                return "ERROR:" + e.getMessage();
            }
        }

        @Override
        protected void onPostExecute(String result) {
            hideProgress();
            if (result.startsWith("ERROR:")) {
                Toast.makeText(TextEditorActivity.this, result.substring(6), Toast.LENGTH_LONG).show();
                return;
            }
            etEditor.setText(result);
            updateStats();
            updateEncodingSpinner(detectedEncoding);
            isReadOnlyFormat = readOnly;
            tvFileInfo.setVisibility(View.VISIBLE);
            tvFileInfo.setText(displayInfo + (isReadOnlyFormat ? " | 只读" : " | 可编辑"));
            if (isReadOnlyFormat) {
                etEditor.setFocusable(false);
                etEditor.setFocusableInTouchMode(false);
            } else {
                etEditor.setFocusableInTouchMode(true);
                etEditor.setFocusable(true);
            }
            btnSave.setEnabled(true);

            // EPUB 显示导航
            if ("epub".equals(currentFormat.toLowerCase()) && !epubChapters.isEmpty()) {
                tvFileInfo.setOnClickListener(v -> showEpubNav());
                tvFileInfo.setClickable(true);
                Toast.makeText(TextEditorActivity.this,
                        "文件已打开: " + currentFileName + "（点击文件信息栏打开目录）",
                        Toast.LENGTH_LONG).show();
            }
            // PDF 显示页面导航
            else if ("pdf".equals(currentFormat.toLowerCase()) && pdfTotalPages > 0) {
                tvFileInfo.setOnClickListener(v -> showPdfPageNav());
                tvFileInfo.setClickable(true);
                Toast.makeText(TextEditorActivity.this,
                        "文件已打开: " + currentFileName + "（点击文件信息栏跳转页面）",
                        Toast.LENGTH_LONG).show();
            }
            // TXT 大文件分页导航
            else if (txtIsPaged && txtTotalPages > 1) {
                tvFileInfo.setOnClickListener(v -> showTxtPageNav());
                tvFileInfo.setClickable(true);
                tvFileInfo.setText(currentFileName + " | TXT | 第 1/" + txtTotalPages + " 页 | 点击跳转");
                Toast.makeText(TextEditorActivity.this,
                        "文件已打开: " + currentFileName + "（大文件分页模式，点击文件信息栏跳转页面）",
                        Toast.LENGTH_LONG).show();
            } else {
                tvFileInfo.setClickable(false);
                Toast.makeText(TextEditorActivity.this, "文件已打开: " + currentFileName, Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ==================== 文本格式读取 ====================

    private String readTextFile(Uri uri, boolean isJson) {
        long fileSize = getFileSize(uri);
        // 大文件（>5MB）走分页模式
        if (fileSize > 5 * 1024 * 1024) {
            return initTxtPaged(uri, isJson);
        }

        byte[] fileBytes = readFileBytes(uri, (int) Math.min(fileSize, 64 * 1024));
        if (fileBytes == null || fileBytes.length == 0) return "";
        detectedEncoding = detectEncoding(fileBytes);
        String content = readWithEncodingStream(uri, detectedEncoding);
        if (isJson && content != null && !content.trim().isEmpty()) {
            try {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                Object json = gson.fromJson(content, Object.class);
                content = gson.toJson(json);
            } catch (Exception ignored) {}
        }
        return content != null ? content : "";
    }

    /**
     * 大文本文件分页模式：先缓存到临时文件，统计行数，加载第一页
     */
    private String initTxtPaged(Uri uri, boolean isJson) {
        closeTxtCache();
        txtIsPaged = true;

        try {
            // 复制到临时文件
            txtCacheFile = File.createTempFile("txt_cache_", ".txt", getCacheDir());
            try (InputStream is = getContentResolver().openInputStream(uri);
                 OutputStream os = new FileOutputStream(txtCacheFile)) {
                if (is == null) return "无法读取文件";
                byte[] buffer = new byte[BUFFER_SIZE];
                int len;
                while ((len = is.read(buffer)) != -1) {
                    os.write(buffer, 0, len);
                }
            }

            // 检测编码
            byte[] headBytes = readFileBytes(uri, 64 * 1024);
            if (headBytes != null && headBytes.length > 0) {
                detectedEncoding = detectEncoding(headBytes);
            }

            // 统计总行数
            txtTotalLines = countLines(txtCacheFile, detectedEncoding);
            txtTotalPages = (int) Math.max(1, (txtTotalLines + TEXT_LINES_PER_PAGE - 1) / TEXT_LINES_PER_PAGE);
            txtCurrentPage = 1;

            // 读取第一页
            String pageText = readTxtPage(1, detectedEncoding);

            if (isJson && pageText != null && !pageText.trim().isEmpty()) {
                try {
                    Gson gson = new GsonBuilder().setPrettyPrinting().create();
                    Object json = gson.fromJson(pageText, Object.class);
                    pageText = gson.toJson(json);
                } catch (Exception ignored) {}
            }

            return "━━━ 第 1 / " + txtTotalPages + " 页（共" + txtTotalLines + "行）━━━\n\n" +
                    (pageText != null ? pageText : "（空文件）");
        } catch (Exception e) {
            closeTxtCache();
            txtIsPaged = false;
            return "读取文件失败: " + e.getMessage();
        }
    }

    /**
     * 统计文件行数
     */
    private long countLines(File file, String encoding) throws Exception {
        long count = 0;
        try (InputStream is = new FileInputStream(file);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, Charset.forName(encoding)), BUFFER_SIZE)) {
            while (reader.readLine() != null) {
                count++;
            }
        }
        return count;
    }

    /**
     * 读取指定页的文本
     */
    private String readTxtPage(int page, String encoding) throws Exception {
        if (txtCacheFile == null || !txtCacheFile.exists()) return null;

        int startLine = (page - 1) * TEXT_LINES_PER_PAGE;
        int endLine = page * TEXT_LINES_PER_PAGE;

        StringBuilder sb = new StringBuilder(BUFFER_SIZE);
        try (InputStream is = new FileInputStream(txtCacheFile);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, Charset.forName(encoding)), BUFFER_SIZE)) {
            String line;
            int currentLine = 0;
            while ((line = reader.readLine()) != null) {
                if (currentLine >= endLine) break;
                if (currentLine >= startLine) {
                    if (sb.length() > 0) sb.append("\n");
                    sb.append(line);
                }
                currentLine++;
            }
        }
        return sb.toString();
    }

    /**
     * 异步加载文本指定页
     */
    private void loadTxtPageAsync(int page) {
        showProgress("正在读取第 " + page + " 页...");
        txtCurrentPage = page;
        executorService.execute(() -> {
            try {
                String pageText = readTxtPage(page, detectedEncoding);
                final String displayText = "━━━ 第 " + page + " / " + txtTotalPages + " 页（共" + txtTotalLines + "行）━━━\n\n" +
                        (pageText != null ? pageText : "（空页）");
                runOnUiThread(() -> {
                    etEditor.setText(displayText);
                    updateStats();
                    tvFileInfo.setText(currentFileName + " | " + formatFileSize(txtCacheFile.length()) +
                            " | TXT | 第 " + page + "/" + txtTotalPages + " 页 | 点击跳转");
                    tvFileInfo.setOnClickListener(v -> showTxtPageNav());
                    hideProgress();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    hideProgress();
                    Toast.makeText(this, "读取第 " + page + " 页失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    /**
     * 文本分页导航
     */
    private void showTxtPageNav() {
        if (txtTotalPages <= 0) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("文本导航（共" + txtTotalPages + "页，当前第" + txtCurrentPage + "页）");

        List<String> itemList = new ArrayList<>();
        if (txtCurrentPage > 1) {
            itemList.add("上一页（第" + (txtCurrentPage - 1) + "页）");
        }
        if (txtCurrentPage < txtTotalPages) {
            itemList.add("下一页（第" + (txtCurrentPage + 1) + "页）");
        }
        itemList.add("──────────");
        itemList.add("跳转到指定页...");

        final String[] items = itemList.toArray(new String[0]);
        builder.setItems(items, (dialog, which) -> {
            String item = items[which];
            if (item.startsWith("上一页")) {
                loadTxtPageAsync(txtCurrentPage - 1);
            } else if (item.startsWith("下一页")) {
                loadTxtPageAsync(txtCurrentPage + 1);
            } else if (item.startsWith("跳转")) {
                showTxtPageJumpDialog();
            }
        });
        builder.setNegativeButton("关闭", null);
        builder.show();
    }

    private void showTxtPageJumpDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("跳转到页面（1-" + txtTotalPages + "）");

        // 如果页数太多，分段显示
        if (txtTotalPages > 50) {
            String[] ranges = new String[(txtTotalPages + 49) / 50];
            for (int i = 0; i < ranges.length; i++) {
                int start = i * 50 + 1;
                int end = Math.min((i + 1) * 50, txtTotalPages);
                ranges[i] = "第 " + start + "-" + end + " 页";
            }
            builder.setItems(ranges, (dialog, which) -> {
                int startPage = which * 50 + 1;
                showTxtPageRangeDialog(startPage, Math.min(startPage + 49, txtTotalPages));
            });
        } else {
            String[] pageItems = new String[txtTotalPages];
            for (int i = 0; i < txtTotalPages; i++) {
                pageItems[i] = "第 " + (i + 1) + " 页（行 " + (i * TEXT_LINES_PER_PAGE + 1) + "-" +
                        Math.min((i + 1) * TEXT_LINES_PER_PAGE, (int) txtTotalLines) + "）";
            }
            builder.setItems(pageItems, (dialog, which) -> {
                loadTxtPageAsync(which + 1);
            });
        }
        builder.setNegativeButton("关闭", null);
        builder.show();
    }

    private void showTxtPageRangeDialog(int fromPage, int toPage) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("第 " + fromPage + "-" + toPage + " 页");
        String[] items = new String[toPage - fromPage + 1];
        for (int i = 0; i < items.length; i++) {
            items[i] = "第 " + (fromPage + i) + " 页（行 " + ((fromPage + i - 1) * TEXT_LINES_PER_PAGE + 1) + "-" +
                    Math.min((fromPage + i) * TEXT_LINES_PER_PAGE, (int) txtTotalLines) + "）";
        }
        builder.setItems(items, (dialog, which) -> {
            loadTxtPageAsync(fromPage + which);
        });
        builder.setNegativeButton("关闭", null);
        builder.show();
    }

    private void closeTxtCache() {
        if (txtCacheFile != null && txtCacheFile.exists()) {
            txtCacheFile.delete();
            txtCacheFile = null;
        }
        txtTotalLines = 0;
        txtCurrentPage = 1;
        txtTotalPages = 1;
        txtIsPaged = false;
    }

    private String readWithEncodingStream(Uri uri, String encoding) {
        StringBuilder sb = new StringBuilder(BUFFER_SIZE);
        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
            if (inputStream != null) {
                if ("UTF-8".equals(encoding)) {
                    inputStream.mark(3);
                    byte[] bom = new byte[3];
                    int read = inputStream.read(bom);
                    if (read < 3 || bom[0] != (byte) 0xEF || bom[1] != (byte) 0xBB || bom[2] != (byte) 0xBF) {
                        inputStream.reset();
                    }
                }
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(inputStream, Charset.forName(encoding)), BUFFER_SIZE);
                String line;
                while ((line = reader.readLine()) != null) {
                    if (sb.length() > 0) sb.append("\n");
                    sb.append(line);
                }
            }
        } catch (Exception e) {
            return "";
        }
        return sb.toString();
    }

    // ==================== Word 格式 ====================

    private String readDocx(Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            if (is == null) return "无法读取文件";
            org.apache.poi.xwpf.usermodel.XWPFDocument doc = new org.apache.poi.xwpf.usermodel.XWPFDocument(is);
            StringBuilder sb = new StringBuilder();
            for (org.apache.poi.xwpf.usermodel.XWPFParagraph para : doc.getParagraphs()) {
                sb.append(para.getText()).append("\n");
            }
            for (org.apache.poi.xwpf.usermodel.XWPFTable table : doc.getTables()) {
                for (org.apache.poi.xwpf.usermodel.XWPFTableRow row : table.getRows()) {
                    for (org.apache.poi.xwpf.usermodel.XWPFTableCell cell : row.getTableCells()) {
                        sb.append(cell.getText()).append("\t");
                    }
                    sb.append("\n");
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return "读取DOCX失败: " + e.getMessage();
        }
    }

    private String readDoc(Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            if (is == null) return "无法读取文件";
            BufferedInputStream bis = new BufferedInputStream(is);
            HWPFDocument doc = new HWPFDocument(bis);
            WordExtractor extractor = new WordExtractor(doc);
            return extractor.getText();
        } catch (Exception e) {
            return "读取DOC失败: " + e.getMessage();
        }
    }

    // ==================== Excel 格式 ====================

    private String readXlsx(Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            if (is == null) return "无法读取文件";
            XSSFWorkbook workbook = new XSSFWorkbook(is);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                XSSFSheet sheet = workbook.getSheetAt(i);
                sb.append("=== Sheet: ").append(sheet.getSheetName()).append(" ===\n");
                for (int r = 0; r <= sheet.getLastRowNum(); r++) {
                    XSSFRow row = sheet.getRow(r);
                    if (row == null) continue;
                    for (int j = 0; j < row.getLastCellNum(); j++) {
                        XSSFCell cell = row.getCell(j);
                        sb.append(getCellValue(cell)).append("\t");
                    }
                    sb.append("\n");
                }
                sb.append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return "读取XLSX失败: " + e.getMessage();
        }
    }

    private String readXls(Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            if (is == null) return "无法读取文件";
            HSSFWorkbook workbook = new HSSFWorkbook(is);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                HSSFSheet sheet = workbook.getSheetAt(i);
                sb.append("=== Sheet: ").append(sheet.getSheetName()).append(" ===\n");
                for (int j = 0; j <= sheet.getLastRowNum(); j++) {
                    HSSFRow row = sheet.getRow(j);
                    if (row == null) continue;
                    for (int k = 0; k < row.getLastCellNum(); k++) {
                        HSSFCell cell = row.getCell(k);
                        sb.append(getCellValueLegacy(cell)).append("\t");
                    }
                    sb.append("\n");
                }
                sb.append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return "读取XLS失败: " + e.getMessage();
        }
    }

    private String getCellValue(XSSFCell cell) {
        if (cell == null) return "";
        CellType type = cell.getCellType();
        switch (type) {
            case STRING: return cell.getStringCellValue();
            case NUMERIC: return String.valueOf(cell.getNumericCellValue());
            case BOOLEAN: return String.valueOf(cell.getBooleanCellValue());
            case FORMULA: return cell.getCellFormula();
            default: return "";
        }
    }

    private String getCellValueLegacy(HSSFCell cell) {
        if (cell == null) return "";
        CellType type = cell.getCellType();
        switch (type) {
            case STRING: return cell.getStringCellValue();
            case NUMERIC: return String.valueOf(cell.getNumericCellValue());
            case BOOLEAN: return String.valueOf(cell.getBooleanCellValue());
            case FORMULA: return cell.getCellFormula();
            default: return "";
        }
    }

    // ==================== PPT 格式 ====================

    private String readPptx(Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            if (is == null) return "无法读取文件";
            XMLSlideShow pptx = new XMLSlideShow(is);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < pptx.getSlides().size(); i++) {
                sb.append("=== 幻灯片 ").append(i + 1).append(" ===\n");
                XSLFSlide slide = pptx.getSlides().get(i);
                for (org.apache.poi.xslf.usermodel.XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape) {
                        XSLFTextShape textShape = (XSLFTextShape) shape;
                        if (textShape.getText() != null && !textShape.getText().isEmpty()) {
                            for (XSLFTextParagraph para : textShape) {
                                for (XSLFTextRun run : para) {
                                    sb.append(run.getRawText());
                                }
                                sb.append("\n");
                            }
                        }
                    }
                }
                sb.append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return "读取PPTX失败: " + e.getMessage();
        }
    }

    private String readPpt(Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            if (is == null) return "无法读取文件";
            BufferedInputStream bis = new BufferedInputStream(is);
            HSLFSlideShow ppt = new HSLFSlideShow(bis);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < ppt.getSlides().size(); i++) {
                sb.append("=== 幻灯片 ").append(i + 1).append(" ===\n");
                HSLFSlide slide = ppt.getSlides().get(i);
                for (org.apache.poi.hslf.usermodel.HSLFShape shape : slide.getShapes()) {
                    if (shape instanceof HSLFTextShape) {
                        HSLFTextShape textShape = (HSLFTextShape) shape;
                        if (textShape.getText() != null && !textShape.getText().isEmpty()) {
                            for (HSLFTextParagraph para : textShape) {
                                for (HSLFTextRun run : para) {
                                    sb.append(run.getRawText());
                                }
                                sb.append("\n");
                            }
                        }
                    }
                }
                sb.append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return "读取PPT失败: " + e.getMessage();
        }
    }

    // ==================== EPUB 格式（分页加载）====================

    /**
     * 初始化 EPUB：解析 OPF 获取章节列表，加载第一章
     * 不一次性加载全部内容
     */
    private String initEpub(Uri uri) {
        try {
            epubChapters.clear();
            epubBaseDir = "";

            // 解析 container.xml 获取 OPF 路径
            String opfPath = "";
            try (InputStream is = getContentResolver().openInputStream(uri);
                 ZipInputStream zis = new ZipInputStream(is)) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if ("META-INF/container.xml".equals(entry.getName())) {
                        String xml = readZipEntryString(zis, 64 * 1024);
                        int idx = xml.indexOf("full-path=\"");
                        if (idx >= 0) {
                            int end = xml.indexOf("\"", idx + 11);
                            if (end > 0) {
                                opfPath = xml.substring(idx + 11, end);
                                int slashIdx = opfPath.lastIndexOf('/');
                                epubBaseDir = slashIdx > 0 ? opfPath.substring(0, slashIdx + 1) : "";
                            }
                        }
                        break;
                    }
                }
            }

            // 解析 OPF
            java.util.Map<String, String> manifestIdToHref = new java.util.HashMap<>();
            List<String> spineOrder = new ArrayList<>();
            String bookTitle = "";

            if (!opfPath.isEmpty()) {
                try (InputStream is = getContentResolver().openInputStream(uri);
                     ZipInputStream zis = new ZipInputStream(is)) {
                    ZipEntry entry;
                    while ((entry = zis.getNextEntry()) != null) {
                        if (entry.getName().equals(opfPath)) {
                            String opfXml = readZipEntryString(zis, 256 * 1024);
                            bookTitle = extractXmlTag(opfXml, "dc:title");
                            manifestIdToHref = extractManifest(opfXml);
                            spineOrder = extractSpineOrder(opfXml, manifestIdToHref);
                            break;
                        }
                    }
                }
            }

            // 按 spine 顺序构建章节列表（只保存元数据，不加载内容）
            if (!spineOrder.isEmpty()) {
                for (String href : spineOrder) {
                    String entryName = epubBaseDir + href;
                    if (entryName.startsWith("/")) entryName = entryName.substring(1);

                    // 预读取标题（只读前4KB）
                    String preview = readZipEntryByName(uri, entryName, 4096);
                    String title = preview != null ? extractChapterTitle(preview) : "";
                    if (title.isEmpty()) {
                        int slashPos = entryName.lastIndexOf('/');
                        title = slashPos >= 0 ? entryName.substring(slashPos + 1) : entryName;
                    }
                    epubChapters.add(new EpubChapter(title, entryName));
                }
            } else {
                // 回退：按 ZIP 条目顺序
                try (InputStream is = getContentResolver().openInputStream(uri);
                     ZipInputStream zis = new ZipInputStream(is)) {
                    ZipEntry entry;
                    while ((entry = zis.getNextEntry()) != null) {
                        String name = entry.getName().toLowerCase();
                        if (name.endsWith(".xhtml") || name.endsWith(".html") || name.endsWith(".htm")) {
                            String preview = readZipEntryString(zis, 4096);
                            String title = extractChapterTitle(preview);
                            if (title.isEmpty()) {
                                int slashPos = entry.getName().lastIndexOf('/');
                                title = slashPos >= 0 ? entry.getName().substring(slashPos + 1) : entry.getName();
                            }
                            epubChapters.add(new EpubChapter(title, entry.getName()));
                        }
                    }
                }
            }

            // 加载第一章
            if (!epubChapters.isEmpty()) {
                epubCurrentChapter = 0;
                String firstHtml = readZipEntryByName(uri, epubChapters.get(0).entryName, 2 * 1024 * 1024);
                String firstText = firstHtml != null ? stripHtmlTags(firstHtml) : "";
                return (bookTitle.isEmpty() ? "" : "书名: " + bookTitle + "\n\n") +
                        "━━━ " + epubChapters.get(0).title + " ━━━\n\n" +
                        (firstText.trim().isEmpty() ? "（本章无文本内容）" : firstText);
            }
            return "EPUB 文件为空";
        } catch (Exception e) {
            return "读取EPUB失败: " + e.getMessage();
        }
    }

    private java.util.Map<String, String> extractManifest(String opfXml) {
        java.util.Map<String, String> idToHref = new java.util.HashMap<>();
        int manifestIdx = opfXml.indexOf("<manifest");
        if (manifestIdx < 0) return idToHref;
        int manifestEnd = opfXml.indexOf("</manifest>", manifestIdx);
        if (manifestEnd < 0) return idToHref;
        String manifest = opfXml.substring(manifestIdx, manifestEnd);
        int pos = 0;
        while ((pos = manifest.indexOf("<item ", pos)) >= 0) {
            String id = extractXmlAttribute(manifest, pos, "id");
            String href = extractXmlAttribute(manifest, pos, "href");
            if (id != null && href != null) {
                idToHref.put(id, href);
            }
            pos++;
        }
        return idToHref;
    }

    private List<String> extractSpineOrder(String opfXml, java.util.Map<String, String> idToHref) {
        List<String> hrefs = new ArrayList<>();
        int spineIdx = opfXml.indexOf("<spine");
        if (spineIdx < 0) return hrefs;
        int spineEnd = opfXml.indexOf("</spine>", spineIdx);
        if (spineEnd < 0) return hrefs;
        String spine = opfXml.substring(spineIdx, spineEnd);
        int pos = 0;
        while ((pos = spine.indexOf("<itemref ", pos)) >= 0) {
            String idref = extractXmlAttribute(spine, pos, "idref");
            if (idref != null && idToHref.containsKey(idref)) {
                hrefs.add(idToHref.get(idref));
            }
            pos++;
        }
        return hrefs;
    }

    private String extractXmlAttribute(String xml, int startPos, String attrName) {
        String search = attrName + "=\"";
        int idx = xml.indexOf(search, startPos);
        if (idx < 0) return null;
        int valueStart = idx + search.length();
        int valueEnd = xml.indexOf("\"", valueStart);
        if (valueEnd < 0) return null;
        return xml.substring(valueStart, valueEnd);
    }

    private String extractChapterTitle(String html) {
        String[] tags = {"h1", "h2", "h3"};
        for (String tag : tags) {
            int start = html.indexOf("<" + tag);
            if (start >= 0) {
                int contentStart = html.indexOf(">", start);
                if (contentStart >= 0) {
                    contentStart++;
                    int end = html.indexOf("</" + tag, contentStart);
                    if (end > contentStart) {
                        String title = stripHtmlTags(html.substring(contentStart, end)).trim();
                        if (!title.isEmpty()) return title;
                    }
                }
            }
        }
        int titleStart = html.indexOf("<title>");
        if (titleStart >= 0) {
            int contentStart = titleStart + 7;
            int titleEnd = html.indexOf("</title>", contentStart);
            if (titleEnd > contentStart) {
                return html.substring(contentStart, titleEnd).trim();
            }
        }
        return "";
    }

    private String readZipEntryByName(Uri uri, String entryName, int maxBytes) {
        try (InputStream is = getContentResolver().openInputStream(uri);
             ZipInputStream zis = new ZipInputStream(is)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals(entryName)) {
                    return readZipEntryString(zis, maxBytes);
                }
            }
        } catch (Exception e) {
            android.util.Log.e("TextEditor", "读取ZIP条目失败: " + entryName, e);
        }
        return null;
    }

    private String readZipEntryString(ZipInputStream zis, int maxBytes) throws Exception {
        byte[] buffer = new byte[8192];
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int len;
        while ((len = zis.read(buffer)) > 0) {
            baos.write(buffer, 0, len);
            if (baos.size() >= maxBytes) break;
        }
        return baos.toString(StandardCharsets.UTF_8.name());
    }

    private String extractXmlTag(String xml, String tag) {
        int start = xml.indexOf("<" + tag);
        if (start < 0) return "";
        int contentStart = xml.indexOf(">", start);
        if (contentStart < 0) return "";
        contentStart++;
        int end = xml.indexOf("</" + tag, contentStart);
        if (end < 0) return "";
        return xml.substring(contentStart, end).trim();
    }

    // ==================== PDF 格式（单页加载）====================

    /**
     * 初始化 PDF：缓存到临时文件，加载文档，返回第一页内容
     * 不一次性提取全部页面
     */
    private String initPdf(Uri uri) {
        closePdfCache();
        try {
            // 复制到临时文件
            pdfCacheFile = File.createTempFile("pdf_cache_", ".pdf", getCacheDir());
            try (InputStream is = getContentResolver().openInputStream(uri);
                 OutputStream os = new FileOutputStream(pdfCacheFile)) {
                if (is == null) return "无法读取文件";
                byte[] buffer = new byte[BUFFER_SIZE];
                int len;
                while ((len = is.read(buffer)) != -1) {
                    os.write(buffer, 0, len);
                }
            }

            // 加载文档
            try {
                pdfDocument = PDDocument.load(pdfCacheFile);
            } catch (Exception e) {
                try {
                    pdfDocument = PDDocument.load(pdfCacheFile, "");
                } catch (Exception e2) {
                    closePdfCache();
                    return "读取PDF失败: 无法解析PDF文件，文件可能已损坏或加密";
                }
            }

            pdfTotalPages = pdfDocument.getNumberOfPages();
            pdfCurrentPage = 1;

            // 只提取第一页
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(1);
            String text = stripper.getText(pdfDocument);

            return "━━━ 第 1 / " + pdfTotalPages + " 页 ━━━\n\n" +
                    (text != null && !text.trim().isEmpty() ? text : "（本页无文本内容）");

        } catch (OutOfMemoryError e) {
            closePdfCache();
            return "读取PDF失败: 内存不足，文件过大";
        } catch (Exception e) {
            closePdfCache();
            return "读取PDF失败: " + e.getMessage();
        }
    }

    // ==================== AZW3/MOBI 格式（流式读取）====================

    /**
     * 初始化 MOBI：复制到临时文件，流式解析第一块内容
     */
    private String initMobi(Uri uri) {
        closeMobiCache();
        try {
            mobiCacheFile = File.createTempFile("mobi_cache_", ".bin", getCacheDir());
            try (InputStream is = getContentResolver().openInputStream(uri);
                 OutputStream os = new FileOutputStream(mobiCacheFile)) {
                if (is == null) return "无法读取文件";
                byte[] buffer = new byte[BUFFER_SIZE];
                int len;
                while ((len = is.read(buffer)) != -1) {
                    os.write(buffer, 0, len);
                }
            }

            long fileSize = mobiCacheFile.length();
            String text = parseMobiFileStream(mobiCacheFile, fileSize);

            if (!text.trim().isEmpty()) {
                return text;
            }
            return "AZW3/MOBI格式文件已加载。\n\n" +
                    "该格式为Amazon Kindle专有二进制格式。\n" +
                    "建议：使用Calibre将AZW3/MOBI转换为EPUB或TXT格式后打开。\n\n" +
                    "文件大小: " + formatFileSize(fileSize);
        } catch (OutOfMemoryError e) {
            closeMobiCache();
            return "读取AZW3/MOBI失败: 内存不足";
        } catch (Exception e) {
            closeMobiCache();
            return "读取AZW3/MOBI失败: " + e.getMessage();
        }
    }

    /**
     * 流式解析 MOBI 文件，不一次性加载全部内容到内存
     */
    private String parseMobiFileStream(File file, long fileSize) throws Exception {
        RandomAccessFile raf = new RandomAccessFile(file, "r");
        try {
            // 读取 PalmDB 头部
            byte[] header = new byte[78];
            raf.readFully(header);

            String palmType = new String(header, 60, 8, StandardCharsets.ISO_8859_1).trim();
            if (!"BOOKMOBI".equals(palmType)) {
                // 非标准 MOBI，流式搜索 HTML 内容
                return searchHtmlInFileStream(raf, fileSize);
            }

            int numRecords = ((header[76] & 0xFF) << 8) | (header[77] & 0xFF);
            raf.seek(78 + numRecords * 8);

            byte[] palmDocHeader = new byte[16];
            raf.readFully(palmDocHeader);

            // 流式搜索 HTML 标签（只搜索前 4MB）
            raf.seek(0);
            long searchLimit = Math.min(fileSize, 4 * 1024 * 1024);
            byte[] searchBuffer = new byte[(int) searchLimit];
            int bytesRead = raf.read(searchBuffer);

            String content = findHtmlContent(searchBuffer, bytesRead);
            if (!content.trim().isEmpty()) {
                return content;
            }

            // 搜索文件后半部分
            long searchStart = Math.max(0, fileSize / 4);
            raf.seek(searchStart);
            long tailSize = Math.min(fileSize - searchStart, 2 * 1024 * 1024);
            byte[] tailBuffer = new byte[(int) tailSize];
            int tailRead = raf.read(tailBuffer);
            content = extractReadableText(tailBuffer, tailRead);
            if (!content.trim().isEmpty()) {
                return content;
            }

            return "";
        } finally {
            raf.close();
        }
    }

    private String searchHtmlInFileStream(RandomAccessFile raf, long fileSize) throws Exception {
        long searchLimit = Math.min(fileSize, 4 * 1024 * 1024);
        byte[] buffer = new byte[(int) searchLimit];
        raf.seek(0);
        int bytesRead = raf.read(buffer);

        String content = findHtmlContent(buffer, bytesRead);
        if (!content.trim().isEmpty()) return content;
        return extractReadableText(buffer, bytesRead);
    }

    private String findHtmlContent(byte[] data, int length) {
        String lower = new String(data, 0, length, StandardCharsets.ISO_8859_1).toLowerCase();
        int bodyStart = lower.indexOf("<html");
        if (bodyStart < 0) bodyStart = lower.indexOf("<body");
        if (bodyStart < 0) bodyStart = lower.indexOf("<?xml");

        if (bodyStart >= 0) {
            String html = new String(data, bodyStart, Math.min(length - bodyStart, 2 * 1024 * 1024), StandardCharsets.ISO_8859_1);
            return stripHtmlTags(html);
        }
        return "";
    }

    private String extractReadableText(byte[] data, int length) {
        StringBuilder sb = new StringBuilder();
        StringBuilder currentText = new StringBuilder();
        int consecutiveBinary = 0;

        for (int i = 0; i < length; i++) {
            byte b = data[i];
            if (isPrintable(b)) {
                currentText.append((char) (b & 0xFF));
                consecutiveBinary = 0;
            } else {
                consecutiveBinary++;
                if (consecutiveBinary > 4 && currentText.length() > 10) {
                    String text = currentText.toString().trim();
                    if (text.length() > 10) {
                        sb.append(text).append("\n\n");
                    }
                    currentText.setLength(0);
                }
            }
        }

        if (currentText.length() > 10) {
            String text = currentText.toString().trim();
            if (text.length() > 10) sb.append(text);
        }
        return sb.toString();
    }

    private boolean isPrintable(byte b) {
        return (b >= 0x20 && b <= 0x7E) || (b >= 0x0A && b <= 0x0D) || b == 0x09;
    }

    // ==================== 工具方法 ====================

    private String stripHtmlTags(String html) {
        if (html == null) return "";
        StringBuilder sb = new StringBuilder();
        boolean inTag = false;
        for (int i = 0; i < html.length(); i++) {
            char c = html.charAt(i);
            if (c == '<') inTag = true;
            else if (c == '>') inTag = false;
            else if (!inTag) sb.append(c);
        }
        return sb.toString().replaceAll("\n{3,}", "\n\n").trim();
    }

    private String getFileName(Uri uri) {
        String displayName = "未知文件";
        try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (nameIndex >= 0) displayName = cursor.getString(nameIndex);
            }
        } catch (Exception ignored) {}
        return displayName;
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) return "txt";
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
            return fileName.substring(dotIndex + 1).toLowerCase();
        }
        return "txt";
    }

    private String formatFileSize(long size) {
        if (size <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB"};
        int digitGroups = (int) (Math.log10((double) size) / Math.log10(1024.0));
        digitGroups = Math.min(digitGroups, units.length - 1);
        return String.format("%.2f %s", size / Math.pow(1024.0, digitGroups), units[digitGroups]);
    }

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

    private String detectEncoding(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return "UTF-8";
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) return "UTF-8";
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xFF) == 0xFF) return "UTF-16BE";
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE) return "UTF-16LE";
        if (isValidUtf8(bytes)) return "UTF-8";
        if (isValidEncoding(bytes, "GB18030")) return "GB18030";
        if (isValidEncoding(bytes, "GBK")) return "GBK";
        if (isValidEncoding(bytes, "GB2312")) return "GB2312";
        return "UTF-8";
    }

    private boolean isValidUtf8(byte[] bytes) {
        int i = 0;
        while (i < bytes.length) {
            int b = bytes[i] & 0xFF;
            if (b < 0x80) { i++; continue; }
            if ((b & 0xE0) == 0xC0) {
                if (i + 1 >= bytes.length || (bytes[i + 1] & 0xC0) != 0x80) return false;
                i += 2; continue;
            }
            if ((b & 0xF0) == 0xE0) {
                if (i + 2 >= bytes.length || (bytes[i + 1] & 0xC0) != 0x80 || (bytes[i + 2] & 0xC0) != 0x80) return false;
                i += 3; continue;
            }
            if ((b & 0xF8) == 0xF0) {
                if (i + 3 >= bytes.length || (bytes[i + 1] & 0xC0) != 0x80 || (bytes[i + 2] & 0xC0) != 0x80 || (bytes[i + 3] & 0xC0) != 0x80) return false;
                i += 4; continue;
            }
            return false;
        }
        return true;
    }

    private boolean isValidEncoding(byte[] bytes, String encoding) {
        try {
            String decoded = new String(bytes, Charset.forName(encoding));
            byte[] reEncoded = decoded.getBytes(Charset.forName(encoding));
            return Arrays.equals(bytes, reEncoded);
        } catch (Exception e) {
            return false;
        }
    }

    private void updateEncodingSpinner(String encoding) {
        int index = SUPPORTED_ENCODINGS.indexOf(encoding);
        if (index >= 0) spinnerEncoding.setSelection(index);
    }

    private long getFileSize(Uri uri) {
        try (android.database.Cursor cursor = getContentResolver().query(
                uri, new String[]{android.provider.OpenableColumns.SIZE}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE);
                if (sizeIndex >= 0) return cursor.getLong(sizeIndex);
            }
        } catch (Exception ignored) {}
        return -1;
    }
}
