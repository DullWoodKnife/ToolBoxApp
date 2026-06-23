package com.toolbox.alltools.readest;

import com.toolbox.alltools.R;
import com.toolbox.alltools.ToolModule;

/**
 * Readest 阅读器模块注册类
 * 参考 Readest (Next.js + Tauri) 的 UI 风格，用 Android 原生代码实现
 */
public class ReadestModule implements ToolModule {
    @Override
    public String getModuleName() {
        return "阅读器Readest版";
    }

    @Override
    public String getModuleDesc() {
        return "现代化电子书阅读器";
    }

    @Override
    public int getModuleIcon() {
        return R.drawable.ic_text_editor;
    }

    @Override
    public Class<?> getModuleActivityClass() {
        return ReadestActivity.class;
    }
}
