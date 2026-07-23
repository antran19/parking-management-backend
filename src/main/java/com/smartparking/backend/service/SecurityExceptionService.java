package com.smartparking.backend.service;

import com.smartparking.backend.dto.request.SecurityExceptionRequest;
import com.smartparking.backend.dto.response.ExceptionLogResponse;
import com.smartparking.backend.entity.ExceptionLog;
import com.smartparking.backend.entity.ParkingPass;
import com.smartparking.backend.entity.ParkingSession;
import com.smartparking.backend.entity.User;
import com.smartparking.backend.exception.BusinessException;
import com.smartparking.backend.repository.ExceptionLogRepository;
import com.smartparking.backend.repository.ParkingPassRepository;
import com.smartparking.backend.repository.ParkingSessionRepository;
import com.smartparking.backend.repository.UserRepository;
import com.smartparking.backend.util.LicensePlateUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SecurityExceptionService — Ghi nhận sự cố an ninh (Thiên phụ trách)
 *
 * TODO (Thiên): Implement:
 * - logException(request) → Tạo ExceptionLog lưu vào DB
 * - updateException(id, request) → Cập nhật sự cố
 * - resolveException(id, handledByUserId) → Đánh dấu đã giải quyết
 * - getAllExceptions() → Lấy danh sách sự cố (sắp xếp mới nhất trước)
 */
@Service
public class SecurityExceptionService {

    private final ExceptionLogRepository exceptionLogRepository;

    private final UserRepository userRepository;

    private final ParkingSessionRepository parkingSessionRepository;

    private final ParkingPassRepository parkingPassRepository;

    // Constructor injection (Quy tắc bắt buộc)
    public SecurityExceptionService(ExceptionLogRepository exceptionLogRepository,
            UserRepository userRepository,
            ParkingSessionRepository parkingSessionRepository,
            ParkingPassRepository parkingPassRepository) {
        this.exceptionLogRepository = exceptionLogRepository;
        this.userRepository = userRepository;
        this.parkingSessionRepository = parkingSessionRepository;
        this.parkingPassRepository = parkingPassRepository;
    }

    /**
     * Kiểm tra biển số xe để báo cáo sự cố: có phiên đỗ đang mở hoặc gói cước còn hạn hay không.
     * (Khôi phục từ branch feature/security-be-v7 — Thiên phụ trách)
     */
    public Map<String, Object> checkPlateForException(String licensePlate) {
        if (licensePlate == null || licensePlate.isBlank()) {
            throw new BusinessException("Biển số không được để trống");
        }
        String normalizedPlate = LicensePlateUtil.normalize(licensePlate);

        // 1. Kiểm tra phiên đỗ đang hoạt động
        boolean hasActiveSession = parkingSessionRepository
                .findByLicensePlateAndStatus(normalizedPlate, ParkingSession.SessionStatus.ACTIVE)
                .isPresent();

        // 2. Kiểm tra gói cước định kỳ còn hạn
        LocalDate today = LocalDate.now();
        boolean hasActivePass = parkingPassRepository
                .findAll()
                .stream()
                .filter(pass -> pass.getStatus() == ParkingPass.PassStatus.ACTIVE)
                .filter(pass -> LicensePlateUtil.normalize(pass.getLicensePlate()).equals(normalizedPlate))
                .anyMatch(pass -> !today.isBefore(pass.getStartDate()) && !today.isAfter(pass.getEndDate()));

        Map<String, Object> result = new HashMap<>();
        result.put("licensePlate", normalizedPlate);
        result.put("hasActiveSession", hasActiveSession);
        result.put("hasActivePass", hasActivePass);
        return result;
    }

    /**
     * Tạo ExceptionLog lưu vào DB
     */
    @Transactional
    public ExceptionLogResponse logException(SecurityExceptionRequest request) {

        User handledBy = null;
        if (request.getHandledByUserId() != null) {
            handledBy = userRepository.findById(request.getHandledByUserId())
                    .orElseThrow(() -> new BusinessException("Người xử lý không tồn tại"));
        }

        ExceptionLog exceptionLog = ExceptionLog.builder()
                .exceptionType(request.getExceptionType())
                .description(request.getDescription())
                .licensePlate(request.getLicensePlate())
                .vehicleType(request.getVehicleType())
                .handledBy(handledBy)
                .imageUrls(request.getImageUrls())
                .status(ExceptionLog.ExceptionStatus.PENDING)
                .build();

        ExceptionLog saved = exceptionLogRepository.save(exceptionLog);
        return mapToResponse(saved);
    }

