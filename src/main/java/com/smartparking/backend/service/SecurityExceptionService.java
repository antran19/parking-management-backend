package com.smartparking.backend.service;

import com.smartparking.backend.dto.request.SecurityExceptionRequest;
import com.smartparking.backend.dto.response.ExceptionLogResponse;
import com.smartparking.backend.entity.ExceptionLog;
import com.smartparking.backend.entity.ParkingSession;
import com.smartparking.backend.entity.User;
import com.smartparking.backend.exception.BusinessException;
import com.smartparking.backend.repository.ExceptionLogRepository;
import com.smartparking.backend.repository.ParkingSessionRepository;
import com.smartparking.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * SecurityExceptionService — Ghi nhận sự cố an ninh (Thiên phụ trách)
 *
 * TODO (Thiên): Implement:
 * - logException(request) → Tạo ExceptionLog lưu vào DB
 * - getAllExceptions() → Lấy danh sách sự cố (sắp xếp mới nhất trước)
 */
@Service
public class SecurityExceptionService {

    private final ExceptionLogRepository exceptionLogRepository;
    private final ParkingSessionRepository parkingSessionRepository;
    private final UserRepository userRepository;

    // Constructor injection (Quy tắc bắt buộc)
    public SecurityExceptionService(ExceptionLogRepository exceptionLogRepository,
            ParkingSessionRepository parkingSessionRepository,
            UserRepository userRepository) {
        this.exceptionLogRepository = exceptionLogRepository;
        this.parkingSessionRepository = parkingSessionRepository;
        this.userRepository = userRepository;
    }

    /**
     * Tạo ExceptionLog lưu vào DB
     */
    @Transactional
    public ExceptionLogResponse logException(SecurityExceptionRequest request) {
        ParkingSession session = null;
        if (request.getSessionId() != null) {
            session = parkingSessionRepository.findById(request.getSessionId())
                    .orElseThrow(() -> new BusinessException("Session không tồn tại"));
        }

        User handledBy = null;
        if (request.getHandledByUserId() != null) {
            handledBy = userRepository.findById(request.getHandledByUserId())
                    .orElseThrow(() -> new BusinessException("Người xử lý không tồn tại"));
        }

        ExceptionLog exceptionLog = ExceptionLog.builder()
                .session(session)
                .exceptionType(request.getExceptionType())
                .description(request.getDescription())
                .licensePlate(request.getLicensePlate())
                .imageUrls(request.getImageUrls() != null && !request.getImageUrls().isEmpty() ? String.join(",", request.getImageUrls()) : null)
                .handledBy(handledBy)
                .resolvedAt(LocalDateTime.now())
                .build();

        ExceptionLog saved = exceptionLogRepository.save(exceptionLog);
        return mapToResponse(saved);
    }

    /**
     * Lấy danh sách sự cố (sắp xếp mới nhất trước)
     */
    @Transactional(readOnly = true)
    public List<ExceptionLogResponse> getAllExceptions() {
        return exceptionLogRepository.findAllByOrderByResolvedAtDesc()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    private ExceptionLogResponse mapToResponse(ExceptionLog entity) {
        List<String> urlsList = entity.getImageUrls() != null && !entity.getImageUrls().isEmpty()
                ? Arrays.asList(entity.getImageUrls().split(",")) : new ArrayList<>();

        return ExceptionLogResponse.builder()
                .id(entity.getId())
                .sessionId(entity.getSession() != null ? entity.getSession().getId() : null)
                .licensePlate(entity.getLicensePlate())
                .exceptionType(entity.getExceptionType() != null ? entity.getExceptionType().name() : null)
                .description(entity.getDescription())
                .imageUrls(urlsList)
                .handledBy(entity.getHandledBy() != null ? entity.getHandledBy().getFullName() : null)
                .resolvedAt(entity.getResolvedAt())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}