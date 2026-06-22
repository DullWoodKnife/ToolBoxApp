package com.toolbox.alltools.modules;

import com.toolbox.alltools.R;
import com.toolbox.alltools.ToolModule;

/**
 * 音频格式转换器模块注册
 */
public class AudioConverterModule implements ToolModule {

    @Override
    public String getModuleName() {
        return "音频格式转换";
    }

    @Override
    public int getModuleIcon() {
        return R.drawable.ic_audio_converter;
    }

    @Override
    public Class<?> getModuleActivityClass() {
        return AudioConverterActivity.class;
    }

    @Override
    public String getModuleDesc() {
        return "支持MP3、AAC、WAV、FLAC、OGG、M4A、WMA等音频格式转换";
    }
}
