package com.toolbox.alltools;

/**
 * 工具模块接口，所有工具模块都需要实现此接口
 */
public interface ToolModule {

    /**
     * 获取模块名称
     */
    String getModuleName();

    /**
     * 获取模块图标资源ID
     */
    int getModuleIcon();

    /**
     * 获取模块对应的Activity类
     */
    Class<?> getModuleActivityClass();

    /**
     * 获取模块描述
     */
    String getModuleDesc();
}
