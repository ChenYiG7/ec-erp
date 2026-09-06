package com.own.erp.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ext.javatime.deser.LocalDateTimeDeserializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateTimeSerializer;
import tools.jackson.databind.module.SimpleDeserializers;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.module.SimpleSerializers;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : Jackson 3 全局 java.time 格式收口(docs/09 §3 契约事实"时间 yyyy-MM-dd HH:mm:ss 直显"):
 *     spring.jackson.date-format 仅作用于 java.util.Date,LocalDateTime 走 jsr310 内建序列化产出 ISO(带 T,
 *     如 2026-09-05T13:29:30),前端时间列直显契约被破坏——统一收口本格式,序列化/反序列化对称,禁散落 @JsonFormat;
 *     Boot 4 = Jackson 3(tools.jackson),定制入口为 JsonMapperBuilderCustomizer,
 *     注册走 SimpleModule(Jackson 3 的 MapperBuilder 已无 serializerByType 直挂方法)
 */
@Configuration
public class JacksonConfig {

    /** 时间直显格式(与 application.yml spring.jackson.date-format 同形;LocalDateTime 无时区,JDBC 侧已 GMT+8) */
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Bean
    public JsonMapperBuilderCustomizer localDateTimeFormatCustomizer() {
        return builder -> builder.addModule(new SimpleModule()
                .setSerializers(new SimpleSerializers()
                        .addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(DATETIME_FORMAT)))
                .setDeserializers(new SimpleDeserializers()
                        .addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(DATETIME_FORMAT))));
    }
}
