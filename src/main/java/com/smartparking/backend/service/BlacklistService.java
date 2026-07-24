package com.smartparking.backend.service;

import com.smartparking.backend.dto.request.BlacklistPlateRequest;
import com.smartparking.backend.dto.request.BlacklistRemoveRequest;
import com.smartparking.backend.dto.response.BlacklistAlertResponse;
import com.smartparking.backend.dto.response.BlacklistPlateResponse;
import com.smartparking.backend.entity.BlacklistPlate;
import com.smartparking.backend.entity.Gate;
import com.smartparking.backend.entity.User;
import com.smartparking.backend.exception.BusinessException;
import com.smartparking.backend.repository.BlacklistPlateRepository;
import com.smartparking.backend.repository.GateRepository;
import com.smartparking.backend.repository.UserRepository;
import com.smartparking.backend.util.LicensePlateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * BlacklistService — Quản lý danh sách đen biển số xe (Thiên phụ trách)
 *
 * Lưu ý: Khi thêm/xóa blacklist, broadcast WebSocket /topic/blacklist
 */
@Service
public class BlacklistService {

    private static final Logger log = LoggerFactory.getLogger(BlacklistService.class);

    private final BlacklistPlateRepository blacklistPlateRepository;
    private final UserRepository userRepository;
    private final GateRepository gateRepository;
    private final SimpMessagingTemplate messagingTemplate;

