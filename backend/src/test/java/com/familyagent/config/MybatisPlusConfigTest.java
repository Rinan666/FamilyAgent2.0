package com.familyagent.config;

import org.apache.ibatis.annotations.Mapper;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MybatisPlusConfigTest {

    @Test
    void mapperScan_onlyRegistersExplicitMapperInterfaces() {
        MapperScan mapperScan = MybatisPlusConfig.class.getAnnotation(MapperScan.class);

        assertArrayEquals(new String[]{"com.familyagent.module"}, mapperScan.basePackages());
        assertEquals(Mapper.class, mapperScan.annotationClass());
    }
}
