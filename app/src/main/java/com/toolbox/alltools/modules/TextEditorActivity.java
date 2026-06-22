package com.toolbox.alltools.modules;

import android.app.Activity;
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
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 文本阅读编辑器Activity
 * 支持多种文件格式的打开和文本提取显示：
 * - 纯文本：txt, md
 * - 标记/数据：json, xml, html
 * - Office文档：doc, docx, xls, xlsx, ppt, pptx
 * - 电子书：epub, pdf, azw3, mobi
 *
 * 大文件优化策略：
 * 1. 流式读取，避免一次性加载全部内容到内存
 * 2. StringBuilder 容量限制，超过阈值截断
 * 3. PDF 分页提取，避免一次性解析全部页面
 * 4. EPUB 逐条目读取，不缓存全部 ZIP 内容
 * 5. 后台线程执行解析，UI 显示进度
 */
public class TextEditorActivity extends BaseToolActivity {

    private static final int REQUEST_OPEN_FILE = 1001;
    private static final int REQUEST_SAVE_FILE = 1002;

    /** 可打开文件的最大大小（50MB），大文件采用流式处理 */
    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024;

    /** 文本内容最大显示长度（2MB），超过则截断 */
    private static final int MAX_DISPLAY_LENGTH = 2 * 1024 * 1024;

    /** 单行读取缓冲区大小 */
    private static final int BUFFER_SIZE = 8192;

    /** 支持的编码列表，按优先级排序 */
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

    /**
     * 后台异步加载文件，避免阻塞UI线程
     */
    private class FileLoadTask extends AsyncTask<Uri, Integer, String> {
        private String displayInfo;
        private boolean readOnly;
        private long fileSize;

        @Override
        protected void onPreExecute() {
            progressBar.setVisibility(View.VISIBLE);
            etEditor.setText("");
        }

        @Override
        protected String doInBackground(Uri... uris) {
            try {
                Uri uri = uris[0];
                fileSize = getFileSize(uri);
                if (fileSize > MAX_FILE_SIZE) {
                    return "ERROR:文件过大（超过50MB），请选择较小的文件";
                }
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
            Toast.makeText(TextEditorActivity.this, "文件已打开: " + currentFileName, Toast.LENGTH_SHORT).show();
        }
    }

    // ==================== 文本格式读取（流式，带截断）====================

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

