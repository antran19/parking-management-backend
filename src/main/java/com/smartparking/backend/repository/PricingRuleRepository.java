package com.smartparking.backend.repository;

import com.smartparking.backend.entity.PricingRule;
import com.smartparking.backend.entity.PricingRule.PricingType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PricingRuleRepository extends JpaRepository<PricingRule, UUID> {

    Optional<PricingRule> findByBuildingIdAndVehicleTypeIdAndPricingType(
            UUID buildingId, UUID vehicleTypeId, PricingType pricingType);

    List<PricingRule> findByBuildingId(UUID buildingId);
}
