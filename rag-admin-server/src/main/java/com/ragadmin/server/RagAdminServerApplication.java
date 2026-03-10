package com.ragadmin.server;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
// 仅扫描 MyBatis Plus Mapper，避免把 MapStruct Mapper 误注册为 MyBatis 代理。
@MapperScan(value = "com.ragadmin.server", markerInterface = BaseMapper.class)
@EnableScheduling
public class RagAdminServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagAdminServerApplication.class, args);
    }
}
