package com.toolbox.alltools;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.toolbox.alltools.adapter.ToolCardAdapter;

import java.util.List;

/**
 * 主界面Activity，展示所有工具模块卡片
 * <p>
 * 使用RecyclerView + GridLayoutManager实现网格布局。
 * 模块数据来源于 {@link ToolModuleRegistry}，新增模块无需修改此文件。
 * </p>
 */
public class MainActivity extends AppCompatActivity {

    private RecyclerView rvTools;
    private ToolCardAdapter adapter;
    private LinearLayout llPageIndicator;

    private static final int SPAN_COUNT = 2;
    private static final int ROWS_PER_PAGE = 3;
    private static final int ITEMS_PER_PAGE = SPAN_COUNT * ROWS_PER_PAGE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        initRecyclerView();
        initPageIndicator();
    }

    private void initViews() {
        rvTools = findViewById(R.id.rv_tools);
        llPageIndicator = findViewById(R.id.ll_page_indicator);
    }

    private void initRecyclerView() {
        // 从ToolModuleRegistry获取模块列表
        List<ToolModule> modules = ToolModuleRegistry.getInstance().getAllModules();

        // 创建适配器
        adapter = new ToolCardAdapter(modules);
        adapter.setOnItemClickListener((position, module) -> {
            // 跳转到对应Activity
            Intent intent = new Intent(MainActivity.this, module.getModuleActivityClass());
            startActivity(intent);
        });

        // 设置GridLayoutManager，spanCount=2
        GridLayoutManager layoutManager = new GridLayoutManager(this, SPAN_COUNT);
        rvTools.setLayoutManager(layoutManager);
        rvTools.setAdapter(adapter);

        // 监听滚动更新页面指示器
        rvTools.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                updatePageIndicator();
            }
        });
    }

    /**
     * 根据模块数量动态初始化页面指示器
     */
    private void initPageIndicator() {
        int moduleCount = ToolModuleRegistry.getInstance().getModuleCount();
        int pageCount = (int) Math.ceil((double) moduleCount / ITEMS_PER_PAGE);

        // 如果只有1页或0页，隐藏指示器
        if (pageCount <= 1) {
            llPageIndicator.setVisibility(View.GONE);
            return;
        }

        llPageIndicator.setVisibility(View.VISIBLE);
        llPageIndicator.removeAllViews();

        // 动态创建圆点
        for (int i = 0; i < pageCount; i++) {
            View dot = new View(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    24, 24); // 使用较大尺寸方便点击
            params.setMarginEnd(8);
            dot.setLayoutParams(params);

            if (i == 0) {
                dot.setBackgroundResource(R.drawable.indicator_dot_active);
            } else {
                dot.setBackgroundResource(R.drawable.indicator_dot_inactive);
            }
            llPageIndicator.addView(dot);
        }

        // 默认激活第一个圆点
        updatePageIndicator();
    }

    /**
     * 根据RecyclerView的滚动位置更新页面指示器
     */
    private void updatePageIndicator() {
        int childCount = llPageIndicator.getChildCount();
        if (childCount == 0) return;

        GridLayoutManager layoutManager = (GridLayoutManager) rvTools.getLayoutManager();
        if (layoutManager == null) return;

        int lastVisiblePosition = layoutManager.findLastVisibleItemPosition();

        // 计算当前页码
        int currentPage = lastVisiblePosition / ITEMS_PER_PAGE;
        if (currentPage >= childCount) {
            currentPage = childCount - 1;
        }

        // 更新圆点状态
        for (int i = 0; i < childCount; i++) {
            View dot = llPageIndicator.getChildAt(i);
            if (dot != null) {
                if (i == currentPage) {
                    dot.setBackgroundResource(R.drawable.indicator_dot_active);
                } else {
                    dot.setBackgroundResource(R.drawable.indicator_dot_inactive);
                }
            }
        }
    }
}
