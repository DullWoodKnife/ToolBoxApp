package com.toolbox.alltools.bookshelf;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * 书架数据库帮助类，管理图书、阅读历史和分类数据。
 * 使用原生 Android SQLite API，不使用 Room 或其他 ORM。
 */
public class BookDatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "bookshelf.db";
    private static final int DATABASE_VERSION = 1;

    // 图书表
    public static final String TABLE_BOOKS = "books";
    public static final String COL_BOOK_ID = "id";
    public static final String COL_BOOK_TITLE = "title";
    public static final String COL_BOOK_AUTHOR = "author";
    public static final String COL_BOOK_FILE_PATH = "file_path";
    public static final String COL_BOOK_FILE_URI = "file_uri";
    public static final String COL_BOOK_FORMAT = "format";
    public static final String COL_BOOK_COVER_PATH = "cover_path";
    public static final String COL_BOOK_CATEGORY = "category";
    public static final String COL_BOOK_TOTAL_PAGES = "total_pages";
    public static final String COL_BOOK_CURRENT_PAGE = "current_page";
    public static final String COL_BOOK_CURRENT_CHAPTER = "current_chapter";
    public static final String COL_BOOK_READ_PROGRESS = "read_progress";
    public static final String COL_BOOK_LAST_READ_TIME = "last_read_time";
    public static final String COL_BOOK_FILE_SIZE = "file_size";
    public static final String COL_BOOK_ADDED_TIME = "added_time";
    public static final String COL_BOOK_IS_FAVORITE = "is_favorite";

    // 阅读历史表
    public static final String TABLE_HISTORY = "reading_history";
    public static final String COL_HISTORY_ID = "id";
    public static final String COL_HISTORY_BOOK_ID = "book_id";
    public static final String COL_HISTORY_READ_TIME = "read_time";
    public static final String COL_HISTORY_PAGE = "page";
    public static final String COL_HISTORY_CHAPTER = "chapter";
    public static final String COL_HISTORY_PROGRESS = "progress";

    // 分类表
    public static final String TABLE_CATEGORIES = "categories";
    public static final String COL_CATEGORY_ID = "id";
    public static final String COL_CATEGORY_NAME = "name";
    public static final String COL_CATEGORY_SORT_ORDER = "sort_order";

    // 创建图书表 SQL
    private static final String CREATE_TABLE_BOOKS =
            "CREATE TABLE IF NOT EXISTS " + TABLE_BOOKS + " (" +
                    COL_BOOK_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COL_BOOK_TITLE + " TEXT NOT NULL, " +
                    COL_BOOK_AUTHOR + " TEXT, " +
                    COL_BOOK_FILE_PATH + " TEXT, " +
                    COL_BOOK_FILE_URI + " TEXT, " +
                    COL_BOOK_FORMAT + " TEXT, " +
                    COL_BOOK_COVER_PATH + " TEXT, " +
                    COL_BOOK_CATEGORY + " TEXT DEFAULT '默认', " +
                    COL_BOOK_TOTAL_PAGES + " INTEGER DEFAULT 0, " +
                    COL_BOOK_CURRENT_PAGE + " INTEGER DEFAULT 0, " +
                    COL_BOOK_CURRENT_CHAPTER + " INTEGER DEFAULT 0, " +
                    COL_BOOK_READ_PROGRESS + " REAL DEFAULT 0, " +
                    COL_BOOK_LAST_READ_TIME + " INTEGER DEFAULT 0, " +
                    COL_BOOK_FILE_SIZE + " INTEGER DEFAULT 0, " +
                    COL_BOOK_ADDED_TIME + " INTEGER DEFAULT 0, " +
                    COL_BOOK_IS_FAVORITE + " INTEGER DEFAULT 0" +
                    ")";

    // 创建阅读历史表 SQL
    private static final String CREATE_TABLE_HISTORY =
            "CREATE TABLE IF NOT EXISTS " + TABLE_HISTORY + " (" +
                    COL_HISTORY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COL_HISTORY_BOOK_ID + " INTEGER NOT NULL, " +
                    COL_HISTORY_READ_TIME + " INTEGER DEFAULT 0, " +
                    COL_HISTORY_PAGE + " INTEGER DEFAULT 0, " +
                    COL_HISTORY_CHAPTER + " INTEGER DEFAULT 0, " +
                    COL_HISTORY_PROGRESS + " REAL DEFAULT 0, " +
                    "FOREIGN KEY(" + COL_HISTORY_BOOK_ID + ") REFERENCES " +
                    TABLE_BOOKS + "(" + COL_BOOK_ID + ") ON DELETE CASCADE" +
                    ")";

    // 创建分类表 SQL
    private static final String CREATE_TABLE_CATEGORIES =
            "CREATE TABLE IF NOT EXISTS " + TABLE_CATEGORIES + " (" +
                    COL_CATEGORY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COL_CATEGORY_NAME + " TEXT NOT NULL UNIQUE, " +
                    COL_CATEGORY_SORT_ORDER + " INTEGER DEFAULT 0" +
                    ")";

    // 插入默认分类
    private static final String INSERT_DEFAULT_CATEGORY =
            "INSERT OR IGNORE INTO " + TABLE_CATEGORIES +
                    " (" + COL_CATEGORY_NAME + ", " + COL_CATEGORY_SORT_ORDER + ") VALUES ('默认', 0)";

    private static BookDatabaseHelper instance;

    public static synchronized BookDatabaseHelper getInstance(Context context) {
        if (instance == null) {
            instance = new BookDatabaseHelper(context.getApplicationContext());
        }
        return instance;
    }

    private BookDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_BOOKS);
        db.execSQL(CREATE_TABLE_HISTORY);
        db.execSQL(CREATE_TABLE_CATEGORIES);
        db.execSQL(INSERT_DEFAULT_CATEGORY);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // 后续版本升级时在此处理迁移逻辑
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    // ==================== 图书 CRUD ====================

    /**
     * 插入一本新书
     */
    public long insertBook(Book book) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = bookToContentValues(book);
        long id = db.insert(TABLE_BOOKS, null, values);
        db.close();
        return id;
    }

    /**
     * 根据 ID 更新图书信息
     */
    public int updateBook(Book book) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = bookToContentValues(book);
        int rows = db.update(TABLE_BOOKS, values,
                COL_BOOK_ID + " = ?",
                new String[]{String.valueOf(book.getId())});
        db.close();
        return rows;
    }

    /**
     * 根据 ID 删除图书
     */
    public int deleteBook(long bookId) {
        SQLiteDatabase db = this.getWritableDatabase();
        int rows = db.delete(TABLE_BOOKS,
                COL_BOOK_ID + " = ?",
                new String[]{String.valueOf(bookId)});
        db.close();
        return rows;
    }

    /**
     * 根据 ID 查询单本图书
     */
    public Book getBookById(long bookId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_BOOKS, null,
                COL_BOOK_ID + " = ?",
                new String[]{String.valueOf(bookId)},
                null, null, null);
        Book book = null;
        if (cursor != null && cursor.moveToFirst()) {
            book = cursorToBook(cursor);
            cursor.close();
        }
        db.close();
        return book;
    }

    /**
     * 查询所有图书
     */
    public List<Book> getAllBooks() {
        List<Book> books = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_BOOKS, null, null, null,
                null, null, COL_BOOK_ADDED_TIME + " DESC");
        if (cursor != null) {
            while (cursor.moveToNext()) {
                books.add(cursorToBook(cursor));
            }
            cursor.close();
        }
        db.close();
        return books;
    }

    // ==================== 查询方法 ====================

    /**
     * 按分类查询图书
     */
    public List<Book> getBooksByCategory(String category) {
        List<Book> books = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_BOOKS, null,
                COL_BOOK_CATEGORY + " = ?",
                new String[]{category},
                null, null, COL_BOOK_ADDED_TIME + " DESC");
        if (cursor != null) {
            while (cursor.moveToNext()) {
                books.add(cursorToBook(cursor));
            }
            cursor.close();
        }
        db.close();
        return books;
    }

    /**
     * 获取最近阅读的书籍（按 last_read_time 降序）
     */
    public List<Book> getRecentBooks(int limit) {
        List<Book> books = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_BOOKS, null,
                COL_BOOK_LAST_READ_TIME + " > 0",
                null, null, null,
                COL_BOOK_LAST_READ_TIME + " DESC",
                String.valueOf(limit));
        if (cursor != null) {
            while (cursor.moveToNext()) {
                books.add(cursorToBook(cursor));
            }
            cursor.close();
        }
        db.close();
        return books;
    }

    /**
     * 获取收藏的书籍
     */
    public List<Book> getFavoriteBooks() {
        List<Book> books = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_BOOKS, null,
                COL_BOOK_IS_FAVORITE + " = 1",
                null, null, null,
                COL_BOOK_ADDED_TIME + " DESC");
        if (cursor != null) {
            while (cursor.moveToNext()) {
                books.add(cursorToBook(cursor));
            }
            cursor.close();
        }
        db.close();
        return books;
    }

    /**
     * 搜索图书（按标题或作者模糊匹配）
     */
    public List<Book> searchBooks(String keyword) {
        List<Book> books = new ArrayList<>();
        if (keyword == null || keyword.trim().isEmpty()) {
            return books;
        }
        SQLiteDatabase db = this.getReadableDatabase();
        String likePattern = "%" + keyword.trim() + "%";
        Cursor cursor = db.query(TABLE_BOOKS, null,
                COL_BOOK_TITLE + " LIKE ? OR " + COL_BOOK_AUTHOR + " LIKE ?",
                new String[]{likePattern, likePattern},
                null, null,
                COL_BOOK_ADDED_TIME + " DESC");
        if (cursor != null) {
            while (cursor.moveToNext()) {
                books.add(cursorToBook(cursor));
            }
            cursor.close();
        }
        db.close();
        return books;
    }

    // ==================== 阅读进度 ====================

    /**
     * 更新阅读进度，同时记录阅读历史
     */
    public void updateReadingProgress(long bookId, int currentPage, int currentChapter,
                                      float progress, long timestamp) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.beginTransaction();
        try {
            // 更新图书表
            ContentValues bookValues = new ContentValues();
            bookValues.put(COL_BOOK_CURRENT_PAGE, currentPage);
            bookValues.put(COL_BOOK_CURRENT_CHAPTER, currentChapter);
            bookValues.put(COL_BOOK_READ_PROGRESS, progress);
            bookValues.put(COL_BOOK_LAST_READ_TIME, timestamp);
            db.update(TABLE_BOOKS, bookValues,
                    COL_BOOK_ID + " = ?",
                    new String[]{String.valueOf(bookId)});

            // 插入阅读历史
            ContentValues historyValues = new ContentValues();
            historyValues.put(COL_HISTORY_BOOK_ID, bookId);
            historyValues.put(COL_HISTORY_READ_TIME, timestamp);
            historyValues.put(COL_HISTORY_PAGE, currentPage);
            historyValues.put(COL_HISTORY_CHAPTER, currentChapter);
            historyValues.put(COL_HISTORY_PROGRESS, progress);
            db.insert(TABLE_HISTORY, null, historyValues);

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
            db.close();
        }
    }

    /**
     * 切换收藏状态
     */
    public int toggleFavorite(long bookId, boolean favorite) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_BOOK_IS_FAVORITE, favorite ? 1 : 0);
        int rows = db.update(TABLE_BOOKS, values,
                COL_BOOK_ID + " = ?",
                new String[]{String.valueOf(bookId)});
        db.close();
        return rows;
    }

    // ==================== 分类管理 ====================

    /**
     * 插入新分类
     */
    public long insertCategory(String name, int sortOrder) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_CATEGORY_NAME, name);
        values.put(COL_CATEGORY_SORT_ORDER, sortOrder);
        long id = db.insert(TABLE_CATEGORIES, null, values);
        db.close();
        return id;
    }

    /**
     * 获取所有分类
     */
    public List<String> getAllCategories() {
        List<String> categories = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_CATEGORIES,
                new String[]{COL_CATEGORY_NAME},
                null, null, null, null,
                COL_CATEGORY_SORT_ORDER + " ASC");
        if (cursor != null) {
            while (cursor.moveToNext()) {
                categories.add(cursor.getString(cursor.getColumnIndexOrThrow(COL_CATEGORY_NAME)));
            }
            cursor.close();
        }
        db.close();
        return categories;
    }

    /**
     * 删除分类（不会删除图书，仅删除分类记录）
     */
    public int deleteCategory(long categoryId) {
        SQLiteDatabase db = this.getWritableDatabase();
        int rows = db.delete(TABLE_CATEGORIES,
                COL_CATEGORY_ID + " = ?",
                new String[]{String.valueOf(categoryId)});
        db.close();
        return rows;
    }

    // ==================== 工具方法 ====================

    private ContentValues bookToContentValues(Book book) {
        ContentValues values = new ContentValues();
        values.put(COL_BOOK_TITLE, book.getTitle());
        values.put(COL_BOOK_AUTHOR, book.getAuthor());
        values.put(COL_BOOK_FILE_PATH, book.getFilePath());
        values.put(COL_BOOK_FILE_URI, book.getFileUri());
        values.put(COL_BOOK_FORMAT, book.getFormat());
        values.put(COL_BOOK_COVER_PATH, book.getCoverPath());
        values.put(COL_BOOK_CATEGORY, book.getCategory() != null ? book.getCategory() : "默认");
        values.put(COL_BOOK_TOTAL_PAGES, book.getTotalPages());
        values.put(COL_BOOK_CURRENT_PAGE, book.getCurrentPage());
        values.put(COL_BOOK_CURRENT_CHAPTER, book.getCurrentChapter());
        values.put(COL_BOOK_READ_PROGRESS, book.getReadProgress());
        values.put(COL_BOOK_LAST_READ_TIME, book.getLastReadTime());
        values.put(COL_BOOK_FILE_SIZE, book.getFileSize());
        values.put(COL_BOOK_ADDED_TIME, book.getAddedTime());
        values.put(COL_BOOK_IS_FAVORITE, book.isFavorite() ? 1 : 0);
        return values;
    }

    private Book cursorToBook(Cursor cursor) {
        Book book = new Book();
        book.setId(cursor.getLong(cursor.getColumnIndexOrThrow(COL_BOOK_ID)));
        book.setTitle(cursor.getString(cursor.getColumnIndexOrThrow(COL_BOOK_TITLE)));
        book.setAuthor(cursor.getString(cursor.getColumnIndexOrThrow(COL_BOOK_AUTHOR)));
        book.setFilePath(cursor.getString(cursor.getColumnIndexOrThrow(COL_BOOK_FILE_PATH)));
        book.setFileUri(cursor.getString(cursor.getColumnIndexOrThrow(COL_BOOK_FILE_URI)));
        book.setFormat(cursor.getString(cursor.getColumnIndexOrThrow(COL_BOOK_FORMAT)));
        book.setCoverPath(cursor.getString(cursor.getColumnIndexOrThrow(COL_BOOK_COVER_PATH)));
        book.setCategory(cursor.getString(cursor.getColumnIndexOrThrow(COL_BOOK_CATEGORY)));
        book.setTotalPages(cursor.getInt(cursor.getColumnIndexOrThrow(COL_BOOK_TOTAL_PAGES)));
        book.setCurrentPage(cursor.getInt(cursor.getColumnIndexOrThrow(COL_BOOK_CURRENT_PAGE)));
        book.setCurrentChapter(cursor.getInt(cursor.getColumnIndexOrThrow(COL_BOOK_CURRENT_CHAPTER)));
        book.setReadProgress(cursor.getFloat(cursor.getColumnIndexOrThrow(COL_BOOK_READ_PROGRESS)));
        book.setLastReadTime(cursor.getLong(cursor.getColumnIndexOrThrow(COL_BOOK_LAST_READ_TIME)));
        book.setFileSize(cursor.getLong(cursor.getColumnIndexOrThrow(COL_BOOK_FILE_SIZE)));
        book.setAddedTime(cursor.getLong(cursor.getColumnIndexOrThrow(COL_BOOK_ADDED_TIME)));
        book.setFavorite(cursor.getInt(cursor.getColumnIndexOrThrow(COL_BOOK_IS_FAVORITE)) == 1);
        return book;
    }

    /**
     * 清除所有数据（保留分类表结构）
     */
    public void clearAllData() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(TABLE_BOOKS, null, null);
            db.delete(TABLE_HISTORY, null, null);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
            db.close();
        }
    }
}
