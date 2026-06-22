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
     * 固定使用外部存储根目录 sdcard/ToolBox/，不自动降级到私有目录
     */
    public static File getWorkDir() {
        File externalDir = Environment.getExternalStorageDirectory();
        File workDir = new File(externalDir, DEFAULT_WORK_DIR);
        if (!workDir.exists()) {
            workDir.mkdirs();
        }
        return workDir;
    }

    /**
     * 获取指定功能模块的子目录
     * 固定使用 sdcard/ToolBox/{moduleDir}/，不自动降级
     */
    public static File getModuleDir(String moduleDir) {
        File dir = new File(getWorkDir(), moduleDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    /**
     * 检查默认工作目录是否可写入
     */
    public static boolean isWorkDirWritable() {
        File workDir = getWorkDir();
        return workDir.exists() && workDir.canWrite();
    }
}
