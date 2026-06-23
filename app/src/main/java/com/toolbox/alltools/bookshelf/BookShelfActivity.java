package com.toolbox.alltools.bookshelf;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.toolbox.alltools.R;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 书架主Activity
 * 使用ViewPager2 + TabLayout管理"书架"和"历史"两个页面
 */
public class BookShelfActivity extends AppCompatActivity {

    private static final int REQUEST_PICK_FILE = 2001;
    private static final Set<String> SUPPORTED_FORMATS = new HashSet<>(
            Arrays.asList("pdf", "epub", "mobi", "azw3")
    );

    private Toolbar toolbar;
    private TabLayout tabLayout;
    private ViewPager2 viewPager;
    private ImageButton btnSearch;
    private ImageButton btnAdd;

    private BookShelfFragment bookShelfFragment;
    private BookHistoryFragment bookHistoryFragment;
    private BookDatabaseHelper dbHelper;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_book_shelf);

        dbHelper = BookDatabaseHelper.getInstance(this);
        initViews();
        setupViewPager();
        setupToolbar();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        tabLayout = findViewById(R.id.tab_layout);
        viewPager = findViewById(R.id.view_pager);
        btnSearch = findViewById(R.id.btn_search);
        btnAdd = findViewById(R.id.btn_add);

        btnSearch.setOnClickListener(v -> showSearchDialog());
        btnAdd.setOnClickListener(v -> openFilePicker());
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("书架");
            getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        }
    }

    private void setupViewPager() {
        bookShelfFragment = new BookShelfFragment();
        bookHistoryFragment = new BookHistoryFragment();

        FragmentStateAdapter adapter = new FragmentStateAdapter(this) {
            @NonNull
            @Override
            public Fragment createFragment(int position) {
                return position == 0 ? bookShelfFragment : bookHistoryFragment;
            }

            @Override
            public int getItemCount() {
                return 2;
            }
        };

        viewPager.setAdapter(adapter);

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            tab.setText(position == 0 ? "书架" : "历史");
        }).attach();
    }

    private void showSearchDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("搜索书籍");

        final EditText etSearch = new EditText(this);
        etSearch.setHint("输入书名或作者");
        etSearch.setPadding(32, 32, 32, 32);
        builder.setView(etSearch);

        builder.setPositiveButton("搜索", (dialog, which) -> {
            String keyword = etSearch.getText().toString().trim();
            if (!keyword.isEmpty()) {
                performSearch(keyword);
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void performSearch(String keyword) {
        List<Book> results = dbHelper.searchBooks(keyword);
        if (bookShelfFragment != null) {
            bookShelfFragment.showSearchResults(results, keyword);
        }
        if (!results.isEmpty()) {
            viewPager.setCurrentItem(0, true);
        } else {
            Toast.makeText(this, "未找到匹配的书籍", Toast.LENGTH_SHORT).show();
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
                "application/vnd.amazon.ebook"
        };
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        startActivityForResult(intent, REQUEST_PICK_FILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_FILE && resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                handlePickedFile(uri);
            }
        }
    }

    private void handlePickedFile(Uri uri) {
        String fileName = getFileName(uri);
        String extension = getFileExtension(fileName);

        if (!SUPPORTED_FORMATS.contains(extension.toLowerCase())) {
            Toast.makeText(this, "不支持的文件格式: " + extension, Toast.LENGTH_SHORT).show();
            return;
        }

        String title = fileName.substring(0, fileName.lastIndexOf('.'));
        long fileSize = getFileSize(uri);

        Book book = new Book();
        book.setTitle(title);
        book.setAuthor("");
        book.setFileUri(uri.toString());
        book.setFilePath(uri.toString());
        book.setFormat(extension.toLowerCase());
        book.setCategory("默认");
        book.setFileSize(fileSize);
        book.setAddedTime(System.currentTimeMillis());
        book.setLastReadTime(0);
        book.setReadProgress(0f);
        book.setCurrentPage(0);
        book.setCurrentChapter(0);
        book.setTotalPages(0);
        book.setFavorite(false);

        long id = dbHelper.insertBook(book);
        if (id > 0) {
            book.setId(id);
            Toast.makeText(this, "已添加: " + title, Toast.LENGTH_SHORT).show();
            if (bookShelfFragment != null) {
                bookShelfFragment.refreshBooks();
            }
        } else {
            Toast.makeText(this, "添加失败", Toast.LENGTH_SHORT).show();
        }
    }

    private String getFileName(Uri uri) {
        String displayName = "unknown";
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex >= 0) {
                    displayName = cursor.getString(nameIndex);
                }
            }
        } catch (Exception ignored) {}
        return displayName;
    }

    private String getFileExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(dotIndex + 1) : "";
    }

    private long getFileSize(Uri uri) {
        try (Cursor cursor = getContentResolver().query(
                uri, new String[]{OpenableColumns.SIZE}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (sizeIndex >= 0) return cursor.getLong(sizeIndex);
            }
        } catch (Exception ignored) {}
        return 0;
    }

    public BookDatabaseHelper getDbHelper() {
        return dbHelper;
    }
}
