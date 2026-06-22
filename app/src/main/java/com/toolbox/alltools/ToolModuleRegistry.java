package com.toolbox.alltools;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 工具模块注册表（线程安全单例）
 * <p>
 * 集中管理所有工具模块的注册和获取。
 * 新增工具模块只需在 {@link com.toolbox.alltools.config.ModuleConfig} 中添加注册代码即可，
 * 无需修改其他文件。
 * </p>
 * <p>
 * 所有读写操作均通过synchronized保护，确保多线程安全。
 * </p>
 */
public class ToolModuleRegistry {

    private static volatile ToolModuleRegistry instance;

    /** 使用synchronized保护的模块列表 */
    private final List<ToolModule> modules = new ArrayList<>();

    private ToolModuleRegistry() {
    }

    /**
     * 获取单例实例（双重检查锁定，线程安全）
     */
    public static ToolModuleRegistry getInstance() {
        if (instance == null) {
            synchronized (ToolModuleRegistry.class) {
                if (instance == null) {
                    instance = new ToolModuleRegistry();
                }
            }
        }
        return instance;
    }

    /**
     * 注册单个模块（自动去重，线程安全）
     */
    public synchronized void register(ToolModule module) {
        if (module != null && !modules.contains(module)) {
            modules.add(module);
        }
    }

    /**
     * 注销指定名称的模块（线程安全）
     */
    public synchronized void unregister(String moduleName) {
        modules.removeIf(m -> m.getModuleName().equals(moduleName));
    }

    /**
     * 清除所有已注册的模块（线程安全）
     */
    public synchronized void clearAll() {
        modules.clear();
    }

    /**
     * 获取所有已注册的模块列表（不可修改的快照，线程安全）
     */
    public List<ToolModule> getAllModules() {
        synchronized (this) {
            return Collections.unmodifiableList(new ArrayList<>(modules));
        }
    }

    /**
     * 根据位置获取模块（线程安全）
     */
    public ToolModule getModule(int position) {
        synchronized (this) {
            if (position >= 0 && position < modules.size()) {
                return modules.get(position);
            }
        }
        return null;
    }

    /**
     * 获取模块总数（线程安全）
     */
    public int getModuleCount() {
        synchronized (this) {
            return modules.size();
        }
    }
}
