package com.toolbox.alltools.modules;

import com.toolbox.alltools.R;
import com.toolbox.alltools.ToolModule;

/**
 * 文件阅读器测试模块
 */
public class FileReaderTestModule implements ToolModule {

    @Override
    public String getModuleName() {
        return "阅读器测试";
    }

    @Override
    public String getModuleDesc() {
        return "测试 txt/doc/docx/pdf/epub/mobi/azw3 文件阅读功能";
    }

    @Override
    public int getModuleIcon() {
        return R.drawable.ic_text_editor;
    }

    @Override
    public Class<?> getModuleActivityClass() {
        return FileReaderTestActivity.class;
    }
}
