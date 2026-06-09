package com.smartparking.backend.util;

public final class LicensePlateUtil {

    private LicensePlateUtil() {
    }

    public static String normalize(String licensePlate) {
        if (licensePlate == null) {
            return "";
        }
        return licensePlate.replaceAll("[^a-zA-Z0-9]", "").toUpperCase();
    }
}