    // Tiêm phụ thuộc qua Constructor (Bắt buộc)
    public BlacklistService(BlacklistPlateRepository blacklistPlateRepository,
            UserRepository userRepository,
            GateRepository gateRepository,
            SimpMessagingTemplate messagingTemplate) {
        this.blacklistPlateRepository = blacklistPlateRepository;
        this.userRepository = userRepository;
        this.gateRepository = gateRepository;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Lấy toàn bộ danh sách blacklist, sắp xếp theo ngày thêm mới nhất
     */
    @Transactional(readOnly = true)
    public java.util.List<BlacklistPlateResponse> getAllBlacklist() {
        return blacklistPlateRepository.findAllByOrderByAddedAtDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Thêm mới một biển số vào danh sách đen và broadcast realtime
     */
    @Transactional
    public BlacklistPlateResponse addToBlacklist(BlacklistPlateRequest request) {
        String normalizedPlate = LicensePlateUtil.normalize(request.getLicensePlate());
        if (normalizedPlate.isBlank()) {
            throw new BusinessException("Biển số xe không hợp lệ");
        }

        // Kiểm tra xem biển số xe đã nằm trong blacklist chưa
        boolean exists = blacklistPlateRepository.existsByNormalizedPlateAndIsActiveTrue(normalizedPlate);
        if (exists) {
            throw new BusinessException("Biển số xe này đã nằm trong blacklist từ trước");
        }

        User addedBy = userRepository.findById(request.getAddedByUserId())
                .orElseThrow(() -> new BusinessException("Người thực hiện thêm không tồn tại"));

        BlacklistPlate blacklistPlate = BlacklistPlate.builder()
                .licensePlate(request.getLicensePlate())
                .normalizedPlate(normalizedPlate)
                .vehicleType(request.getVehicleType())
                .reason(request.getReason())
                .description(request.getDescription())
                .imageUrls(request.getImageUrls())
                .isActive(true)
                .addedBy(addedBy)
                .build();

        BlacklistPlateResponse response = toResponse(blacklistPlateRepository.save(blacklistPlate));

        // Phát sự kiện qua WebSocket báo có biển số mới được thêm
        broadcastBlacklistUpdate("ADDED", response);

        log.info("Đã thêm vào Blacklist: {} bởi {}", request.getLicensePlate(), addedBy.getEmail());
        return response;
    }

    /**
     * Cập nhật thông tin một bản ghi blacklist (biển số, lý do, mô tả)
     * PUT /api/v1/security/blacklist/{id}
     */
    @Transactional
    public BlacklistPlateResponse updateBlacklist(UUID id, BlacklistPlateRequest request) {
        BlacklistPlate plate = blacklistPlateRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Không tìm thấy bản ghi blacklist"));

        // Cập nhật biển số (nếu có thay đổi)
        if (request.getLicensePlate() != null && !request.getLicensePlate().isBlank()) {
            String newNormalized = LicensePlateUtil.normalize(request.getLicensePlate());
            // Kiểm tra xem biển số mới có trùng với bản ghi blacklist khác đang active
            // không
            boolean conflict = blacklistPlateRepository
                    .existsByNormalizedPlateAndIsActiveTrue(newNormalized)
                    && !newNormalized.equals(plate.getNormalizedPlate());
            if (conflict) {
                throw new BusinessException("Biển số xe này đã nằm trong blacklist từ trước");
            }
            plate.setLicensePlate(request.getLicensePlate());
            plate.setNormalizedPlate(newNormalized);
        }

        // Cập nhật lý do và mô tả
        if (request.getReason() != null) {
            plate.setReason(request.getReason());
        }
        if (request.getDescription() != null) {
            plate.setDescription(request.getDescription());
        }
        if (request.getVehicleType() != null) {
            plate.setVehicleType(request.getVehicleType());
        }
        if (request.getImageUrls() != null) {
            plate.setImageUrls(request.getImageUrls());
        }

        BlacklistPlateResponse response = toResponse(blacklistPlateRepository.save(plate));
        broadcastBlacklistUpdate("UPDATED", response);
        log.info("Đã cập nhật Blacklist id={}: {}", id, plate.getLicensePlate());
        return response;
    }

    /**
     * Gỡ bỏ một biển số khỏi danh sách đen (Blacklist)
     */
    @Transactional
    public BlacklistPlateResponse removeFromBlacklist(UUID id, BlacklistRemoveRequest request) {
        BlacklistPlate plate = blacklistPlateRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Không tìm thấy thông tin blacklist"));

        if (!plate.getIsActive()) {
            throw new BusinessException("Biển số này đã được gỡ cấm từ trước");
        }

        User removedBy = userRepository.findById(request.getRemovedByUserId())
                .orElseThrow(() -> new BusinessException("Người thực hiện gỡ cấm không tồn tại"));

        plate.setIsActive(false);
        plate.setRemovedBy(removedBy);
        plate.setRemovedAt(LocalDateTime.now());

        BlacklistPlateResponse response = toResponse(blacklistPlateRepository.save(plate));

        // Phát sự kiện qua WebSocket báo có biển số được gỡ
        broadcastBlacklistUpdate("REMOVED", response);

        log.info("Đã gỡ khỏi Blacklist: {} bởi {}", plate.getLicensePlate(), removedBy.getEmail());
        return response;
    }

    /**
     * [HÀM THÊM MỚI SO VỚI DỰ ÁN CŨ]
     * Kiểm tra nhanh xem một biển số xe có đang bị chặn (nằm trong blacklist và
     * active) hay không.
     * Hàm này được viết thêm để các Service khác (như soát vé xe Check-in) có thể
     * gọi kiểm tra
     * nhanh chóng và tiện lợi mà không cần phải truy vấn toàn bộ đối tượng
     * BlacklistPlate lên.
     * 
     * @param licensePlate Biển số xe cần kiểm tra (chưa chuẩn hóa)
     * @return true nếu bị chặn (active blacklist), false nếu không bị chặn
     */
    @Transactional(readOnly = true)
    public boolean isBlacklisted(String licensePlate) {
        String normalizedPlate = LicensePlateUtil.normalize(licensePlate);
        return blacklistPlateRepository.existsByNormalizedPlateAndIsActiveTrue(normalizedPlate);
    }

    /**
     * Đảm bảo biển số xe không nằm trong danh sách đen, nếu có thì văng lỗi
     * BusinessException.
     * Hàm này được các Service Check-in dùng để tự động chặn xe.
     * 
     * @param licensePlate Biển số xe cần kiểm tra
     */
    public void ensurePlateNotBlacklisted(String licensePlate) {
        if (isBlacklisted(licensePlate)) {
            throw new BusinessException(
                    "Xe mang biển số " + licensePlate + " đang nằm trong danh sách đen, từ chối phục vụ.");
        }
    }

    /**
     * Tìm kiếm thực thể blacklist đang active theo biển số
     */
    @Transactional(readOnly = true)
    public Optional<BlacklistPlate> findActiveByPlate(String licensePlate) {
        String normalizedPlate = LicensePlateUtil.normalize(licensePlate);
        return blacklistPlateRepository.findByNormalizedPlateAndIsActiveTrue(normalizedPlate);
    }

    /**
     * Phát cảnh báo khi xe trong blacklist cố gắng đi qua cổng
     */
    public void alertBlacklistAttempt(String licensePlate, BlacklistPlate blacklistPlate, Gate gate) {
        BlacklistAlertResponse alert = BlacklistAlertResponse.builder()
                .type("BLACKLIST_PLATE_DETECTED")
                .licensePlate(licensePlate)
                .normalizedPlate(LicensePlateUtil.normalize(licensePlate))
                .vehicleType(blacklistPlate.getVehicleType())
                .reason(blacklistPlate.getReason())
                .vehicleType(blacklistPlate.getVehicleType())
                .description(blacklistPlate.getDescription())
                .gateId(gate != null ? gate.getId() : null)
                .gateCode(gate != null ? gate.getGateCode() : null)
                .gateName(gate != null ? gate.getGateName() : null)
                .buildingId(gate != null && gate.getBuilding() != null ? gate.getBuilding().getId() : null)
                .buildingName(gate != null && gate.getBuilding() != null ? gate.getBuilding().getName() : null)
                .detectedAt(LocalDateTime.now())
                .message("Phát hiện xe trong danh sách đen cố gắng đi qua cổng: " + licensePlate)
                .build();

        broadcastBlacklistAlert(alert);
    }

    /**
     * Tìm kiếm thông tin và phát cảnh báo
     */
    @Transactional(readOnly = true)
    public void alertBlacklistAttempt(String licensePlate, UUID gateId) {
        Optional<BlacklistPlate> blacklistPlateOpt = findActiveByPlate(licensePlate);

        // Nếu tìm thấy biển số xe trong blacklist thì mới thực hiện phát cảnh báo
        if (blacklistPlateOpt.isPresent()) {
            BlacklistPlate blacklistPlate = blacklistPlateOpt.get();
            Gate gate = null;

            // Tìm kiếm thông tin cổng nếu có gateId
            if (gateId != null) {
                Optional<Gate> gateOpt = gateRepository.findById(gateId);
                if (gateOpt.isPresent()) {
                    gate = gateOpt.get();
                }
            }

            alertBlacklistAttempt(licensePlate, blacklistPlate, gate);
        }
    }

    /**
     * Chuyển đổi Entity sang Response DTO
     */
    private BlacklistPlateResponse toResponse(BlacklistPlate blacklistPlate) {
        User addedBy = blacklistPlate.getAddedBy();
        User removedBy = blacklistPlate.getRemovedBy();

        return BlacklistPlateResponse.builder()
                .id(blacklistPlate.getId())
                .licensePlate(blacklistPlate.getLicensePlate())
                .normalizedPlate(blacklistPlate.getNormalizedPlate())
                .vehicleType(blacklistPlate.getVehicleType())
                .reason(blacklistPlate.getReason())
                .description(blacklistPlate.getDescription())
                .imageUrls(blacklistPlate.getImageUrls())
                .isActive(blacklistPlate.getIsActive())
                .addedBy(addedBy != null ? addedBy.getFullName() : null)
                .addedAt(blacklistPlate.getAddedAt())
                .removedBy(removedBy != null ? removedBy.getFullName() : null)
                .removedAt(blacklistPlate.getRemovedAt())
                .build();
    }

    /**
     * [HÀM THÊM MỚI SO VỚI DỰ ÁN CŨ]
     * Phát tin nhắn WebSocket thông báo sự thay đổi của danh sách đen (khi Thêm
     * hoặc Gỡ blacklist).
     * Hàm này tạo một Map chứa hành động ("ADDED" hoặc "REMOVED") cùng với dữ liệu
     * của biển số xe,
     * sau đó gửi đến địa chỉ (topic) "/topic/blacklist". Frontend sẽ lắng nghe địa
     * chỉ này để
     * tự động vẽ lại danh sách đen trên màn hình Dashboard của bảo vệ mà không cần
     * load lại trang.
     * 
     * @param action Hành động thực hiện ("ADDED" hoặc "REMOVED")
     * @param data   Dữ liệu biển số xe vừa thao tác dưới dạng DTO
     */
    private void broadcastBlacklistUpdate(String action, BlacklistPlateResponse data) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("action", action);
            payload.put("data", data);

            messagingTemplate.convertAndSend("/topic/blacklist", payload);
        } catch (Exception e) {
            log.error("Không thể gửi tin nhắn WebSocket thay đổi blacklist: " + e.getMessage());
        }
    }

    /**
     * Phát tin nhắn cảnh báo an ninh qua WebSocket
     */
    private void broadcastBlacklistAlert(BlacklistAlertResponse alert) {
        try {
            // Gửi trực tiếp DTO để Spring Boot tự động chuyển thành JSON gửi đi
            messagingTemplate.convertAndSend("/topic/security/blacklist-alerts", alert);

            if (alert.getBuildingId() != null) {
                messagingTemplate.convertAndSend("/topic/security/blacklist-alerts/" + alert.getBuildingId(), alert);
            }
        } catch (Exception e) {
            log.error("Không thể gửi tin nhắn WebSocket cảnh báo blacklist: " + e.getMessage());
        }
    }

    
}