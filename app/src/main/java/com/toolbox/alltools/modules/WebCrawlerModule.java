package com.toolbox.alltools.modules;

import com.toolbox.alltools.R;
import com.toolbox.alltools.ToolModule;

/**
 * 爬虫工具箱模块注册
 */
public class WebCrawlerModule implements ToolModule {

    @Override
    public String getModuleName() {
        return "爬虫工具箱";
    }

    @Override
    public int getModuleIcon() {
        return R.drawable.ic_web_crawler;
    }

    @Override
    public Class<?> getModuleActivityClass() {
        return WebCrawlerActivity.class;
    }

    @Override
    public String getModuleDesc() {
        return "发送HTTP请求，解析HTML内容，支持GET/POST请求方法";
    }
}
