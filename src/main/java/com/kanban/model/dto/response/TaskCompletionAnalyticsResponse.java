package com.kanban.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskCompletionAnalyticsResponse {

    private LocalDate periodStart;
    private LocalDate periodEnd;

    // Summary metrics
    private int totalTasks;
    private int completedTasks;
    private int inProgressTasks;
    private int todoTasks;
    private int inReviewTasks;
    private double completionRate;
    private double onTimeRate;
    private int overdueTasks;

    // Average times
    private Double avgCompletionTimeHours;
    private Double avgTimeInProgressHours;

    // Breakdowns
    private List<StatusBreakdown> byStatus;
    private List<PriorityBreakdown> byPriority;
    private List<AssigneeBreakdown> byAssignee;
    private List<DailyTrend> dailyTrend;
    private List<WeeklyTrend> weeklyTrend;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusBreakdown {
        private String status;
        private int count;
        private double percentage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PriorityBreakdown {
        private String priority;
        private int total;
        private int completed;
        private double completionRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssigneeBreakdown {
        private String userId;
        private String userName;
        private int assigned;
        private int completed;
        private double completionRate;
        private int overdue;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyTrend {
        private LocalDate date;
        private int created;
        private int completed;
        private int netChange;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WeeklyTrend {
        private LocalDate weekStart;
        private String weekLabel;
        private int created;
        private int completed;
        private double completionRate;
    }
}
