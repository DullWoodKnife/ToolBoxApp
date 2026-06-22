package com.toolbox.alltools.modules;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.toolbox.alltools.R;
import com.toolbox.alltools.base.BaseToolActivity;

import java.util.Arrays;
import java.util.List;

/**
 * 全能计算器Activity
 * 使用Grid布局展示29个计算子项，每页12项
 */
public class CalculatorActivity extends BaseToolActivity {

    private static final int SPAN_COUNT = 3; // 每行3列
    private static final int ITEMS_PER_PAGE = 12; // 每页12项

    private static final List<String> CALCULATOR_ITEMS = Arrays.asList(
            "计算器", "亲戚称呼", "日期计算", "年龄计算", "时间转换", "BMI指数",
            "退休计算", "房贷计算", "购车计算", "个税计算", "大写金额", "理财计算",
            "汇率换算", "油耗量转换", "进制转换", "电阻转换", "能量转换", "温度转换",
            "速度转换", "重量转换", "功率转换", "热量转换", "角度转换", "密度转换",
            "压强转换", "容量转换", "长度转换", "面积转换", "体积转换"
    );

    private RecyclerView recyclerView;
    private TextView tvPageIndicator;

    @Override
    protected int getLayoutResId() {
        return R.layout.activity_calculator;
    }

    @Override
    protected String getToolTitle() {
        return getString(R.string.title_calculator);
    }

    @Override
    protected void initViews() {
        recyclerView = findViewById(R.id.recycler_calculator);
        tvPageIndicator = findViewById(R.id.tv_page_indicator);

        recyclerView.setLayoutManager(new GridLayoutManager(this, SPAN_COUNT));
        CalculatorAdapter adapter = new CalculatorAdapter(CALCULATOR_ITEMS);
        recyclerView.setAdapter(adapter);

        updatePageIndicator(0);

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                GridLayoutManager layoutManager = (GridLayoutManager) recyclerView.getLayoutManager();
                if (layoutManager != null) {
                    int firstVisible = layoutManager.findFirstVisibleItemPosition();
                    int currentPage = firstVisible / ITEMS_PER_PAGE;
                    updatePageIndicator(currentPage);
                }
            }
        });
    }

    private void updatePageIndicator(int page) {
        int totalPages = (CALCULATOR_ITEMS.size() + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE;
        tvPageIndicator.setText((page + 1) + " / " + totalPages);
    }

    @Override
    protected void initListeners() {}

    @Override
    protected void initData() {}

    private static class CalculatorAdapter extends RecyclerView.Adapter<CalculatorAdapter.ViewHolder> {

        private final List<String> items;

        CalculatorAdapter(List<String> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_calculator_grid, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            String item = items.get(position);
            holder.tvName.setText(item);
            holder.itemView.setOnClickListener(v -> {
                // TODO: 根据item名称跳转到对应的计算页面
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName;

            ViewHolder(View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tv_item_name);
            }
        }
    }
}
