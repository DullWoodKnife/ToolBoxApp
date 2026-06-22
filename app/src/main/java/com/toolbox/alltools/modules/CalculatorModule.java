package com.toolbox.alltools.modules;

import android.content.Context;
import android.content.Intent;

import com.toolbox.alltools.R;
import com.toolbox.alltools.ToolModule;

/**
 * 全能计算器模块
 */
public class CalculatorModule implements ToolModule {

    @Override
    public String getModuleName() {
        return "全能计算器";
    }

    @Override
    public String getModuleDesc() {
        return "计算器、亲戚称呼、日期计算、房贷计算、汇率换算、进制转换等29种计算工具";
    }

    @Override
    public int getModuleIcon() {
        return R.drawable.ic_calculator;
    }

    @Override
    public Class<?> getModuleActivityClass() {
        return CalculatorActivity.class;
    }
}
