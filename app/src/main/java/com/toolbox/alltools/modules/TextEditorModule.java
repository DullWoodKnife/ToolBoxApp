package com.toolbox.alltools.modules;

import com.toolbox.alltools.R;
import com.toolbox.alltools.ToolModule;

/**
 * 文本阅读编辑器模块注册
 */
public class TextEditorModule implements ToolModule {

    @Override
    public String getModuleName() {
        return "文本阅读编辑器";
    }

    @Override
    public int getModuleIcon() {
        return R.drawable.ic_text_editor;
    }

    @Override
    public Class<?> getModuleActivityClass() {
        return TextEditorActivity.class;
    }

    @Override
    public String getModuleDesc() {
        return "打开、编辑、保存文本文件，支持字数统计和行数统计";
    }
}
