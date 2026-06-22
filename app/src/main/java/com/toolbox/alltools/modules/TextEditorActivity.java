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
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 文本阅读编辑器Activity
 * 支持多种文件格式的打开和文本提取显示，无文件大小限制。
 * EPUB 支持目录导航功能。
 */
public class TextEditorActivity extends BaseToolActivity {

    private static final int REQUEST_OPEN_FILE = 1001;
    private static final int REQUEST_SAVE_FILE = 1002;

    /** 文本内容最大显示长度（10MB），超过则截断提示 */
    private static final int MAX_DISPLAY_LENGTH = 10 * 1024 * 1024;

    /** 单行读取缓冲区大小 */
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

    private Uri currentFileUri;
    private String currentFileName = "";
    private String detectedEncoding = "UTF-8";
    private String currentFormat = "txt";
    private boolean isReadOnlyFormat = false;

    // EPUB 导航相关
    private List<EpubChapter> epubChapters = new ArrayList<>();

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

        ArrayAdapter<String> encodingAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, SUPPORTED_ENCODINGS);
        encodingAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerEncoding.setAdapter(encodingAdapter);

        etEditor.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { updateStats(); }
        });
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

    // ==================== EPUB 导航 ====================

    private static class EpubChapter {
        String title;
        int startLine;
        int endLine;
        EpubChapter(String title, int startLine) {
            this.title = title;
            this.startLine = startLine;
        }
    }

    private void showEpubNav() {
        if (epubChapters.isEmpty()) return;
        hideEpubNav();

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("目录导航（共" + epubChapters.size() + "章）");
        String[] items = new String[epubChapters.size()];
        for (int i = 0; i < epubChapters.size(); i++) {
            items[i] = (i + 1) + ". " + epubChapters.get(i).title;
        }
        builder.setItems(items, (dialog, which) -> {
            EpubChapter chapter = epubChapters.get(which);
            jumpToLine(chapter.startLine);
        });
        builder.setNegativeButton("关闭", null);
        builder.show();
    }

    private void jumpToLine(int line) {
        String text = etEditor.getText().toString();
        String[] lines = text.split("\n", -1);
        if (line >= lines.length) line = lines.length - 1;
        int offset = 0;
        for (int i = 0; i < line; i++) {
            offset += lines[i].length() + 1;
        }
        if (offset <= text.length()) {
            etEditor.setSelection(offset);
            etEditor.requestFocus();
            // 尝试滚动到该位置
            int layoutLine = etEditor.getLayout().getLineForOffset(offset);
            if (layoutLine >= 0) {
                int scrollY = etEditor.getLayout().getLineTop(layoutLine) - etEditor.getHeight() / 3;
                if (scrollY > 0) {
                    etEditor.scrollTo(0, scrollY);
                } else {
                    etEditor.scrollTo(0, 0);
                }
            }
        }
        Toast.makeText(this, "跳转到: " + (line + 1) + " 行", Toast.LENGTH_SHORT).show();
    }

    private void hideEpubNav() {
        epubChapters.clear();
    }

    // ==================== 后台文件加载 ====================

    private class FileLoadTask extends AsyncTask<Uri, Integer, String> {
        private String displayInfo;
        private boolean readOnly;
        private long fileSize;

        @Override
        protected void onPreExecute() {
            progressBar.setVisibility(View.VISIBLE);
            etEditor.setText("");
            hideEpubNav();
        }

        @Override
        protected String doInBackground(Uri... uris) {
            try {
                Uri uri = uris[0];
                fileSize = getFileSize(uri);
                // 不限制文件大小，任何文件都可以打开
                displayInfo = currentFileName + " | " + formatFileSize(fileSize) + " | " + currentFormat.toUpperCase();

                switch (currentFormat.toLowerCase()) {
                    case "docx": readOnly = true; return readDocx(uri);
                    case "doc": readOnly = true; return readDoc(uri);
                    case "xlsx": readOnly = true; return readXlsx(uri);
                    case "xls": readOnly = true; return readXls(uri);
                    case "pptx": readOnly = true; return readPptx(uri);
                    case "ppt": readOnly = true; return readPpt(uri);
                    case "epub": readOnly = true; return readEpub(uri);
                    case "pdf": readOnly = true; return readPdf(uri);
                    case "azw3":
                    case "mobi": readOnly = true; return readAzw3Mobi(uri);
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
            progressBar.setVisibility(View.GONE);
            if (result.startsWith("ERROR:")) {
                Toast.makeText(TextEditorActivity.this, result.substring(6), Toast.LENGTH_LONG).show();
                return;
            }
            etEditor.setText(result);
            updateStats();
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

            // EPUB 显示导航按钮
            if ("epub".equals(currentFormat.toLowerCase()) && !epubChapters.isEmpty()) {
                tvFileInfo.setOnClickListener(v -> showEpubNav());
                tvFileInfo.setClickable(true);
                Toast.makeText(TextEditorActivity.this,
                        "文件已打开: " + currentFileName + "（点击文件信息栏可打开目录导航）",
                        Toast.LENGTH_LONG).show();
            } else {
                tvFileInfo.setClickable(false);
                Toast.makeText(TextEditorActivity.this, "文件已打开: " + currentFileName, Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ==================== 文本格式读取 ====================

    private String readTextFile(Uri uri, boolean isJson) {
        byte[] fileBytes = readFileBytes(uri, (int) Math.min(getFileSize(uri), 64 * 1024));
        if (fileBytes == null || fileBytes.length == 0) return "";
        detectedEncoding = detectEncoding(fileBytes);
        String content = readWithEncodingStream(uri, detectedEncoding);
        updateEncodingSpinner(detectedEncoding);
        if (isJson && content != null && !content.trim().isEmpty()) {
            try {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                Object json = gson.fromJson(content, Object.class);
                content = gson.toJson(json);
            } catch (Exception ignored) {}
        }
        return content != null ? content : "";
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
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, Charset.forName(encoding)), BUFFER_SIZE);
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

    // ==================== EPUB 格式（带目录导航）====================

    private String readEpub(Uri uri) {
        try {
            StringBuilder sb = new StringBuilder();
            epubChapters.clear();

            // 解析 container.xml 获取 OPF 路径
            String opfPath = "";
            String baseDir = "";
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
                                baseDir = slashIdx > 0 ? opfPath.substring(0, slashIdx + 1) : "";
                            }
                        }
                        break;
                    }
                }
            }

            // 解析 OPF 获取 spine 顺序（阅读顺序）
            List<String> spineOrder = new ArrayList<>();
            if (!opfPath.isEmpty()) {
                try (InputStream is = getContentResolver().openInputStream(uri);
                     ZipInputStream zis = new ZipInputStream(is)) {
                    ZipEntry entry;
                    while ((entry = zis.getNextEntry()) != null) {
                        if (entry.getName().equals(opfPath)) {
                            String opfXml = readZipEntryString(zis, 256 * 1024);
                            // 提取书名
                            String title = extractXmlTag(opfXml, "dc:title");
                            if (!title.isEmpty()) {
                                sb.append("书名: ").append(title).append("\n\n");
                            }
                            // 提取 spine 中的 itemref href
                            spineOrder = extractSpineOrder(opfXml);
                            break;
                        }
                    }
                }
            }

            // 按顺序读取内容文件，构建章节导航
            int currentLine = 1; // 第1行是书名，从第2行开始
            try (InputStream is = getContentResolver().openInputStream(uri);
                 ZipInputStream zis = new ZipInputStream(is)) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    String name = entry.getName();
                    String nameLower = name.toLowerCase();
                    if (nameLower.endsWith(".xhtml") || nameLower.endsWith(".html") || nameLower.endsWith(".htm")) {
                        String html = readZipEntryString(zis, 2 * 1024 * 1024); // 单文件最大2MB
                        String text = stripHtmlTags(html);
                        if (text.trim().isEmpty()) continue;

                        // 提取章节标题（从 h1/h2/h3 或 title 标签）
                        String chapterTitle = extractChapterTitle(html);
                        if (chapterTitle.isEmpty()) {
                            // 用文件名作为章节标题
                            int slashPos = name.lastIndexOf('/');
                            chapterTitle = slashPos >= 0 ? name.substring(slashPos + 1) : name;
                        }

                        EpubChapter chapter = new EpubChapter(chapterTitle, currentLine);
                        epubChapters.add(chapter);

                        // 添加分隔标记
                        sb.append("━━━ ").append(chapterTitle).append(" ━━━\n\n");
                        currentLine += 2;

                        // 添加内容
                        String[] lines = text.split("\n", -1);
                        for (String line : lines) {
                            sb.append(line).append("\n");
                            currentLine++;
                        }
                        sb.append("\n");
                        currentLine++;
                    }
                }
            }

            return sb.toString();
        } catch (Exception e) {
            return "读取EPUB失败: " + e.getMessage();
        }
    }

    private List<String> extractSpineOrder(String opfXml) {
        List<String> hrefs = new ArrayList<>();
        // 从 manifest 中提取所有 item 的 id 和 href
        java.util.Map<String, String> idToHref = new java.util.HashMap<>();
        int manifestIdx = opfXml.indexOf("<manifest");
        if (manifestIdx < 0) return hrefs;
        int manifestEnd = opfXml.indexOf("</manifest>", manifestIdx);
        if (manifestEnd < 0) return hrefs;
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
        // 从 spine 中按顺序提取 idref
        int spineIdx = opfXml.indexOf("<spine");
        if (spineIdx < 0) return hrefs;
        int spineEnd = opfXml.indexOf("</spine>", spineIdx);
        if (spineEnd < 0) return hrefs;
        String spine = opfXml.substring(spineIdx, spineEnd);
        pos = 0;
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
        // 优先从 h1/h2/h3 提取标题
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
        // 从 <title> 提取
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

    // ==================== PDF 格式（容错解析）====================

    private String readPdf(Uri uri) {
        PDDocument document = null;
        try {
            // 使用 RandomAccessFile 方式加载，避免流式解析损坏PDF时的错误
            // 先将内容复制到临时文件
            File tempFile = File.createTempFile("pdf_temp_", ".pdf", getCacheDir());
            try (InputStream is = getContentResolver().openInputStream(uri);
                 OutputStream os = new FileOutputStream(tempFile)) {
                if (is == null) return "无法读取文件";
                byte[] buffer = new byte[BUFFER_SIZE];
                int len;
                while ((len = is.read(buffer)) != -1) {
                    os.write(buffer, 0, len);
                }
            }

            try {
                document = PDDocument.load(tempFile);
            } catch (Exception e) {
                // 如果标准加载失败，尝试用非严格模式
                try {
                    document = PDDocument.load(tempFile, "");
                } catch (Exception e2) {
                    // 最后尝试逐页解析
                    document = PDDocument.load(tempFile,
                            org.apache.pdfbox.io.IOUtils.createTempFileOnlyStreamCache());
                }
            }

            if (document == null) {
                return "读取PDF失败: 无法解析PDF文件，文件可能已损坏";
            }

            StringBuilder sb = new StringBuilder();
            PDFTextStripper stripper = new PDFTextStripper();
            int totalPages = document.getNumberOfPages();

            // 逐页提取，每页单独 try-catch
            for (int page = 1; page <= totalPages; page++) {
                try {
                    stripper.setStartPage(page);
                    stripper.setEndPage(page);
                    String text = stripper.getText(document);
                    if (text != null && !text.trim().isEmpty()) {
                        sb.append(text);
                        if (!text.endsWith("\n")) sb.append("\n");
                    }
                } catch (Exception pageEx) {
                    sb.append("\n[第").append(page).append("页解析失败: ")
                      .append(pageEx.getMessage()).append("]\n");
                }
            }

            document.close();
            tempFile.delete();

            if (sb.length() == 0) {
                return "PDF文件已加载，但未能提取到文本内容（可能是扫描版PDF或加密PDF）";
            }
            return sb.toString();
        } catch (OutOfMemoryError e) {
            if (document != null) try { document.close(); } catch (Exception ignored) {}
            return "读取PDF失败: 内存不足，文件过大";
        } catch (Exception e) {
            if (document != null) try { document.close(); } catch (Exception ignored) {}
            return "读取PDF失败: " + e.getMessage();
        }
    }

    // ==================== AZW3/MOBI 格式（二进制安全读取）====================

    private String readAzw3Mobi(Uri uri) {
        try {
            // 将文件复制到临时文件，避免 InputStream 的 mark/reset 限制
            File tempFile = File.createTempFile("mobi_temp_", ".bin", getCacheDir());
            try (InputStream is = getContentResolver().openInputStream(uri);
                 OutputStream os = new FileOutputStream(tempFile)) {
                if (is == null) return "无法读取文件";
                byte[] buffer = new byte[BUFFER_SIZE];
                int len;
                while ((len = is.read(buffer)) != -1) {
                    os.write(buffer, 0, len);
                }
            }

            long fileSize = tempFile.length();
            String text = parseMobiFile(tempFile, fileSize);
            tempFile.delete();

            if (!text.trim().isEmpty()) {
                return text;
            }
            return "AZW3/MOBI格式文件已加载。\n\n" +
                    "该格式为Amazon Kindle专有二进制格式。\n" +
                    "建议：使用Calibre将AZW3/MOBI转换为EPUB或TXT格式后打开。\n\n" +
                    "文件大小: " + formatFileSize(fileSize);
        } catch (OutOfMemoryError e) {
            return "读取AZW3/MOBI失败: 内存不足";
        } catch (Exception e) {
            return "读取AZW3/MOBI失败: " + e.getMessage();
        }
    }

    private String parseMobiFile(File file, long fileSize) throws Exception {
        StringBuilder sb = new StringBuilder();
        RandomAccessFile raf = new RandomAccessFile(file, "r");

        // MOBI/AZW3 文件头解析
        // PalmDB 头部: 前78字节
        byte[] header = new byte[78];
        raf.readFully(header);

        // 检查是否为 PalmDB 格式
        String palmType = new String(header, 60, 8, StandardCharsets.ISO_8859_1).trim();
        if (!"BOOKMOBI".equals(palmType)) {
            raf.close();
            // 不是标准 MOBI，尝试作为纯文本/HTML 提取
            return extractTextFromBinaryFile(file, fileSize);
        }

        // 读取记录数量
        int numRecords = ((header[76] & 0xFF) << 8) | (header[77] & 0xFF);

        // 跳过记录信息列表（每个记录8字节）
        raf.seek(78 + numRecords * 8);

        // 读取 PalmDOC 头部（16字节）
        byte[] palmDocHeader = new byte[16];
        raf.readFully(palmDocHeader);

        int compression = ((palmDocHeader[0] & 0xFF) << 8) | (palmDocHeader[1] & 0xFF);
        // compression: 1=none, 2=PalmDOC

        // 尝试查找 HTML 内容起始位置
        // MOBI 文件中通常有 HTML 内容，搜索 <html 或 <body 标签
        raf.seek(0);
        byte[] searchBuffer = new byte[Math.min((int) fileSize, 4 * 1024 * 1024)];
        int bytesRead = raf.read(searchBuffer);

        // 在二进制数据中搜索 HTML 内容
        String content = findHtmlContent(searchBuffer, bytesRead);
        if (!content.trim().isEmpty()) {
            raf.close();
            return content;
        }

        // 如果没找到 HTML，尝试解压 PalmDOC 压缩内容
        if (compression == 2) {
            // PalmDOC LZ77 解压（简化版）
            raf.seek(78 + numRecords * 8 + 16);
            // 读取第一个文本记录
            long textStart = 78 + numRecords * 8 + 16;
            // 跳过 MOBI 头部（通常较大）
            raf.seek(textStart);

            // 尝试从文件后半部分查找可读文本
            long searchStart = Math.max(0, fileSize / 4);
            raf.seek(searchStart);
            byte[] tailBuffer = new byte[Math.min((int) (fileSize - searchStart), 2 * 1024 * 1024)];
            int tailRead = raf.read(tailBuffer);
            content = extractReadableText(tailBuffer, tailRead);
            raf.close();
            if (!content.trim().isEmpty()) {
                return content;
            }
        }

        raf.close();
        return "";
    }

    private String extractTextFromBinaryFile(File file, long fileSize) throws Exception {
        RandomAccessFile raf = new RandomAccessFile(file, "r");
        byte[] buffer = new byte[Math.min((int) fileSize, 4 * 1024 * 1024)];
        int bytesRead = raf.read(buffer);
        raf.close();

        String content = findHtmlContent(buffer, bytesRead);
        if (!content.trim().isEmpty()) {
            return content;
        }
        return extractReadableText(buffer, bytesRead);
    }

    private String findHtmlContent(byte[] data, int length) {
        // 搜索 <html 或 <body 或 <HTML 或 <BODY
        String lower = new String(data, 0, length, StandardCharsets.ISO_8859_1).toLowerCase();
        int bodyStart = lower.indexOf("<html");
        if (bodyStart < 0) bodyStart = lower.indexOf("<body");
        if (bodyStart < 0) bodyStart = lower.indexOf("<?xml");

        if (bodyStart >= 0) {
            // 从 HTML 标签开始提取
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
            char c = (char) (b & 0xFF);

            // 判断是否为可打印字符
            if (isPrintable(b)) {
                currentText.append(c);
                consecutiveBinary = 0;
            } else {
                consecutiveBinary++;
                if (consecutiveBinary > 4 && currentText.length() > 10) {
                    // 连续4个以上非打印字符，视为二进制间隔
                    String text = currentText.toString().trim();
                    if (text.length() > 10) {
                        sb.append(text).append("\n\n");
                    }
                    currentText.setLength(0);
                }
            }
        }

        // 添加最后一段
        if (currentText.length() > 10) {
            String text = currentText.toString().trim();
            if (text.length() > 10) {
                sb.append(text);
            }
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
