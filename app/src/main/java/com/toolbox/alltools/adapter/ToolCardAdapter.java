package com.toolbox.alltools.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.toolbox.alltools.R;
import com.toolbox.alltools.ToolModule;

import java.util.List;

/**
 * 工具卡片RecyclerView适配器
 * <p>
 * 负责展示工具模块卡片，支持交替卡片背景色和点击事件回调。
 * </p>
 */
public class ToolCardAdapter extends RecyclerView.Adapter<ToolCardAdapter.ToolCardViewHolder> {

    private final List<ToolModule> moduleList;
    private OnItemClickListener onItemClickListener;

    /**
     * 点击事件回调接口
     */
    public interface OnItemClickListener {
        void onItemClick(int position, ToolModule module);
    }

    public ToolCardAdapter(List<ToolModule> moduleList) {
        this.moduleList = moduleList;
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.onItemClickListener = listener;
    }

    @NonNull
    @Override
    public ToolCardViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_tool_card, parent, false);
        return new ToolCardViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ToolCardViewHolder holder, int position) {
        ToolModule module = moduleList.get(position);

        // 设置图标
        holder.ivIcon.setImageResource(module.getModuleIcon());

        // 设置名称
        holder.tvName.setText(module.getModuleName());

        // 交替使用深色和浅色卡片背景
        int cardColor;
        if (position % 2 == 0) {
            cardColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.card_dark);
        } else {
            cardColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.card_light);
        }
        holder.cardView.setCardBackgroundColor(cardColor);

        // 设置点击事件
        holder.itemView.setOnClickListener(v -> {
            if (onItemClickListener != null) {
                onItemClickListener.onItemClick(position, module);
            }
        });
    }

    @Override
    public int getItemCount() {
        return moduleList != null ? moduleList.size() : 0;
    }

    /**
     * ViewHolder
     */
    static class ToolCardViewHolder extends RecyclerView.ViewHolder {

        MaterialCardView cardView;
        ImageView ivIcon;
        TextView tvName;

        ToolCardViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = (MaterialCardView) itemView;
            ivIcon = itemView.findViewById(R.id.iv_tool_icon);
            tvName = itemView.findViewById(R.id.tv_tool_name);
        }
    }
}
