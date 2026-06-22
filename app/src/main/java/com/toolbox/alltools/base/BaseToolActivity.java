package com.toolbox.alltools.base;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.toolbox.alltools.R;

/**
 * 所有工具Activity的基类
 * <p>
 * 提供通用的Activity生命周期管理和UI初始化模板。
 * 子类只需实现抽象方法即可快速创建一个标准化的工具页面。
 * </p>
 *
 * <p>包含以下通用功能：</p>
 * <ul>
 *     <li>自动查找并设置Toolbar（如果布局中存在androidx.appcompat.widget.Toolbar）</li>
 *     <li>自动查找并绑定btn_back返回按钮（如果布局中存在ImageButton id=btn_back）</li>
 *     <li>标准化的初始化流程（布局 -> 标题 -> 视图 -> 监听器 -> 数据）</li>
 * </ul>
 *
 * <p>子类实现示例：</p>
 * <pre>
 *     public class CalculatorActivity extends BaseToolActivity {
 *         &#64;Override
 *         protected int getLayoutResId() { return R.layout.activity_calculator; }
 *
 *         &#64;Override
 *         protected String getToolTitle() { return "计算器"; }
 *
 *         &#64;Override
 *         protected void initViews() { // 初始化视图组件 }
 *
 *         &#64;Override
 *         protected void initListeners() { // 设置监听器 }
 *
 *         &#64;Override
 *         protected void initData() { // 加载数据 }
 *     }
 * </pre>
 */
public abstract class BaseToolActivity extends AppCompatActivity {

    /** 通用Toolbar，子类可直接访问（如果布局中使用了Toolbar） */
    protected Toolbar toolbar;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. 设置内容布局
        int layoutResId = getLayoutResId();
        if (layoutResId != 0) {
            setContentView(layoutResId);
        }

        // 2. 初始化Toolbar（仅在布局中存在真正的Toolbar时设置）
        setupToolbar();

        // 3. 自动绑定返回按钮（btn_back ImageButton）
        setupBackButton();

        // 4. 设置页面标题
        setupTitle();

        // 5. 按顺序初始化子类组件
        initViews();
        initListeners();
        initData();
    }

    /**
     * 初始化Toolbar
     * <p>直接使用R.id.toolbar查找，配合instanceof检查类型安全。
     * 如果布局中不存在此ID或类型不是Toolbar，则安全跳过。</p>
     */
    private void setupToolbar() {
        View view = findViewById(R.id.toolbar);
        if (view instanceof Toolbar) {
            toolbar = (Toolbar) view;
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                getSupportActionBar().setDisplayShowHomeEnabled(true);
            }
        }
    }

    /**
     * 自动绑定返回按钮
     * <p>查找布局中ID为btn_back的View，如果是ImageButton则设置点击返回事件。
     * 如果布局中没有此控件或类型不匹配，则安全跳过。</p>
     */
    private void setupBackButton() {
        View view = findViewById(R.id.btn_back);
        if (view instanceof ImageButton) {
            view.setOnClickListener(v -> onBackPressed());
        }
    }

    /**
     * 设置页面标题
     * <p>将子类提供的工具标题设置到ActionBar和窗口标题上。</p>
     */
    private void setupTitle() {
        String title = getToolTitle();
        if (title != null && !title.isEmpty()) {
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle(title);
            }
            setTitle(title);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // 处理Toolbar返回按钮点击事件
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * 获取布局资源ID
     * <p>子类必须实现此方法，返回对应的布局文件资源ID。</p>
     *
     * @return 布局资源ID，如 R.layout.activity_calculator
     */
    protected abstract int getLayoutResId();

    /**
     * 获取工具页面标题
     * <p>子类必须实现此方法，返回当前工具页面的标题文字。</p>
     *
     * @return 页面标题字符串
     */
    protected abstract String getToolTitle();

    /**
     * 初始化视图组件
     * <p>子类在此方法中执行findViewById等视图初始化操作。
     * 此方法在onCreate中被调用，顺序在布局设置和Toolbar初始化之后。</p>
     */
    protected abstract void initViews();

    /**
     * 初始化监听器
     * <p>子类在此方法中设置按钮点击、文本变化等监听器。
     * 此方法在initViews()之后调用。</p>
     */
    protected abstract void initListeners();

    /**
     * 初始化数据
     * <p>子类在此方法中加载和填充数据，如从数据库读取、网络请求等。
     * 此方法在initListeners()之后调用，是初始化流程的最后一步。</p>
     */
    protected abstract void initData();
}
