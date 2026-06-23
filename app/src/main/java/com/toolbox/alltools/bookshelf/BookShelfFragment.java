package com.toolbox.alltools.bookshelf;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.toolbox.alltools.R;

import java.util.ArrayList;
import java.util.List;

/**
 * 书架Fragment
 * 使用RecyclerView + GridLayoutManager(3列)展示书籍
 * 支持分类筛选、长按菜单、搜索结果显示
 */
public class BookShelfFragment extends Fragment {

    private static final int REQUEST_PICK_FILE = 2002;

    private RecyclerView rvBookShelf;
    private TextView tvBookCount;
    private FloatingActionButton fabAddBook;
    private ChipGroup chipGroupCategories;
    private ViewGroup categoryContainer;

    private BookGridAdapter bookAdapter;
    private BookDatabaseHelper dbHelper;
    private List<Book> allBooks = new ArrayList<>();
    private String currentCategory = null;
    private String currentSearchKeyword = null;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_book_shelf, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        dbHelper = BookDatabaseHelper.getInstance(requireContext());
        initViews(view);
        setupRecyclerView();
        loadBooks();
    }

    private void initViews(View view) {
        rvBookShelf = view.findViewById(R.id.rv_book_shelf);
        tvBookCount = view.findViewById(R.id.tv_book_count);
        fabAddBook = view.findViewById(R.id.fab_add_book);

        fabAddBook.setOnClickListener(v -> openFilePicker());

        // 动态添加分类筛选ChipGroup
        categoryContainer = new android.widget.HorizontalScrollView(requireContext());
        categoryContainer.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        ((android.widget.HorizontalScrollView) categoryContainer).setHorizontalScrollBarEnabled(false);

        chipGroupCategories = new ChipGroup(requireContext());
        chipGroupCategories.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        chipGroupCategories.setPadding(16, 8, 16, 8);
        chipGroupCategories.setSingleSelection(true);
        ((android.widget.HorizontalScrollView) categoryContainer).addView(chipGroupCategories);

        // 将分类栏插入到RecyclerView上方
        ViewGroup parent = (ViewGroup) rvBookShelf.getParent();
        if (parent instanceof android.widget.LinearLayout) {
            int index = ((android.widget.LinearLayout) parent).indexOfChild(rvBookShelf);
            ((android.widget.LinearLayout) parent).addView(categoryContainer, index);
        }
    }

    private void setupRecyclerView() {
        bookAdapter = new BookGridAdapter(requireContext());
        rvBookShelf.setLayoutManager(new GridLayoutManager(requireContext(), 3));
        rvBookShelf.setAdapter(bookAdapter);

        bookAdapter.setOnBookActionListener(new BookGridAdapter.OnBookActionListener() {
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
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_FILE && resultCode == android.app.Activity.RESULT_OK && data != null) {
            android.net.Uri uri = data.getData();
            if (uri != null) {
                handlePickedFile(uri);
            }
        }
    }

    private void handlePickedFile(android.net.Uri uri) {
        String fileName = getFileName(uri);
        String extension = getFileExtension(fileName);

        java.util.Set<String> supported = new java.util.HashSet<>(
                java.util.Arrays.asList("pdf", "epub", "mobi", "azw3"));
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
            refreshBooks();
        } else {
            Toast.makeText(requireContext(), "添加失败", Toast.LENGTH_SHORT).show();
        }
    }

    private String getFileName(android.net.Uri uri) {
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

    private long getFileSize(android.net.Uri uri) {
        try (android.database.Cursor cursor = requireContext().getContentResolver().query(
                uri, new String[]{android.provider.OpenableColumns.SIZE}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE);
                if (sizeIndex >= 0) return cursor.getLong(sizeIndex);
            }
        } catch (Exception ignored) {}
        return 0;
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
                    refreshBooks();
                    Toast.makeText(requireContext(), "已移动到: " + newCategory, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void openBookReader(Book book) {
        Intent intent = new Intent(requireContext(), BookReaderActivity.class);
        intent.putExtra("book", book);
        startActivity(intent);
    }

    public void refreshBooks() {
        loadBooks();
    }

    private void loadBooks() {
        allBooks = dbHelper.getAllBooks();
        currentSearchKeyword = null;
        applyFilter();
        updateCategoryChips();
    }

    public void showSearchResults(List<Book> results, String keyword) {
        currentSearchKeyword = keyword;
        currentCategory = null;
        bookAdapter.setBooks(results);
        tvBookCount.setText("搜索\"" + keyword + "\" 共" + results.size() + "本");
        clearChipSelection();
    }

    private void applyFilter() {
        List<Book> filtered = new ArrayList<>();
        if (currentCategory != null && !"全部".equals(currentCategory)) {
            for (Book book : allBooks) {
                if (currentCategory.equals(book.getCategory())) {
                    filtered.add(book);
                }
            }
        } else {
            filtered.addAll(allBooks);
        }
        bookAdapter.setBooks(filtered);
        tvBookCount.setText("共" + filtered.size() + "本");
    }

    private void updateCategoryChips() {
        chipGroupCategories.removeAllViews();

        List<String> categories = new ArrayList<>();
        categories.add("全部");
        categories.addAll(dbHelper.getAllCategories());

        for (String category : categories) {
            Chip chip = new Chip(requireContext());
            chip.setText(category);
            chip.setCheckable(true);
            chip.setClickable(true);
            if ("全部".equals(category)) {
                chip.setChecked(true);
            }
            chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    currentCategory = "全部".equals(category) ? null : category;
                    currentSearchKeyword = null;
                    applyFilter();
                }
            });
            chipGroupCategories.addView(chip);
        }
    }

    private void clearChipSelection() {
        for (int i = 0; i < chipGroupCategories.getChildCount(); i++) {
            View child = chipGroupCategories.getChildAt(i);
            if (child instanceof Chip) {
                ((Chip) child).setChecked(false);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshBooks();
    }
}
