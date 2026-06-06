package com.smartparking.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Cấu hình Redis cho ứng dụng.
 * - Cung cấp StringRedisTemplate để thao tác với các key dạng chuỗi.
 * - Các service khác (RedisZoneCounterService, cache QR/pass...) sẽ @Autowired bean này.
 */
@Configuration
public class RedisConfig {

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        // sử dụng StringRedisTemplate vì tất cả keys/values hiện tại lưu dưới dạng string
        return new StringRedisTemplate(connectionFactory);
    }
}
