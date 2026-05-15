package com.smartparking.backend.service;

import com.smartparking.backend.dto.response.SlotMapResponse;
import com.smartparking.backend.entity.Building;
import com.smartparking.backend.entity.Slot;
import com.smartparking.backend.entity.Slot.SlotStatus;
import com.smartparking.backend.exception.ResourceNotFoundException;
import com.smartparking.backend.repository.BuildingRepository;
import com.smartparking.backend.repository.SlotRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * SlotMapService — Tạo dữ liệu sơ đồ bãi xe cho FE render.
 */
@Service
public class SlotMapService {

    private final SlotRepository slotRepository;
    private final BuildingRepository buildingRepository;

    public SlotMapService(SlotRepository slotRepository, BuildingRepository buildingRepository) {
        this.slotRepository = slotRepository;
        this.buildingRepository = buildingRepository;
    }

    /**
     * Lấy toàn bộ trạng thái slot của tòa nhà, nhóm theo Floor → Zone.
     */
    public SlotMapResponse getSlotMap(UUID buildingId) {
        Building building = buildingRepository.findById(buildingId)
                .orElseThrow(() -> new ResourceNotFoundException("Tòa nhà không tồn tại: " + buildingId));

        List<Slot> allSlots = slotRepository.findAllByBuildingId(buildingId);

        // Nhóm theo Floor
        Map<UUID, List<Slot>> slotsByFloor = allSlots.stream()
                .collect(Collectors.groupingBy(s -> s.getFloor().getId()));

        List<SlotMapResponse.FloorMap> floors = new ArrayList<>();
        int totalAvailable = 0, totalOccupied = 0, totalReserved = 0;

        for (Map.Entry<UUID, List<Slot>> entry : slotsByFloor.entrySet()) {
            List<Slot> floorSlots = entry.getValue();
            if (floorSlots.isEmpty()) continue;

            // Lấy thông tin tầng từ slot đầu tiên
            var floor = floorSlots.get(0).getFloor();

            // Tạo slot info list (gom tất cả vào 1 zone mặc định)
            List<SlotMapResponse.SlotInfo> slotInfos = floorSlots.stream()
                    .map(s -> SlotMapResponse.SlotInfo.builder()
                            .slotId(s.getId())
                            .slotCode(s.getSlotCode())
                            .status(s.getStatus())
                            .build())
                    .toList();

            // Đếm trạng thái
            for (Slot s : floorSlots) {
                switch (s.getStatus()) {
                    case AVAILABLE -> totalAvailable++;
                    case OCCUPIED -> totalOccupied++;
                    case RESERVED -> totalReserved++;
                    default -> {}
                }
            }

            String vehicleTypeName = floor.getVehicleType() != null
                    ? floor.getVehicleType().getName() : "Tất cả";

            SlotMapResponse.ZoneMap defaultZone = SlotMapResponse.ZoneMap.builder()
                    .zoneId(floor.getId()) // dùng floorId tạm làm zoneId
                    .zoneCode("A")
                    .zoneName(floor.getFloorName() + " - " + vehicleTypeName)
                    .vehicleType(vehicleTypeName)
                    .slots(slotInfos)
                    .build();

            floors.add(SlotMapResponse.FloorMap.builder()
                    .floorId(floor.getId())
                    .floorName(floor.getFloorName())
                    .floorNumber(floor.getFloorNumber())
                    .zones(List.of(defaultZone))
                    .build());
        }

        // Sort tầng theo floorNumber
        floors.sort(Comparator.comparingInt(SlotMapResponse.FloorMap::getFloorNumber));

        return SlotMapResponse.builder()
                .buildingId(buildingId)
                .buildingName(building.getName())
                .totalSlots(allSlots.size())
                .availableSlots(totalAvailable)
                .occupiedSlots(totalOccupied)
                .reservedSlots(totalReserved)
                .floors(floors)
                .build();
    }
}
