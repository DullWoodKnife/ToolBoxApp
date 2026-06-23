package com.toolbox.alltools.bookshelf;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.toolbox.alltools.R;

import java.io.File;

/**
 * 设置Fragment
 * Koodo Reader 浅色主题风格
 * 参考Koodo Reader设置页面，去除三方服务/发送邮件/联系我们/关于我们
 */
public class BookSettingsFragment extends Fragment {

    private static final String PREFS_NAME = "reader_settings";
    private static final String KEY_FONT_SIZE = "font_size";
    private static final String KEY_LINE_SPACING = "line_spacing";
    private static final String KEY_PAGE_ANIMATION = "page_animation";
    private static final String KEY_NIGHT_MODE = "night_mode";

    private SeekBar seekFontSize;
    private SeekBar seekLineSpacing;
    private Switch switchPageAnimation;
    private Switch switchNightMode;
    private LinearLayout bgColorSelector;
    private LinearLayout btnBackupRestore;
    private LinearLayout btnClearCache;
    private LinearLayout btnClearAllData;

    private SharedPreferences prefs;
    private BookDatabaseHelper dbHelper;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_book_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        prefs = requireContext().getSharedPreferences(PREFS_NAME, 0);
        dbHelper = BookDatabaseHelper.getInstance(requireContext());
        initViews(view);
        loadSettings();
        setupListeners();
    }

    private void initViews(View view) {
        seekFontSize = view.findViewById(R.id.seek_font_size);
        seekLineSpacing = view.findViewById(R.id.seek_line_spacing);
        switchPageAnimation = view.findViewById(R.id.switch_page_animation);
        switchNightMode = view.findViewById(R.id.switch_night_mode);
        bgColorSelector = view.findViewById(R.id.bg_color_selector);
        btnBackupRestore = view.findViewById(R.id.btn_backup_restore);
        btnClearCache = view.findViewById(R.id.btn_clear_cache);
        btnClearAllData = view.findViewById(R.id.btn_clear_all_data);
    }

    private void loadSettings() {
        seekFontSize.setProgress(prefs.getInt(KEY_FONT_SIZE, 16));
        seekLineSpacing.setProgress(prefs.getInt(KEY_LINE_SPACING, 10));
        switchPageAnimation.setChecked(prefs.getBoolean(KEY_PAGE_ANIMATION, true));
        switchNightMode.setChecked(prefs.getBoolean(KEY_NIGHT_MODE, false));
    }

    private void setupListeners() {
        seekFontSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {}
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                prefs.edit().putInt(KEY_FONT_SIZE, seekBar.getProgress()).apply();
            }
        });

        seekLineSpacing.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {}
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                prefs.edit().putInt(KEY_LINE_SPACING, seekBar.getProgress()).apply();
            }
        });

        switchPageAnimation.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(KEY_PAGE_ANIMATION, isChecked).apply();
        });

        switchNightMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(KEY_NIGHT_MODE, isChecked).apply();
        });

        btnBackupRestore.setOnClickListener(v -> showBackupRestoreDialog());
        btnClearCache.setOnClickListener(v -> clearCache());
        btnClearAllData.setOnClickListener(v -> showClearAllDataDialog());
    }

    private void showBackupRestoreDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("备份与恢复")
                .setMessage("此功能将在后续版本中添加。\n\n当前您可以通过导出数据库文件手动备份。")
                .setPositiveButton("确定", null)
                .show();
    }

    private void clearCache() {
        File cacheDir = requireContext().getCacheDir();
        long sizeBefore = getDirSize(cacheDir);
        deleteDirContents(cacheDir);
        long sizeAfter = getDirSize(cacheDir);
        long cleared = sizeBefore - sizeAfter;
        Toast.makeText(requireContext(),
                "已清理缓存: " + formatSize(cleared), Toast.LENGTH_SHORT).show();
    }

    private void showClearAllDataDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("清除所有数据")
                .setMessage("确定要清除所有书籍数据和阅读记录吗？此操作不可恢复！")
                .setPositiveButton("清除", (dialog, which) -> {
                    dbHelper.clearAllData();
                    clearCache();
                    Toast.makeText(requireContext(), "所有数据已清除", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private long getDirSize(File dir) {
        long size = 0;
        if (dir == null || !dir.exists()) return 0;
        File[] files = dir.listFiles();
        if (files == null) return 0;
        for (File file : files) {
            if (file.isDirectory()) {
                size += getDirSize(file);
            } else {
                size += file.length();
            }
        }
        return size;
    }

    private void deleteDirContents(File dir) {
        if (dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) {
                deleteDirContents(file);
            }
            file.delete();
        }
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }
}
