package com.toolbox.alltools.bookshelf;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;

import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.google.android.material.button.MaterialButton;
import com.toolbox.alltools.R;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.io.MemoryUsageSetting;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 书籍阅读器Activity
 * 支持PDF/EPUB/MOBI/AZW3格式
 * 全屏沉浸模式、底部控制面板、夜间模式、阅读进度保存
 */
public class BookReaderActivity extends AppCompatActivity {

    public static final String EXTRA_BOOK_ID = "book_id";

    private static final int BUFFER_SIZE = 16384;
    private static final String PREFS_READER = "reader_prefs";
    private static final String KEY_BRIGHTNESS = "brightness";
    private static final String KEY_FONT_SIZE = "font_size";
    private static final String KEY_NIGHT_MODE = "night_mode";

    // UI
    private ScrollView scrollContent;
    private TextView tvReaderContent;
    private ConstraintLayout clBottomPanel;
    private TextView tvChapterTitle;
    private TextView tvReadProgress;
    private MaterialButton btnPrevChapter;
    private MaterialButton btnNextChapter;
    private LinearLayout btnCatalog;
    private LinearLayout btnBrightness;
    private LinearLayout btnNightMode;
    private LinearLayout btnSettings;

    private Book book;
    private BookDatabaseHelper dbHelper;
    private ExecutorService executorService;
    private SharedPreferences prefs;

    private boolean isNightMode = false;
    private boolean isPanelVisible = true;
    private float fontSize = 18f;
    private int screenBrightness = 128;

    // PDF
    private File pdfCacheFile = null;
    private PDDocument pdfDocument = null;
    private int pdfTotalPages = 0;
    private int pdfCurrentPage = 1;
    private final String[] pdfPageCache = new String[2];
    private int pdfPageCacheIndex = -1;

    // EPUB
    private List<EpubChapter> epubChapters = new ArrayList<>();
    private int epubCurrentChapter = 0;
    private String epubBaseDir = "";
    private File epubCacheFile = null;

