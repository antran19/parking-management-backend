package com.smartparking.backend.service;

import com.smartparking.backend.entity.Floor;
import com.smartparking.backend.entity.Gate;
import com.smartparking.backend.entity.VehicleType;
import com.smartparking.backend.entity.Zone;
import com.smartparking.backend.exception.BusinessException;
import com.smartparking.backend.exception.ResourceNotFoundException;
import com.smartparking.backend.repository.FloorRepository;
import com.smartparking.backend.repository.GateRepository;
import com.smartparking.backend.repository.VehicleTypeRepository;
import com.smartparking.backend.repository.ZoneRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service quy hoạch zone theo diện tích tầng — thay cho việc admin gõ tay số
 * capacity của zone. Diện tích mỗi zone được chia trực tiếp từ tổng diện tích
 * tầng (floorArea), và số slot mỗi zone = floor(diện tích zone /
 * VehicleType.slotAreaSqm).
 *
 * Quy tắc "tầng tổng hợp": 1 tầng chỉ được kết hợp nhiều loại xe nếu TẤT CẢ
 * các loại đó có VehicleType.mixable = true (mặc định Xe máy/Xe đạp — admin có
 * thể bật cho loại xe mới). Loại xe không mixable luôn là tầng chuyên dụng
 * (chỉ 1 loại xe duy nhất trên tầng).
 */
@Service
public class ZonePlanningService {

    private final ZoneRepository zoneRepository;
    private final FloorRepository floorRepository;
    private final VehicleTypeRepository vehicleTypeRepository;
    private final GateRepository gateRepository;

    public ZonePlanningService(ZoneRepository zoneRepository, FloorRepository floorRepository,
            VehicleTypeRepository vehicleTypeRepository, GateRepository gateRepository) {
        this.zoneRepository = zoneRepository;
        this.floorRepository = floorRepository;
        this.vehicleTypeRepository = vehicleTypeRepository;
        this.gateRepository = gateRepository;
    }

    private record PlannedZone(VehicleType vehicleType, String zoneCode, double area, int capacity) {
    }

    private record AllocationInput(VehicleType vehicleType, double area, int zoneCount) {
    }

    public Map<String, Object> getFloorCapacitySummary(UUID floorId) {
        Floor floor = requireFloor(floorId);
        List<Zone> existingZones = zoneRepository.findAllByFloorId(floorId);

        double usableArea = usableArea(floor); // diện tích sử dụng = floorArea
        double allocatedArea = allocatedArea(existingZones); // diện tích đã phân bổ = tổng diện tích các zone đã có
        double remainingArea = Math.max(0, usableArea - allocatedArea); // diện tích còn lại = diện tích sử dụng - diện
                                                                        // tích đã phân bổ

        Set<String> existingTypeNames = existingZones.stream()
                .map(z -> z.getVehicleType().getName())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        boolean isLocked = existingZones.stream()
                .anyMatch(z -> !Boolean.TRUE.equals(z.getVehicleType().getMixable()));
        boolean hasLegacyZonesWithoutArea = existingZones.stream()
                .anyMatch(z -> z.getZoneArea() == null && z.getCapacity() != null && z.getCapacity() > 0);

        double smallestSlotArea = vehicleTypeRepository.findAll().stream()
                .map(VehicleType::getSlotAreaSqm)
                .filter(Objects::nonNull)
                .filter(a -> a > 0)
                .min(Double::compareTo)
                .orElse(1.0);
        int suggestedMaxAdditionalZones = Math.max(0, (int) Math.floor(remainingArea / smallestSlotArea));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("floorId", floor.getId());
        result.put("floorName", floor.getFloorName());
        result.put("floorArea", floor.getFloorArea());
        result.put("usableArea", round2(usableArea));
        result.put("allocatedArea", round2(allocatedArea));
        result.put("remainingArea", round2(remainingArea));
        result.put("existingZoneCount", existingZones.size());
        result.put("existingVehicleTypeNames", existingTypeNames);
        result.put("isMixedEligible", existingTypeNames.isEmpty() || !isLocked);
        result.put("isLocked", isLocked);
        result.put("suggestedMaxAdditionalZones", suggestedMaxAdditionalZones);
        result.put("hasLegacyZonesWithoutArea", hasLegacyZonesWithoutArea);
        return result;
    }

    /**
     * Tính thử, không lưu DB. Trả về valid=false kèm lý do thay vì ném lỗi, để UI
     * preview mượt khi admin đang chỉnh input.
     */
    public Map<String, Object> previewGeneratedZones(UUID floorId, List<Map<String, Object>> allocations) {
        Floor floor = requireFloor(floorId);
        List<Zone> existingZones = zoneRepository.findAllByFloorId(floorId);
        try {
            List<PlannedZone> planned = plan(floor, existingZones, allocations);
            return buildPreviewResponse(floor, existingZones, planned, null);
        } catch (BusinessException | ResourceNotFoundException ex) {
            return buildPreviewResponse(floor, existingZones, List.of(), ex.getMessage());
        }
    }

