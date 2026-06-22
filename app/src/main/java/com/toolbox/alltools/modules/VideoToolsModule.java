package com.toolbox.alltools.modules;

import com.toolbox.alltools.R;
import com.toolbox.alltools.ToolModule;

/**
 * 视频工具模块注册
 */
public class VideoToolsModule implements ToolModule {

    @Override
    public String getModuleName() {
        return "视频工具";
    }

    @Override
    public int getModuleIcon() {
        return R.drawable.ic_video_tools;
    }

    @Override
    public Class<?> getModuleActivityClass() {
        return VideoToolsActivity.class;
    }

    @Override
    public String getModuleDesc() {
        return "支持MP4、AVI、MKV、MOV、WebM、FLV、WMV等视频格式处理";
    }
}