    // MOBI
    private File mobiCacheFile = null;
    private List<MobiChapter> mobiChapters = new ArrayList<>();
    private int mobiCurrentChapter = 0;
    private int mobiCompressionType = 1;
    private int mobiTextEncoding = 65001;
    private int mobiTextRecordCount = 0;
    private int mobiFirstContentRecord = 1;
    private int mobiRecordSize = 4096;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_book_reader);

        book = getIntent().getParcelableExtra("book");
        if (book == null) {
            long bookId = getIntent().getLongExtra(EXTRA_BOOK_ID, -1);
            if (bookId > 0) {
                dbHelper = BookDatabaseHelper.getInstance(this);
                book = dbHelper.getBookById(bookId);
            }
        }
        if (book == null) {
            Toast.makeText(this, "书籍信息错误", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        dbHelper = BookDatabaseHelper.getInstance(this);
        executorService = Executors.newSingleThreadExecutor();
        prefs = getSharedPreferences(PREFS_READER, MODE_PRIVATE);
        loadSettings();

        initViews();
        setupImmersiveMode();
        setupClickListeners();
        applyTheme();
        openBook();
    }

    private void initViews() {
        scrollContent = findViewById(R.id.scroll_content);
        tvReaderContent = findViewById(R.id.tv_reader_content);
        clBottomPanel = findViewById(R.id.cl_bottom_panel);
        tvChapterTitle = findViewById(R.id.tv_chapter_title);
        tvReadProgress = findViewById(R.id.tv_read_progress);
        btnPrevChapter = findViewById(R.id.btn_prev_chapter);
        btnNextChapter = findViewById(R.id.btn_next_chapter);
        btnCatalog = findViewById(R.id.btn_catalog);
        btnBrightness = findViewById(R.id.btn_brightness);
        btnNightMode = findViewById(R.id.btn_night_mode);
        btnSettings = findViewById(R.id.btn_settings);
    }

    private void setupImmersiveMode() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    private void setupClickListeners() {
        // 点击内容区域切换底部面板
        scrollContent.setOnClickListener(v -> toggleBottomPanel());
        tvReaderContent.setOnClickListener(v -> toggleBottomPanel());

        btnPrevChapter.setOnClickListener(v -> goToPrevChapter());
        btnNextChapter.setOnClickListener(v -> goToNextChapter());

        btnCatalog.setOnClickListener(v -> showCatalog());
        btnBrightness.setOnClickListener(v -> showBrightnessDialog());
        btnNightMode.setOnClickListener(v -> toggleNightMode());
        btnSettings.setOnClickListener(v -> showSettingsDialog());
    }

    private void toggleBottomPanel() {
        isPanelVisible = !isPanelVisible;
        clBottomPanel.setVisibility(isPanelVisible ? View.VISIBLE : View.GONE);
    }

    private void loadSettings() {
        isNightMode = prefs.getBoolean(KEY_NIGHT_MODE, false);
        fontSize = prefs.getFloat(KEY_FONT_SIZE, 18f);
        screenBrightness = prefs.getInt(KEY_BRIGHTNESS, 128);
    }

    private void saveSettings() {
        prefs.edit()
                .putBoolean(KEY_NIGHT_MODE, isNightMode)
                .putFloat(KEY_FONT_SIZE, fontSize)
                .putInt(KEY_BRIGHTNESS, screenBrightness)
                .apply();
    }

    private void applyTheme() {
        if (isNightMode) {
            tvReaderContent.setBackgroundColor(0xFF1A1A2E);
            tvReaderContent.setTextColor(0xFFE0E0E0);
            scrollContent.setBackgroundColor(0xFF1A1A2E);
        } else {
            tvReaderContent.setBackgroundColor(0xFFF5E6C8);
            tvReaderContent.setTextColor(0xFF3E2723);
            scrollContent.setBackgroundColor(0xFFF5E6C8);
        }
        tvReaderContent.setTextSize(fontSize);
        setScreenBrightness(screenBrightness);
    }

    private void setScreenBrightness(int brightness) {
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = brightness / 255f;
        getWindow().setAttributes(lp);
    }

    private void toggleNightMode() {
        isNightMode = !isNightMode;
        applyTheme();
        saveSettings();
    }

    private void showBrightnessDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("屏幕亮度");

        final android.widget.SeekBar seekBar = new android.widget.SeekBar(this);
        seekBar.setMax(255);
        seekBar.setProgress(screenBrightness);
        seekBar.setPadding(40, 20, 40, 20);
        builder.setView(seekBar);

        builder.setPositiveButton("确定", (dialog, which) -> {
            screenBrightness = seekBar.getProgress();
            setScreenBrightness(screenBrightness);
            saveSettings();
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void showSettingsDialog() {
        String[] items = {"字体大小: " + (int) fontSize + "sp", "重置阅读进度"};
        new AlertDialog.Builder(this)
                .setTitle("阅读设置")
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        showFontSizeDialog();
                    } else if (which == 1) {
                        resetProgress();
                    }
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    private void showFontSizeDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("字体大小");
        final android.widget.SeekBar seekBar = new android.widget.SeekBar(this);
        seekBar.setMax(32);
        seekBar.setMin(12);
        seekBar.setProgress((int) fontSize);
        seekBar.setPadding(40, 20, 40, 20);
        builder.setView(seekBar);
        builder.setPositiveButton("确定", (dialog, which) -> {
            fontSize = seekBar.getProgress();
            tvReaderContent.setTextSize(fontSize);
            saveSettings();
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void resetProgress() {
        book.setReadProgress(0f);
        book.setCurrentPage(0);
        book.setCurrentChapter(0);
        dbHelper.updateBook(book);
        Toast.makeText(this, "阅读进度已重置", Toast.LENGTH_SHORT).show();
    }

    // ==================== 书籍打开 ====================

    private void openBook() {
        String format = book.getFormat().toLowerCase();
        Uri uri = Uri.parse(book.getFileUri());

        switch (format) {
            case "pdf":
                initPdf(uri);
                break;
            case "epub":
                initEpub(uri);
                break;
            case "mobi":
            case "azw3":
                initMobi(uri);
                break;
            default:
                Toast.makeText(this, "不支持的格式: " + format, Toast.LENGTH_SHORT).show();
                finish();
        }
    }

    // ==================== PDF ====================

    private void initPdf(Uri uri) {
        closePdfCache();
        try {
            PDFBoxResourceLoader.init(getApplicationContext());
            pdfCacheFile = File.createTempFile("pdf_cache_", ".pdf", getCacheDir());
            try (InputStream is = getContentResolver().openInputStream(uri);
                 OutputStream os = new FileOutputStream(pdfCacheFile)) {
                if (is == null) {
                    showError("无法读取文件");
                    return;
                }
                byte[] buffer = new byte[BUFFER_SIZE];
                int len;
                while ((len = is.read(buffer)) != -1) {
                    os.write(buffer, 0, len);
                }
            }

            MemoryUsageSetting memSetting = MemoryUsageSetting.setupMixed(16 * 1024 * 1024);
            memSetting.setTempDir(getCacheDir());

            try {
                pdfDocument = PDDocument.load(pdfCacheFile, memSetting);
            } catch (Exception e) {
                try {
                    pdfDocument = PDDocument.load(pdfCacheFile, "", memSetting);
                } catch (Exception e2) {
                    closePdfCache();
                    showError("读取PDF失败: 无法解析PDF文件");
                    return;
                }
            }

            pdfTotalPages = pdfDocument.getNumberOfPages();
            pdfCurrentPage = Math.max(1, Math.min(book.getCurrentPage(), pdfTotalPages));
            if (pdfCurrentPage <= 0) pdfCurrentPage = 1;

            loadPdfPageAsync(pdfCurrentPage);
        } catch (Exception e) {
            showError("读取PDF失败: " + e.getMessage());
        }
    }

    private void loadPdfPageAsync(int page) {
        if (page < 1 || page > pdfTotalPages) return;
        pdfCurrentPage = page;

        // 检查缓存
        if (pdfPageCacheIndex == page && pdfPageCache[0] != null) {
            displayPdfPage(page, pdfPageCache[0]);
            preloadPdfPage(page + 1);
            return;
        }

        executorService.execute(() -> {
            try {
                if (pdfDocument == null || pdfCacheFile == null || !pdfCacheFile.exists()) {
                    runOnUiThread(() -> showError("PDF 缓存已失效"));
                    return;
                }
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(pdfDocument);

                final String displayText = (text != null && !text.trim().isEmpty()) ? text :
                        "第 " + page + " 页无文本内容（可能是扫描版或图片页）";

                pdfPageCache[0] = displayText;
                pdfPageCache[1] = null;
                pdfPageCacheIndex = page;

                runOnUiThread(() -> {
                    displayPdfPage(page, displayText);
                    preloadPdfPage(page + 1);
                });
            } catch (Exception e) {
                runOnUiThread(() -> showError("读取第 " + page + " 页失败: " + e.getMessage()));
            }
        });
    }

    private void displayPdfPage(int page, String text) {
        tvReaderContent.setText("━━━ 第 " + page + " / " + pdfTotalPages + " 页 ━━━\n\n" + text);
        tvChapterTitle.setText("第 " + page + " / " + pdfTotalPages + " 页");
        float progress = pdfTotalPages > 0 ? (page * 100f / pdfTotalPages) : 0;
        tvReadProgress.setText((int) progress + "%");
        updateNavButtons();
    }

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

    // ==================== EPUB ====================

    private void initEpub(Uri uri) {
        try {
            epubChapters.clear();
            epubBaseDir = "";
            closeEpubCache();

            epubCacheFile = File.createTempFile("epub_cache_", ".epub", getCacheDir());
            try (InputStream is = getContentResolver().openInputStream(uri);
                 OutputStream os = new FileOutputStream(epubCacheFile)) {
                if (is == null) {
                    showError("无法读取文件");
                    return;
                }
                byte[] buffer = new byte[BUFFER_SIZE];
                int len;
                while ((len = is.read(buffer)) != -1) {
                    os.write(buffer, 0, len);
                }
            }

            String opfPath = "";
            try (ZipFile zf = new ZipFile(epubCacheFile, StandardCharsets.UTF_8)) {
                ZipEntry containerEntry = zf.getEntry("META-INF/container.xml");
                if (containerEntry != null) {
                    String xml = readZipFileEntry(zf, containerEntry, 64 * 1024);
                    int idx = xml.indexOf("full-path=\"");
                    if (idx >= 0) {
                        int end = xml.indexOf("\"", idx + 11);
                        if (end > 0) {
                            opfPath = xml.substring(idx + 11, end);
                            int slashIdx = opfPath.lastIndexOf('/');
                            epubBaseDir = slashIdx > 0 ? opfPath.substring(0, slashIdx + 1) : "";
                        }
                    }
                }
            }

            java.util.Map<String, String> manifestIdToHref = new java.util.HashMap<>();
            List<String> spineOrder = new ArrayList<>();

            if (!opfPath.isEmpty()) {
                try (ZipFile zf = new ZipFile(epubCacheFile, StandardCharsets.UTF_8)) {
                    ZipEntry opfEntry = zf.getEntry(opfPath);
                    if (opfEntry != null) {
                        String opfXml = readZipFileEntry(zf, opfEntry, 256 * 1024);
                        manifestIdToHref = extractManifest(opfXml);
                        spineOrder = extractSpineOrder(opfXml, manifestIdToHref);
                    }
                }
            }

            if (!spineOrder.isEmpty()) {
                try (ZipFile zf = new ZipFile(epubCacheFile, StandardCharsets.UTF_8)) {
                    for (String href : spineOrder) {
                        String entryName = epubBaseDir + href;
                        if (entryName.startsWith("/")) entryName = entryName.substring(1);
                        String title = "";
                        ZipEntry chEntry = zf.getEntry(entryName);
                        if (chEntry != null) {
                            String preview = readZipFileEntry(zf, chEntry, 4096);
                            title = preview != null ? extractChapterTitle(preview) : "";
                        }
                        if (title.isEmpty()) {
                            int slashPos = entryName.lastIndexOf('/');
                            title = slashPos >= 0 ? entryName.substring(slashPos + 1) : entryName;
                        }
                        epubChapters.add(new EpubChapter(title, entryName));
                    }
                }
            }

            if (!epubChapters.isEmpty()) {
                epubCurrentChapter = Math.max(0, Math.min(book.getCurrentChapter(), epubChapters.size() - 1));
                loadEpubChapterAsync(epubCurrentChapter);
            } else {
                showError("EPUB 文件为空");
            }
        } catch (Exception e) {
            showError("读取EPUB失败: " + e.getMessage());
        }
    }

    private void loadEpubChapterAsync(int chapterIndex) {
        if (chapterIndex < 0 || chapterIndex >= epubChapters.size()) return;
        epubCurrentChapter = chapterIndex;
        final EpubChapter chapter = epubChapters.get(chapterIndex);
        executorService.execute(() -> {
            try {
                String html = null;
                if (epubCacheFile != null && epubCacheFile.exists()) {
                    try (ZipFile zf = new ZipFile(epubCacheFile, StandardCharsets.UTF_8)) {
                        ZipEntry entry = zf.getEntry(chapter.entryName);
                        if (entry != null) {
                            html = readZipFileEntry(zf, entry, 2 * 1024 * 1024);
                        }
                    }
                }
                if (html == null) {
                    runOnUiThread(() -> showError("无法读取章节: " + chapter.title));
                    return;
                }
                String text = stripHtmlTags(html);
                final String displayText = "━━━ " + chapter.title + " ━━━\n\n" +
                        (text.trim().isEmpty() ? "（本章无文本内容）" : text);

                runOnUiThread(() -> {
                    tvReaderContent.setText(displayText);
                    tvChapterTitle.setText(chapter.title);
                    float progress = epubChapters.size() > 0
                            ? ((epubCurrentChapter + 1) * 100f / epubChapters.size()) : 0;
                    tvReadProgress.setText((int) progress + "%");
                    updateNavButtons();
                });
            } catch (Exception e) {
                runOnUiThread(() -> showError("读取章节失败: " + e.getMessage()));
            }
        });
    }

    private void closeEpubCache() {
        if (epubCacheFile != null && epubCacheFile.exists()) {
            epubCacheFile.delete();
            epubCacheFile = null;
        }
        epubChapters.clear();
        epubCurrentChapter = 0;
        epubBaseDir = "";
    }

    // ==================== MOBI/AZW3 ====================

    private void initMobi(Uri uri) {
        closeMobiCache();
        mobiChapters.clear();
        try {
            File directFile = uriToFile(uri);
            if (directFile != null && directFile.exists() && directFile.canRead()) {
                mobiCacheFile = directFile;
                parseMobiWithChapters(directFile);
                return;
            }

            mobiCacheFile = File.createTempFile("mobi_cache_", ".bin", getCacheDir());
            try (InputStream is = getContentResolver().openInputStream(uri);
                 OutputStream os = new FileOutputStream(mobiCacheFile)) {
                if (is == null) {
                    showError("无法读取文件");
                    return;
                }
                byte[] buffer = new byte[BUFFER_SIZE];
                int len;
                while ((len = is.read(buffer)) != -1) {
                    os.write(buffer, 0, len);
                }
            }
            parseMobiWithChapters(mobiCacheFile);
        } catch (Exception e) {
            showError("读取AZW3/MOBI失败: " + e.getMessage());
        }
    }

    private void parseMobiWithChapters(File file) throws Exception {
        long fileSize = file.length();
        RandomAccessFile raf = new RandomAccessFile(file, "r");
        try {
            byte[] pdbHeader = new byte[78];
            raf.readFully(pdbHeader);
            int numRecords = ((pdbHeader[76] & 0xFF) << 8) | (pdbHeader[77] & 0xFF);

            long[] recordOffsets = new long[numRecords];
            raf.seek(78);
            for (int i = 0; i < numRecords; i++) {
                recordOffsets[i] = raf.readInt() & 0xFFFFFFFFL;
                raf.skipBytes(4);
            }
            long[] recordSizes = new long[numRecords];
            for (int i = 0; i < numRecords - 1; i++) {
                recordSizes[i] = recordOffsets[i + 1] - recordOffsets[i];
            }
            recordSizes[numRecords - 1] = fileSize - recordOffsets[numRecords - 1];

            byte[] palmDocHeader = new byte[16];
            raf.seek(recordOffsets[0]);
            raf.readFully(palmDocHeader);

            mobiCompressionType = ((palmDocHeader[0] & 0xFF) << 8) | (palmDocHeader[1] & 0xFF);
            mobiTextRecordCount = ((palmDocHeader[8] & 0xFF) << 8) | (palmDocHeader[9] & 0xFF);
            mobiRecordSize = ((palmDocHeader[10] & 0xFF) << 8) | (palmDocHeader[11] & 0xFF);
            int encryptionType = ((palmDocHeader[12] & 0xFF) << 8) | (palmDocHeader[13] & 0xFF);

            if (encryptionType != 0) {
                showError("该文件已加密（DRM保护），无法读取");
                return;
            }
            if (mobiCompressionType == 17480) {
                showError("该文件使用HUFF/CDIC压缩，暂不支持");
                return;
            }

            if (recordSizes[0] >= 20) {
                byte[] mobiHeader = new byte[Math.min((int) recordSizes[0], 268)];
                raf.seek(recordOffsets[0]);
                raf.readFully(mobiHeader);
                if (mobiHeader.length >= 20 && new String(mobiHeader, 16, 4, StandardCharsets.ISO_8859_1).equals("MOBI")) {
                    if (mobiHeader.length >= 32) {
                        mobiTextEncoding = ((mobiHeader[28] & 0xFF) << 24) | ((mobiHeader[29] & 0xFF) << 16) |
                                ((mobiHeader[30] & 0xFF) << 8) | (mobiHeader[31] & 0xFF);
                    }
                    if (mobiHeader.length >= 194) {
                        mobiFirstContentRecord = ((mobiHeader[192] & 0xFF) << 8) | (mobiHeader[193] & 0xFF);
                    }
                }
            }

            int lastTextRecord = Math.min(mobiFirstContentRecord + mobiTextRecordCount, numRecords);
            final int RECORDS_PER_CHAPTER = 10;
            int chapterNum = 0;

            for (int rec = mobiFirstContentRecord; rec < lastTextRecord; rec += RECORDS_PER_CHAPTER) {
                int endRec = Math.min(rec + RECORDS_PER_CHAPTER, lastTextRecord);
                String title = "第 " + (chapterNum + 1) + " 部分";
                if (rec < numRecords && recordSizes[rec] > 0 && recordSizes[rec] <= 10 * 1024 * 1024) {
                    try {
                        byte[] recData = new byte[(int) Math.min(recordSizes[rec], 64 * 1024)];
                        raf.seek(recordOffsets[rec]);
                        raf.readFully(recData);
                        byte[] decompressed = decompressMobiRecord(recData);
                        String recText = mobiDecode(decompressed);
                        String chTitle = extractMobiChapterTitle(recText);
                        if (!chTitle.isEmpty()) title = chTitle;
                    } catch (Exception ignored) {}
                }
                long chapterOffset = recordOffsets[rec];
                long chapterSize = 0;
                for (int r = rec; r < endRec && r < numRecords; r++) {
                    chapterSize += recordSizes[r];
                }
                mobiChapters.add(new MobiChapter(title, chapterOffset, chapterSize, rec, endRec));
                chapterNum++;
            }

            if (mobiChapters.isEmpty()) {
                showError("MOBI/AZW3文件为空或格式不支持");
                return;
            }

            mobiCurrentChapter = Math.max(0, Math.min(book.getCurrentChapter(), mobiChapters.size() - 1));
            loadMobiChapterAsync(mobiCurrentChapter);
        } finally {
            raf.close();
        }
    }

    private void loadMobiChapterAsync(int chapterIndex) {
        if (chapterIndex < 0 || chapterIndex >= mobiChapters.size()) return;
        mobiCurrentChapter = chapterIndex;
        executorService.execute(() -> {
            try {
                if (mobiCacheFile == null || !mobiCacheFile.exists()) {
                    runOnUiThread(() -> showError("MOBI 缓存已失效"));
                    return;
                }
                RandomAccessFile raf = new RandomAccessFile(mobiCacheFile, "r");
                String text;
                try {
                    text = readMobiChapter(raf, mobiChapters.get(chapterIndex));
                } finally {
                    raf.close();
                }
                final String displayText = "━━━ " + mobiChapters.get(chapterIndex).title + " ━━━\n\n" +
                        (text.isEmpty() ? "（本章无文本内容）" : text);
                runOnUiThread(() -> {
                    tvReaderContent.setText(displayText);
                    tvChapterTitle.setText(mobiChapters.get(chapterIndex).title);
                    float progress = mobiChapters.size() > 0
                            ? ((mobiCurrentChapter + 1) * 100f / mobiChapters.size()) : 0;
                    tvReadProgress.setText((int) progress + "%");
                    updateNavButtons();
                });
            } catch (Exception e) {
                runOnUiThread(() -> showError("读取章节失败: " + e.getMessage()));
            }
        });
    }

    private String readMobiChapter(RandomAccessFile raf, MobiChapter chapter) throws Exception {
        StringBuilder sb = new StringBuilder(64 * 1024);
        if (chapter.startRecord > 0 && chapter.endRecord > chapter.startRecord) {
            for (int rec = chapter.startRecord; rec < chapter.endRecord; rec++) {
                raf.seek(78 + rec * 8);
                long recOffset = raf.readInt() & 0xFFFFFFFFL;
                raf.skipBytes(4);
                long nextOffset;
                if (rec + 1 < chapter.endRecord) {
                    raf.seek(78 + (rec + 1) * 8);
                    nextOffset = raf.readInt() & 0xFFFFFFFFL;
                } else {
                    nextOffset = recOffset + chapter.size;
                }
                long recSize = nextOffset - recOffset;
                if (recSize <= 0 || recSize > 10 * 1024 * 1024) continue;
                byte[] compressed = new byte[(int) Math.min(recSize, 64 * 1024)];
                raf.seek(recOffset);
                raf.readFully(compressed);
                byte[] decompressed = decompressMobiRecord(compressed);
                String text = mobiDecode(decompressed);
                sb.append(text);
            }
        } else {
            long readSize = Math.min(chapter.size, 2 * 1024 * 1024);
            byte[] data = new byte[(int) readSize];
            raf.seek(chapter.offset);
            raf.readFully(data);
            byte[] decompressed = decompressMobiRecord(data);
            sb.append(mobiDecode(decompressed));
        }
        return stripHtmlTags(sb.toString());
    }

    private byte[] decompressMobiRecord(byte[] compressed) throws Exception {
        if (mobiCompressionType == 1) return compressed;
        if (mobiCompressionType == 2) return palmDocDecompress(compressed);
        return compressed;
    }

    private byte[] palmDocDecompress(byte[] data) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length * 2);
        int pos = 0;
        while (pos < data.length) {
            byte b = data[pos++];
            if (b >= 0x00 && b <= 0x07) {
                if (pos >= data.length) break;
                byte b2 = data[pos++];
                int distance = ((b & 0xFF) << 8) | (b2 & 0xFF);
                if (pos >= data.length) break;
                byte b3 = data[pos++];
                int length = (b3 & 0x07) + 3;
                copyFromOutput(out, distance, length);
            } else if (b >= 0x08 && b <= 0x7F) {
                out.write(b);
            } else if (b >= (byte) 0x80 && b <= (byte) 0xBF) {
                if (pos >= data.length) break;
                byte b2 = data[pos++];
                int distance = ((b & 0x3F) << 8) | (b2 & 0xFF);
                int length = ((b >> 2) & 0x07) + 3;
                copyFromOutput(out, distance, length);
            } else {
                out.write((byte) 0x20);
                out.write(b & 0x7F);
            }
        }
        return out.toByteArray();
    }

    private void copyFromOutput(ByteArrayOutputStream out, int distance, int length) {
        byte[] buf = out.toByteArray();
        for (int i = 0; i < length; i++) {
            int idx = buf.length - distance + (i % distance);
            if (idx >= 0 && idx < buf.length) {
                out.write(buf[idx]);
            } else {
                out.write(buf[buf.length - 1]);
            }
        }
    }

    private String mobiDecode(byte[] data) {
        if (data == null || data.length == 0) return "";
        try {
            if (mobiTextEncoding == 65001) return new String(data, StandardCharsets.UTF_8);
            if (mobiTextEncoding == 1252) return new String(data, StandardCharsets.ISO_8859_1);
        } catch (Exception ignored) {}
        return decodeWithFallback(data, data.length);
    }

    private String decodeWithFallback(byte[] data, int length) {
        String utf8 = null;
        try {
            utf8 = new String(data, 0, length, StandardCharsets.UTF_8);
            if (utf8.indexOf('\uFFFD') < 0 && countChineseChars(utf8) >= 3) return utf8;
        } catch (Exception ignored) {}
        String gbk = null;
        try {
            gbk = new String(data, 0, length, Charset.forName("GBK"));
            int gbkChinese = countChineseChars(gbk);
            if (gbkChinese >= 3) {
                int utf8Chinese = utf8 != null ? countChineseChars(utf8) : 0;
                if (gbkChinese > utf8Chinese) return gbk;
            }
        } catch (Exception ignored) {}
        if (utf8 != null && utf8.indexOf('\uFFFD') < 0) return utf8;
        if (gbk != null && countChineseChars(gbk) >= 3) return gbk;
        return new String(data, 0, length, StandardCharsets.ISO_8859_1);
    }

    private int countChineseChars(String text) {
        if (text == null) return 0;
        int count = 0;
        int checkLen = Math.min(text.length(), 200);
        for (int i = 0; i < checkLen; i++) {
            char c = text.charAt(i);
            if (c >= 0x4E00 && c <= 0x9FFF) count++;
        }
        return count;
    }

    private String extractMobiChapterTitle(String preview) {
        String[] tags = {"h1", "h2", "h3", "title"};
        for (String tag : tags) {
            int start = preview.indexOf("<" + tag);
            if (start >= 0) {
                int contentStart = preview.indexOf(">", start);
                if (contentStart >= 0) {
                    contentStart++;
                    int end = preview.indexOf("</" + tag, contentStart);
                    if (end > contentStart) {
                        String title = stripHtmlTags(preview.substring(contentStart, end)).trim();
                        if (!title.isEmpty() && title.length() < 100) return title;
                    }
                }
            }
        }
        return "";
    }

    private void closeMobiCache() {
        if (mobiCacheFile != null && mobiCacheFile.exists() && mobiCacheFile.getName().startsWith("mobi_cache_")) {
            mobiCacheFile.delete();
        }
        mobiCacheFile = null;
        mobiChapters.clear();
        mobiCurrentChapter = 0;
        mobiCompressionType = 1;
        mobiTextEncoding = 65001;
        mobiTextRecordCount = 0;
        mobiFirstContentRecord = 1;
        mobiRecordSize = 4096;
    }

    // ==================== 导航与目录 ====================

    private void goToPrevChapter() {
        String format = book.getFormat().toLowerCase();
        switch (format) {
            case "pdf":
                if (pdfCurrentPage > 1) loadPdfPageAsync(pdfCurrentPage - 1);
                break;
            case "epub":
                if (epubCurrentChapter > 0) loadEpubChapterAsync(epubCurrentChapter - 1);
                break;
            case "mobi":
            case "azw3":
                if (mobiCurrentChapter > 0) loadMobiChapterAsync(mobiCurrentChapter - 1);
                break;
        }
    }

    private void goToNextChapter() {
        String format = book.getFormat().toLowerCase();
        switch (format) {
            case "pdf":
                if (pdfCurrentPage < pdfTotalPages) loadPdfPageAsync(pdfCurrentPage + 1);
                break;
            case "epub":
                if (epubCurrentChapter < epubChapters.size() - 1) loadEpubChapterAsync(epubCurrentChapter + 1);
                break;
            case "mobi":
            case "azw3":
                if (mobiCurrentChapter < mobiChapters.size() - 1) loadMobiChapterAsync(mobiCurrentChapter + 1);
                break;
        }
    }

    private void updateNavButtons() {
        String format = book.getFormat().toLowerCase();
        boolean hasPrev = false;
        boolean hasNext = false;
        switch (format) {
            case "pdf":
                hasPrev = pdfCurrentPage > 1;
                hasNext = pdfCurrentPage < pdfTotalPages;
                break;
            case "epub":
                hasPrev = epubCurrentChapter > 0;
                hasNext = epubCurrentChapter < epubChapters.size() - 1;
                break;
            case "mobi":
            case "azw3":
                hasPrev = mobiCurrentChapter > 0;
                hasNext = mobiCurrentChapter < mobiChapters.size() - 1;
                break;
        }
        btnPrevChapter.setEnabled(hasPrev);
        btnNextChapter.setEnabled(hasNext);
        btnPrevChapter.setAlpha(hasPrev ? 1f : 0.5f);
        btnNextChapter.setAlpha(hasNext ? 1f : 0.5f);
    }

    private void showCatalog() {
        String format = book.getFormat().toLowerCase();
        List<String> titles = new ArrayList<>();
        switch (format) {
            case "pdf":
                if (pdfTotalPages <= 0) return;
                int pagesPerGroup = 50;
                int groups = (pdfTotalPages + pagesPerGroup - 1) / pagesPerGroup;
                for (int g = 0; g < groups; g++) {
                    int start = g * pagesPerGroup + 1;
                    int end = Math.min((g + 1) * pagesPerGroup, pdfTotalPages);
                    titles.add("第 " + start + "-" + end + " 页");
                }
                break;
            case "epub":
                for (EpubChapter ch : epubChapters) titles.add(ch.title);
                break;
            case "mobi":
            case "azw3":
                for (MobiChapter ch : mobiChapters) titles.add(ch.title);
                break;
        }
        if (titles.isEmpty()) return;

        String[] items = titles.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle("目录")
                .setItems(items, (dialog, which) -> {
                    switch (format) {
                        case "pdf":
                            int pg = which * 50 + 1;
                            loadPdfPageAsync(pg);
                            break;
                        case "epub":
                            loadEpubChapterAsync(which);
                            break;
                        case "mobi":
                        case "azw3":
                            loadMobiChapterAsync(which);
                            break;
                    }
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    // ==================== 工具方法 ====================

    private File uriToFile(Uri uri) {
        if ("file".equals(uri.getScheme())) {
            return new File(uri.getPath());
        }
        return null;
    }

    private String readZipFileEntry(ZipFile zf, ZipEntry entry, int maxBytes) throws Exception {
        if (entry == null || entry.getSize() == 0) return "";
        try (InputStream is = zf.getInputStream(entry)) {
            byte[] buffer = new byte[8192];
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int len;
            long totalRead = 0;
            while ((len = is.read(buffer)) > 0) {
                int toWrite = (int) Math.min(len, maxBytes - totalRead);
                baos.write(buffer, 0, toWrite);
                totalRead += toWrite;
                if (totalRead >= maxBytes) break;
            }
            return baos.toString(StandardCharsets.UTF_8.name());
        }
    }

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
                        if (!title.isEmpty() && title.length() < 200) return title;
                    }
                }
            }
        }
        return "";
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
            if (id != null && href != null) idToHref.put(id, href);
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
            if (idref != null && idToHref.containsKey(idref)) hrefs.add(idToHref.get(idref));
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

    private void showError(String message) {
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_LONG).show());
    }

    // ==================== 进度保存 ====================

    private void saveReadingProgress() {
        if (book == null) return;
        String format = book.getFormat().toLowerCase();
        float progress = 0f;
        int currentPage = 0;
        int currentChapter = 0;

        switch (format) {
            case "pdf":
                currentPage = pdfCurrentPage;
                progress = pdfTotalPages > 0 ? (pdfCurrentPage * 100f / pdfTotalPages) : 0;
                break;
            case "epub":
                currentChapter = epubCurrentChapter;
                progress = epubChapters.size() > 0 ? ((epubCurrentChapter + 1) * 100f / epubChapters.size()) : 0;
                break;
            case "mobi":
            case "azw3":
                currentChapter = mobiCurrentChapter;
                progress = mobiChapters.size() > 0 ? ((mobiCurrentChapter + 1) * 100f / mobiChapters.size()) : 0;
                break;
        }

        book.setCurrentPage(currentPage);
        book.setCurrentChapter(currentChapter);
        book.setReadProgress(progress);
        book.setLastReadTime(System.currentTimeMillis());
        dbHelper.updateReadingProgress(book.getId(), currentPage, currentChapter, progress, System.currentTimeMillis());
    }

    // ==================== 生命周期 ====================

    @Override
    protected void onPause() {
        super.onPause();
        saveReadingProgress();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        saveReadingProgress();
        closePdfCache();
        closeEpubCache();
        closeMobiCache();
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            setupImmersiveMode();
        }
    }

    // ==================== 内部类 ====================

    private static class EpubChapter {
        String title;
        String entryName;
        EpubChapter(String title, String entryName) {
            this.title = title;
            this.entryName = entryName;
        }
    }

    private static class MobiChapter {
        String title;
        long offset;
        long size;
        int startRecord;
        int endRecord;
        MobiChapter(String title, long offset, long size, int startRecord, int endRecord) {
            this.title = title;
            this.offset = offset;
            this.size = size;
            this.startRecord = startRecord;
            this.endRecord = endRecord;
        }
    }
}
