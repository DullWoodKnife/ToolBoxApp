package com.toolbox.alltools.modules;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.toolbox.alltools.R;
import com.toolbox.alltools.base.BaseToolActivity;
import com.toolbox.alltools.config.AppConfig;

import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.usermodel.Range;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.hslf.usermodel.HSLFSlide;
import org.apache.poi.hslf.usermodel.HSLFTextShape;
import org.apache.poi.hslf.usermodel.HSLFTextParagraph;
import org.apache.poi.hslf.usermodel.HSLFTextRun;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;

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
import java.util.concurrent.atomic.AtomicBoolean;

import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;

/**
 * 文本格式转换器Activity
 * 支持多种文件格式之间的文本提取和转换：
 * - 纯文本：txt, md
 * - 标记/数据：json, xml, html
 * - Office文档：doc, docx, xls, xlsx, ppt, pptx
 * - 电子书：epub, pdf, azw3, mobi
 */
public class TextConverterActivity extends BaseToolActivity {

    private static final int REQUEST_SELECT_INPUT = 4001;
    private static final int REQUEST_SELECT_OUTPUT = 4002;

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    private static final String[] SOURCE_FORMATS = {
            "TXT", "MD", "JSON", "XML", "HTML",
            "DOC", "DOCX", "XLS", "XLSX", "PPT", "PPTX",
            "EPUB", "PDF", "AZW3", "MOBI"
    };

    private static final String[] TARGET_FORMATS = {
            "TXT", "MD", "JSON", "XML", "HTML",
            "EPUB", "PDF", "MOBI", "AZW3", "DOCX"
    };

    private Spinner spinnerSourceFormat;
    private Spinner spinnerTargetFormat;
    private MaterialButton btnSelectInput;
    private MaterialButton btnSelectOutput;
    private MaterialButton btnConvert;
    private MaterialButton btnSave;
    private EditText etPreview;
    private TextView tvInputInfo;
    private TextView tvOutputPath;
    private ProgressBar progressBar;

    private Uri inputFileUri;
    private Uri outputFileUri;
    private String outputFilePath = "";
    private String inputFileName = "";
    private String extractedText = "";

    private final AtomicBoolean isAlive = new AtomicBoolean(true);

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
        spinnerSourceFormat = findViewById(R.id.spinner_source_format);
        spinnerTargetFormat = findViewById(R.id.spinner_target_format);
        btnSelectInput = findViewById(R.id.btn_select_input);
        btnSelectOutput = findViewById(R.id.btn_select_output);
        btnConvert = findViewById(R.id.btn_convert);
        btnSave = findViewById(R.id.btn_save);
        etPreview = findViewById(R.id.et_preview);
        tvInputInfo = findViewById(R.id.tv_input_info);
        tvOutputPath = findViewById(R.id.tv_output_path);
        progressBar = findViewById(R.id.progress_bar);

