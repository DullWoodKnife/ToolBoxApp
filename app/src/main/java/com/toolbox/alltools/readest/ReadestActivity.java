package com.toolbox.alltools.readest;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.appbar.MaterialToolbar;
import com.toolbox.alltools.R;
import com.toolbox.alltools.bookshelf.Book;
import com.toolbox.alltools.bookshelf.BookDatabaseHelper;

import java.util.List;

/**
 * Readest 主Activity
 * 参考Readest的现代化UI风格，使用Toolbar导航、搜索、导入、视图切换和设置功能
 * 已移除：登录、云端上传、高级设置（备份恢复/管理缓存/清除数据）、关于Readest
 */
public class ReadestActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "readest_prefs";
    private static final String KEY_VIEW_MODE = "view_mode";
    private static final int VIEW_MODE_LIST = 0;
    private static final int VIEW_MODE_GRID = 1;

    private SharedPreferences prefs;
    private BookDatabaseHelper dbHelper;
    private ReadestLibraryFragment libraryFragment;
    private int currentViewMode = VIEW_MODE_GRID;

    private ActivityResultLauncher<Intent> filePickerLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_readest);

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        currentViewMode = prefs.getInt(KEY_VIEW_MODE, VIEW_MODE_GRID);
        dbHelper = BookDatabaseHelper.getInstance(this);

        initFilePicker();
        setupToolbar();
        loadLibraryFragment();
    }

    private void initFilePicker() {
        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            handleImportedFile(uri);
                        }
                    }
                });
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Readest");
            getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        }
    }

    private void loadLibraryFragment() {
        libraryFragment = new ReadestLibraryFragment();
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.replace(R.id.container, libraryFragment);
        ft.commit();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_readest_toolbar, menu);

        MenuItem searchItem = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) searchItem.getActionView();
        searchView.setQueryHint("搜索书籍...");
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                performSearch(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if (newText.isEmpty()) {
                    if (libraryFragment != null) {
                        libraryFragment.refreshBooks();
                    }
                }
                return false;
            }
        });

        // 设置视图切换图标状态
        MenuItem viewToggleItem = menu.findItem(R.id.action_toggle_view);
        updateViewToggleIcon(viewToggleItem);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_import) {
            openFilePicker();
            return true;
        } else if (itemId == R.id.action_toggle_view) {
            toggleViewMode(item);
            return true;
        } else if (itemId == R.id.action_settings) {
            showSettingsDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void performSearch(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            if (libraryFragment != null) {
                libraryFragment.refreshBooks();
            }
            return;
        }
        List<Book> results = dbHelper.searchBooks(keyword.trim());
        if (libraryFragment != null) {
            libraryFragment.showSearchResults(results, keyword.trim());
        }
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        String[] mimeTypes = {
                "application/pdf",
                "application/epub+zip",
                "application/x-mobipocket-ebook",
                "application/vnd.amazon.ebook",
                "text/plain"
        };
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        filePickerLauncher.launch(intent);
    }

    private void toggleViewMode(MenuItem item) {
        currentViewMode = (currentViewMode == VIEW_MODE_GRID) ? VIEW_MODE_LIST : VIEW_MODE_GRID;
        prefs.edit().putInt(KEY_VIEW_MODE, currentViewMode).apply();
        updateViewToggleIcon(item);
        if (libraryFragment != null) {
            libraryFragment.setViewMode(currentViewMode);
        }
    }

    private void updateViewToggleIcon(MenuItem item) {
        if (item != null) {
            item.setIcon(currentViewMode == VIEW_MODE_GRID
                    ? R.drawable.ic_view_list
                    : R.drawable.ic_view_grid);
            item.setTitle(currentViewMode == VIEW_MODE_GRID ? "列表视图" : "网格视图");
        }
    }

    private void showSettingsDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_readest_settings, null);
        new AlertDialog.Builder(this)
                .setTitle("设置")
                .setView(dialogView)
                .setPositiveButton("确定", (dialog, which) -> {
                    applySettings(dialogView);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void applySettings(View dialogView) {
        // 设置应用逻辑可在此扩展
        Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();
    }

    private void handleImportedFile(Uri uri) {
        String fileName = getFileNameFromUri(uri);
        String extension = getFileExtension(fileName);
        String format = extension.toLowerCase();

        java.util.Set<String> supportedFormats = new java.util.HashSet<>(
                java.util.Arrays.asList("pdf", "epub", "mobi", "azw3", "txt"));
        if (!supportedFormats.contains(format)) {
            Toast.makeText(this, "不支持的格式: " + format, Toast.LENGTH_SHORT).show();
            return;
        }

        Book book = new Book();
        book.setTitle(fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName);
        book.setAuthor("");
        book.setFileUri(uri.toString());
        book.setFilePath(uri.toString());
        book.setFormat(format);
        book.setCategory("默认");
        book.setAddedTime(System.currentTimeMillis());
        book.setLastReadTime(0);
        book.setReadProgress(0f);
        book.setCurrentPage(0);
        book.setCurrentChapter(0);
        book.setTotalPages(0);
        book.setFavorite(false);

        try (android.database.Cursor cursor = getContentResolver().query(
                uri, new String[]{android.provider.OpenableColumns.SIZE}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE);
                if (sizeIndex >= 0) {
                    book.setFileSize(cursor.getLong(sizeIndex));
                }
            }
        } catch (Exception ignored) {
        }

        long bookId = dbHelper.insertBook(book);
        if (bookId > 0) {
            Toast.makeText(this, "已添加: " + book.getTitle(), Toast.LENGTH_SHORT).show();
            if (libraryFragment != null) {
                libraryFragment.refreshBooks();
            }
        } else {
            Toast.makeText(this, "添加失败", Toast.LENGTH_SHORT).show();
        }
    }

    private String getFileNameFromUri(Uri uri) {
        String displayName = "unknown";
        try (android.database.Cursor cursor = getContentResolver().query(
                uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (nameIndex >= 0) {
                    displayName = cursor.getString(nameIndex);
                }
            }
        } catch (Exception ignored) {
        }
        return displayName;
    }

    private String getFileExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return (dotIndex > 0 && dotIndex < fileName.length() - 1)
                ? fileName.substring(dotIndex + 1) : "";
    }

    /**
     * 获取当前视图模式
     * @return VIEW_MODE_LIST (0) 或 VIEW_MODE_GRID (1)
     */
    public int getViewMode() {
        return currentViewMode;
    }

    /**
     * 获取BookDatabaseHelper实例
     */
    public BookDatabaseHelper getDbHelper() {
        return dbHelper;
    }
}
