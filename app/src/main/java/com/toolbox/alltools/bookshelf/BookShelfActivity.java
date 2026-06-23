package com.toolbox.alltools.bookshelf;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.toolbox.alltools.R;



/**
 * 书架主Activity
 * 参考Koodo Reader设计，底部导航栏：首页/书架/笔记/设置
 */
public class BookShelfActivity extends AppCompatActivity {

    private static final int REQUEST_PICK_BOOK = 1001;

    private ViewPager2 viewPager;
    private BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_book_shelf);

        initViews();
        setupViewPager();
        setupBottomNav();
    }

    private void initViews() {
        viewPager = findViewById(R.id.view_pager);
        bottomNav = findViewById(R.id.bottom_nav);
    }

    private void setupViewPager() {
        viewPager.setAdapter(new FragmentStateAdapter(this) {
            @Override
            public int getItemCount() {
                return 4;
            }

            @Override
            public Fragment createFragment(int position) {
                switch (position) {
                    case 0: return new BookHomeFragment();
                    case 1: return new BookShelfFragment();
                    case 2: return new BookNotesFragment();
                    case 3: return new BookSettingsFragment();
                    default: return new BookHomeFragment();
                }
            }
        });

        viewPager.setOffscreenPageLimit(3);
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                int itemId;
                switch (position) {
                    case 0: itemId = R.id.nav_home; break;
                    case 1: itemId = R.id.nav_shelf; break;
                    case 2: itemId = R.id.nav_notes; break;
                    case 3: itemId = R.id.nav_settings; break;
                    default: itemId = R.id.nav_home;
                }
                bottomNav.setSelectedItemId(itemId);
            }
        });
    }

    private void setupBottomNav() {
        bottomNav.setOnItemSelectedListener(item -> {
            int position;
            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) {
                position = 0;
            } else if (itemId == R.id.nav_shelf) {
                position = 1;
            } else if (itemId == R.id.nav_notes) {
                position = 2;
            } else if (itemId == R.id.nav_settings) {
                position = 3;
            } else {
                position = 0;
            }
            viewPager.setCurrentItem(position, false);
            return true;
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_BOOK && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                addBookFromUri(uri);
            }
        }
    }

    private void addBookFromUri(Uri uri) {
        String fileName = getFileNameFromUri(uri);
        String extension = getFileExtension(fileName);
        String format = extension.toLowerCase();

        if (!isSupportedFormat(format)) {
            Toast.makeText(this, "不支持的格式: " + format, Toast.LENGTH_SHORT).show();
            return;
        }

        Book book = new Book();
        book.setTitle(fileName.replaceFirst("\\.[^.]+$", ""));
        book.setAuthor("");
        book.setFileUri(uri.toString());
        book.setFormat(format);
        book.setCategory("默认");
        book.setAddedTime(System.currentTimeMillis());

        BookDatabaseHelper db = BookDatabaseHelper.getInstance(this);
        long bookId = db.insertBook(book);

        if (bookId > 0) {
            Toast.makeText(this, "已添加: " + book.getTitle(), Toast.LENGTH_SHORT).show();
            // 通知书架Fragment刷新
            Fragment shelfFragment = getSupportFragmentManager().findFragmentByTag("f1");
            if (shelfFragment instanceof BookShelfFragment) {
                ((BookShelfFragment) shelfFragment).refreshBooks();
            }
        } else {
            Toast.makeText(this, "添加失败", Toast.LENGTH_SHORT).show();
        }
    }

    private String getFileNameFromUri(Uri uri) {
        String path = uri.getLastPathSegment();
        if (path != null) {
            int slashIndex = path.lastIndexOf('/');
            if (slashIndex >= 0) {
                return path.substring(slashIndex + 1);
            }
            return path;
        }
        return "unknown";
    }

    private String getFileExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
            return fileName.substring(dotIndex + 1);
        }
        return "";
    }

    private boolean isSupportedFormat(String format) {
        return "pdf".equals(format) || "epub".equals(format)
                || "mobi".equals(format) || "azw3".equals(format);
    }
}