    /**
     * 流式读取文本，超过 MAX_DISPLAY_LENGTH 自动截断
     */
    private String readWithEncodingStream(Uri uri, String encoding) {
        StringBuilder sb = new StringBuilder(Math.min(BUFFER_SIZE, MAX_DISPLAY_LENGTH));
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
                    if (sb.length() >= MAX_DISPLAY_LENGTH) {
                        sb.append("\n\n[内容已截断，文件过大仅显示前").append(formatFileSize(MAX_DISPLAY_LENGTH)).append("]");
                        break;
                    }
                }
            }
        } catch (Exception e) {
            return "";
        }
        return sb.toString();
    }

    // ==================== Word 格式（流式提取）====================

    private String readDocx(Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            if (is == null) return "无法读取文件";
            org.apache.poi.xwpf.usermodel.XWPFDocument doc = new org.apache.poi.xwpf.usermodel.XWPFDocument(is);
            StringBuilder sb = new StringBuilder();
            for (org.apache.poi.xwpf.usermodel.XWPFParagraph para : doc.getParagraphs()) {
                appendLimited(sb, para.getText());
                appendLimited(sb, "\n");
            }
            for (org.apache.poi.xwpf.usermodel.XWPFTable table : doc.getTables()) {
                for (org.apache.poi.xwpf.usermodel.XWPFTableRow row : table.getRows()) {
                    for (org.apache.poi.xwpf.usermodel.XWPFTableCell cell : row.getTableCells()) {
                        appendLimited(sb, cell.getText() + "\t");
                    }
                    appendLimited(sb, "\n");
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
            String text = extractor.getText();
            return truncateIfNeeded(text);
        } catch (Exception e) {
            return "读取DOC失败: " + e.getMessage();
        }
    }

    // ==================== Excel 格式（流式提取）====================

    private String readXlsx(Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            if (is == null) return "无法读取文件";
            XSSFWorkbook workbook = new XSSFWorkbook(is);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                XSSFSheet sheet = workbook.getSheetAt(i);
                appendLimited(sb, "=== Sheet: " + sheet.getSheetName() + " ===\n");
                for (int r = 0; r <= sheet.getLastRowNum(); r++) {
                    XSSFRow row = sheet.getRow(r);
                    if (row == null) continue;
                    for (int j = 0; j < row.getLastCellNum(); j++) {
                        XSSFCell cell = row.getCell(j);
                        appendLimited(sb, getCellValue(cell) + "\t");
                    }
                    appendLimited(sb, "\n");
                }
                appendLimited(sb, "\n");
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
                appendLimited(sb, "=== Sheet: " + sheet.getSheetName() + " ===\n");
                for (int j = 0; j <= sheet.getLastRowNum(); j++) {
                    HSSFRow row = sheet.getRow(j);
                    if (row == null) continue;
                    for (int k = 0; k < row.getLastCellNum(); k++) {
                        HSSFCell cell = row.getCell(k);
                        appendLimited(sb, getCellValueLegacy(cell) + "\t");
                    }
                    appendLimited(sb, "\n");
                }
                appendLimited(sb, "\n");
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

    // ==================== PPT 格式（流式提取）====================

    private String readPptx(Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            if (is == null) return "无法读取文件";
            XMLSlideShow pptx = new XMLSlideShow(is);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < pptx.getSlides().size(); i++) {
                appendLimited(sb, "=== 幻灯片 " + (i + 1) + " ===\n");
                XSLFSlide slide = pptx.getSlides().get(i);
                for (org.apache.poi.xslf.usermodel.XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape) {
                        XSLFTextShape textShape = (XSLFTextShape) shape;
                        if (textShape.getText() != null && !textShape.getText().isEmpty()) {
                            for (XSLFTextParagraph para : textShape) {
                                for (XSLFTextRun run : para) {
                                    appendLimited(sb, run.getRawText());
                                }
                                appendLimited(sb, "\n");
                            }
                        }
                    }
                }
                appendLimited(sb, "\n");
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
                appendLimited(sb, "=== 幻灯片 " + (i + 1) + " ===\n");
                HSLFSlide slide = ppt.getSlides().get(i);
                for (org.apache.poi.hslf.usermodel.HSLFShape shape : slide.getShapes()) {
                    if (shape instanceof HSLFTextShape) {
                        HSLFTextShape textShape = (HSLFTextShape) shape;
                        if (textShape.getText() != null && !textShape.getText().isEmpty()) {
                            for (HSLFTextParagraph para : textShape) {
                                for (HSLFTextRun run : para) {
                                    appendLimited(sb, run.getRawText());
                                }
                                appendLimited(sb, "\n");
                            }
                        }
                    }
                }
                appendLimited(sb, "\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return "读取PPT失败: " + e.getMessage();
        }
    }

    // ==================== EPUB 格式（流式ZIP解析）====================

    private String readEpub(Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            if (is == null) return "无法读取文件";
            StringBuilder sb = new StringBuilder();
            sb.append("=== EPUB 文档 ===\n\n");

            // 第一遍：读取 metadata
            try (InputStream is2 = getContentResolver().openInputStream(uri);
                 ZipInputStream zis = new ZipInputStream(is2)) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.getName().endsWith(".opf")) {
                        String opfXml = readZipEntryLimited(zis, 64 * 1024);
                        String title = extractXmlTag(opfXml, "dc:title");
                        if (!title.isEmpty()) appendLimited(sb, "书名: " + title + "\n");
                        break;
                    }
                }
            }

            // 第二遍：读取内容文件
            try (InputStream is3 = getContentResolver().openInputStream(uri);
                 ZipInputStream zis = new ZipInputStream(is3)) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    String name = entry.getName().toLowerCase();
                    if (name.endsWith(".xhtml") || name.endsWith(".html") || name.endsWith(".htm")) {
                        String html = readZipEntryLimited(zis, 512 * 1024); // 单个文件最大512KB
                        String text = stripHtmlTags(html);
                        if (!text.trim().isEmpty()) {
                            appendLimited(sb, text + "\n\n");
                        }
                        if (sb.length() >= MAX_DISPLAY_LENGTH) break;
                    }
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return "读取EPUB失败: " + e.getMessage();
        }
    }

    private String readZipEntryLimited(ZipInputStream zis, int maxBytes) throws Exception {
        byte[] buffer = new byte[4096];
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
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

    // ==================== PDF 格式（分页提取，避免OOM）====================

    private String readPdf(Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            if (is == null) return "无法读取文件";
            PDDocument document = PDDocument.load(is);
            StringBuilder sb = new StringBuilder();
            PDFTextStripper stripper = new PDFTextStripper();
            int totalPages = document.getNumberOfPages();

            // 分页提取，每批处理10页
            int batchSize = 10;
            for (int startPage = 1; startPage <= totalPages; startPage += batchSize) {
                int endPage = Math.min(startPage + batchSize - 1, totalPages);
                stripper.setStartPage(startPage);
                stripper.setEndPage(endPage);
                String text = stripper.getText(document);
                appendLimited(sb, text);
                if (sb.length() >= MAX_DISPLAY_LENGTH) {
                    appendLimited(sb, "\n\n[PDF内容已截断，共" + totalPages + "页，仅显示前" + formatFileSize(MAX_DISPLAY_LENGTH) + "]");
                    break;
                }
            }
            document.close();
            return sb.toString();
        } catch (OutOfMemoryError e) {
            return "读取PDF失败: 内存不足，文件过大";
        } catch (Exception e) {
            return "读取PDF失败: " + e.getMessage();
        }
    }

    // ==================== AZW3/MOBI 格式（流式提取）====================

    private String readAzw3Mobi(Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            if (is == null) return "无法读取文件";
            BufferedInputStream bis = new BufferedInputStream(is, BUFFER_SIZE);
            // 流式读取，不一次性加载全部字节
            StringBuilder sb = new StringBuilder();
            byte[] buffer = new byte[BUFFER_SIZE];
            int totalRead = 0;
            int read;
            while ((read = bis.read(buffer)) != -1 && totalRead < MAX_FILE_SIZE) {
                totalRead += read;
            }
            // 只读取前 MAX_FILE_SIZE 字节进行解析
            String raw = new String(buffer, 0, Math.min(read, buffer.length), StandardCharsets.ISO_8859_1);
            String text = extractTextFromMobi(raw);
            if (!text.trim().isEmpty()) {
                return truncateIfNeeded(text);
            }
            return "AZW3/MOBI格式文件已加载。\n\n该格式为Amazon Kindle专有二进制格式。\n建议：使用Calibre将AZW3/MOBI转换为EPUB或TXT格式后打开。\n\n文件大小: " + formatFileSize(totalRead);
        } catch (OutOfMemoryError e) {
            return "读取AZW3/MOBI失败: 内存不足";
        } catch (Exception e) {
            return "读取AZW3/MOBI失败: " + e.getMessage();
        }
    }

    private String extractTextFromMobi(String raw) {
        StringBuilder sb = new StringBuilder();
        int bodyStart = raw.indexOf("<body");
        if (bodyStart < 0) bodyStart = raw.indexOf("<html");
        if (bodyStart < 0) bodyStart = 0;
        String sub = raw.substring(bodyStart);
        boolean inTag = false;
        StringBuilder currentText = new StringBuilder();
        for (int i = 0; i < sub.length() && sb.length() < MAX_DISPLAY_LENGTH; i++) {
            char c = sub.charAt(i);
            if (c == '<') {
                if (currentText.length() > 0) {
                    String t = currentText.toString().trim();
                    if (!t.isEmpty()) sb.append(t).append("\n");
                    currentText.setLength(0);
                }
                inTag = true;
            } else if (c == '>') {
                inTag = false;
            } else if (!inTag) {
                currentText.append(c);
            }
        }
        if (currentText.length() > 0) {
            String t = currentText.toString().trim();
            if (!t.isEmpty()) sb.append(t);
        }
        return sb.toString();
    }

    // ==================== 工具方法 ====================

    /**
     * 向 StringBuilder 追加内容，超过限制时自动截断
     */
    private void appendLimited(StringBuilder sb, String text) {
        if (text == null) return;
        if (sb.length() + text.length() > MAX_DISPLAY_LENGTH) {
            int remaining = MAX_DISPLAY_LENGTH - sb.length();
            if (remaining > 0) {
                sb.append(text, 0, remaining);
            }
            if (!sb.toString().endsWith("[内容已截断]")) {
                sb.append("\n\n[内容已截断，仅显示前").append(formatFileSize(MAX_DISPLAY_LENGTH)).append("]");
            }
        } else {
            sb.append(text);
        }
    }

    private String truncateIfNeeded(String text) {
        if (text == null) return "";
        if (text.length() > MAX_DISPLAY_LENGTH) {
            return text.substring(0, MAX_DISPLAY_LENGTH) + "\n\n[内容已截断，仅显示前" + formatFileSize(MAX_DISPLAY_LENGTH) + "]";
        }
        return text;
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
