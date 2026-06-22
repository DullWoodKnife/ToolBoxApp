package com.toolbox.alltools.config;

import android.os.Environment;

import java.io.File;

/**
 * 应用全局配置常量
 */
public class AppConfig {

    /** 应用默认工作目录：sdcard/ToolBox/ */
    public static final String DEFAULT_WORK_DIR = "ToolBox";

    /** 文本编辑器子目录 */
    public static final String DIR_TEXT_EDITOR = "TextEditor";

    /** 文本转换器子目录 */
    public static final String DIR_TEXT_CONVERTER = "TextConverter";

    /** 音频转换器子目录 */
    public static final String DIR_AUDIO_CONVERTER = "AudioConverter";

    /** 视频工具子目录 */
    public static final String DIR_VIDEO_TOOLS = "VideoTools";

    /** 爬虫工具子目录 */
    public static final String DIR_WEB_CRAWLER = "WebCrawler";

    /**
     * 获取默认工作目录 File 对象
     * 优先使用外部存储根目录，如果无权限则使用应用私有目录
     */
    public static File getWorkDir() {
        // 尝试使用外部存储根目录 sdcard/ToolBox/
        File externalDir = Environment.getExternalStorageDirectory();
        if (externalDir != null && externalDir.canWrite()) {
            File workDir = new File(externalDir, DEFAULT_WORK_DIR);
            if (workDir.exists() || workDir.mkdirs()) {
                return workDir;
            }
        }
        // 备用：使用应用外部私有目录（无需额外权限）
        android.content.Context ctx = com.toolbox.alltools.ToolApplication.getInstance();
        if (ctx != null) {
            File appExternalDir = ctx.getExternalFilesDir(null);
            if (appExternalDir != null) {
                File workDir = new File(appExternalDir, DEFAULT_WORK_DIR);
                if (workDir.exists() || workDir.mkdirs()) {
                    return workDir;
                }
            }
        }
        // 最后备用：使用外部存储根目录（即使可能失败）
        return new File(externalDir, DEFAULT_WORK_DIR);
    }

    /**
     * 获取指定功能模块的子目录
     */
    public static File getModuleDir(String moduleDir) {
        File dir = new File(getWorkDir(), moduleDir);
        if (!dir.exists()) {
            boolean success = dir.mkdirs();
            if (!success) {
                // 如果创建失败，尝试在应用私有目录创建
                android.content.Context ctx = com.toolbox.alltools.ToolApplication.getInstance();
                if (ctx != null) {
                    File fallbackDir = ctx.getExternalFilesDir(moduleDir);
                    if (fallbackDir != null) {
                        if (!fallbackDir.exists()) fallbackDir.mkdirs();
                        return fallbackDir;
                    }
                }
            }
        }
        return dir;
    }
}
