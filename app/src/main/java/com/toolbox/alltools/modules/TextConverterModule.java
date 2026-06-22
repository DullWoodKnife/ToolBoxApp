package com.toolbox.alltools.modules;

import com.toolbox.alltools.R;
import com.toolbox.alltools.ToolModule;

/**
 * 文本格式转换器模块注册
 */
public class TextConverterModule implements ToolModule {

    @Override
    public String getModuleName() {
        return "文本格式转换器";
    }

    @Override
    public int getModuleIcon() {
        return R.drawable.ic_text_converter;
    }

    @Override
    public Class<?> getModuleActivityClass() {
        return TextConverterActivity.class;
    }

    @Override
    public String getModuleDesc() {
        return "支持DOC/DOCX/XLS/XLSX/PPT/PPTX/EPUB/PDF/AZW3/MOBI/TXT/MD/JSON/XML/HTML互转";
    }
}
