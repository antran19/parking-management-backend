package com.smartparking.backend.config;

import com.smartparking.backend.entity.Zone;
import com.smartparking.backend.repository.ZoneRepository;
import com.smartparking.backend.service.RedisZoneCounterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ZoneRedisSyncRunner — đồng bộ trạng thái zones từ DB sang Redis khi app start.
 * Mục đích:
 *  - Đảm bảo các key zone:count:{zoneId} được khởi tạo bằng giá trị hiện tại trong DB
 *  - Tránh trường hợp Redis trống khiến suggest/decision dùng fallback DB và gây không nhất quán
 * Lưu ý:
 *  - Đây là sync 1 chiều khi khởi động. Trong runtime, luồng check-in/check-out phải cập nhật Redis
 *    (Redis là nguồn dữ liệu thời gian thực cho counters).
 */
@Component
public class ZoneRedisSyncRunner implements ApplicationListener<ContextRefreshedEvent> {

    private static final Logger log = LoggerFactory.getLogger(ZoneRedisSyncRunner.class);

    private final ZoneRepository zoneRepository;
    private final RedisZoneCounterService redisZoneCounterService;

    public ZoneRedisSyncRunner(ZoneRepository zoneRepository, RedisZoneCounterService redisZoneCounterService) {
        this.zoneRepository = zoneRepository;
        this.redisZoneCounterService = redisZoneCounterService;
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        try {
            List<Zone> zones = zoneRepository.findAll();
            // Ghi giá trị hiện tại của DB vào Redis để có snapshot ban đầu
            zones.forEach(z -> redisZoneCounterService.setCount(z.getId(), z.getCurrentCount()));
            log.info("Synced {} zones to Redis zone:count:*", zones.size());
        } catch (Exception e) {
            // Không throw để không làm fail startup, log để điều tra
            log.warn("Failed to sync zones to Redis: {}", e.getMessage());
        }
    }
}
