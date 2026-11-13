package com.onlinecurlearnmanos.support;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;

/**
 * 纯单元测试没有 MyBatis 容器，而 {@code LambdaUpdateWrapper#set} 会在调用时立即把
 * 方法引用解析成列名，因此需要先手工注册实体元数据，否则会抛出
 * "can not find lambda cache for this entity"。
 */
public final class MybatisLambdaCache {

    private MybatisLambdaCache() {
    }

    public static void register(Class<?>... entities) {
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "");
        for (Class<?> entity : entities) {
            if (TableInfoHelper.getTableInfo(entity) == null) {
                TableInfoHelper.initTableInfo(assistant, entity);
            }
        }
    }
}
