package com.toolbox.alltools.bookshelf;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.toolbox.alltools.R;

import java.util.ArrayList;
import java.util.List;

/**
 * 阅读历史Fragment
 * 使用RecyclerView线性布局展示最近阅读的书籍
 * 支持点击打开阅读器、滑动删除历史记录
 */
public class BookHistoryFragment extends Fragment {

    private RecyclerView rvBookHistory;
    private LinearLayout llEmptyState;

    private BookHistoryAdapter historyAdapter;
    private BookDatabaseHelper dbHelper;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_book_history, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        dbHelper = BookDatabaseHelper.getInstance(requireContext());
        initViews(view);
        setupRecyclerView();
        loadHistory();
    }

    private void initViews(View view) {
        rvBookHistory = view.findViewById(R.id.rv_book_history);
        llEmptyState = view.findViewById(R.id.ll_empty_state);
    }

    private void setupRecyclerView() {
        historyAdapter = new BookHistoryAdapter(requireContext());
        rvBookHistory.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvBookHistory.setAdapter(historyAdapter);

        historyAdapter.setOnBookClickListener((book, position) -> {
            openBookReader(book);
        });

        // 添加滑动删除功能
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                if (position >= 0 && position < historyAdapter.getBooks().size()) {
                    Book book = historyAdapter.getBooks().get(position);
                    resetReadingProgress(book, position);
                }
            }
        });
        itemTouchHelper.attachToRecyclerView(rvBookHistory);
    }

    private void resetReadingProgress(Book book, int position) {
        book.setReadProgress(0f);
        book.setCurrentPage(0);
        book.setCurrentChapter(0);
        book.setLastReadTime(0);
        dbHelper.updateBook(book);
        historyAdapter.removeItem(position);
        checkEmptyState();
        Toast.makeText(requireContext(), "已清除阅读记录", Toast.LENGTH_SHORT).show();
    }

    private void openBookReader(Book book) {
        Intent intent = new Intent(requireContext(), BookReaderActivity.class);
        intent.putExtra("book", book);
        startActivity(intent);
    }

    private void loadHistory() {
        List<Book> recentBooks = dbHelper.getRecentBooks(100);
        historyAdapter.setBooks(recentBooks);
        checkEmptyState();
    }

    private void checkEmptyState() {
        if (historyAdapter.getItemCount() == 0) {
            llEmptyState.setVisibility(View.VISIBLE);
            rvBookHistory.setVisibility(View.GONE);
        } else {
            llEmptyState.setVisibility(View.GONE);
            rvBookHistory.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        loadHistory();
    }
}
