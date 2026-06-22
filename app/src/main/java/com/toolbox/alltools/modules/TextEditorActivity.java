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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.toolbox.alltools.R;
import com.toolbox.alltools.base.BaseToolActivity;

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

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import nl.siegmann.epublib.domain.Book;
import nl.siegmann.epublib.epub.EpubReader;

import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;

import com.toolbox.alltools.config.AppConfig;

/**
 * 文本阅读编辑器Activity
 * 支持多种文件格式的打开和文本提取显示：
 * - 纯文本：txt, md
 * - 标记/数据：json, xml, html
 * - Office文档：doc, docx, xls, xlsx, ppt, pptx
 * - 电子书：epub, pdf, azw3, mobi
 */
public class TextEditorActivity extends BaseToolActivity {

    private static final int REQUEST_OPEN_FILE = 1001;
    private static final int REQUEST_SAVE_FILE = 1002;

    /** 可打开文件的最大大小（10MB），防止OOM */
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

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
    private TextView tvFileInfo;
    private ImageButton btnOpenFile;
    private ImageButton btnNewFile;
    private ImageButton btnSearch;
    private ImageButton btnSave;
    private Spinner spinnerEncoding;

    private Uri currentFileUri;
    private String currentFileName = "";
    /** 当前文件检测到的编码 */
    private String detectedEncoding = "UTF-8";
    /** 当前文件格式 */
    private String currentFormat = "txt";
    /** 是否为只读格式（二进制格式只能查看，不能编辑保存） */
    private boolean isReadOnlyFormat = false;

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
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                // 纯文本
                "text/plain",
                "text/markdown",
                // 标记/数据
                "text/html", "application/xhtml+xml",
                "application/json",
                "application/xml", "text/xml",
                // Word
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                // Excel
                "application/vnd.ms-excel",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                // PPT
                "application/vnd.ms-powerpoint",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                // EPUB
                "application/epub+zip",
                // PDF
                "application/pdf",
                // 通用（用于azw3/mobi等无标准MIME类型的文件）
                "application/octet-stream"
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
            Toast.makeText(this, "当前格式为只读模式，请另存为txt文件",
                    Toast.LENGTH_LONG).show();
        }
        if (currentFileUri != null) {
            saveToFile(currentFileUri);
        } else {
            // 默认保存到 sdcard/ToolBox/TextEditor/
            saveToDefaultPath();
        }
    }

    /**
     * 保存到默认工作路径 sdcard/ToolBox/TextEditor/
     * 同时提供另存为可选路径功能
     */
    private void saveToDefaultPath() {
        String encoding = (String) spinnerEncoding.getSelectedItem();
        if (encoding == null) encoding = "UTF-8";

        File moduleDir = AppConfig.getModuleDir(AppConfig.DIR_TEXT_EDITOR);
        String fileName = TextUtils.isEmpty(currentFileName) ? "untitled.txt" : currentFileName;
        File outputFile = new File(moduleDir, fileName);

        // 处理重名
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
            Toast.makeText(this,
                    "已保存到: " + outputFile.getAbsolutePath() + " (" + encoding + ")",
                    Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
            // 获取文件名
            currentFileName = getFileName(currentFileUri);
            currentFormat = getFileExtension(currentFileName);
            readFileByFormat(currentFileUri, currentFormat);
        } else if (requestCode == REQUEST_SAVE_FILE) {
            currentFileUri = data.getData();
            isReadOnlyFormat = false;
            saveToFile(currentFileUri);
        }
    }

    /**
     * 根据文件格式分发读取逻辑
     */
    private void readFileByFormat(Uri uri, String format) {
        try {
            long fileSize = getFileSize(uri);
            if (fileSize > MAX_FILE_SIZE) {
                Toast.makeText(this, "文件过大（超过10MB），请选择较小的文件",
                        Toast.LENGTH_LONG).show();
                return;
            }

            String content;
            String displayInfo = currentFileName + " | " + formatFileSize(fileSize) + " | " + format.toUpperCase();

            switch (format.toLowerCase()) {
                case "docx":
                    content = readDocx(uri);
                    isReadOnlyFormat = true;
                    break;
                case "doc":
                    content = readDoc(uri);
                    isReadOnlyFormat = true;
                    break;
                case "xlsx":
                    content = readXlsx(uri);
                    isReadOnlyFormat = true;
                    break;
                case "xls":
                    content = readXls(uri);
                    isReadOnlyFormat = true;
                    break;
                case "pptx":
                    content = readPptx(uri);
                    isReadOnlyFormat = true;
                    break;
                case "ppt":
                    content = readPpt(uri);
                    isReadOnlyFormat = true;
                    break;
                case "epub":
                    content = readEpub(uri);
                    isReadOnlyFormat = true;
                    break;
                case "pdf":
                    content = readPdf(uri);
                    isReadOnlyFormat = true;
                    break;
                case "azw3":
                case "mobi":
                    content = readAzw3Mobi(uri);
                    isReadOnlyFormat = true;
                    break;
                case "json":
                    content = readTextFile(uri, true);
                    isReadOnlyFormat = false;
                    break;
                case "html":
                case "htm":
                case "xml":
                case "md":
                case "txt":
                default:
                    content = readTextFile(uri, false);
                    isReadOnlyFormat = false;
                    break;
            }

            etEditor.setText(content);
            updateStats();
            tvFileInfo.setVisibility(View.VISIBLE);
            tvFileInfo.setText(displayInfo + (isReadOnlyFormat ? " | 只读" : " | 可编辑"));

            if (isReadOnlyFormat) {
                etEditor.setFocusable(false);
                etEditor.setFocusableInTouchMode(false);
                btnSave.setEnabled(true); // 允许另存为txt
            } else {
                etEditor.setFocusableInTouchMode(true);
                etEditor.setFocusable(true);
                btnSave.setEnabled(true);
            }

            Toast.makeText(this,
                    "文件已打开: " + currentFileName,
                    Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Toast.makeText(this, "打开文件失败: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    // ==================== 文本格式读取 ====================

    /**
     * 读取纯文本文件，自动检测编码
     */
    private String readTextFile(Uri uri, boolean isJson) {
        byte[] fileBytes = readFileBytes(uri, (int) Math.min(getFileSize(uri), 64 * 1024));
        if (fileBytes == null || fileBytes.length == 0) return "";

        detectedEncoding = detectEncoding(fileBytes);
        String content = readWithEncoding(uri, detectedEncoding);
        updateEncodingSpinner(detectedEncoding);

        // JSON格式化显示
        if (isJson && content != null && !content.trim().isEmpty()) {
            try {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                Object json = gson.fromJson(content, Object.class);
                content = gson.toJson(json);
            } catch (Exception ignored) {
                // JSON解析失败，保持原文
            }
        }

        return content != null ? content : "";
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
            // 读取表格
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
                for (XSSFRow row : sheet) {
                    if (row == null) continue;
                    for (int j = 0; j < row.getLastCellNum(); j++) {
                        XSSFCell cell = row.getCell(j);
                        String value = getCellValue(cell);
                        sb.append(value != null ? value : "").append("\t");
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
                        String value = getCellValueLegacy(cell);
                        sb.append(value != null ? value : "").append("\t");
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
                for (XSLFTextShape shape : slide.getShapes()) {
                    if (shape.getText() != null && !shape.getText().isEmpty()) {
                        for (XSLFTextParagraph para : shape) {
                            for (XSLFTextRun run : para) {
                                sb.append(run.getRawText());
                            }
                            sb.append("\n");
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
                for (HSLFTextShape shape : slide.getShapes()) {
                    if (shape.getText() != null && !shape.getText().isEmpty()) {
                        for (HSLFTextParagraph para : shape) {
                            for (HSLFTextRun run : para) {
                                sb.append(run.getRawText());
                            }
                            sb.append("\n");
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

    // ==================== EPUB 格式 ====================

    private String readEpub(Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            if (is == null) return "无法读取文件";
            EpubReader epubReader = new EpubReader();
            Book book = epubReader.readEpub(is);
            StringBuilder sb = new StringBuilder();

            // 书名
            sb.append("书名: ").append(book.getTitle() != null ? book.getTitle() : "未知").append("\n");
            // 作者
            if (book.getMetadata().getAuthors() != null) {
                sb.append("作者: ");
                for (String author : book.getMetadata().getAuthors()) {
                    sb.append(author).append(" ");
                }
                sb.append("\n");
            }
            sb.append("\n");

            // 读取所有章节内容
            for (nl.siegmann.epublib.domain.SpineReference ref : book.getSpine().getSpineReferences()) {
                try {
                    nl.siegmann.epublib.domain.Resource resource = ref.getResource();
                    if (resource.getMediaType() == nl.siegmann.epublib.domain.MediaType.XHTML) {
                        String html = new String(resource.getData(), StandardCharsets.UTF_8);
                        // 简单去除HTML标签提取纯文本
                        String text = stripHtmlTags(html);
                        if (!text.trim().isEmpty()) {
                            sb.append(text).append("\n\n");
                        }
                    }
                } catch (Exception ignored) {
                    // 跳过无法读取的章节
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return "读取EPUB失败: " + e.getMessage();
        }
    }

    // ==================== PDF 格式 ====================

    private String readPdf(Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            if (is == null) return "无法读取文件";
            PDDocument document = PDDocument.load(is);
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            document.close();
            return text != null ? text : "";
        } catch (Exception e) {
            return "读取PDF失败: " + e.getMessage();
        }
    }

    // ==================== AZW3/MOBI 格式 ====================

    private String readAzw3Mobi(Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            if (is == null) return "无法读取文件";
            // AZW3/MOBI 是二进制格式，尝试提取其中的文本内容
            // 读取全部字节，搜索HTML内容区域
            BufferedInputStream bis = new BufferedInputStream(is);
            byte[] data = new byte[(int) Math.min(getFileSize(uri), MAX_FILE_SIZE)];
            int totalRead = 0;
            while (totalRead < data.length) {
                int read = bis.read(data, totalRead, data.length - totalRead);
                if (read <= 0) break;
                totalRead += read;
            }

            // 搜索HTML body内容
            String raw = new String(data, 0, totalRead, StandardCharsets.ISO_8859_1);
            // 尝试提取HTML标签之间的文本
            String text = extractTextFromMobi(raw);
            if (!text.trim().isEmpty()) {
                return text;
            }

            // 如果提取不到结构化文本，返回提示
            return "AZW3/MOBI格式文件已加载。\n\n" +
                    "该格式为Amazon Kindle专有二进制格式，完整解析需要专用库支持。\n" +
                    "当前版本支持提取部分文本内容。\n\n" +
                    "建议：\n" +
                    "1. 使用Calibre将AZW3/MOBI转换为EPUB或TXT格式后打开\n" +
                    "2. 后续版本将增强对Kindle格式的支持\n\n" +
                    "文件大小: " + formatFileSize(totalRead) + "\n" +
                    "原始字节数: " + totalRead;
        } catch (Exception e) {
            return "读取AZW3/MOBI失败: " + e.getMessage() +
                    "\n\n建议使用Calibre将文件转换为EPUB或TXT格式后打开。";
        }
    }

    /**
     * 从MOBI原始数据中尝试提取文本
     */
    private String extractTextFromMobi(String raw) {
        StringBuilder sb = new StringBuilder();
        // 查找HTML标签中的文本内容
        int bodyStart = raw.indexOf("<body");
        if (bodyStart < 0) bodyStart = raw.indexOf("<html");
        if (bodyStart < 0) bodyStart = 0;

        // 简单提取标签间文本
        String sub = raw.substring(bodyStart);
        boolean inTag = false;
        StringBuilder currentText = new StringBuilder();
        for (int i = 0; i < sub.length() && sb.length() < MAX_FILE_SIZE; i++) {
            char c = sub.charAt(i);
            if (c == '<') {
                if (currentText.length() > 0) {
                    String t = currentText.toString().trim();
                    if (!t.isEmpty()) {
                        sb.append(t).append("\n");
                    }
                    currentText.setLength(0);
                }
                inTag = true;
            } else if (c == '>') {
                inTag = false;
            } else if (!inTag) {
                // 解码HTML实体
                if (c == '&') {
                    int semi = sub.indexOf(';', i);
                    if (semi > 0 && semi - i < 10) {
                        String entity = sub.substring(i, semi + 1);
                        String decoded = decodeHtmlEntity(entity);
                        currentText.append(decoded);
                        i = semi;
                        continue;
                    }
                }
                currentText.append(c);
            }
        }
        // 最后一段
        if (currentText.length() > 0) {
            String t = currentText.toString().trim();
            if (!t.isEmpty()) sb.append(t);
        }
        return sb.toString();
    }

    private String decodeHtmlEntity(String entity) {
        switch (entity) {
            case "&amp;": return "&";
            case "&lt;": return "<";
            case "&gt;": return ">";
            case "&quot;": return "\"";
            case "&apos;": return "'";
            case "&nbsp;": return " ";
            default: return " ";
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 去除HTML标签提取纯文本
     */
    private String stripHtmlTags(String html) {
        if (html == null) return "";
        StringBuilder sb = new StringBuilder();
        boolean inTag = false;
        for (int i = 0; i < html.length(); i++) {
            char c = html.charAt(i);
            if (c == '<') {
                inTag = true;
            } else if (c == '>') {
                inTag = false;
            } else if (!inTag) {
                sb.append(c);
            }
        }
        // 合并多余空行
        return sb.toString().replaceAll("\n{3,}", "\n\n").trim();
    }

    /**
     * 获取文件名
     */
    private String getFileName(Uri uri) {
        String displayName = "未知文件";
        try (android.database.Cursor cursor = getContentResolver().query(
                uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(
                        android.provider.OpenableColumns.DISPLAY_NAME);
                if (nameIndex >= 0) {
                    displayName = cursor.getString(nameIndex);
                }
            }
        } catch (Exception ignored) {}
        return displayName;
    }

    /**
     * 获取文件扩展名（小写）
     */
    private String getFileExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) return "txt";
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
            return fileName.substring(dotIndex + 1).toLowerCase();
        }
        return "txt";
    }

    /**
     * 格式化文件大小
     */
    private String formatFileSize(long size) {
        if (size <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB"};
        int digitGroups = (int) (Math.log10((double) size) / Math.log10(1024.0));
        digitGroups = Math.min(digitGroups, units.length - 1);
        return String.format("%.2f %s", size / Math.pow(1024.0, digitGroups), units[digitGroups]);
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
     */
    private String detectEncoding(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return "UTF-8";

        // BOM检测
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

    private String readWithEncoding(Uri uri, String encoding) {
        StringBuilder sb = new StringBuilder();
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
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, Charset.forName(encoding)));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (sb.length() > 0) sb.append("\n");
                    sb.append(line);
                    if (sb.length() > MAX_FILE_SIZE) break;
                }
            }
        } catch (Exception e) {
            return "";
        }
        return sb.toString();
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
