package com.toolbox.alltools.modules;

import com.toolbox.alltools.R;
import com.toolbox.alltools.ToolModule;

/**
 * 音视频格式转换器模块注册
 */
public class MediaConverterModule implements ToolModule {

    @Override
    public String getModuleName() {
        return "音视频格式转换器";
    }

    @Override
    public int getModuleIcon() {
        return R.drawable.ic_media_converter;
    }

    @Override
    public Class<?> getModuleActivityClass() {
        return MediaConverterActivity.class;
    }

    @Override
    public String getModuleDesc() {
        return "支持MP3、AAC、WAV、MP4、AVI、MKV等音视频格式转换";
    }
}
