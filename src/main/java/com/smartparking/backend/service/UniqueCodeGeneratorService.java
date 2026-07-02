package com.smartparking.backend.service;

import com.smartparking.backend.repository.ParkingPassRepository;
import com.smartparking.backend.repository.ParkingSessionRepository;
import com.smartparking.backend.repository.ReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class UniqueCodeGeneratorService {

    private final ParkingSessionRepository parkingSessionRepository;
    private final ReservationRepository reservationRepository;
    private final ParkingPassRepository parkingPassRepository;

    public UniqueCodeGeneratorService(ParkingSessionRepository parkingSessionRepository,
                                      ReservationRepository reservationRepository,
                                      ParkingPassRepository parkingPassRepository) {
        this.parkingSessionRepository = parkingSessionRepository;
        this.reservationRepository = reservationRepository;
        this.parkingPassRepository = parkingPassRepository;
    }

    /**
     * Sinh mã session duy nhất: PS + yymmdd + - + 5 chữ số tự tăng.
     * Ví dụ: PS260701-00001
     */
    @Transactional
    public synchronized String generateSessionCode() {
        String todayStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
        String prefix = "PS" + todayStr + "-";
        
        String maxCode = parkingSessionRepository.findMaxSessionCodeByPrefix(prefix);
        int nextNum = 1;
        if (maxCode != null && maxCode.length() > prefix.length()) {
            try {
                String suffix = maxCode.substring(prefix.length());
                nextNum = Integer.parseInt(suffix) + 1;
            } catch (NumberFormatException e) {
                // fallback if parsing fails
            }
        }
        return prefix + String.format("%05d", nextNum);
    }

    /**
     * Sinh mã đặt trước duy nhất: RS + yymmdd + - + 4 chữ số tự tăng.
     * Ví dụ: RS260701-0001
     */
    @Transactional
    public synchronized String generateReservationCode() {
        String todayStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
        String prefix = "RS" + todayStr + "-";

        String maxCode = reservationRepository.findMaxReservationCodeByPrefix(prefix);
        int nextNum = 1;
        if (maxCode != null && maxCode.length() > prefix.length()) {
            try {
                String suffix = maxCode.substring(prefix.length());
                nextNum = Integer.parseInt(suffix) + 1;
            } catch (NumberFormatException e) {
                // fallback if parsing fails
            }
        }
        return prefix + String.format("%04d", nextNum);
    }

    /**
     * Sinh mã vé tháng duy nhất: PP + yymmdd + - + 4 chữ số tự tăng.
     * Ví dụ: PP260701-0001
     */
    @Transactional
    public synchronized String generateParkingPassCode() {
        String todayStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
        String prefix = "PP" + todayStr + "-";

        String maxCode = parkingPassRepository.findMaxParkingPassCodeByPrefix(prefix);
        int nextNum = 1;
        if (maxCode != null && maxCode.length() > prefix.length()) {
            try {
                String suffix = maxCode.substring(prefix.length());
                nextNum = Integer.parseInt(suffix) + 1;
            } catch (NumberFormatException e) {
                // fallback if parsing fails
            }
        }
        return prefix + String.format("%04d", nextNum);
    }

    /**
     * Sinh mã định danh xe đạp duy nhất: BC + yymmdd + - + 4 chữ số tự tăng.
     * Ví dụ: BC260701-0001
     */
    @Transactional
    public synchronized String generateBicycleIdentifier() {
        String todayStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
        String prefix = "BC" + todayStr;

        String maxSessionPlate = parkingSessionRepository.findMaxLicensePlateByPrefix(prefix);
        String maxReservationPlate = reservationRepository.findMaxLicensePlateByPrefix(prefix);
        String maxPassPlate = parkingPassRepository.findMaxLicensePlateByPrefix(prefix);

        int maxNum = 0;
        maxNum = Math.max(maxNum, extractNumberSuffix(maxSessionPlate, prefix));
        maxNum = Math.max(maxNum, extractNumberSuffix(maxReservationPlate, prefix));
        maxNum = Math.max(maxNum, extractNumberSuffix(maxPassPlate, prefix));

        int nextNum = maxNum + 1;
        return prefix + String.format("%04d", nextNum);
    }

    private int extractNumberSuffix(String code, String prefix) {
        if (code != null && code.length() > prefix.length()) {
            try {
                String suffix = code.substring(prefix.length());
                return Integer.parseInt(suffix);
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        return 0;
    }
}
