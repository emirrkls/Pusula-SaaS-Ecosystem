package com.pusula.backend.dto;

import java.util.List;

public class SystemStatusDTO {
    private double cpuUsagePercent;
    private double ramUsagePercent;
    private String jvmUptime;
    private long activeDbConnections;
    private long totalDbRecords;
    private List<SystemLogDTO> recentLogs;

    public SystemStatusDTO() {
    }

    public double getCpuUsagePercent() {
        return cpuUsagePercent;
    }

    public void setCpuUsagePercent(double cpuUsagePercent) {
        this.cpuUsagePercent = cpuUsagePercent;
    }

    public double getRamUsagePercent() {
        return ramUsagePercent;
    }

    public void setRamUsagePercent(double ramUsagePercent) {
        this.ramUsagePercent = ramUsagePercent;
    }

    public String getJvmUptime() {
        return jvmUptime;
    }

    public void setJvmUptime(String jvmUptime) {
        this.jvmUptime = jvmUptime;
    }

    public long getActiveDbConnections() {
        return activeDbConnections;
    }

    public void setActiveDbConnections(long activeDbConnections) {
        this.activeDbConnections = activeDbConnections;
    }

    public long getTotalDbRecords() {
        return totalDbRecords;
    }

    public void setTotalDbRecords(long totalDbRecords) {
        this.totalDbRecords = totalDbRecords;
    }

    public List<SystemLogDTO> getRecentLogs() {
        return recentLogs;
    }

    public void setRecentLogs(List<SystemLogDTO> recentLogs) {
        this.recentLogs = recentLogs;
    }

    public static class SystemLogDTO {
        private String level; // INFO, WARN, ERROR
        private String timestamp;
        private String message;

        public SystemLogDTO(String level, String timestamp, String message) {
            this.level = level;
            this.timestamp = timestamp;
            this.message = message;
        }

        public String getLevel() {
            return level;
        }

        public String getTimestamp() {
            return timestamp;
        }

        public String getMessage() {
            return message;
        }
    }
}
