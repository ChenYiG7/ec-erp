package com.own.erp.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : JacksonConfig 单测:LocalDateTime 序列化不带 T(docs/09 §3 契约)、
 *     反序列化同格式对称可逆、ISO 形态(带 T)入参被拒(契约单一格式)
 */
class JacksonConfigTest {

    private JsonMapper mapper;

    @BeforeEach
    void setUp() {
        JsonMapper.Builder builder = JsonMapper.builder();
        new JacksonConfig().localDateTimeFormatCustomizer().customize(builder);
        mapper = builder.build();
    }

    @Test
    void serializesWithoutT() {
        String json = mapper.writeValueAsString(LocalDateTime.of(2026, 9, 5, 13, 29, 30));
        assertEquals("\"2026-09-05 13:29:30\"", json);
    }

    @Test
    void deserializesSymmetrically() {
        LocalDateTime parsed = mapper.readValue("\"2026-09-05 13:29:30\"", LocalDateTime.class);
        assertEquals(LocalDateTime.of(2026, 9, 5, 13, 29, 30), parsed);
    }

    @Test
    void rejectsIsoFormOnDeserialize() {
        assertThrows(Exception.class, () -> mapper.readValue("\"2026-09-05T13:29:30\"", LocalDateTime.class));
    }
}
