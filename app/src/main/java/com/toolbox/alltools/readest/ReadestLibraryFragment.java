package com.toolbox.alltools.readest;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.toolbox.alltools.R;
import com.toolbox.alltools.bookshelf.Book;
import com.toolbox.alltools.bookshelf.BookDatabaseHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Readest 书架Fragment
 * 参考Readest风格，支持列表/网格视图切换、排序、搜索、长按操作
 */
public class ReadestLibraryFragment extends Fragment {

    private static final int REQUEST_PICK_FILE = 3002;
    private static final int VIEW_MODE_LIST = 0;
    private static final int VIEW_MODE_GRID = 1;
    private static final int SORT_BY_TITLE = 0;
    private static final int SORT_BY_AUTHOR = 1;
    private static final int SORT_BY_ADDED_TIME = 2;
    private static final int SORT_BY_LAST_READ = 3;

    private RecyclerView rvBooks;
    private TextView tvBookCount;
    private TextView tvEmptyState;
    private LinearLayout layoutEmptyState;
    private FloatingActionButton fabImport;
    private ImageView ivSortButton;

    private ReadestBookListAdapter listAdapter;
    private ReadestBookGridAdapter gridAdapter;
    private BookDatabaseHelper dbHelper;
    private List<Book> allBooks = new ArrayList<>();
    private List<Book> displayBooks = new ArrayList<>();
    private int currentViewMode = VIEW_MODE_GRID;
    private int currentSortMode = SORT_BY_ADDED_TIME;
    private String currentSearchKeyword = null;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_readest_library, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        dbHelper = BookDatabaseHelper.getInstance(requireContext());
        initViews(view);
        setupAdapters();
        loadBooks();
    }

    private void initViews(View view) {
        rvBooks = view.findViewById(R.id.rv_books);
        tvBookCount = view.findViewById(R.id.tv_book_count);
        tvEmptyState = view.findViewById(R.id.tv_empty_state);
        layoutEmptyState = (LinearLayout) view.findViewById(R.id.tv_empty_state).getParent();
        fabImport = view.findViewById(R.id.fab_import);
        ivSortButton = view.findViewById(R.id.btn_sort);

        fabImport.setOnClickListener(v -> openFilePicker());

        ivSortButton.setOnClickListener(v -> showSortDialog());
    }

    private void setupAdapters() {
        // 列表适配器
        listAdapter = new ReadestBookListAdapter(requireContext());
        listAdapter.setOnBookActionListener(new ReadestBookListAdapter.OnBookActionListener() {
            @Override
            public void onDeleteBook(Book book) {
                confirmDeleteBook(book);
            }

            @Override
            public void onToggleFavorite(Book book) {
                toggleFavorite(book);
            }
        });

        // 网格适配器
        gridAdapter = new ReadestBookGridAdapter(requireContext());
        gridAdapter.setOnBookActionListener(new ReadestBookGridAdapter.OnBookActionListener() {
            @Override
            public void onDeleteBook(Book book) {
                confirmDeleteBook(book);
            }

            @Override
            public void onToggleFavorite(Book book) {
                toggleFavorite(book);
            }
        });

        applyViewMode();
    }

    private void applyViewMode() {
        if (currentViewMode == VIEW_MODE_LIST) {
            rvBooks.setLayoutManager(new LinearLayoutManager(requireContext()));
            rvBooks.setAdapter(listAdapter);
            listAdapter.setBooks(displayBooks);
        } else {
            rvBooks.setLayoutManager(new GridLayoutManager(requireContext(), 3));
            rvBooks.setAdapter(gridAdapter);
            gridAdapter.setBooks(displayBooks);
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
        startActivityForResult(intent, REQUEST_PICK_FILE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_FILE && resultCode == Activity.RESULT_OK && data != null) {
            android.net.Uri uri = data.getData();
            if (uri != null) {
                handlePickedFile(uri);
            }
        }
    }

    private void handlePickedFile(android.net.Uri uri) {
        String fileName = getFileName(uri);
        String extension = getFileExtension(fileName);
        String format = extension.toLowerCase();

        java.util.Set<String> supported = new java.util.HashSet<>(
                java.util.Arrays.asList("pdf", "epub", "mobi", "azw3", "txt"));
        if (!supported.contains(format)) {
            Toast.makeText(requireContext(), "不支持的文件格式: " + extension, Toast.LENGTH_SHORT).show();
            return;
        }

        String title = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;

        Book book = new Book();
        book.setTitle(title);
        book.setAuthor("");
        book.setFileUri(uri.toString());
        book.setFilePath(uri.toString());
        book.setFormat(format);
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
            refreshBooks();
        } else {
            Toast.makeText(requireContext(), "添加失败", Toast.LENGTH_SHORT).show();
        }
    }

    private String getFileName(android.net.Uri uri) {
        String displayName = "unknown";
        try (android.database.Cursor cursor = requireContext().getContentResolver().query(
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
        return dotIndex > 0 ? fileName.substring(dotIndex + 1) : "";
    }

    private long getFileSize(android.net.Uri uri) {
        try (android.database.Cursor cursor = requireContext().getContentResolver().query(
                uri, new String[]{android.provider.OpenableColumns.SIZE}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE);
                if (sizeIndex >= 0) return cursor.getLong(sizeIndex);
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    private void showSortDialog() {
        String[] items = {"按标题", "按作者", "按添加时间", "按最近阅读"};
        new AlertDialog.Builder(requireContext())
                .setTitle("排序方式")
                .setSingleChoiceItems(items, currentSortMode, (dialog, which) -> {
                    currentSortMode = which;
                    sortAndDisplay();
                    dialog.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void sortAndDisplay() {
        Comparator<Book> comparator;
        switch (currentSortMode) {
            case SORT_BY_TITLE:
                comparator = (a, b) -> {
                    String titleA = a.getTitle() != null ? a.getTitle() : "";
                    String titleB = b.getTitle() != null ? b.getTitle() : "";
                    return titleA.compareToIgnoreCase(titleB);
                };
                break;
            case SORT_BY_AUTHOR:
                comparator = (a, b) -> {
                    String authorA = a.getAuthor() != null ? a.getAuthor() : "";
                    String authorB = b.getAuthor() != null ? b.getAuthor() : "";
                    return authorA.compareToIgnoreCase(authorB);
                };
                break;
            case SORT_BY_LAST_READ:
                comparator = (a, b) -> Long.compare(b.getLastReadTime(), a.getLastReadTime());
                break;
            case SORT_BY_ADDED_TIME:
            default:
                comparator = (a, b) -> Long.compare(b.getAddedTime(), a.getAddedTime());
                break;
        }
        Collections.sort(displayBooks, comparator);
        updateAdapters();
    }

    private void confirmDeleteBook(Book book) {
        new AlertDialog.Builder(requireContext())
                .setTitle("删除书籍")
                .setMessage("确定要删除《" + book.getTitle() + "》吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    dbHelper.deleteBook(book.getId());
                    refreshBooks();
                    Toast.makeText(requireContext(), "已删除", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void toggleFavorite(Book book) {
        boolean newState = !book.isFavorite();
        dbHelper.toggleFavorite(book.getId(), newState);
        refreshBooks();
        Toast.makeText(requireContext(), newState ? "已收藏" : "已取消收藏", Toast.LENGTH_SHORT).show();
    }

    /**
     * 刷新书籍列表（公开方法，供Activity调用）
     */
    public void refreshBooks() {
        loadBooks();
    }

    /**
     * 显示搜索结果
     */
    public void showSearchResults(List<Book> results, String keyword) {
        currentSearchKeyword = keyword;
        displayBooks = results != null ? new ArrayList<>(results) : new ArrayList<>();
        sortAndDisplay();
        tvBookCount.setText("搜索\"" + keyword + "\" 共" + displayBooks.size() + "本");
        updateEmptyState();
    }

    /**
     * 设置视图模式（由Activity调用）
     */
    public void setViewMode(int viewMode) {
        this.currentViewMode = viewMode;
        applyViewMode();
    }

    private void loadBooks() {
        allBooks = dbHelper.getAllBooks();
        currentSearchKeyword = null;
        displayBooks = new ArrayList<>(allBooks);
        sortAndDisplay();
        tvBookCount.setText("共" + displayBooks.size() + "本");
        updateEmptyState();
    }

    private void updateAdapters() {
        if (currentViewMode == VIEW_MODE_LIST) {
            listAdapter.setBooks(displayBooks);
        } else {
            gridAdapter.setBooks(displayBooks);
        }
    }

    private void updateEmptyState() {
        if (displayBooks.isEmpty()) {
            rvBooks.setVisibility(View.GONE);
            layoutEmptyState.setVisibility(View.VISIBLE);
            if (currentSearchKeyword != null) {
                tvEmptyState.setText("未找到匹配的书籍");
            } else {
                tvEmptyState.setText("书架空空如也\n点击 + 导入你的第一本书");
            }
        } else {
            rvBooks.setVisibility(View.VISIBLE);
            layoutEmptyState.setVisibility(View.GONE);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshBooks();
    }
}
