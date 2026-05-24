package com.smartparking.backend.service;

import com.smartparking.backend.dto.request.SecurityExceptionRequest;
import com.smartparking.backend.entity.ExceptionLog;
import com.smartparking.backend.entity.ParkingSession;
import com.smartparking.backend.entity.User;
import com.smartparking.backend.exception.ResourceNotFoundException;
import com.smartparking.backend.repository.ExceptionLogRepository;
import com.smartparking.backend.repository.ParkingSessionRepository;
import com.smartparking.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class SecurityExceptionService {

    private final ExceptionLogRepository exceptionLogRepository;
    private final ParkingSessionRepository parkingSessionRepository;
    private final UserRepository userRepository;

    public SecurityExceptionService(ExceptionLogRepository exceptionLogRepository,
                                    ParkingSessionRepository parkingSessionRepository,
                                    UserRepository userRepository) {
        this.exceptionLogRepository = exceptionLogRepository;
        this.parkingSessionRepository = parkingSessionRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public ExceptionLog logException(SecurityExceptionRequest request) {
        ParkingSession session = null;
        if (request.getSessionId() != null) {
            session = parkingSessionRepository.findById(request.getSessionId())
                    .orElseThrow(() -> new ResourceNotFoundException("Session không tồn tại"));
        }

        User handledBy = null;
        if (request.getHandledByUserId() != null) {
            handledBy = userRepository.findById(request.getHandledByUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("Người xử lý không tồn tại"));
        }

        ExceptionLog exceptionLog = ExceptionLog.builder()
                .session(session)
                .exceptionType(request.getExceptionType())
                .description(request.getDescription())
                .handledBy(handledBy)
                .resolvedAt(LocalDateTime.now())
                .build();

        return exceptionLogRepository.save(exceptionLog);
    }
}
