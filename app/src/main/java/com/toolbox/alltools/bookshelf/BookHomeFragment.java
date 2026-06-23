package com.toolbox.alltools.bookshelf;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.toolbox.alltools.R;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 首页Fragment：展示当前阅读卡片、快捷功能栏、书库网格
 * Koodo Reader 浅色主题风格
 */
public class BookHomeFragment extends Fragment {

    private static final int REQUEST_PICK_FILE = 2003;

    private RecyclerView rvHomeBooks;
    private LinearLayout llEmptyHome;
    private MaterialCardView cardCurrentReading;
    private ImageView ivCurrentCover;
    private TextView tvCurrentTitle;
    private TextView tvCurrentAuthor;
    private TextView tvCurrentProgress;
    private ProgressBar progressCurrent;
    private ImageButton btnSearch;
    private ImageButton btnRefresh;
    private LinearLayout btnAddBook;

    private BookGridAdapter homeAdapter;
    private BookDatabaseHelper dbHelper;
    private List<Book> allBooks = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_book_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        dbHelper = BookDatabaseHelper.getInstance(requireContext());
        initViews(view);
        setupRecyclerView();
        setupQuickActions(view);
        loadHomeData();
    }

    private void initViews(View view) {
        rvHomeBooks = view.findViewById(R.id.rv_home_books);
        llEmptyHome = view.findViewById(R.id.ll_empty_home);
        cardCurrentReading = view.findViewById(R.id.card_current_reading);
        ivCurrentCover = view.findViewById(R.id.iv_current_cover);
        tvCurrentTitle = view.findViewById(R.id.tv_current_title);
        tvCurrentAuthor = view.findViewById(R.id.tv_current_author);
        tvCurrentProgress = view.findViewById(R.id.tv_current_progress);
        progressCurrent = view.findViewById(R.id.progress_current);
        btnSearch = view.findViewById(R.id.btn_search);
        btnRefresh = view.findViewById(R.id.btn_refresh);
        btnAddBook = view.findViewById(R.id.btn_add_book);

        btnAddBook.setOnClickListener(v -> openFilePicker());
        btnRefresh.setOnClickListener(v -> loadHomeData());
        btnSearch.setOnClickListener(v -> showSearchDialog());

        // 点击当前阅读卡片打开阅读器
        cardCurrentReading.setOnClickListener(v -> {
            Book currentBook = getCurrentReadingBook();
            if (currentBook != null) {
                openBookReader(currentBook.getId());
            }
        });
    }

    private void setupRecyclerView() {
        homeAdapter = new BookGridAdapter(requireContext());
        rvHomeBooks.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        rvHomeBooks.setAdapter(homeAdapter);

        homeAdapter.setOnBookActionListener(new BookGridAdapter.OnBookActionListener() {
            @Override
            public void onDeleteBook(Book book) {
                confirmDeleteBook(book);
            }
            @Override
            public void onToggleFavorite(Book book) {
                toggleFavorite(book);
            }
            @Override
            public void onMoveCategory(Book book) {
                showMoveCategoryDialog(book);
            }
        });
    }

    private void setupQuickActions(View view) {
        // 我的喜爱
        view.findViewById(R.id.btn_favorites).setOnClickListener(v -> {
            List<Book> favorites = dbHelper.getFavoriteBooks();
            if (favorites.isEmpty()) {
                Toast.makeText(requireContext(), "暂无收藏书籍", Toast.LENGTH_SHORT).show();
            } else {
                homeAdapter.setBooks(favorites);
                rvHomeBooks.setVisibility(View.VISIBLE);
                llEmptyHome.setVisibility(View.GONE);
            }
        });

        // 最近添加
        view.findViewById(R.id.btn_recent_added).setOnClickListener(v -> {
            allBooks = dbHelper.getAllBooks();
            List<Book> recentAdded = new ArrayList<>(allBooks);
            Collections.sort(recentAdded, (a, b) ->
                    Long.compare(b.getAddedTime(), a.getAddedTime()));
            if (recentAdded.isEmpty()) {
                Toast.makeText(requireContext(), "暂无书籍", Toast.LENGTH_SHORT).show();
            } else {
                homeAdapter.setBooks(recentAdded);
                rvHomeBooks.setVisibility(View.VISIBLE);
                llEmptyHome.setVisibility(View.GONE);
            }
        });

        // 最近阅读
        view.findViewById(R.id.btn_recent_read).setOnClickListener(v -> {
            List<Book> recentRead = dbHelper.getRecentBooks(20);
            if (recentRead.isEmpty()) {
                Toast.makeText(requireContext(), "暂无阅读记录", Toast.LENGTH_SHORT).show();
            } else {
                homeAdapter.setBooks(recentRead);
                rvHomeBooks.setVisibility(View.VISIBLE);
                llEmptyHome.setVisibility(View.GONE);
            }
        });

        // 我的回收 - 显示所有书籍（模拟回收站功能）
        view.findViewById(R.id.btn_trash).setOnClickListener(v -> {
            Toast.makeText(requireContext(), "回收站功能开发中", Toast.LENGTH_SHORT).show();
        });
    }

    private void loadHomeData() {
        allBooks = dbHelper.getAllBooks();

        // 显示当前阅读卡片
        Book currentBook = getCurrentReadingBook();
        if (currentBook != null) {
            cardCurrentReading.setVisibility(View.VISIBLE);
            tvCurrentTitle.setText(currentBook.getTitle());
            tvCurrentAuthor.setText(currentBook.getAuthor() != null && !currentBook.getAuthor().isEmpty()
                    ? currentBook.getAuthor() : "未知作者");
            int progress = (int) currentBook.getReadProgress();
            progressCurrent.setProgress(progress);
            tvCurrentProgress.setText(progress + "%");

            // 封面加载
            String coverPath = currentBook.getCoverPath();
            if (coverPath != null && !coverPath.isEmpty() && new File(coverPath).exists()) {
                ivCurrentCover.setVisibility(View.VISIBLE);
                ivCurrentCover.setImageResource(R.drawable.ic_text_editor);
            } else {
                ivCurrentCover.setVisibility(View.VISIBLE);
                ivCurrentCover.setImageResource(R.drawable.ic_text_editor);
            }
        } else {
            cardCurrentReading.setVisibility(View.GONE);
        }

        // 书库网格
        if (allBooks.isEmpty()) {
            rvHomeBooks.setVisibility(View.GONE);
            llEmptyHome.setVisibility(View.VISIBLE);
        } else {
            rvHomeBooks.setVisibility(View.VISIBLE);
            llEmptyHome.setVisibility(View.GONE);
            homeAdapter.setBooks(allBooks);
        }
    }

    private Book getCurrentReadingBook() {
        if (allBooks == null || allBooks.isEmpty()) {
            allBooks = dbHelper.getAllBooks();
        }
        Book latest = null;
        long latestTime = 0;
        for (Book book : allBooks) {
            if (book.getLastReadTime() > latestTime) {
                latestTime = book.getLastReadTime();
                latest = book;
            }
        }
        return latest;
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
        startActivityForResult(intent, REQUEST_PICK_FILE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_FILE && resultCode == android.app.Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                handlePickedFile(uri);
            }
        }
    }

    private void handlePickedFile(Uri uri) {
        String fileName = getFileName(uri);
        String extension = getFileExtension(fileName);

        java.util.Set<String> supported = new java.util.HashSet<>(
                java.util.Arrays.asList("pdf", "epub", "mobi", "azw3", "txt"));
        if (!supported.contains(extension.toLowerCase())) {
            Toast.makeText(requireContext(), "不支持的文件格式: " + extension, Toast.LENGTH_SHORT).show();
            return;
        }

        String title = fileName.substring(0, fileName.lastIndexOf('.'));

        Book book = new Book();
        book.setTitle(title);
        book.setAuthor("");
        book.setFileUri(uri.toString());
        book.setFilePath(uri.toString());
        book.setFormat(extension.toLowerCase());
        book.setCategory("默认");
        book.setFileSize(getFileSize(uri));
        book.setAddedTime(System.currentTimeMillis());
        book.setLastReadTime(0);
        book.setReadProgress(0f);
        book.setCurrentPage(0);
        book.setCurrentChapter(0);
        book.setTotalPages(0);
        book.setFavorite(false);

        long id = dbHelper.insertBook(book);
        if (id > 0) {
            Toast.makeText(requireContext(), "已添加: " + title, Toast.LENGTH_SHORT).show();
            loadHomeData();
        } else {
            Toast.makeText(requireContext(), "添加失败", Toast.LENGTH_SHORT).show();
        }
    }

    private String getFileName(Uri uri) {
        String displayName = "unknown";
        try (android.database.Cursor cursor = requireContext().getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
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
        try (android.database.Cursor cursor = requireContext().getContentResolver().query(
                uri, new String[]{android.provider.OpenableColumns.SIZE}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE);
                if (sizeIndex >= 0) return cursor.getLong(sizeIndex);
            }
        } catch (Exception ignored) {}
        return 0;
    }

    private void showSearchDialog() {
        android.widget.EditText etSearch = new android.widget.EditText(requireContext());
        etSearch.setHint("搜索书名或作者...");
        etSearch.setPadding(48, 32, 48, 32);
        etSearch.setBackground(getResources().getDrawable(R.drawable.bg_edittext));

        new AlertDialog.Builder(requireContext())
                .setTitle("搜索书籍")
                .setView(etSearch)
                .setPositiveButton("搜索", (dialog, which) -> {
                    String keyword = etSearch.getText().toString().trim();
                    if (keyword.isEmpty()) {
                        loadHomeData();
                        return;
                    }
                    List<Book> results = dbHelper.searchBooks(keyword);
                    homeAdapter.setBooks(results);
                    if (results.isEmpty()) {
                        Toast.makeText(requireContext(), "未找到匹配的书籍", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void confirmDeleteBook(Book book) {
        new AlertDialog.Builder(requireContext())
                .setTitle("删除书籍")
                .setMessage("确定要删除《" + book.getTitle() + "》吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    dbHelper.deleteBook(book.getId());
                    loadHomeData();
                    Toast.makeText(requireContext(), "已删除", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void toggleFavorite(Book book) {
        boolean newState = !book.isFavorite();
        dbHelper.toggleFavorite(book.getId(), newState);
        loadHomeData();
        Toast.makeText(requireContext(), newState ? "已收藏" : "已取消收藏", Toast.LENGTH_SHORT).show();
    }

    private void showMoveCategoryDialog(Book book) {
        List<String> categories = dbHelper.getAllCategories();
        if (categories.isEmpty()) {
            categories.add("默认");
        }
        String[] items = categories.toArray(new String[0]);
        new AlertDialog.Builder(requireContext())
                .setTitle("移动到分类")
                .setItems(items, (dialog, which) -> {
                    String newCategory = items[which];
                    book.setCategory(newCategory);
                    dbHelper.updateBook(book);
                    loadHomeData();
                    Toast.makeText(requireContext(), "已移动到: " + newCategory, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void openBookReader(long bookId) {
        Intent intent = new Intent(requireContext(), BookReaderActivity.class);
        intent.putExtra(BookReaderActivity.EXTRA_BOOK_ID, bookId);
        startActivity(intent);
    }

    @Override
    public void onResume() {
        super.onResume();
        loadHomeData();
    }
}
