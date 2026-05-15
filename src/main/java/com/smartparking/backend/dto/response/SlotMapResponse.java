package com.smartparking.backend.dto.response;

import com.smartparking.backend.entity.Slot;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * Response cho sơ đồ bãi xe — dùng để FE render Interactive Slot Map.
 * GET /api/v1/slots/map/{buildingId}
 */
@Data
@Builder
public class SlotMapResponse {

    private UUID buildingId;
    private String buildingName;
    private int totalSlots;
    private int availableSlots;
    private int occupiedSlots;
    private int reservedSlots;

    private List<FloorMap> floors;

    @Data
    @Builder
    public static class FloorMap {
        private UUID floorId;
        private String floorName;   // "B1", "T1"
        private int floorNumber;    // -1, 1
        private List<ZoneMap> zones;
    }

    @Data
    @Builder
    public static class ZoneMap {
        private UUID zoneId;
        private String zoneCode;     // "A", "B"
        private String zoneName;     // "Khu A - Xe máy"
        private String vehicleType;  // "Xe máy"
        private List<SlotInfo> slots;
    }

    @Data
    @Builder
    public static class SlotInfo {
        private UUID slotId;
        private String slotCode;     // "B1-A01"
        private Slot.SlotStatus status;  // AVAILABLE / OCCUPIED / RESERVED / MAINTENANCE / LOCKED
        // licensePlate chỉ trả về cho STAFF/MANAGER (không trả cho Driver)
        private String licensePlate;
    }
}
