package com.toolbox.alltools;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.toolbox.alltools.adapter.ToolCardAdapter;
import com.toolbox.alltools.config.AppConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * 主界面Activity，展示所有工具模块卡片
 * <p>
 * 使用RecyclerView + GridLayoutManager实现网格布局。
 * 模块数据来源于 {@link ToolModuleRegistry}，新增模块无需修改此文件。
 * </p>
 */
public class MainActivity extends AppCompatActivity {

    private RecyclerView rvTools;
    private ToolCardAdapter adapter;
    private LinearLayout llPageIndicator;

    private static final int SPAN_COUNT = 2;
    private static final int ROWS_PER_PAGE = 3;
    private static final int ITEMS_PER_PAGE = SPAN_COUNT * ROWS_PER_PAGE;

    /** 存储权限请求码 */
    private static final int REQUEST_STORAGE_PERMISSIONS = 100;
    /** 管理所有文件权限请求码（Android 11+） */
    private static final int REQUEST_MANAGE_ALL_FILES = 101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        checkAndRequestPermissions();
    }

    private void initViews() {
        rvTools = findViewById(R.id.rv_tools);
        llPageIndicator = findViewById(R.id.ll_page_indicator);
    }

    // ==================== 权限申请 ====================

    /**
     * 检查并申请存储权限
     */
    private void checkAndRequestPermissions() {
        // Android 11+ (API 30+) 需要 MANAGE_EXTERNAL_STORAGE 权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!android.os.Environment.isExternalStorageManager()) {
                showManageStorageDialog();
                return;
            }
        } else {
            // Android 6~10 需要运行时权限申请
            List<String> permissionsToRequest = new ArrayList<>();
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }

            if (!permissionsToRequest.isEmpty()) {
                ActivityCompat.requestPermissions(this,
                        permissionsToRequest.toArray(new String[0]),
                        REQUEST_STORAGE_PERMISSIONS);
                return;
            }
        }

        // 权限已授予，初始化界面
        initApp();
    }

    /**
     * Android 11+ 显示管理所有文件权限的引导对话框
     */
    private void showManageStorageDialog() {
        new AlertDialog.Builder(this)
                .setTitle("需要存储权限")
                .setMessage("本应用需要将文件保存到 sdcard/ToolBox/ 目录。\n\n" +
                        "请点击「前往设置」，在设置页面中开启「允许管理所有文件」权限。")
                .setCancelable(false)
                .setPositiveButton("前往设置", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivityForResult(intent, REQUEST_MANAGE_ALL_FILES);
                })
                .setNegativeButton("退出应用", (dialog, which) -> finish())
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_STORAGE_PERMISSIONS) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                initApp();
            } else {
                // 有权限被拒绝
                boolean shouldShowRationale = false;
                for (String permission : permissions) {
                    if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                        shouldShowRationale = true;
                        break;
                    }
                }
                if (shouldShowRationale) {
                    new AlertDialog.Builder(this)
                            .setTitle("权限被拒绝")
                            .setMessage("存储权限是本应用的核心功能，用于保存转换后的文件到 sdcard/ToolBox/ 目录。\n\n是否重新申请？")
                            .setCancelable(false)
                            .setPositiveButton("重新申请", (dialog, which) -> checkAndRequestPermissions())
                            .setNegativeButton("退出应用", (dialog, which) -> finish())
                            .show();
                } else {
                    // 用户选择了"不再询问"，引导到设置页面
                    new AlertDialog.Builder(this)
                            .setTitle("权限被永久拒绝")
                            .setMessage("您已永久拒绝存储权限。请前往设置 -> 应用 -> 工具箱 -> 权限，手动开启存储权限。")
                            .setCancelable(false)
                            .setPositiveButton("前往设置", (dialog, which) -> {
                                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                intent.setData(Uri.parse("package:" + getPackageName()));
                                startActivity(intent);
                                finish();
                            })
                            .setNegativeButton("退出应用", (dialog, which) -> finish())
                            .show();
                }
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_MANAGE_ALL_FILES) {
            // 从设置页面返回，重新检查权限
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (android.os.Environment.isExternalStorageManager()) {
                    initApp();
                } else {
                    showManageStorageDialog();
                }
            }
        }
    }

    // ==================== 应用初始化 ====================

    private void initApp() {
        // 确保工作目录存在
        AppConfig.getWorkDir();
        AppConfig.getModuleDir(AppConfig.DIR_TEXT_EDITOR);
        AppConfig.getModuleDir(AppConfig.DIR_TEXT_CONVERTER);
        AppConfig.getModuleDir(AppConfig.DIR_AUDIO_CONVERTER);
        AppConfig.getModuleDir(AppConfig.DIR_VIDEO_TOOLS);
        AppConfig.getModuleDir(AppConfig.DIR_WEB_CRAWLER);

        initRecyclerView();
        initPageIndicator();
    }

    private void initRecyclerView() {
        // 从ToolModuleRegistry获取模块列表
        List<ToolModule> modules = ToolModuleRegistry.getInstance().getAllModules();

        // 创建适配器
        adapter = new ToolCardAdapter(modules);
        adapter.setOnItemClickListener((position, module) -> {
            // 跳转到对应Activity
            Intent intent = new Intent(MainActivity.this, module.getModuleActivityClass());
            startActivity(intent);
        });

        // 设置GridLayoutManager，spanCount=2
        GridLayoutManager layoutManager = new GridLayoutManager(this, SPAN_COUNT);
        rvTools.setLayoutManager(layoutManager);
        rvTools.setAdapter(adapter);

        // 监听滚动更新页面指示器
        rvTools.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                updatePageIndicator();
            }
        });
    }

    /**
     * 根据模块数量动态初始化页面指示器
     */
    private void initPageIndicator() {
        int moduleCount = ToolModuleRegistry.getInstance().getModuleCount();
        int pageCount = (int) Math.ceil((double) moduleCount / ITEMS_PER_PAGE);

        // 如果只有1页或0页，隐藏指示器
        if (pageCount <= 1) {
            llPageIndicator.setVisibility(View.GONE);
            return;
        }

        llPageIndicator.setVisibility(View.VISIBLE);
        llPageIndicator.removeAllViews();

        // 动态创建圆点
        for (int i = 0; i < pageCount; i++) {
            View dot = new View(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    24, 24); // 使用较大尺寸方便点击
            params.setMarginEnd(8);
            dot.setLayoutParams(params);

            if (i == 0) {
                dot.setBackgroundResource(R.drawable.indicator_dot_active);
            } else {
                dot.setBackgroundResource(R.drawable.indicator_dot_inactive);
            }
            llPageIndicator.addView(dot);
        }

        // 默认激活第一个圆点
        updatePageIndicator();
    }

    /**
     * 根据RecyclerView的滚动位置更新页面指示器
     */
    private void updatePageIndicator() {
        int childCount = llPageIndicator.getChildCount();
        if (childCount == 0) return;

        GridLayoutManager layoutManager = (GridLayoutManager) rvTools.getLayoutManager();
        if (layoutManager == null) return;

        int lastVisiblePosition = layoutManager.findLastVisibleItemPosition();

        // 计算当前页码
        int currentPage = lastVisiblePosition / ITEMS_PER_PAGE;
        if (currentPage >= childCount) {
            currentPage = childCount - 1;
        }

        // 更新圆点状态
        for (int i = 0; i < childCount; i++) {
            View dot = llPageIndicator.getChildAt(i);
            if (dot != null) {
                if (i == currentPage) {
                    dot.setBackgroundResource(R.drawable.indicator_dot_active);
                } else {
                    dot.setBackgroundResource(R.drawable.indicator_dot_inactive);
                }
            }
        }
    }
}