    /**
     * Cập nhật sự cố an ninh đã ghi nhận
     */
    @Transactional
    public ExceptionLogResponse updateException(UUID id, SecurityExceptionRequest request) {
        ExceptionLog exceptionLog = exceptionLogRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Sự cố không tồn tại"));

        if (request.getExceptionType() != null) {
            exceptionLog.setExceptionType(request.getExceptionType());
        }

        if (request.getDescription() != null) {
            exceptionLog.setDescription(request.getDescription());
        }

        if (request.getLicensePlate() != null) {
            exceptionLog.setLicensePlate(request.getLicensePlate());
        }

        if (request.getVehicleType() != null) {
            exceptionLog.setVehicleType(request.getVehicleType());
        }

        if (request.getImageUrls() != null) {
            exceptionLog.setImageUrls(request.getImageUrls());
        }

        if (request.getStatus() != null) {
            ExceptionLog.ExceptionStatus status = request.getStatus();
            exceptionLog.setStatus(status);
            if (status == ExceptionLog.ExceptionStatus.RESOLVED && exceptionLog.getResolvedAt() == null) {
                exceptionLog.setResolvedAt(LocalDateTime.now());
            }
        }

        if (request.getResolution() != null) {
            exceptionLog.setResolution(request.getResolution());
        }

        if (request.getResolutionImageUrls() != null) {
            exceptionLog.setResolutionImageUrls(request.getResolutionImageUrls());
        }

        ExceptionLog saved = exceptionLogRepository.save(exceptionLog);
        return mapToResponse(saved);
    }

    /**
     * Đánh dấu sự cố đã được giải quyết
     */
    @Transactional
    public ExceptionLogResponse resolveException(UUID id, UUID handledByUserId, String resolution,
            List<String> resolutionImageUrls) {
        ExceptionLog exceptionLog = exceptionLogRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Sự cố không tồn tại"));

        User handledBy = userRepository.findById(handledByUserId)
                .orElseThrow(() -> new BusinessException("Người xử lý không tồn tại"));

        exceptionLog.setStatus(ExceptionLog.ExceptionStatus.RESOLVED);
        exceptionLog.setResolvedAt(LocalDateTime.now());
        exceptionLog.setHandledBy(handledBy);
        exceptionLog.setResolution(resolution);
        exceptionLog.setResolutionImageUrls(resolutionImageUrls);

        if (resolution != null && !resolution.isBlank()) {
            String existingDesc = exceptionLog.getDescription() == null ? "" : exceptionLog.getDescription() + "\n\n";
            exceptionLog.setDescription(existingDesc + "=== GHI CHÚ GIẢI QUYẾT ===\n" + resolution);
        }

        ExceptionLog saved = exceptionLogRepository.save(exceptionLog);
        return mapToResponse(saved);
    }

    /**
     * Lấy danh sách sự cố (sắp xếp mới nhất trước)
     */
    @Transactional(readOnly = true)
    public List<ExceptionLogResponse> getAllExceptions() {
        return exceptionLogRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    private ExceptionLogResponse mapToResponse(ExceptionLog entity) {
        return ExceptionLogResponse.builder()
                .id(entity.getId())
                .licensePlate(entity.getLicensePlate())
                .vehicleType(entity.getVehicleType())
                .exceptionType(entity.getExceptionType() != null ? entity.getExceptionType().name() : null)
                .description(entity.getDescription())
                .handledBy(entity.getHandledBy() != null ? entity.getHandledBy().getFullName() : null)
                .status(entity.getStatus() != null ? entity.getStatus().name() : null)
                .imageUrls(entity.getImageUrls())
                .resolution(entity.getResolution())
                .resolutionImageUrls(entity.getResolutionImageUrls())
                .resolvedAt(entity.getResolvedAt())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}