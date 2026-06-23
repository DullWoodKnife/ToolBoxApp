package com.toolbox.alltools.bookshelf;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.toolbox.alltools.R;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 书架网格布局 RecyclerView Adapter
 * Koodo Reader 浅色主题风格
 * 封面使用格式色块banner + 标题 + footer 的空封面占位
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

    /**
     * 根据格式获取对应的颜色
     */
    private int getFormatColor(String format) {
        if (format == null) return R.color.koodo_text_secondary;
        switch (format.toLowerCase()) {
            case "pdf":
                return R.color.koodo_pdf;
            case "epub":
                return R.color.koodo_epub;
            case "mobi":
                return R.color.koodo_mobi;
            case "azw3":
                return R.color.koodo_azw3;
            case "txt":
                return R.color.koodo_txt;
            default:
                return R.color.koodo_text_secondary;
        }
    }

    class BookViewHolder extends RecyclerView.ViewHolder {

        private final ImageView ivCover;
        private final LinearLayout llEmptyCover;
        private final TextView tvCoverBanner;
        private final TextView tvCoverTitle;
        private final TextView tvCoverFooter;
        private final TextView tvTitle;
        private final TextView tvReadStatus;
        private final ImageView ivFavorite;

        BookViewHolder(@NonNull View itemView) {
            super(itemView);
            ivCover = itemView.findViewById(R.id.iv_book_cover);
            llEmptyCover = itemView.findViewById(R.id.ll_empty_cover);
            tvCoverBanner = itemView.findViewById(R.id.tv_cover_banner);
            tvCoverTitle = itemView.findViewById(R.id.tv_cover_title);
            tvCoverFooter = itemView.findViewById(R.id.tv_cover_footer);
            tvTitle = itemView.findViewById(R.id.tv_book_title);
            tvReadStatus = itemView.findViewById(R.id.tv_read_status);
            ivFavorite = itemView.findViewById(R.id.iv_favorite);

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

            String format = book.getFormat() != null ? book.getFormat().toUpperCase() : "UNKNOWN";

            // 阅读进度
            if (book.getReadProgress() <= 0) {
                tvReadStatus.setText("New");
            } else if (book.getReadProgress() >= 100) {
                tvReadStatus.setText("Done");
            } else {
                tvReadStatus.setText(String.format("%.0f%%", book.getReadProgress()));
            }

            // 收藏图标
            if (book.isFavorite()) {
                ivFavorite.setVisibility(View.VISIBLE);
            } else {
                ivFavorite.setVisibility(View.GONE);
            }

            // 封面加载
            String coverPath = book.getCoverPath();
            if (coverPath != null && !coverPath.isEmpty() && new File(coverPath).exists()) {
                ivCover.setVisibility(View.VISIBLE);
                llEmptyCover.setVisibility(View.GONE);
                ivCover.setImageResource(R.drawable.ic_text_editor);
            } else {
                // 空封面：Koodo Reader 风格
                ivCover.setVisibility(View.GONE);
                llEmptyCover.setVisibility(View.VISIBLE);

                // 格式banner颜色
                int formatColor = getFormatColor(book.getFormat());
                tvCoverBanner.setText(format);
                GradientDrawable bannerBg = new GradientDrawable();
                bannerBg.setColor(context.getResources().getColor(formatColor));
                tvCoverBanner.setBackground(bannerBg);

                // 标题
                tvCoverTitle.setText(book.getTitle());

                // Footer
                tvCoverFooter.setText("Koodo Reader");
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
