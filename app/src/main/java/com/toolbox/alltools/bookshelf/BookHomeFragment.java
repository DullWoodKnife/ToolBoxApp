package com.toolbox.alltools.bookshelf;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.toolbox.alltools.R;

import java.util.ArrayList;
import java.util.List;

/**
 * 首页Fragment：展示最近阅读、推荐书籍
 */
public class BookHomeFragment extends Fragment {

    private RecyclerView rvRecentBooks;
    private TextView tvRecentTitle;
    private TextView tvEmptyRecent;
    private BookGridAdapter recentAdapter;
    private BookDatabaseHelper dbHelper;

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
        loadRecentBooks();
    }

    private void initViews(View view) {
        rvRecentBooks = view.findViewById(R.id.rv_book_shelf);
        tvRecentTitle = view.findViewById(R.id.tv_book_count);
        tvEmptyRecent = view.findViewById(R.id.tv_empty_state);

        tvRecentTitle.setText("最近阅读");
        recentAdapter = new BookGridAdapter(requireContext());
        rvRecentBooks.setLayoutManager(new GridLayoutManager(requireContext(), 3));
        rvRecentBooks.setAdapter(recentAdapter);

        recentAdapter.setOnBookActionListener(new BookGridAdapter.OnBookActionListener() {
            @Override
            public void onDeleteBook(Book book) {}
            @Override
            public void onToggleFavorite(Book book) {}
            @Override
            public void onMoveCategory(Book book) {}
        });
    }

    private void loadRecentBooks() {
        List<Book> recentBooks = dbHelper.getRecentBooks(6);
        if (recentBooks.isEmpty()) {
            tvEmptyRecent.setVisibility(View.VISIBLE);
            rvRecentBooks.setVisibility(View.GONE);
        } else {
            tvEmptyRecent.setVisibility(View.GONE);
            rvRecentBooks.setVisibility(View.VISIBLE);
            recentAdapter.setBooks(recentBooks);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        loadRecentBooks();
    }
}