    @Transactional
    public List<Zone> generateZones(UUID floorId, List<Map<String, Object>> allocations) {
        Floor floor = requireFloor(floorId);
        List<Zone> existingZones = zoneRepository.findAllByFloorId(floorId);
        List<PlannedZone> planned = plan(floor, existingZones, allocations);

        List<Zone> toCreate = planned.stream()
                .map(p -> Zone.builder()
                        .floor(floor)
                        .vehicleType(p.vehicleType())
                        .zoneCode(p.zoneCode())
                        .zoneName("Khu " + p.zoneCode() + " - " + p.vehicleType().getName())
                        .capacity(p.capacity())
                        .zoneArea(p.area())
                        .currentCount(0)
                        .reservedCount(0)
                        .status(Zone.ZoneStatus.ACTIVE)
                        .build())
                .toList();
        List<Zone> saved = zoneRepository.saveAll(toCreate);
        gateRepository.saveAll(saved.stream().flatMap(this::buildZoneGates).toList());

        List<Zone> allZones = new ArrayList<>(existingZones);
        allZones.addAll(saved);
        int totalSlots = allZones.stream().mapToInt(z -> z.getCapacity() != null ? z.getCapacity() : 0).sum();
        floor.setTotalSlots(totalSlots);
        floor.setMaxZones(allZones.size());
        floorRepository.save(floor);

        return saved;
    }

    // ===== Core planning/validation — dùng chung cho preview & generate =====
    private List<PlannedZone> plan(Floor floor, List<Zone> existingZones, List<Map<String, Object>> allocationsInput) {
        if (allocationsInput == null || allocationsInput.isEmpty()) {
            throw new BusinessException("Cần ít nhất 1 loại xe để quy hoạch zone");
        }
        if (floor.getFloorArea() == null || floor.getFloorArea() <= 0) {
            throw new BusinessException(
                    "Tầng chưa có diện tích, vui lòng cập nhật diện tích tầng trước khi quy hoạch zone");
        }

        double usableArea = usableArea(floor);
        double remainingArea = Math.max(0, usableArea - allocatedArea(existingZones));

        Map<UUID, AllocationInput> byType = new LinkedHashMap<>();
        double totalRequestedArea = 0.0;

        for (Map<String, Object> raw : allocationsInput) {
            UUID vehicleTypeId = uuid(raw, "vehicleTypeId");
            if (vehicleTypeId == null) {
                throw new BusinessException("Thiếu vehicleTypeId trong yêu cầu quy hoạch zone");
            }
            if (byType.containsKey(vehicleTypeId)) {
                throw new BusinessException("Loại xe bị lặp lại trong cùng 1 yêu cầu — hãy gộp vào 1 mục duy nhất");
            }
            VehicleType vt = vehicleTypeRepository.findById(vehicleTypeId)
                    .orElseThrow(() -> new ResourceNotFoundException("Loại xe không tồn tại"));

            Double explicitArea = doubleVal(raw, "area");
            Double areaPercent = doubleVal(raw, "areaPercent");
            double area;
            if (explicitArea != null && explicitArea > 0) {
                area = explicitArea;
            } else if (areaPercent != null && areaPercent > 0) {
                area = remainingArea * (areaPercent / 100.0);
            } else {
                throw new BusinessException(
                        "Cần chỉ định diện tích (area) hoặc phần trăm diện tích (areaPercent) cho loại xe "
                                + vt.getName());
            }

            int zoneCount = intVal(raw, "zoneCount", 1);
            if (zoneCount < 1) {
                throw new BusinessException("Số zone phải lớn hơn 0 cho loại xe " + vt.getName());
            }

            totalRequestedArea += area;
            byType.put(vehicleTypeId, new AllocationInput(vt, area, zoneCount));
        }

        // Luật gộp tầng: toàn bộ loại xe (đã có trên tầng + mới thêm) phải là 1 loại
        // duy nhất,
        // hoặc TẤT CẢ đều có mixable = true (cho phép gộp tầng tổng hợp).
        Map<UUID, VehicleType> existingTypes = new LinkedHashMap<>();
        existingZones.forEach(z -> existingTypes.put(z.getVehicleType().getId(), z.getVehicleType()));
        Map<UUID, VehicleType> newTypes = new LinkedHashMap<>();
        byType.values().forEach(a -> newTypes.put(a.vehicleType().getId(), a.vehicleType()));

        Map<UUID, VehicleType> allTypes = new LinkedHashMap<>(existingTypes);
        allTypes.putAll(newTypes);

        if (allTypes.size() > 1
                && !allTypes.values().stream().allMatch(vt -> Boolean.TRUE.equals(vt.getMixable()))) {
            String newNames = newTypes.values().stream().map(VehicleType::getName)
                    .collect(Collectors.joining(", "));
            if (existingTypes.isEmpty()) {
                throw new BusinessException(
                        "Chỉ được phép kết hợp các loại xe cho phép gộp tầng (vd Xe máy + Xe đạp) trong 1 tầng — không thể tạo đồng thời "
                                + newNames);
            }
            String existingNames = existingTypes.values().stream().map(VehicleType::getName)
                    .collect(Collectors.joining(", "));
            throw new BusinessException("Tầng " + floor.getFloorName() + " hiện đang dùng cho "
                    + existingNames
                    + " — chỉ được phép kết hợp thêm loại xe cho phép gộp tầng, không thể thêm "
                    + newNames);
        }

        if (totalRequestedArea > remainingArea + 0.01) {
            throw new BusinessException(String.format(
                    "Diện tích yêu cầu (%.1fm²) vượt quá diện tích còn trống của tầng %s (%.1fm²) — thiếu %.1fm²",
                    totalRequestedArea, floor.getFloorName(), remainingArea, totalRequestedArea - remainingArea));
        }

        char nextCode = nextZoneCode(existingZones);
        List<PlannedZone> planned = new ArrayList<>();
        for (AllocationInput alloc : byType.values()) {
            VehicleType vt = alloc.vehicleType();

            double areaPerZone = alloc.area() / alloc.zoneCount();
            double slotArea = vt.getSlotAreaSqm() != null && vt.getSlotAreaSqm() > 0 ? vt.getSlotAreaSqm() : 1.0;
            int capacityPerZone = (int) Math.floor(areaPerZone / slotArea);

            if (capacityPerZone < 1) {
                throw new BusinessException("Diện tích mỗi zone (" + String.format("%.1f", areaPerZone)
                        + "m²) quá nhỏ để chứa dù chỉ 1 slot " + vt.getName() + " (" + slotArea + "m²/slot)");
            }

            for (int i = 0; i < alloc.zoneCount(); i++) {
                if (nextCode > 'Z') {
                    throw new BusinessException("Tầng " + floor.getFloorName() + " đã đạt giới hạn 26 zone (A-Z)");
                }
                planned.add(new PlannedZone(vt, String.valueOf(nextCode), areaPerZone, capacityPerZone));
                nextCode++;
            }
        }
        return planned;
    }

