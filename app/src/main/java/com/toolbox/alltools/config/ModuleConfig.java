package com.toolbox.alltools.config;

import android.content.Context;

import com.toolbox.alltools.ToolModule;
import com.toolbox.alltools.ToolModuleRegistry;
import com.toolbox.alltools.bookshelf.BookShelfModule;
import com.toolbox.alltools.modules.AudioConverterModule;
import com.toolbox.alltools.modules.CalculatorModule;
import com.toolbox.alltools.modules.TextConverterModule;
import com.toolbox.alltools.modules.TextEditorModule;
import com.toolbox.alltools.modules.VideoToolsModule;
import com.toolbox.alltools.modules.WebCrawlerModule;
import com.toolbox.alltools.readest.ReadestModule;

/**
 * 模块配置类
 * <p>
 * 负责在Application启动时集中注册所有工具模块。
 * 新增工具模块时，只需在此类中添加对应的注册代码即可。
 * </p>
 */
public class ModuleConfig {

    /**
     * 注册所有工具模块
     *
     * @param context Application上下文，用于模块初始化
     */
    public static void registerAllModules(Context context) {
        ToolModuleRegistry registry = ToolModuleRegistry.getInstance();

        // 清除已有注册（防止重复调用时产生重复模块）
        registry.clearAll();

        // ==================== 注册所有工具模块 ====================

        // 文本格式转换器
        registry.register(new TextConverterModule());

        // 文本编辑器
        registry.register(new TextEditorModule());

        // 阅读器Readest版
        registry.register(new ReadestModule());

        // 阅读器Koodo版
        registry.register(new BookShelfModule());

        // 音频格式转换
        registry.register(new AudioConverterModule());

        // 视频工具
        registry.register(new VideoToolsModule());

        // 爬虫工具箱
        registry.register(new WebCrawlerModule());

        // 全能计算器
        registry.register(new CalculatorModule());

        // ==================== 新增模块在此处添加 ====================
        // registry.register(new YourNewModule());

        // 注册完成后可打印日志，便于调试
        logModuleRegistrationStatus(registry);
    }

    /**
     * 打印模块注册状态日志
     */
    private static void logModuleRegistrationStatus(ToolModuleRegistry registry) {
        int count = registry.getModuleCount();
        if (count == 0) {
            android.util.Log.w("ModuleConfig", "未注册任何工具模块");
            return;
        }
        android.util.Log.i("ModuleConfig", "已注册 " + count + " 个工具模块：");
        for (ToolModule module : registry.getAllModules()) {
            android.util.Log.i("ModuleConfig",
                    "  - " + module.getModuleName() + " (" + module.getModuleDesc() + ")");
        }
    }
}
