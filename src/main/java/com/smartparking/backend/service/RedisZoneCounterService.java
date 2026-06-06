package com.smartparking.backend.service;

import com.smartparking.backend.entity.Zone;
import com.smartparking.backend.repository.ZoneRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * RedisZoneCounterService — wrapper cho các thao tác counter/lock trên Redis dùng cho Zone.
 * Key patterns:
 *  - zone:count:{zoneId} -> string integer (số xe hiện tại trong zone)
 *  - zone:lock:{zoneId}  -> string sessionId (owner của lock)
 *
 * Quy ước:
 *  - increment(): atomic INCR trên Redis, trả về giá trị sau khi tăng.
 *  - decrement(): atomic DECR trên Redis, trả về giá trị sau khi giảm (không cho âm).
 *  - getCount(): ưu tiên đọc từ Redis; nếu không có hoặc không parse được thì fallback DB.
 *  - tryLock/unlock: đơn giản dựa trên SETNX với TTL.
 */
@Service
public class RedisZoneCounterService {

    private final StringRedisTemplate redisTemplate;
    private final ZoneRepository zoneRepository;

    public RedisZoneCounterService(StringRedisTemplate redisTemplate, ZoneRepository zoneRepository) {
        this.redisTemplate = redisTemplate;
        this.zoneRepository = zoneRepository;
    }

    private String key(UUID zoneId) {
        return "zone:count:" + zoneId.toString();
    }

    private String lockKey(UUID zoneId) { return "zone:lock:" + zoneId.toString(); }

    /**
     * Tăng counter atomically trên Redis.
     * Trả về giá trị mới (số xe sau khi vào).
     */
    public long increment(UUID zoneId) {
        Long v = redisTemplate.opsForValue().increment(key(zoneId));
        return v == null ? 0 : v;
    }

    /**
     * Giảm counter atomically trên Redis. Nếu kết quả âm, set về 0.
     * Trả về giá trị sau khi giảm.
     */
    public long decrement(UUID zoneId) {
        Long v = redisTemplate.opsForValue().decrement(key(zoneId));
        if (v == null) return 0;
        if (v < 0) {
            // bảo vệ khỏi âm do race hoặc dữ liệu không nhất quán
            redisTemplate.opsForValue().set(key(zoneId), "0");
            return 0;
        }
        return v;
    }

    /**
     * Lấy counter hiện tại. Nếu Redis có thì ưu tiên, nếu không parse được thì fallback DB.
     * Việc fallback giúp ứng dụng chịu lỗi khi Redis trống hoặc key bị xóa.
     */
    public int getCount(UUID zoneId) {
        String v = redisTemplate.opsForValue().get(key(zoneId));
        if (v != null) {
            try {
                return Integer.parseInt(v);
            } catch (NumberFormatException e) {
                // nếu value không parse được, sẽ đọc từ DB
            }
        }
        Optional<Zone> z = zoneRepository.findById(zoneId);
        return z.map(Zone::getCurrentCount).orElse(0);
    }

    /**
     * Thử lấy distributed lock cho zone trong `seconds` giây. Giá trị của khoá là sessionId
     * để đảm bảo chỉ owner mới được unlock.
     * Trả về true nếu SETNX thành công.
     */
    public boolean tryLock(UUID zoneId, String sessionId, long seconds) {
        Boolean ok = redisTemplate.opsForValue().setIfAbsent(lockKey(zoneId), sessionId, Duration.ofSeconds(seconds));
        return Boolean.TRUE.equals(ok);
    }

    /**
     * Unlock chỉ khi owner hiện tại khớp với sessionId truyền vào.
     */
    public void unlock(UUID zoneId, String sessionId) {
        String k = lockKey(zoneId);
        String curr = redisTemplate.opsForValue().get(k);
        if (sessionId.equals(curr)) {
            redisTemplate.delete(k);
        }
    }

    // Optional: set initial count (used by sync)
    public void setCount(UUID zoneId, int count) {
        redisTemplate.opsForValue().set(key(zoneId), String.valueOf(count));
    }
}
