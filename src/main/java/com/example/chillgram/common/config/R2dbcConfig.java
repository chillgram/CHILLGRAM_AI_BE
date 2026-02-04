package com.example.chillgram.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions;
import org.springframework.data.r2dbc.dialect.MySqlDialect;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class R2dbcConfig {

    @Bean
    public R2dbcCustomConversions r2dbcCustomConversions() {
        List<Converter<?, ?>> converters = new ArrayList<>();
        converters.add(new BooleanToByteConverter());
        converters.add(new ByteToBooleanConverter());
        return R2dbcCustomConversions.of(MySqlDialect.INSTANCE, converters);
    }

    /**
     * Writing Converter: Boolean -> Byte (DB 저장 시)
     */
    @WritingConverter
    public static class BooleanToByteConverter implements Converter<Boolean, Byte> {
        @Override
        public Byte convert(Boolean source) {
            return (byte) (Boolean.TRUE.equals(source) ? 1 : 0);
        }
    }

    /**
     * Reading Converter: Byte -> Boolean (DB 조회 시)
     */
    @ReadingConverter
    public static class ByteToBooleanConverter implements Converter<Byte, Boolean> {
        @Override
        public Boolean convert(Byte source) {
            return source != 0;
        }
    }
}
