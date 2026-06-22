package com.toolbox.alltools;

import android.app.Application;
import android.util.Log;

import com.toolbox.alltools.config.AppConfig;
import com.toolbox.alltools.config.ModuleConfig;

/**
 * 全局Application类
 * <p>
 * 应用程序的入口点，负责全局初始化工作。
 * 包括工具模块的注册、全局配置的初始化等。</p>
 *
 * <p>需要在AndroidManifest.xml中配置：</p>
 * <pre>
 *     &lt;application
 *         android:name=".ToolApplication"
 *         ... &gt;
 *     &lt;/application&gt;
 * </pre>
 *
 * <p>初始化流程：</p>
 * <ol>
 *     <li>调用父类onCreate()完成系统级初始化</li>
 *     <li>注册所有工具模块到 {@link ToolModuleRegistry}</li>
 *     <li>输出初始化完成日志</li>
 * </ol>
 */
public class ToolApplication extends Application {

    private static final String TAG = "ToolApplication";

    /** 全局Application实例，供需要Context的地方使用 */
    private static ToolApplication instance;

    @Override
    public void onCreate() {
        super.onCreate();

        // 保存Application实例引用
        instance = this;

        // 注册所有工具模块
        ModuleConfig.registerAllModules(this);

        // 确保默认工作目录存在
        ensureWorkDirsExist();

        Log.i(TAG, "ToolApplication 初始化完成");
    }

    /**
     * 获取全局Application实例
     * <p>在需要Context但无法直接获取的场景下（如工具类、静态方法中）使用。</p>
     *
     * @return ToolApplication全局实例
     */
    public static ToolApplication getInstance() {
        return instance;
    }

    /**
     * 确保所有默认工作目录存在
     */
    private void ensureWorkDirsExist() {
        try {
            AppConfig.getWorkDir();
            AppConfig.getModuleDir(AppConfig.DIR_TEXT_EDITOR);
            AppConfig.getModuleDir(AppConfig.DIR_TEXT_CONVERTER);
            AppConfig.getModuleDir(AppConfig.DIR_AUDIO_CONVERTER);
            AppConfig.getModuleDir(AppConfig.DIR_VIDEO_TOOLS);
            AppConfig.getModuleDir(AppConfig.DIR_WEB_CRAWLER);
            Log.i(TAG, "工作目录检查完成");
        } catch (Exception e) {
            Log.e(TAG, "创建工作目录失败: " + e.getMessage());
        }
    }
}
