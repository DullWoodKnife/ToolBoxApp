package com.toolbox.alltools.bookshelf;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.toolbox.alltools.R;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 阅读历史列表 RecyclerView Adapter
 * 用于在历史列表中展示图书封面、标题、作者、进度条和最后阅读时间
 */
public class BookHistoryAdapter extends RecyclerView.Adapter<BookHistoryAdapter.HistoryViewHolder> {

    private final Context context;
    private List<Book> books = new ArrayList<>();

    public BookHistoryAdapter(Context context) {
        this.context = context;
    }

    public void setBooks(List<Book> books) {
        this.books = books != null ? books : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public HistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_book_history, parent, false);
        return new HistoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull HistoryViewHolder holder, int position) {
        Book book = books.get(position);
        holder.bind(book);
    }

    @Override
    public int getItemCount() {
        return books.size();
    }

    class HistoryViewHolder extends RecyclerView.ViewHolder {

        private final ImageView ivCover;
        private final TextView tvTitle;
        private final TextView tvAuthor;
        private final ProgressBar progressBar;
        private final TextView tvProgress;
        private final TextView tvLastReadTime;

        HistoryViewHolder(@NonNull View itemView) {
            super(itemView);
            ivCover = itemView.findViewById(R.id.iv_history_cover);
            tvTitle = itemView.findViewById(R.id.tv_history_title);
            tvAuthor = itemView.findViewById(R.id.tv_history_author);
            progressBar = itemView.findViewById(R.id.progress_history);
            tvProgress = itemView.findViewById(R.id.tv_history_progress);
            tvLastReadTime = itemView.findViewById(R.id.tv_last_read_time);

            itemView.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    Book book = books.get(pos);
                    openBookReader(book.getId());
                }
            });
        }

        void bind(Book book) {
            tvTitle.setText(book.getTitle());
            tvAuthor.setText(book.getAuthor() != null && !book.getAuthor().isEmpty()
                    ? book.getAuthor() : "未知作者");

            // 进度条和百分比
            int progress = (int) book.getReadProgress();
            progressBar.setProgress(progress);
            if (progress <= 0) {
                tvProgress.setText("未读");
            } else {
                tvProgress.setText(String.format("已读%d%%", progress));
            }

            // 最后阅读时间（相对时间）
            tvLastReadTime.setText(formatRelativeTime(book.getLastReadTime()));

            // 封面加载
            String coverPath = book.getCoverPath();
            if (coverPath != null && !coverPath.isEmpty() && new File(coverPath).exists()) {
                Glide.with(context)
                        .load(new File(coverPath))
                        .placeholder(R.drawable.ic_text_editor)
                        .error(R.drawable.ic_text_editor)
                        .into(ivCover);
            } else {
                ivCover.setImageResource(R.drawable.ic_text_editor);
            }
        }

        private void openBookReader(long bookId) {
            Intent intent = new Intent(context, BookReaderActivity.class);
            intent.putExtra(BookReaderActivity.EXTRA_BOOK_ID, bookId);
            context.startActivity(intent);
        }
    }

    /**
     * 将时间戳格式化为相对时间字符串
     *
     * @param timestamp 毫秒时间戳
     * @return 相对时间描述，如 "刚刚", "5分钟前", "2小时前", "3天前" 等
     */
    public static String formatRelativeTime(long timestamp) {
        if (timestamp <= 0) {
            return "从未阅读";
        }

        long now = System.currentTimeMillis();
        long diff = now - timestamp;

        if (diff < 0) {
            return "未来";
        }

        long seconds = diff / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        long weeks = days / 7;
        long months = days / 30;
        long years = days / 365;

        if (seconds < 60) {
            return "刚刚";
        } else if (minutes < 60) {
            return minutes + "分钟前";
        } else if (hours < 24) {
            return hours + "小时前";
        } else if (days < 7) {
            return days + "天前";
        } else if (weeks < 4) {
            return weeks + "周前";
        } else if (months < 12) {
            return months + "个月前";
        } else {
            return years + "年前";
        }
    }
}
