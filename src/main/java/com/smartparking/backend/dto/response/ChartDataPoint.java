package com.smartparking.backend.dto.response;

public class ChartDataPoint {
    private String label;
    private Number value;

    public ChartDataPoint() {
    }

    public ChartDataPoint(String label, Number value) {
        this.label = label;
        this.value = value;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Number getValue() {
        return value;
    }

    public void setValue(Number value) {
        this.value = value;
    }
}
