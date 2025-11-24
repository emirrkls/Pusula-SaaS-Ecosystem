package com.pusula.backend.dto;

import java.util.HashMap;
import java.util.Map;

public class TechnicianPerformanceDTO {
    private String technicianName;
    private Map<String, Integer> dailyCounts; // date -> count

    public TechnicianPerformanceDTO() {
        this.dailyCounts = new HashMap<>();
    }

    public TechnicianPerformanceDTO(String technicianName) {
        this.technicianName = technicianName;
        this.dailyCounts = new HashMap<>();
    }

    public String getTechnicianName() {
        return technicianName;
    }

    public void setTechnicianName(String technicianName) {
        this.technicianName = technicianName;
    }

    public Map<String, Integer> getDailyCounts() {
        return dailyCounts;
    }

    public void setDailyCounts(Map<String, Integer> dailyCounts) {
        this.dailyCounts = dailyCounts;
    }

    public void addDailyCount(String date, int count) {
        this.dailyCounts.put(date, count);
    }
}
