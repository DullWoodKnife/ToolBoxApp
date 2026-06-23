package com.toolbox.alltools.bookshelf;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.toolbox.alltools.R;

/**
 * 笔记Fragment：展示阅读笔记列表
 * Koodo Reader 浅色主题风格
 */
public class BookNotesFragment extends Fragment {

    private RecyclerView rvNotes;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_book_history, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        rvNotes = view.findViewById(R.id.rv_book_history);

        // 空状态显示
        View emptyView = view.findViewById(R.id.ll_empty_state);
        if (emptyView != null) {
            emptyView.setVisibility(View.VISIBLE);
        }
        rvNotes.setVisibility(View.GONE);

        rvNotes.setLayoutManager(new LinearLayoutManager(requireContext()));
    }
}
