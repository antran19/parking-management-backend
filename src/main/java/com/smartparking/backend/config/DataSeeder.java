package com.smartparking.backend.config;

import com.smartparking.backend.entity.*;
import com.smartparking.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalTime;
// Tạo ra 1 class cấu hình DataSeer để tự động thêm data mẫu để test

@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {


    private final  BuildingRepository buildingRepository;
    private final  FloorRepository floorRepository;
    private final  SlotRepository slotRepository;
    private final  UserRepository userRepository;
    private final  VehicleTypeRepository vehicleTypeRepository;
    private final  PricingRuleRepository pricingRuleRepository;


    @Override
    public void run(String... args) throws Exception {
        if (buildingRepository.count() > 0) {
            return;
        }


        // =====================
        // 1. VEHICLE TYPES
        // =====================
        VehicleType motobike = vehicleTypeRepository.save(VehicleType.builder()
                .name("xe máy")
                .description("Xe máy, xe máy các loại")
                .build());

        VehicleType car = vehicleTypeRepository.save(
                VehicleType.builder()
                        .name("Ô tô")
                        .description("Ô tô các loại")
                        .build()
        );


        // =====================
        // 2. BUILDING
        // =====================
        Building building = buildingRepository.save(Building.builder()
                .name("SmartParking Tower")
                .address("123 Nguyễn Văn Linh, Q7, TP.HCM")
                .operatingHoursEnd(LocalTime.of(6, 0))
                .operatingHoursEnd(LocalTime.of(22, 0))
                .build());


        // =====================
        // 3. PRICING RULES
        // =====================
        PricingRule pricingRule = pricingRuleRepository.save(PricingRule.builder()
                .building(building)
                .vehicleType(motobike)
                .pricingType(PricingRule.PricingType.HOURLY)
                .pricePerUnit(new BigDecimal("50000"))
                .freeMinutes(15)
                .build());

        pricingRuleRepository.save(
                PricingRule.builder()
                        .building(building)
                        .vehicleType(car)
                        .pricingType(PricingRule.PricingType.HOURLY)
                        .pricePerUnit(new BigDecimal("15000"))
                        .freeMinutes(15)
                        .build()
        );

        // =====================
        // 4. FLOORS + SLOTS
        // =====================
        // B2: xe máy, floorNumber = -2
        createFloorWithSlots(building, -2, "B2", motobike, 20);

        // B1: xe máy, floorNumber = -1
        createFloorWithSlots(building, -1, "B1", motobike, 20);

        // T1: ô tô, floorNumber = 1
        createFloorWithSlots(building, 1, "T1", car, 20);


        // =====================
        // 5. USERS
        // =====================
        userRepository.save(User.builder()
                .email("admin@gmail.com")
                .passwordHash("1")
                .fullName("Admin System")
                .phone("12345")
                .role(User.Role.ADMIN)
                .isActive(true)
                .build()
        );

        userRepository.save(User.builder()
                .email("manager@gmail.com")
                .passwordHash("2")
                .fullName("Manager Tower")
                .phone("123456")
                .role(User.Role.MANAGER)
                .isActive(true)
                .build()
        );

        userRepository.save(User.builder()
                .email("staff@gmail.com")
                .passwordHash("3")
                .fullName("Staff Tower")
                .phone("123456")
                .role(User.Role.STAFF)
                .isActive(true)
                .build()
        );

        userRepository.save(User.builder()
                .email("driver@gmail.com")
                .passwordHash("4")
                .fullName("Driver Test")
                .phone("123456")
                .role(User.Role.DRIVER)
                .isActive(true)
                .build()
        );


    }


    private void createFloorWithSlots(Building building, int floorNumber,
                                      String floorName, VehicleType vehicleType,
                                      int totalSlots) {


        Floor floor = floorRepository.save(
                Floor.builder()
                        .building(building)
                        .floorNumber(floorNumber)
                        .floorName(floorName)
                        .vehicleType(vehicleType)
                        .totalSlots(totalSlots)
                        .build()
        );
        for (int i = 1; i <= totalSlots; i++) {
            slotRepository.save(
                    Slot.builder()
                            .floor(floor)
                            .slotCode(floorName + "-" + String.format("%02d", i))
                            .vehicleType(vehicleType)
                            .status(Slot.SlotStatus.AVAILABLE)
                            .build()
            );


        }
    }

}
