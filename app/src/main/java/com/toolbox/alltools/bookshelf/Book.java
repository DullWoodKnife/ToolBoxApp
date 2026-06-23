package com.toolbox.alltools.bookshelf;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * 图书数据类，表示书架中的一本书。
 */
public class Book implements Parcelable {

    private long id;
    private String title;
    private String author;
    private String filePath;
    private String fileUri;
    private String format;       // "pdf", "epub", "mobi", "azw3"
    private String coverPath;    // nullable
    private String category;     // 默认 "默认"
    private int totalPages;
    private int currentPage;
    private int currentChapter;
    private float readProgress;  // 0 - 100
    private long lastReadTime;   // timestamp
    private long fileSize;
    private long addedTime;      // timestamp
    private boolean isFavorite;

    public Book() {
        this.category = "默认";
        this.readProgress = 0f;
        this.currentPage = 0;
        this.currentChapter = 0;
        this.totalPages = 0;
        this.isFavorite = false;
    }

    public Book(long id, String title, String author, String filePath, String fileUri,
                String format, String coverPath, String category, int totalPages,
                int currentPage, int currentChapter, float readProgress, long lastReadTime,
                long fileSize, long addedTime, boolean isFavorite) {
        this.id = id;
        this.title = title;
        this.author = author;
        this.filePath = filePath;
        this.fileUri = fileUri;
        this.format = format;
        this.coverPath = coverPath;
        this.category = category != null ? category : "默认";
        this.totalPages = totalPages;
        this.currentPage = currentPage;
        this.currentChapter = currentChapter;
        this.readProgress = readProgress;
        this.lastReadTime = lastReadTime;
        this.fileSize = fileSize;
        this.addedTime = addedTime;
        this.isFavorite = isFavorite;
    }

    protected Book(Parcel in) {
        id = in.readLong();
        title = in.readString();
        author = in.readString();
        filePath = in.readString();
        fileUri = in.readString();
        format = in.readString();
        coverPath = in.readString();
        category = in.readString();
        totalPages = in.readInt();
        currentPage = in.readInt();
        currentChapter = in.readInt();
        readProgress = in.readFloat();
        lastReadTime = in.readLong();
        fileSize = in.readLong();
        addedTime = in.readLong();
        isFavorite = in.readByte() != 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(id);
        dest.writeString(title);
        dest.writeString(author);
        dest.writeString(filePath);
        dest.writeString(fileUri);
        dest.writeString(format);
        dest.writeString(coverPath);
        dest.writeString(category);
        dest.writeInt(totalPages);
        dest.writeInt(currentPage);
        dest.writeInt(currentChapter);
        dest.writeFloat(readProgress);
        dest.writeLong(lastReadTime);
        dest.writeLong(fileSize);
        dest.writeLong(addedTime);
        dest.writeByte((byte) (isFavorite ? 1 : 0));
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<Book> CREATOR = new Creator<Book>() {
        @Override
        public Book createFromParcel(Parcel in) {
            return new Book(in);
        }

        @Override
        public Book[] newArray(int size) {
            return new Book[size];
        }
    };

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getFileUri() {
        return fileUri;
    }

    public void setFileUri(String fileUri) {
        this.fileUri = fileUri;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getCoverPath() {
        return coverPath;
    }

    public void setCoverPath(String coverPath) {
        this.coverPath = coverPath;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category != null ? category : "默认";
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public void setCurrentPage(int currentPage) {
        this.currentPage = currentPage;
    }

    public int getCurrentChapter() {
        return currentChapter;
    }

    public void setCurrentChapter(int currentChapter) {
        this.currentChapter = currentChapter;
    }

    public float getReadProgress() {
        return readProgress;
    }

    public void setReadProgress(float readProgress) {
        this.readProgress = readProgress;
    }

    public long getLastReadTime() {
        return lastReadTime;
    }

    public void setLastReadTime(long lastReadTime) {
        this.lastReadTime = lastReadTime;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public long getAddedTime() {
        return addedTime;
    }

    public void setAddedTime(long addedTime) {
        this.addedTime = addedTime;
    }

    public boolean isFavorite() {
        return isFavorite;
    }

    public void setFavorite(boolean favorite) {
        isFavorite = favorite;
    }

    @Override
    public String toString() {
        return "Book{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", author='" + author + '\'' +
                ", format='" + format + '\'' +
                ", category='" + category + '\'' +
                ", readProgress=" + readProgress +
                ", isFavorite=" + isFavorite +
                '}';
    }
}