        ArrayAdapter<String> sourceAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, SOURCE_FORMATS);
        sourceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSourceFormat.setAdapter(sourceAdapter);

        ArrayAdapter<String> targetAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, TARGET_FORMATS);
        targetAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTargetFormat.setAdapter(targetAdapter);
    }

    @Override
    protected void initListeners() {
        btnSelectInput.setOnClickListener(v -> selectInputFile());
        btnSelectOutput.setOnClickListener(v -> selectOutputPath());
        btnConvert.setOnClickListener(v -> startConvert());
        btnSave.setOnClickListener(v -> saveResult());
    }

    @Override
    protected void initData() {
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isAlive.set(false);
    }

    private void selectInputFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "text/plain", "text/markdown", "text/html",
                "application/json", "application/xml", "text/xml",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.ms-excel",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.ms-powerpoint",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                "application/epub+zip",
                "application/pdf",
                "application/octet-stream"
        });
        startActivityForResult(intent, REQUEST_SELECT_INPUT);
    }

    private void selectOutputPath() {
        String targetFormat = (String) spinnerTargetFormat.getSelectedItem();
        String defaultName = "converted_" + System.currentTimeMillis() + "." + targetFormat.toLowerCase();

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, defaultName);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI,
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toURI());
        }
        startActivityForResult(intent, REQUEST_SELECT_OUTPUT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK || data == null) return;

        if (requestCode == REQUEST_SELECT_INPUT) {
            inputFileUri = data.getData();
            inputFileName = getFileName(inputFileUri);
            String ext = getFileExtension(inputFileName).toUpperCase();
            tvInputInfo.setText("源文件: " + inputFileName);

            // 自动匹配源格式
            for (int i = 0; i < SOURCE_FORMATS.length; i++) {
                if (SOURCE_FORMATS[i].equals(ext)) {
                    spinnerSourceFormat.setSelection(i);
                    break;
                }
            }
        } else if (requestCode == REQUEST_SELECT_OUTPUT) {
            outputFileUri = data.getData();
            outputFilePath = outputFileUri.toString();
            tvOutputPath.setText("保存路径: " + outputFilePath);
            Toast.makeText(this, "已选择保存路径", Toast.LENGTH_SHORT).show();
        }
    }

    private void startConvert() {
        if (inputFileUri == null) {
            Toast.makeText(this, "请先选择源文件", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        btnConvert.setEnabled(false);
        extractedText = "";

        new Thread(() -> {
            try {
                String ext = getFileExtension(inputFileName).toLowerCase();
                String content = extractTextFromFile(inputFileUri, ext);

                if (!isAlive.get()) return;
                final String result = content;
                extractedText = content;

                runOnUiThread(() -> {
                    if (!isAlive.get()) return;
                    etPreview.setText(result);
                    progressBar.setVisibility(View.GONE);
                    btnConvert.setEnabled(true);
                    Toast.makeText(this, "转换完成，预览内容已显示", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                final String msg = e.getMessage();
                runOnUiThread(() -> {
                    if (!isAlive.get()) return;
                    progressBar.setVisibility(View.GONE);
                    btnConvert.setEnabled(true);
                    Toast.makeText(this, "转换失败: " + msg, Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void saveResult() {
        if (TextUtils.isEmpty(extractedText)) {
            Toast.makeText(this, "没有可保存的内容，请先转换", Toast.LENGTH_SHORT).show();
            return;
        }

        String targetFormat = (String) spinnerTargetFormat.getSelectedItem();
        if (targetFormat == null) targetFormat = "TXT";

        // 如果没有选择输出路径，使用默认路径 sdcard/ToolBox/TextConverter/
        if (outputFileUri == null) {
            createDefaultOutputFile(targetFormat);
        }

        String contentToSave = formatOutput(extractedText, targetFormat);

        // 二进制格式特殊处理
        String upperFormat = targetFormat.toUpperCase();
        if (upperFormat.equals("PDF") || upperFormat.equals("DOCX") ||
                upperFormat.equals("EPUB") || upperFormat.equals("MOBI") || upperFormat.equals("AZW3")) {
            saveAsBinaryFormat(contentToSave, targetFormat);
            return;
        }

        OutputStream outputStream = null;
        try {
            if (outputFileUri != null) {
                outputStream = getContentResolver().openOutputStream(outputFileUri);
            } else {
                File outputFile = new File(outputFilePath);
                File parentDir = outputFile.getParentFile();
                if (parentDir != null && !parentDir.exists()) {
                    parentDir.mkdirs();
                }
                outputStream = new FileOutputStream(outputFile);
            }

            if (outputStream != null) {
                OutputStreamWriter writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
                writer.write(contentToSave);
                writer.flush();
                Toast.makeText(this, "文件已保存", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            try {
                if (outputStream != null) outputStream.close();
            } catch (Exception ignored) {}
        }
    }

    /**
     * 保存为二进制格式（PDF/DOCX/EPUB/MOBI/AZW3）
     * 目前生成简化版本，包含基本结构
     */
    private void saveAsBinaryFormat(String text, String targetFormat) {
        OutputStream outputStream = null;
        try {
            if (outputFileUri != null) {
                outputStream = getContentResolver().openOutputStream(outputFileUri);
            } else {
                File outputFile = new File(outputFilePath);
                File parentDir = outputFile.getParentFile();
                if (parentDir != null && !parentDir.exists()) {
                    parentDir.mkdirs();
                }
                outputStream = new FileOutputStream(outputFile);
            }

            if (outputStream == null) {
                Toast.makeText(this, "无法创建输出文件", Toast.LENGTH_SHORT).show();
                return;
            }

            switch (targetFormat.toUpperCase()) {
                case "PDF":
                    saveAsPdf(outputStream, text);
                    break;
                case "DOCX":
                    saveAsDocx(outputStream, text);
                    break;
                case "EPUB":
                    saveAsEpub(outputStream, text);
                    break;
                case "MOBI":
                case "AZW3":
                    saveAsMobi(outputStream, text);
                    break;
                default:
                    OutputStreamWriter writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
                    writer.write(text);
                    writer.flush();
            }
            Toast.makeText(this, targetFormat.toUpperCase() + " 文件已保存", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            try {
                if (outputStream != null) outputStream.close();
            } catch (Exception ignored) {}
        }
    }

    /**
     * 使用PDFBox生成PDF文件
     */
    private void saveAsPdf(OutputStream outputStream, String text) throws Exception {
        com.tom_roush.pdfbox.pdmodel.PDDocument document = new com.tom_roush.pdfbox.pdmodel.PDDocument();
        com.tom_roush.pdfbox.pdmodel.PDPage page = new com.tom_roush.pdfbox.pdmodel.PDPage();
        document.addPage(page);

        com.tom_roush.pdfbox.pdmodel.PDPageContentStream contentStream =
                new com.tom_roush.pdfbox.pdmodel.PDPageContentStream(document, page);
        contentStream.beginText();
        contentStream.setFont(com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA, 12);
        contentStream.newLineAtOffset(50, 750);

        int lineHeight = 15;
        int currentY = 750;
        for (String line : text.split("\n")) {
            if (currentY < 50) {
                contentStream.endText();
                contentStream.close();
                page = new com.tom_roush.pdfbox.pdmodel.PDPage();
                document.addPage(page);
                contentStream = new com.tom_roush.pdfbox.pdmodel.PDPageContentStream(document, page);
                contentStream.beginText();
                contentStream.setFont(com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA, 12);
                currentY = 750;
                contentStream.newLineAtOffset(50, currentY);
            }
            // 处理长行截断
            String remaining = line;
            while (remaining.length() > 80) {
                String chunk = remaining.substring(0, 80);
                contentStream.showText(chunk);
                contentStream.newLineAtOffset(0, -lineHeight);
                currentY -= lineHeight;
                remaining = remaining.substring(80);
            }
            contentStream.showText(remaining);
            contentStream.newLineAtOffset(0, -lineHeight);
            currentY -= lineHeight;
        }

        contentStream.endText();
        contentStream.close();
        document.save(outputStream);
        document.close();
    }

    /**
     * 使用Apache POI生成DOCX文件
     */
    private void saveAsDocx(OutputStream outputStream, String text) throws Exception {
        org.apache.poi.xwpf.usermodel.XWPFDocument doc = new org.apache.poi.xwpf.usermodel.XWPFDocument();
        for (String line : text.split("\n")) {
            org.apache.poi.xwpf.usermodel.XWPFParagraph para = doc.createParagraph();
            org.apache.poi.xwpf.usermodel.XWPFRun run = para.createRun();
            run.setText(line);
            run.setFontSize(12);
        }
        doc.write(outputStream);
        doc.close();
    }

    /**
     * 生成简化版EPUB文件
     */
    private void saveAsEpub(OutputStream outputStream, String text) throws Exception {
        java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(outputStream);

        // mimetype
        zos.putNextEntry(new java.util.zip.ZipEntry("mimetype"));
        zos.write("application/epub+zip".getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();

        // META-INF/container.xml
        zos.putNextEntry(new java.util.zip.ZipEntry("META-INF/container.xml"));
        zos.write(("<?xml version=\"1.0\"?>\n" +
                "<container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\">\n" +
                "  <rootfiles>\n" +
                "    <rootfile full-path=\"OEBPS/content.opf\" media-type=\"application/oebps-package+xml\"/>\n" +
                "  </rootfiles>\n" +
                "</container>").getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();

        // OEBPS/content.opf
        zos.putNextEntry(new java.util.zip.ZipEntry("OEBPS/content.opf"));
        zos.write(("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<package version=\"2.0\" xmlns=\"http://www.idpf.org/2007/opf\">\n" +
                "  <metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">\n" +
                "    <dc:title>Converted Document</dc:title>\n" +
                "    <dc:language>zh</dc:language>\n" +
                "  </metadata>\n" +
                "  <manifest>\n" +
                "    <item id=\"content\" href=\"content.xhtml\" media-type=\"application/xhtml+xml\"/>\n" +
                "  </manifest>\n" +
                "  <spine>\n" +
                "    <itemref idref=\"content\"/>\n" +
                "  </spine>\n" +
                "</package>").getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();

        // OEBPS/content.xhtml
        StringBuilder html = new StringBuilder();
        html.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        html.append("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.1//EN\" \"http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd\">\n");
        html.append("<html xmlns=\"http://www.w3.org/1999/xhtml\">\n<head>\n");
        html.append("<title>Converted Document</title>\n");
        html.append("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\"/>\n");
        html.append("</head>\n<body>\n");
        for (String line : text.split("\n")) {
            html.append("<p>").append(escapeXml(line)).append("</p>\n");
        }
        html.append("</body>\n</html>");

        zos.putNextEntry(new java.util.zip.ZipEntry("OEBPS/content.xhtml"));
        zos.write(html.toString().getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();

        zos.close();
    }

    /**
     * 生成简化版MOBI/AZW3文件（基于HTML内容）
     */
    private void saveAsMobi(OutputStream outputStream, String text) throws Exception {
        // MOBI/AZW3是复杂二进制格式，这里生成一个包含HTML内容的简化文件
        // 实际使用建议通过Calibre等工具转换
        StringBuilder sb = new StringBuilder();
        sb.append("<html>\n<head>\n");
        sb.append("<meta charset=\"UTF-8\">\n");
        sb.append("<title>Converted Document</title>\n");
        sb.append("</head>\n<body>\n");
        for (String line : text.split("\n")) {
            sb.append("<p>").append(escapeXml(line)).append("</p>\n");
        }
        sb.append("</body>\n</html>");

        // 写入带简单头部的HTML内容
        outputStream.write(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private void createDefaultOutputFile(String targetFormat) {
        try {
            String baseName = inputFileName;
            int dotIndex = baseName.lastIndexOf('.');
            if (dotIndex > 0) {
                baseName = baseName.substring(0, dotIndex);
            }
            String fileName = baseName + "_converted." + targetFormat.toLowerCase();

            // 默认保存到 sdcard/ToolBox/TextConverter/
            File moduleDir = AppConfig.getModuleDir(AppConfig.DIR_TEXT_CONVERTER);
            File outputFile = new File(moduleDir, fileName);
            int counter = 1;
            while (outputFile.exists()) {
                outputFile = new File(moduleDir, baseName + "_converted(" + counter + ")." + targetFormat.toLowerCase());
                counter++;
            }
            outputFilePath = outputFile.getAbsolutePath();
            tvOutputPath.setText("保存路径: " + outputFilePath);
        } catch (Exception e) {
            Toast.makeText(this, "创建默认输出路径失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 根据目标格式格式化输出内容
     */
    private String formatOutput(String text, String targetFormat) {
        switch (targetFormat.toUpperCase()) {
            case "JSON":
                try {
                    Gson gson = new GsonBuilder().setPrettyPrinting().create();
                    JsonElement element = JsonParser.parseString(text);
                    return gson.toJson(element);
                } catch (Exception e) {
                    return text;
                }
            case "XML":
                return wrapInXml(text);
            case "HTML":
                return wrapInHtml(text);
            case "MD":
                return convertToMarkdown(text);
            case "EPUB":
                return wrapInHtml(text); // EPUB基于HTML内容
            case "PDF":
                return text; // PDF为二进制，保存时特殊处理
            case "MOBI":
            case "AZW3":
                return wrapInHtml(text); // MOBI/AZW3基于HTML内容
            case "DOCX":
                return text; // DOCX为二进制，保存时特殊处理
            case "TXT":
            default:
                return text;
        }
    }

    private String wrapInXml(String text) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<document>\n");
        for (String line : text.split("\n")) {
            sb.append("  <paragraph>").append(escapeXml(line)).append("</paragraph>\n");
        }
        sb.append("</document>");
        return sb.toString();
    }

    private String escapeXml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private String wrapInHtml(String text) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html>\n<head>\n");
        sb.append("<meta charset=\"UTF-8\">\n");
        sb.append("<title>Converted Document</title>\n");
        sb.append("</head>\n<body>\n");
        for (String line : text.split("\n")) {
            sb.append("<p>").append(line).append("</p>\n");
        }
        sb.append("</body>\n</html>");
        return sb.toString();
    }

    private String convertToMarkdown(String text) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Converted Document\n\n");
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                sb.append("\n");
            } else {
                sb.append(trimmed).append("\n\n");
            }
        }
        return sb.toString();
    }

    // ==================== 文件提取逻辑 ====================

    private String extractTextFromFile(Uri uri, String ext) {
        switch (ext.toLowerCase()) {
            case "docx": return readDocx(uri);
            case "doc": return readDoc(uri);
            case "xlsx": return readXlsx(uri);
            case "xls": return readXls(uri);
            case "pptx": return readPptx(uri);
            case "ppt": return readPpt(uri);
            case "epub": return readEpub(uri);
            case "pdf": return readPdf(uri);
            case "azw3":
            case "mobi": return readAzw3Mobi(uri);
            case "json":
            case "xml":
            case "html":
            case "htm":
            case "md":
            case "txt":
            default: return readTextFile(uri);
        }
    }

    private String readTextFile(Uri uri) {
        StringBuilder sb = new StringBuilder();
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            if (is == null) return "";
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(line);
                if (sb.length() > MAX_FILE_SIZE) break;
            }
        } catch (Exception e) {
            return "读取文本失败: " + e.getMessage();
        }
        return sb.toString();
    }

    private String readDocx(Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            if (is == null) return "无法读取文件";
            XWPFDocument doc = new XWPFDocument(is);
            StringBuilder sb = new StringBuilder();
            for (XWPFParagraph para : doc.getParagraphs()) {
                sb.append(para.getText()).append("\n");
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
            Range range = doc.getRange();
            return range.text();
        } catch (Exception e) {
            return "读取DOC失败: " + e.getMessage();
        }
    }

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

    private String readEpub(Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            if (is == null) return "无法读取文件";
            java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(is);
            StringBuilder sb = new StringBuilder();
            java.util.zip.ZipEntry entry;

            sb.append("=== EPUB 文档 ===\n\n");

            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.endsWith(".xhtml") || name.endsWith(".html") || name.endsWith(".htm")) {
                    byte[] data = readZipEntryBytes(zis);
                    String html = new String(data, StandardCharsets.UTF_8);
                    String text = stripHtmlTags(html);
                    if (!text.trim().isEmpty()) {
                        sb.append(text).append("\n\n");
                    }
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return "读取EPUB失败: " + e.getMessage();
        }
    }

    private byte[] readZipEntryBytes(java.util.zip.ZipInputStream zis) throws Exception {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int len;
        while ((len = zis.read(buffer)) > 0) {
            baos.write(buffer, 0, len);
        }
        return baos.toByteArray();
    }

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

    private String readAzw3Mobi(Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            if (is == null) return "无法读取文件";
            BufferedInputStream bis = new BufferedInputStream(is);
            byte[] data = new byte[(int) Math.min(getFileSize(uri), MAX_FILE_SIZE)];
            int totalRead = 0;
            while (totalRead < data.length) {
                int read = bis.read(data, totalRead, data.length - totalRead);
                if (read <= 0) break;
                totalRead += read;
            }
            String raw = new String(data, 0, totalRead, StandardCharsets.ISO_8859_1);
            String text = extractTextFromMobi(raw);
            if (!text.trim().isEmpty()) return text;
            return "AZW3/MOBI格式文件已加载。\n\n建议使用Calibre将文件转换为EPUB或TXT格式后打开。";
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
        for (int i = 0; i < sub.length() && sb.length() < MAX_FILE_SIZE; i++) {
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

    // ==================== 工具方法 ====================

    private String getFileName(Uri uri) {
        String displayName = "未知文件";
        try (android.database.Cursor cursor = getContentResolver().query(
                uri, null, null, null, null)) {
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
