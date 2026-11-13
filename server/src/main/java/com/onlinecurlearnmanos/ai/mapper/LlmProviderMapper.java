package com.onlinecurlearnmanos.ai.mapper;

import com.onlinecurlearnmanos.ai.entity.LlmProviderConfig;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LlmProviderMapper extends BaseMapper<LlmProviderConfig> {
}
