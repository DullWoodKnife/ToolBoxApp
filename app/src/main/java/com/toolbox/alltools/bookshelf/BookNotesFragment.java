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

import java.util.ArrayList;
import java.util.List;

/**
 * 笔记Fragment：展示阅读笔记列表
 */
public class BookNotesFragment extends Fragment {

    private RecyclerView rvNotes;
    private TextView tvEmptyNotes;

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
        View emptyView = view.findViewById(R.id.ll_empty_state);
        if (emptyView instanceof TextView) {
            tvEmptyNotes = (TextView) emptyView;
        } else {
            tvEmptyNotes = view.findViewById(R.id.tv_empty_state);
        }

        tvEmptyNotes.setText("暂无笔记\n\n阅读时选中文字即可添加笔记");
        rvNotes.setLayoutManager(new LinearLayoutManager(requireContext()));

        // 笔记功能后续实现
        tvEmptyNotes.setVisibility(View.VISIBLE);
        rvNotes.setVisibility(View.GONE);
    }
}
