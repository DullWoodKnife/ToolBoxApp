package com.toolbox.alltools.bookshelf;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.toolbox.alltools.R;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 书架网格布局 RecyclerView Adapter
 * 用于在网格中展示图书封面、标题、格式标签和阅读进度
 */
public class BookGridAdapter extends RecyclerView.Adapter<BookGridAdapter.BookViewHolder> {

    private final Context context;
    private List<Book> books = new ArrayList<>();
    private OnBookActionListener actionListener;

    public BookGridAdapter(Context context) {
        this.context = context;
    }

    public void setBooks(List<Book> books) {
        this.books = books != null ? books : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void setOnBookActionListener(OnBookActionListener listener) {
        this.actionListener = listener;
    }

    @NonNull
    @Override
    public BookViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_book_grid, parent, false);
        return new BookViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BookViewHolder holder, int position) {
        Book book = books.get(position);
        holder.bind(book);
    }

    @Override
    public int getItemCount() {
        return books.size();
    }

    class BookViewHolder extends RecyclerView.ViewHolder {

        private final ImageView ivCover;
        private final TextView tvTitle;
        private final TextView tvFormatBadge;
        private final TextView tvCoverPlaceholder;
        private final TextView tvReadStatus;

        BookViewHolder(@NonNull View itemView) {
            super(itemView);
            ivCover = itemView.findViewById(R.id.iv_book_cover);
            tvTitle = itemView.findViewById(R.id.tv_book_title);
            tvFormatBadge = itemView.findViewById(R.id.tv_format_badge);
            tvCoverPlaceholder = itemView.findViewById(R.id.tv_cover_placeholder);
            tvReadStatus = itemView.findViewById(R.id.tv_read_status);

            itemView.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    Book book = books.get(pos);
                    openBookReader(book.getId());
                }
            });

            itemView.setOnLongClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    Book book = books.get(pos);
                    showPopupMenu(v, book);
                }
                return true;
            });
        }

        void bind(Book book) {
            tvTitle.setText(book.getTitle());

            // 格式标签
            String format = book.getFormat() != null ? book.getFormat().toUpperCase() : "UNKNOWN";
            tvFormatBadge.setText(format);

            // 阅读进度
            if (book.getReadProgress() <= 0) {
                tvReadStatus.setText("未读");
            } else {
                tvReadStatus.setText(String.format("已读%.0f%%", book.getReadProgress()));
            }

            // 封面加载
            String coverPath = book.getCoverPath();
            if (coverPath != null && !coverPath.isEmpty() && new File(coverPath).exists()) {
                ivCover.setVisibility(View.VISIBLE);
                tvCoverPlaceholder.setVisibility(View.GONE);
                Glide.with(context)
                        .load(new File(coverPath))
                        .placeholder(R.drawable.ic_text_editor)
                        .error(R.drawable.ic_text_editor)
                        .into(ivCover);
            } else {
                // 无封面时显示格式占位文字
                ivCover.setVisibility(View.GONE);
                tvCoverPlaceholder.setVisibility(View.VISIBLE);
                tvCoverPlaceholder.setText("-" + format + "-");
            }
        }

        private void openBookReader(long bookId) {
            Intent intent = new Intent(context, BookReaderActivity.class);
            intent.putExtra(BookReaderActivity.EXTRA_BOOK_ID, bookId);
            context.startActivity(intent);
        }

        private void showPopupMenu(View anchorView, Book book) {
            PopupMenu popupMenu = new PopupMenu(context, anchorView);
            popupMenu.getMenuInflater().inflate(R.menu.menu_book_grid_item, popupMenu.getMenu());

            popupMenu.setOnMenuItemClickListener(item -> {
                int itemId = item.getItemId();
                if (itemId == R.id.action_delete) {
                    if (actionListener != null) {
                        actionListener.onDeleteBook(book);
                    }
                    return true;
                } else if (itemId == R.id.action_favorite) {
                    if (actionListener != null) {
                        actionListener.onToggleFavorite(book);
                    }
                    return true;
                } else if (itemId == R.id.action_move_category) {
                    if (actionListener != null) {
                        actionListener.onMoveCategory(book);
                    }
                    return true;
                }
                return false;
            });
            popupMenu.show();
        }
    }

    /**
     * 图书操作回调接口
     */
    public interface OnBookActionListener {
        void onDeleteBook(Book book);
        void onToggleFavorite(Book book);
        void onMoveCategory(Book book);
    }
}
