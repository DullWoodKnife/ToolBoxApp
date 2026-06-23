package com.toolbox.alltools.readest;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.toolbox.alltools.R;
import com.toolbox.alltools.bookshelf.Book;
import com.toolbox.alltools.bookshelf.BookReaderActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Readest 网格视图适配器
 * 以网格形式展示书籍：封面区域、书名、格式标签
 */
public class ReadestBookGridAdapter extends RecyclerView.Adapter<ReadestBookGridAdapter.BookViewHolder> {

    private final Context context;
    private List<Book> books = new ArrayList<>();
    private OnBookActionListener actionListener;
    private final ExecutorService imageExecutor = Executors.newSingleThreadExecutor();

    public ReadestBookGridAdapter(Context context) {
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
        View view = LayoutInflater.from(context).inflate(R.layout.item_readest_book_grid, parent, false);
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

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        imageExecutor.shutdownNow();
    }

    class BookViewHolder extends RecyclerView.ViewHolder {

        private final ImageView ivCover;
        private final TextView tvCoverPlaceholder;
        private final TextView tvTitle;
        private final TextView tvFormatBadge;

        BookViewHolder(@NonNull View itemView) {
            super(itemView);
            ivCover = itemView.findViewById(R.id.iv_book_cover);
            tvCoverPlaceholder = itemView.findViewById(R.id.tv_cover_placeholder);
            tvTitle = itemView.findViewById(R.id.tv_book_title);
            tvFormatBadge = itemView.findViewById(R.id.tv_format_badge);

            itemView.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    openBookReader(books.get(pos).getId());
                }
            });

            itemView.setOnLongClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    showPopupMenu(v, books.get(pos));
                }
                return true;
            });
        }

        void bind(Book book) {
            tvTitle.setText(book.getTitle() != null ? book.getTitle() : "未知书名");

            // 格式标签
            String format = book.getFormat() != null ? book.getFormat().toUpperCase() : "UNKNOWN";
            tvFormatBadge.setText(format);

            // 封面加载
            String coverPath = book.getCoverPath();
            if (coverPath != null && !coverPath.isEmpty() && new File(coverPath).exists()) {
                ivCover.setVisibility(View.VISIBLE);
                tvCoverPlaceholder.setVisibility(View.GONE);
                loadCoverImage(ivCover, coverPath);
            } else {
                // 无封面时显示格式占位文字
                ivCover.setVisibility(View.GONE);
                tvCoverPlaceholder.setVisibility(View.VISIBLE);
                tvCoverPlaceholder.setText("-" + format + "-");

                // 设置占位文字背景色
                int bgColor;
                switch (format) {
                    case "PDF":
                        bgColor = 0xFFE74C3C;
                        break;
                    case "EPUB":
                        bgColor = 0xFF3498DB;
                        break;
                    case "MOBI":
                        bgColor = 0xFFE67E22;
                        break;
                    case "AZW3":
                        bgColor = 0xFF9B59B6;
                        break;
                    case "TXT":
                        bgColor = 0xFF27AE60;
                        break;
                    default:
                        bgColor = 0xFF95A5A6;
                        break;
                }
                android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
                bg.setColor(bgColor);
                bg.setCornerRadius(8);
                tvCoverPlaceholder.setBackground(bg);
            }
        }

        private void openBookReader(long bookId) {
            Intent intent = new Intent(context, BookReaderActivity.class);
            intent.putExtra(BookReaderActivity.EXTRA_BOOK_ID, bookId);
            context.startActivity(intent);
        }

        private void showPopupMenu(View anchorView, Book book) {
            PopupMenu popupMenu = new PopupMenu(context, anchorView);
            popupMenu.getMenuInflater().inflate(R.menu.menu_readest_book_item, popupMenu.getMenu());

            // 根据收藏状态更新菜单文字
            popupMenu.getMenu().findItem(R.id.action_readest_favorite)
                    .setTitle(book.isFavorite() ? "取消收藏" : "收藏");

            popupMenu.setOnMenuItemClickListener(item -> {
                int itemId = item.getItemId();
                if (itemId == R.id.action_readest_delete) {
                    if (actionListener != null) {
                        actionListener.onDeleteBook(book);
                    }
                    return true;
                } else if (itemId == R.id.action_readest_favorite) {
                    if (actionListener != null) {
                        actionListener.onToggleFavorite(book);
                    }
                    return true;
                }
                return false;
            });
            popupMenu.show();
        }
    }

    /**
     * 异步加载封面图片（不使用第三方库）
     */
    private void loadCoverImage(ImageView imageView, String path) {
        imageExecutor.execute(() -> {
            try {
                // 先采样缩小，避免OOM
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(path, options);

                options.inSampleSize = calculateInSampleSize(options, 200, 280);
                options.inJustDecodeBounds = false;

                final Bitmap bitmap = BitmapFactory.decodeFile(path, options);
                if (bitmap != null) {
                    android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                    mainHandler.post(() -> imageView.setImageBitmap(bitmap));
                }
            } catch (Exception ignored) {
            }
        });
    }

    /**
     * 计算图片采样率
     */
    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight
                    && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    /**
     * 图书操作回调接口
     */
    public interface OnBookActionListener {
        void onDeleteBook(Book book);
        void onToggleFavorite(Book book);
    }
}
