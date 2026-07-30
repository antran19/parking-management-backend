package com.smartparking.backend.util;

public final class LicensePlateUtil {

    private LicensePlateUtil() {
    }

    public static String normalize(String licensePlate) {
        if (licensePlate == null) {
            return "";
        }
        return licensePlate.toUpperCase().replaceAll("Đ", "D").replaceAll("[^A-Z0-9]", "");
    }

    public static String getVehicleTypeKey(String vehicleType) {
        if (vehicleType == null) return "CAR";
        // NFD-normalize + xóa combining marks trước khi so khớp — chỉ thay "đ" không đủ vì các
        // nguyên âm có dấu khác (ạ, á, ...) trong "xe đạp" vẫn còn dấu, khiến contains("xe dap") sai.
        String text = java.text.Normalizer.normalize(vehicleType.toLowerCase(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace("đ", "d");

        if (text.contains("xe dap") || text.contains("bicycle")) return "BICYCLE";
        if (text.contains("xe may") || text.contains("moto") || text.contains("motorbike")) return "MOTORBIKE";
        if (text.contains("xe tai") || text.contains("truck")) return "TRUCK";

        return "CAR";
    }

    public static String getLicensePlateValidationError(String value, String vehicleType) {
        String plate = normalize(value);
        if (plate.isEmpty()) return "Vui lòng nhập biển số xe.";

        String type = "ANY";
        if (vehicleType != null) {
            String tStr = vehicleType.toLowerCase();
            if (tStr.contains("máy") || tStr.contains("moto") || tStr.contains("motorbike") || tStr.contains("ebike")) {
                type = "MOTORBIKE";
            } else if (tStr.contains("đạp") || tStr.contains("bicycle") || tStr.contains("bike")) {
                type = "BICYCLE";
            } else if (tStr.contains("oto") || tStr.contains("ô tô") || tStr.contains("car") || tStr.contains("tải") || tStr.contains("truck")) {
                type = "CAR";
            }
        }

        boolean isBicycle = plate.matches("^BC\\d{10}$");

        String carSpecial = "LD|DA|MK|HC|TK|NG|NN|QT|CV|AT";
        String provinceCode = "(?:1[124-9]|[23][0-9]|4[0137-9]|[5-7][0-9]|8[0-689]|9[02-57-9])";
        String tailPattern = "\\d{4,5}";

        String carSeries = "(?:[A-Z]{1}|" + carSpecial + ")";
        String motoSeries = "(?:(?!(" + carSpecial + "))[A-Z]{2}|[A-Z][0-9]|MD[0-9]?)";

        boolean isCar = plate.matches("^(" + provinceCode + carSeries + tailPattern + ")$|^([A-Z]{2}" + tailPattern + ")$");
        boolean isMotorbike = plate.matches("^(" + provinceCode + motoSeries + tailPattern + ")$");

        if ("CAR".equals(type)) {
            if (isCar) return null;
            if (isMotorbike) return "Đây là biển số xe máy. Vui lòng chọn lại loại xe hoặc kiểm tra lại biển số!";
            return "Biển số ô tô không đúng định dạng (VD: 30K-123.45, 29LD-001.23).";
        }

        if ("MOTORBIKE".equals(type)) {
            if (isMotorbike) return null;
            if (isCar) return "Đây là biển số ô tô. Vui lòng chọn lại loại xe hoặc kiểm tra lại biển số!";
            return "Biển số xe máy không đúng định dạng (VD: 29C1-123.45, 36AA-123.45, 29MD1-123.45).";
        }

        if ("BICYCLE".equals(type)) {
            if (isBicycle) return null;
            return "Mã xe đạp không đúng định dạng. Định dạng đúng: BCyymmdd-nnnn, ví dụ BC260701-0001.";
        }

        if (isCar || isMotorbike || isBicycle) return null;
        return "Biển số không hợp lệ hoặc không đúng định dạng xe Việt Nam.";
    }

    public static boolean isValidVietnamLicensePlate(String value, String vehicleType) {
        return getLicensePlateValidationError(value, vehicleType) == null;
    }
}
