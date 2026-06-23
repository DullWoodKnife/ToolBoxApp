package com.toolbox.alltools.bookshelf;

import com.toolbox.alltools.R;
import com.toolbox.alltools.ToolModule;

/**
 * 书架模块注册类
 * 实现 ToolModule 接口，用于在主界面注册书架功能入口
 */
public class BookShelfModule implements ToolModule {

    @Override
    public String getModuleName() {
        return "阅读器";
    }

    @Override
    public String getModuleDesc() {
        return "管理PDF/EPUB/MOBI/AZW3电子书";
    }

    @Override
    public int getModuleIcon() {
        return R.drawable.ic_text_editor;
    }

    @Override
    public Class<?> getModuleActivityClass() {
        return BookShelfActivity.class;
    }
}