    private Map<String, Object> buildPreviewResponse(Floor floor, List<Zone> existingZones,
            List<PlannedZone> planned, String errorMessage) {
        double usableArea = usableArea(floor);
        double allocatedArea = allocatedArea(existingZones);
        double plannedArea = planned.stream().mapToDouble(PlannedZone::area).sum();
        double remainingAfter = Math.max(0, usableArea - allocatedArea - plannedArea);

        List<Map<String, Object>> zonesOut = planned.stream().map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("zoneCode", p.zoneCode());
            m.put("vehicleTypeId", p.vehicleType().getId());
            m.put("vehicleTypeName", p.vehicleType().getName());
            m.put("area", round2(p.area()));
            m.put("capacity", p.capacity());
            return m;
        }).toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("valid", errorMessage == null);
        result.put("message", errorMessage);
        result.put("floorId", floor.getId());
        result.put("plannedZones", zonesOut);
        result.put("totalRequestedArea", round2(plannedArea));
        result.put("remainingAreaAfter", round2(remainingAfter));
        return result;
    }

    /**
     * Mỗi zone quy hoạch mới phải có sẵn 1 cặp cổng vào/ra, nếu không sẽ không
     * check-in/check-out được
     * (ZonePlanningService trước đây chỉ tạo Zone, để lại cổng cho admin tự thêm
     * tay ở tab Cổng kiểm soát —
     * dễ quên, khiến zone tạo ra không dùng được thật). Đặt tên theo đúng quy ước
     * cổng đã seed sẵn.
     */
    private java.util.stream.Stream<Gate> buildZoneGates(Zone zone) {
        String floorName = zone.getFloor().getFloorName();
        // gate_code giới hạn varchar(20) — cắt bớt tên tầng để
        // "GATE-{floor}-{zone}-OUT" luôn vừa,
        // bất kể admin đặt tên tầng dài bao nhiêu (khác với B1/T1 ngắn gọn lúc seed dữ
        // liệu mẫu).
        String floorPart = floorName.length() > 8 ? floorName.substring(0, 8) : floorName;
        String baseCode = ("GATE-" + floorPart + "-" + zone.getZoneCode()).toUpperCase();
        String label = floorName + "-" + zone.getZoneCode() + " (" + zone.getVehicleType().getName() + ")";
        Gate entry = Gate.builder()
                .building(zone.getFloor().getBuilding())
                .zone(zone)
                .gateCode(baseCode)
                .gateName("Cổng vào Zone " + label)
                .gateType(Gate.GateType.ZONE_ENTRY)
                .isActive(true)
                .barrierState("CLOSED")
                .build();
        Gate exit = Gate.builder()
                .building(zone.getFloor().getBuilding())
                .zone(zone)
                .gateCode(baseCode + "-OUT")
                .gateName("Cổng ra Zone " + label)
                .gateType(Gate.GateType.ZONE_EXIT)
                .isActive(true)
                .barrierState("CLOSED")
                .build();
        return java.util.stream.Stream.of(entry, exit);
    }

    /**
     * Sửa sức chứa 1 zone đã có — tính lại zoneArea tương ứng (capacity ×
     * slotAreaSqm) và
     * chặn nếu vượt quá diện tích tầng còn trống, để tab "Phân khu đỗ xe" không thể
     * tự ý
     * đặt capacity lệch khỏi diện tích thật mà tab "Cấu hình hạ tầng" đang theo
     * dõi.
     */
    @Transactional
    public Zone resizeZoneCapacity(UUID zoneId, int newCapacity) {
        Zone zone = zoneRepository.findById(zoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Zone không tồn tại"));
        if (newCapacity < 1) {
            throw new BusinessException("Sức chứa zone phải lớn hơn 0");
        }
        int occupied = (zone.getCurrentCount() != null ? zone.getCurrentCount() : 0)
                + (zone.getReservedCount() != null ? zone.getReservedCount() : 0);
        if (newCapacity < occupied) {
            throw new BusinessException("Zone đang có " + occupied
                    + " xe/chỗ đã đặt trước — không thể đặt sức chứa nhỏ hơn " + occupied);
        }

        Floor floor = zone.getFloor();
        Double slotAreaRaw = zone.getVehicleType().getSlotAreaSqm();
        double slotArea = slotAreaRaw != null && slotAreaRaw > 0 ? slotAreaRaw : 1.0;
        double newZoneArea = newCapacity * slotArea;

        List<Zone> otherZones = zoneRepository.findAllByFloorId(floor.getId()).stream()
                .filter(z -> !z.getId().equals(zoneId))
                .toList();
        double remainingForThisZone = usableArea(floor) - allocatedArea(otherZones);

        if (newZoneArea > remainingForThisZone + 0.01) {
            throw new BusinessException(String.format(
                    "Sức chứa %d slot cần %.1fm², vượt quá diện tích tầng %s còn lại cho zone này (%.1fm²)",
                    newCapacity, newZoneArea, floor.getFloorName(), Math.max(0, remainingForThisZone)));
        }

        zone.setCapacity(newCapacity);
        zone.setZoneArea(round2(newZoneArea));
        Zone saved = zoneRepository.save(zone);

        List<Zone> allZones = new ArrayList<>(otherZones);
        allZones.add(saved);
        int totalSlots = allZones.stream().mapToInt(z -> z.getCapacity() != null ? z.getCapacity() : 0).sum();
        floor.setTotalSlots(totalSlots);
        floorRepository.save(floor);

        return saved;
    }

    private Floor requireFloor(UUID floorId) {
        return floorRepository.findById(floorId)
                .orElseThrow(() -> new ResourceNotFoundException("Tầng không tồn tại"));
    }

    private double usableArea(Floor floor) {
        return floor.getFloorArea() != null ? floor.getFloorArea() : 0.0;
    }

    private double allocatedArea(List<Zone> zones) {
        return zones.stream().mapToDouble(z -> z.getZoneArea() != null ? z.getZoneArea() : 0.0).sum();
    }

    private char nextZoneCode(List<Zone> existingZones) {
        char max = 'A' - 1;
        for (Zone z : existingZones) {
            String code = z.getZoneCode();
            if (code != null && code.length() == 1) {
                char c = Character.toUpperCase(code.charAt(0));
                if (c >= 'A' && c <= 'Z' && c > max)
                    max = c;
            }
        }
        return (char) (max + 1);
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private UUID uuid(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null)
            return null;
        try {
            return UUID.fromString(String.valueOf(v));
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Giá trị " + key + " không hợp lệ");
        }
    }

    private Double doubleVal(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null || String.valueOf(v).isBlank())
            return null;
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (NumberFormatException e) {
            throw new BusinessException("Giá trị " + key + " không hợp lệ");
        }
    }

    private int intVal(Map<String, Object> body, String key, int defaultVal) {
        Object v = body.get(key);
        if (v == null || String.valueOf(v).isBlank())
            return defaultVal;
        try {
            return (int) Double.parseDouble(String.valueOf(v));
        } catch (NumberFormatException e) {
            throw new BusinessException("Giá trị " + key + " không hợp lệ");
        }
    }
}
