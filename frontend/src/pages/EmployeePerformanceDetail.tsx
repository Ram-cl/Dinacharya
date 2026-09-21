import { useMemo } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useEmployeePerformanceDetail } from '@/hooks/usePerformanceAnalytics';
import { useEmployeeAttendanceDashboard } from '@/hooks/useAttendanceAnalytics';
import { apiClient } from '@/api/client';
import { Task, Page } from '@/types';
import { useQuery } from '@tanstack/react-query';

function toLocalIsoDate(date: Date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function currentMonthRange() {
  const now = new Date();
  const start = new Date(now);
  start.setDate(start.getDate() - 60);
  return { from: toLocalIsoDate(start), to: toLocalIsoDate(now) };
}

function currentYearRange() {
  const now = new Date();
  const start = new Date(now.getFullYear(), 0, 1);
  return { from: toLocalIsoDate(start), to: toLocalIsoDate(now) };
}

function useUserTasks(userId?: string) {
  return useQuery({
    queryKey: ['user-tasks', userId],
    queryFn: async () => {
      const response = await apiClient.get<Page<Task>>(
        `/tasks?assignedToId=${userId}&page=0&size=100`
      );
      return response.data;
    },
    enabled: Boolean(userId),
  });
}

export default function EmployeePerformanceDetail() {
  const { userId } = useParams<{ userId: string }>();
  const defaultRange = useMemo(() => currentMonthRange(), []);
  const yearRange = useMemo(() => currentYearRange(), []);

  const { data: employee, isLoading } = useEmployeePerformanceDetail(
    userId,
    defaultRange.from,
    defaultRange.to
  );
  const { data: tasksPage } = useUserTasks(userId);
  const { data: attendance } = useEmployeeAttendanceDashboard(
    userId,
    yearRange.from,
    yearRange.to
  );

  const tasks = tasksPage?.content ?? [];
  const todoTasks = tasks.filter((t) => t.status === 'TODO');
  const inProgressTasks = tasks.filter((t) => t.status === 'IN_PROGRESS');
  const inReviewTasks = tasks.filter((t) => t.status === 'IN_REVIEW');
  const doneTasks = tasks.filter((t) => t.status === 'DONE');
  const overdueTasks = tasks.filter(
    (t) => t.deadline && new Date(t.deadline) < new Date() && t.status !== 'DONE'
  );

  if (isLoading && !employee) {
    return (
      <div className="flex items-center justify-center h-full">
        <span className="material-symbols-outlined text-[48px] text-charcoal-light animate-spin">
          refresh
        </span>
      </div>
    );
  }

  if (!employee) {
    return (
      <div className="card text-center py-12">
        <p className="text-body-lg text-charcoal-muted mb-4">Employee performance not found.</p>
        <Link to="/performance" className="btn btn-secondary">
          Back to overview
        </Link>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="tms-header-light rounded-xl px-6 py-5 border border-warm-border">
        <Link
          to="/performance"
          className="inline-flex items-center gap-1 text-sm text-charcoal-muted hover:text-charcoal mb-3"
        >
          <span className="material-symbols-outlined text-[18px]">arrow_back</span>
          Back to team performance
        </Link>
        <div className="flex flex-col gap-2 md:flex-row md:items-end md:justify-between">
          <div>
            <h1 className="text-2xl font-semibold text-charcoal">{employee.userName}</h1>
            <p className="text-body-md text-charcoal-muted mt-1">
              {employee.department || 'No department'} · {employee.periodStart} to {employee.periodEnd}
            </p>
          </div>
          <div className="flex flex-col items-start md:items-end gap-3">
            <Link to={`/attendance/${userId}`} className="btn btn-secondary">
              View attendance dashboard
            </Link>
            <div className="text-right">
              <p className="text-label-md text-charcoal-muted">Performance Index</p>
              <p className="text-3xl font-semibold text-charcoal">{employee.performanceIndex}</p>
              {employee.rollingIndex != null && (
                <p className="text-sm text-charcoal-muted">
                  3-month rolling: {employee.rollingIndex}
                </p>
              )}
            </div>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        <div className="card">
          <p className="text-label-md text-charcoal-muted mb-2">Productivity</p>
          <p className="text-2xl font-semibold text-[#6B8F71]">{employee.productivityScore}</p>
        </div>
        <div className="card">
          <p className="text-label-md text-charcoal-muted mb-2">Task Completion</p>
          <p className="text-2xl font-semibold text-[#D4AF37]">{employee.disciplineScore}%</p>
          <p className="text-sm text-charcoal-muted mt-1">
            {employee.tasksCompleted}/{employee.tasksAssigned} tasks
          </p>
        </div>
        <div className="card">
          <p className="text-label-md text-charcoal-muted mb-2">On-time Delivery</p>
          <p className="text-2xl font-semibold text-terracotta">{employee.efficiencyScore}%</p>
          <p className="text-sm text-charcoal-muted mt-1">{employee.onTimeTasks} on time</p>
        </div>
        <div className="card">
          <p className="text-label-md text-charcoal-muted mb-2">Tasks completed</p>
          <p className="text-2xl font-semibold text-charcoal">{employee.tasksCompleted}</p>
          <p className="text-sm text-charcoal-muted mt-1">{employee.onTimeTasks} on time</p>
        </div>
      </div>

      <div className="grid grid-cols-1 xl:grid-cols-2 gap-6">
        {/* Tasks Section */}
        <div className="card">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-lg font-semibold text-charcoal">Tasks</h2>
            <span className="text-sm text-charcoal-muted">{tasks.length} total</span>
          </div>

          {/* Task Status Summary */}
          <div className="grid grid-cols-2 gap-3 mb-4">
            <div className="rounded-lg bg-sand/50 p-3">
              <div className="flex items-center gap-2">
                <span className="w-3 h-3 rounded-full bg-[#94a3b8]"></span>
                <span className="text-sm text-charcoal-muted">To Do</span>
              </div>
              <p className="text-xl font-semibold text-charcoal mt-1">{todoTasks.length}</p>
            </div>
            <div className="rounded-lg bg-sand/50 p-3">
              <div className="flex items-center gap-2">
                <span className="w-3 h-3 rounded-full bg-[#3b82f6]"></span>
                <span className="text-sm text-charcoal-muted">In Progress</span>
              </div>
              <p className="text-xl font-semibold text-charcoal mt-1">{inProgressTasks.length}</p>
            </div>
            <div className="rounded-lg bg-sand/50 p-3">
              <div className="flex items-center gap-2">
                <span className="w-3 h-3 rounded-full bg-[#f59e0b]"></span>
                <span className="text-sm text-charcoal-muted">In Review</span>
              </div>
              <p className="text-xl font-semibold text-charcoal mt-1">{inReviewTasks.length}</p>
            </div>
            <div className="rounded-lg bg-sand/50 p-3">
              <div className="flex items-center gap-2">
                <span className="w-3 h-3 rounded-full bg-[#22c55e]"></span>
                <span className="text-sm text-charcoal-muted">Done</span>
              </div>
              <p className="text-xl font-semibold text-charcoal mt-1">{doneTasks.length}</p>
            </div>
          </div>

          {/* Overdue Warning */}
          {overdueTasks.length > 0 && (
            <div className="flex items-center gap-2 px-3 py-2 rounded-lg bg-[#ef4444]/10 text-[#ef4444] text-sm mb-4">
              <span className="material-symbols-outlined text-[18px]">warning</span>
              {overdueTasks.length} overdue task{overdueTasks.length > 1 ? 's' : ''}
            </div>
          )}

          {/* Progress Bar */}
          <div className="space-y-2">
            <div className="flex justify-between text-sm">
              <span className="text-charcoal-muted">Completion Progress</span>
              <span className="font-medium text-charcoal">
                {tasks.length > 0 ? Math.round((doneTasks.length / tasks.length) * 100) : 0}%
              </span>
            </div>
            <div className="h-3 rounded-full bg-sand overflow-hidden flex">
              {doneTasks.length > 0 && (
                <div
                  className="h-full bg-[#22c55e]"
                  style={{ width: `${(doneTasks.length / tasks.length) * 100}%` }}
                />
              )}
              {inReviewTasks.length > 0 && (
                <div
                  className="h-full bg-[#f59e0b]"
                  style={{ width: `${(inReviewTasks.length / tasks.length) * 100}%` }}
                />
              )}
              {inProgressTasks.length > 0 && (
                <div
                  className="h-full bg-[#3b82f6]"
                  style={{ width: `${(inProgressTasks.length / tasks.length) * 100}%` }}
                />
              )}
            </div>
          </div>

          {/* Recent Tasks List */}
          {tasks.length > 0 && (
            <div className="mt-4 pt-4 border-t border-warm-border">
              <p className="text-sm font-medium text-charcoal-muted mb-2">Recent Tasks</p>
              <div className="space-y-2 max-h-[180px] overflow-y-auto">
                {tasks.slice(0, 5).map((task) => (
                  <div
                    key={task.id}
                    className="flex items-center justify-between gap-2 py-2 border-b border-sand-dark/40 last:border-0"
                  >
                    <div className="min-w-0 flex-1">
                      <p className="text-sm font-medium text-charcoal truncate">{task.title}</p>
                      <p className="text-xs text-charcoal-muted">{task.assignedTo?.department || '—'}</p>
                    </div>
                    <span
                      className={`shrink-0 px-2 py-0.5 rounded text-xs font-medium ${
                        task.status === 'DONE'
                          ? 'bg-[#22c55e]/10 text-[#22c55e]'
                          : task.status === 'IN_PROGRESS'
                            ? 'bg-[#3b82f6]/10 text-[#3b82f6]'
                            : task.status === 'IN_REVIEW'
                              ? 'bg-[#f59e0b]/10 text-[#f59e0b]'
                              : 'bg-[#94a3b8]/10 text-[#5C5E58]'
                      }`}
                    >
                      {task.status.replace('_', ' ')}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>

        {/* Attendance Section */}
        <div className="card">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-lg font-semibold text-charcoal">Attendance</h2>
            <Link
              to={`/attendance/${userId}`}
              className="text-sm text-terracotta hover:text-terracotta-dark"
            >
              View details →
            </Link>
          </div>

          {attendance ? (
            <>
              {/* Overall Attendance */}
              <div className="flex items-center gap-4 mb-4">
                <div className="relative w-20 h-20">
                  <svg className="w-20 h-20 -rotate-90" viewBox="0 0 36 36">
                    <path
                      d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"
                      fill="none"
                      stroke="#E8E4DC"
                      strokeWidth="3"
                    />
                    <path
                      d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"
                      fill="none"
                      stroke={
                        attendance.overallPercent >= 90
                          ? '#22c55e'
                          : attendance.overallPercent >= 75
                            ? '#f59e0b'
                            : '#ef4444'
                      }
                      strokeWidth="3"
                      strokeDasharray={`${Math.min(attendance.overallPercent, 100)}, 100`}
                      strokeLinecap="round"
                    />
                  </svg>
                  <div className="absolute inset-0 flex items-center justify-center">
                    <span className="text-lg font-bold text-charcoal">
                      {attendance.overallPercent}%
                    </span>
                  </div>
                </div>
                <div className="flex-1">
                  <p className="text-2xl font-semibold text-charcoal">{attendance.totalSessions}</p>
                  <p className="text-sm text-charcoal-muted">Total sessions this year</p>
                </div>
              </div>

              {/* Attendance Stats */}
              <div className="grid grid-cols-3 gap-3 mb-4">
                <div className="rounded-lg bg-[#22c55e]/10 p-3 text-center">
                  <p className="text-xl font-semibold text-[#22c55e]">{attendance.presentCount}</p>
                  <p className="text-xs text-charcoal-muted">Present</p>
                </div>
                <div className="rounded-lg bg-[#ef4444]/10 p-3 text-center">
                  <p className="text-xl font-semibold text-[#ef4444]">{attendance.absentCount}</p>
                  <p className="text-xs text-charcoal-muted">Absent</p>
                </div>
                <div className="rounded-lg bg-[#f59e0b]/10 p-3 text-center">
                  <p className="text-xl font-semibold text-[#f59e0b]">{attendance.lateCount}</p>
                  <p className="text-xs text-charcoal-muted">Late</p>
                </div>
              </div>

              {/* Recent Months */}
              {attendance.months.length > 0 && (
                <div className="pt-4 border-t border-warm-border">
                  <p className="text-sm font-medium text-charcoal-muted mb-2">Recent Months</p>
                  <div className="space-y-2">
                    {attendance.months.slice(0, 3).map((month) => (
                      <div
                        key={month.monthKey}
                        className="flex items-center justify-between py-2 border-b border-sand-dark/40 last:border-0"
                      >
                        <span className="text-sm font-medium text-charcoal">{month.monthLabel}</span>
                        <div className="flex items-center gap-3">
                          <span className="text-sm text-[#22c55e]">{month.presentCount}P</span>
                          <span className="text-sm text-[#ef4444]">{month.absentCount}A</span>
                          <span className="text-sm font-semibold text-charcoal">
                            {month.attendancePercent}%
                          </span>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </>
          ) : (
            <div className="flex flex-col items-center justify-center py-8 text-charcoal-muted">
              <span className="material-symbols-outlined text-[48px] mb-2">calendar_month</span>
              <p>No attendance data available</p>
            </div>
          )}
        </div>
      </div>

      <div className="card">
        <h2 className="text-lg font-semibold text-charcoal mb-4">Activity summary</h2>
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          <div className="rounded-lg border border-warm-border p-4">
            <p className="text-label-md text-charcoal-muted">Tasks completed</p>
            <p className="text-xl font-semibold text-charcoal mt-1">{employee.tasksCompleted}</p>
          </div>
          <div className="rounded-lg border border-warm-border p-4">
            <p className="text-label-md text-charcoal-muted">On-time completion</p>
            <p className="text-xl font-semibold text-charcoal mt-1">
              {employee.tasksCompleted > 0
                ? `${Math.round((employee.onTimeTasks / employee.tasksCompleted) * 100)}%`
                : '—'}
            </p>
          </div>
          <div className="rounded-lg border border-warm-border p-4">
            <p className="text-label-md text-charcoal-muted">Attendance</p>
            <p className="text-xl font-semibold text-charcoal mt-1">
              {employee.attendanceDays}/{employee.workingDays} days
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}
