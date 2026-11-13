package com.onlinecurlearnmanos.security.mapper;

import com.onlinecurlearnmanos.security.entity.RevokedToken;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RevokedTokenMapper extends BaseMapper<RevokedToken> {
}
