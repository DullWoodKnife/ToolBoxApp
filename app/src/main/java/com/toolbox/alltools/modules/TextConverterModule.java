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
        return "支持JSON、XML、CSV、YAML、Base64、URL编解码等多种格式转换";
    }
}
