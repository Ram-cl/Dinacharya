import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  Line,
  LineChart,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { useTaskCompletionAnalytics } from '@/hooks/useTaskAnalytics';
import { useDepartments } from '@/hooks/useUsers';

function toLocalIsoDate(date: Date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function defaultRange() {
  const now = new Date();
  const from = new Date(now);
  from.setDate(from.getDate() - 30);
  return { from: toLocalIsoDate(from), to: toLocalIsoDate(now) };
}

const STATUS_COLORS: Record<string, string> = {
  TODO: '#94a3b8',
  IN_PROGRESS: '#3b82f6',
  IN_REVIEW: '#f59e0b',
  DONE: '#22c55e',
};

const PRIORITY_COLORS: Record<string, string> = {
  LOW: '#94a3b8',
  MEDIUM: '#3b82f6',
  HIGH: '#f59e0b',
  URGENT: '#ef4444',
};

export default function TaskAnalyticsPage() {
  const range = useMemo(() => defaultRange(), []);
  const [from, setFrom] = useState(range.from);
  const [to, setTo] = useState(range.to);
  const [department, setDepartment] = useState('');

  const { data: departmentsList = [] } = useDepartments();
  const { data: analytics, isLoading } = useTaskCompletionAnalytics(from, to, department || undefined);

  const statusData = useMemo(() => {
    if (!analytics) return [];
    return analytics.byStatus
      .filter((s) => s.count > 0)
      .map((s) => ({
        name: s.status.replace('_', ' '),
        value: s.count,
        percentage: s.percentage,
        fill: STATUS_COLORS[s.status] || '#6b7280',
      }));
  }, [analytics]);

  const priorityData = useMemo(() => {
    if (!analytics) return [];
    return analytics.byPriority.map((p) => ({
      name: p.priority,
      total: p.total,
      completed: p.completed,
      rate: p.completionRate,
      fill: PRIORITY_COLORS[p.priority] || '#6b7280',
    }));
  }, [analytics]);

  const dailyData = useMemo(() => {
    if (!analytics) return [];
    return analytics.dailyTrend.slice(-14).map((d) => ({
      date: new Date(d.date).toLocaleDateString(undefined, { month: 'short', day: 'numeric' }),
      created: d.created,
      completed: d.completed,
      net: d.netChange,
    }));
  }, [analytics]);

  const weeklyData = useMemo(() => {
    if (!analytics) return [];
    return analytics.weeklyTrend.map((w) => ({
      week: w.weekLabel,
      created: w.created,
      completed: w.completed,
      rate: w.completionRate,
    }));
  }, [analytics]);

  if (isLoading && !analytics) {
    return (
      <div className="flex items-center justify-center h-full">
        <span className="material-symbols-outlined text-[48px] text-charcoal-light animate-spin">
          refresh
        </span>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="tms-header-light rounded-xl px-6 py-5 flex flex-col gap-4 md:flex-row md:items-center md:justify-between border border-warm-border">
        <div>
          <h1 className="text-2xl font-semibold text-charcoal">Task Completion Analytics</h1>
          <p className="text-body-md text-charcoal-muted mt-1">
            Track completion rates, trends, and team productivity
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-3">
          <input
            type="date"
            className="input-field"
            value={from}
            onChange={(e) => setFrom(e.target.value)}
          />
          <span className="text-charcoal-muted">to</span>
          <input
            type="date"
            className="input-field"
            value={to}
            onChange={(e) => setTo(e.target.value)}
          />
          <select
            className="input-field min-w-[160px]"
            value={department}
            onChange={(e) => setDepartment(e.target.value)}
          >
            <option value="">All departments</option>
            {departmentsList.map((dept) => (
              <option key={dept} value={dept}>{dept}</option>
            ))}
          </select>
        </div>
      </div>

      {/* Summary Cards */}
      <div className="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-4">
        <div className="card">
          <p className="text-label-md text-charcoal-muted mb-2">Total Tasks</p>
          <p className="text-2xl font-semibold text-charcoal">{analytics?.totalTasks ?? 0}</p>
        </div>
        <div className="card">
          <p className="text-label-md text-charcoal-muted mb-2">Completed</p>
          <p className="text-2xl font-semibold text-[#22c55e]">{analytics?.completedTasks ?? 0}</p>
        </div>
        <div className="card">
          <p className="text-label-md text-charcoal-muted mb-2">In Progress</p>
          <p className="text-2xl font-semibold text-[#3b82f6]">{analytics?.inProgressTasks ?? 0}</p>
        </div>
        <div className="card">
          <p className="text-label-md text-charcoal-muted mb-2">Completion Rate</p>
          <p className="text-2xl font-semibold text-terracotta">{analytics?.completionRate ?? 0}%</p>
        </div>
        <div className="card">
          <p className="text-label-md text-charcoal-muted mb-2">On-Time Rate</p>
          <p className="text-2xl font-semibold text-[#6B8F71]">{analytics?.onTimeRate ?? 0}%</p>
        </div>
        <div className="card">
          <p className="text-label-md text-charcoal-muted mb-2">Overdue</p>
          <p className="text-2xl font-semibold text-[#ef4444]">{analytics?.overdueTasks ?? 0}</p>
        </div>
      </div>

      {/* Charts Row 1: Status Pie + Priority Bar */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="card">
          <h2 className="text-lg font-semibold text-charcoal mb-4">Tasks by Status</h2>
          {statusData.length > 0 ? (
            <ResponsiveContainer width="100%" height={280}>
              <PieChart>
                <Pie
                  data={statusData}
                  cx="50%"
                  cy="50%"
                  innerRadius={60}
                  outerRadius={100}
                  dataKey="value"
                  labelLine={false}
                >
                  {statusData.map((entry, index) => (
                    <Cell key={`cell-${index}`} fill={entry.fill} />
                  ))}
                </Pie>
                <Tooltip
                  formatter={(value, name) => [
                    `${value} tasks`,
                    name,
                  ]}
                />
                <Legend />
              </PieChart>
            </ResponsiveContainer>
          ) : (
            <div className="flex items-center justify-center h-[280px] text-charcoal-muted">
              No task data available
            </div>
          )}
        </div>

        <div className="card">
          <h2 className="text-lg font-semibold text-charcoal mb-4">Completion by Priority</h2>
          <ResponsiveContainer width="100%" height={280}>
            <BarChart data={priorityData}>
              <CartesianGrid strokeDasharray="3 3" stroke="#E8E4DC" />
              <XAxis dataKey="name" tick={{ fill: '#5C5E58' }} />
              <YAxis tick={{ fill: '#5C5E58' }} />
              <Tooltip />
              <Legend />
              <Bar dataKey="total" fill="#94a3b8" name="Total" />
              <Bar dataKey="completed" fill="#22c55e" name="Completed" />
            </BarChart>
          </ResponsiveContainer>
        </div>
      </div>

      {/* Charts Row 2: Daily Trend + Weekly Trend */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="card">
          <h2 className="text-lg font-semibold text-charcoal mb-4">Daily Trend (Last 14 Days)</h2>
          <ResponsiveContainer width="100%" height={280}>
            <LineChart data={dailyData}>
              <CartesianGrid strokeDasharray="3 3" stroke="#E8E4DC" />
              <XAxis dataKey="date" tick={{ fill: '#5C5E58', fontSize: 11 }} />
              <YAxis tick={{ fill: '#5C5E58' }} />
              <Tooltip />
              <Legend />
              <Line
                type="monotone"
                dataKey="created"
                stroke="#3b82f6"
                name="Created"
                strokeWidth={2}
              />
              <Line
                type="monotone"
                dataKey="completed"
                stroke="#22c55e"
                name="Completed"
                strokeWidth={2}
              />
            </LineChart>
          </ResponsiveContainer>
        </div>

        <div className="card">
          <h2 className="text-lg font-semibold text-charcoal mb-4">Weekly Summary</h2>
          <ResponsiveContainer width="100%" height={280}>
            <BarChart data={weeklyData}>
              <CartesianGrid strokeDasharray="3 3" stroke="#E8E4DC" />
              <XAxis dataKey="week" tick={{ fill: '#5C5E58' }} />
              <YAxis tick={{ fill: '#5C5E58' }} />
              <Tooltip />
              <Legend />
              <Bar dataKey="created" fill="#3b82f6" name="Created" />
              <Bar dataKey="completed" fill="#22c55e" name="Completed" />
            </BarChart>
          </ResponsiveContainer>
        </div>
      </div>

      {/* Assignee Breakdown Table */}
      {analytics && analytics.byAssignee.length > 0 && (
        <div className="card overflow-x-auto">
          <h2 className="text-lg font-semibold text-charcoal mb-4">Top Assignees</h2>
          <table className="w-full text-left">
            <thead>
              <tr className="border-b border-sand-dark text-label-md text-charcoal-muted">
                <th className="py-3 pr-4">Employee</th>
                <th className="py-3 pr-4">Assigned</th>
                <th className="py-3 pr-4">Completed</th>
                <th className="py-3 pr-4">Completion Rate</th>
                <th className="py-3">Overdue</th>
              </tr>
            </thead>
            <tbody>
              {analytics.byAssignee.map((assignee) => (
                <tr
                  key={assignee.userId}
                  className="border-b border-sand-dark/60 hover:bg-sand/40"
                >
                  <td className="py-3 pr-4 font-medium text-charcoal">
                    <Link
                      to={`/performance/${assignee.userId}`}
                      className="text-terracotta hover:text-terracotta-dark hover:underline"
                    >
                      {assignee.userName}
                    </Link>
                  </td>
                  <td className="py-3 pr-4 text-charcoal">{assignee.assigned}</td>
                  <td className="py-3 pr-4 text-[#22c55e] font-medium">{assignee.completed}</td>
                  <td className="py-3 pr-4">
                    <div className="flex items-center gap-2">
                      <div className="h-2 w-24 rounded-full bg-sand overflow-hidden">
                        <div
                          className="h-full rounded-full bg-terracotta transition-all"
                          style={{ width: `${Math.min(assignee.completionRate, 100)}%` }}
                        />
                      </div>
                      <span className="text-sm font-medium text-charcoal">
                        {assignee.completionRate}%
                      </span>
                    </div>
                  </td>
                  <td className="py-3">
                    <span
                      className={`font-medium ${assignee.overdue > 0 ? 'text-[#ef4444]' : 'text-charcoal-muted'}`}
                    >
                      {assignee.overdue}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Average Completion Time */}
      {analytics?.avgCompletionTimeHours != null && (
        <div className="card">
          <h2 className="text-lg font-semibold text-charcoal mb-2">Average Completion Time</h2>
          <p className="text-3xl font-semibold text-terracotta">
            {analytics.avgCompletionTimeHours < 24
              ? `${analytics.avgCompletionTimeHours.toFixed(1)} hours`
              : `${(analytics.avgCompletionTimeHours / 24).toFixed(1)} days`}
          </p>
          <p className="text-sm text-charcoal-muted mt-1">
            From task creation to completion
          </p>
        </div>
      )}

      {/* Individual Progress Cards */}
      {analytics && analytics.byAssignee.length > 0 && (
        <div>
          <h2 className="text-xl font-semibold text-charcoal mb-4">Individual Progress</h2>
          <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
            {analytics.byAssignee.map((person) => {
              const pendingTasks = person.assigned - person.completed;
              const completionPct = Math.min(person.completionRate, 100);
              const isHighPerformer = person.completionRate >= 80;
              const hasOverdue = person.overdue > 0;

              return (
                <div
                  key={person.userId}
                  className="card border border-warm-border hover:shadow-md transition-shadow"
                >
                  {/* Header */}
                  <div className="flex items-start justify-between mb-4">
                    <div className="flex items-center gap-3">
                      <div className="w-10 h-10 rounded-full bg-terracotta/10 flex items-center justify-center">
                        <span className="text-lg font-semibold text-terracotta">
                          {person.userName.charAt(0).toUpperCase()}
                        </span>
                      </div>
                      <div>
                        <Link
                          to={`/performance/${person.userId}`}
                          className="font-semibold text-charcoal hover:text-terracotta transition-colors"
                        >
                          {person.userName}
                        </Link>
                        <p className="text-xs text-charcoal-muted">
                          {person.assigned} tasks assigned
                        </p>
                      </div>
                    </div>
                    {isHighPerformer && (
                      <span className="inline-flex items-center gap-1 px-2 py-1 rounded-full bg-[#22c55e]/10 text-[#22c55e] text-xs font-medium">
                        <span className="material-symbols-outlined text-[14px]">star</span>
                        Top
                      </span>
                    )}
                  </div>

                  {/* Progress Ring */}
                  <div className="flex items-center gap-4 mb-4">
                    <div className="relative w-16 h-16">
                      <svg className="w-16 h-16 -rotate-90" viewBox="0 0 36 36">
                        <path
                          d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"
                          fill="none"
                          stroke="#E8E4DC"
                          strokeWidth="3"
                        />
                        <path
                          d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"
                          fill="none"
                          stroke={completionPct >= 80 ? '#22c55e' : completionPct >= 50 ? '#f59e0b' : '#ef4444'}
                          strokeWidth="3"
                          strokeDasharray={`${completionPct}, 100`}
                          strokeLinecap="round"
                        />
                      </svg>
                      <div className="absolute inset-0 flex items-center justify-center">
                        <span className="text-sm font-bold text-charcoal">{person.completionRate}%</span>
                      </div>
                    </div>
                    <div className="flex-1 space-y-1">
                      <div className="flex justify-between text-sm">
                        <span className="text-charcoal-muted">Completed</span>
                        <span className="font-medium text-[#22c55e]">{person.completed}</span>
                      </div>
                      <div className="flex justify-between text-sm">
                        <span className="text-charcoal-muted">Pending</span>
                        <span className="font-medium text-[#3b82f6]">{pendingTasks}</span>
                      </div>
                      <div className="flex justify-between text-sm">
                        <span className="text-charcoal-muted">Overdue</span>
                        <span className={`font-medium ${hasOverdue ? 'text-[#ef4444]' : 'text-charcoal-muted'}`}>
                          {person.overdue}
                        </span>
                      </div>
                    </div>
                  </div>

                  {/* Status Bar */}
                  <div className="h-2 rounded-full bg-sand overflow-hidden flex">
                    {person.completed > 0 && (
                      <div
                        className="h-full bg-[#22c55e]"
                        style={{ width: `${(person.completed / person.assigned) * 100}%` }}
                        title={`Completed: ${person.completed}`}
                      />
                    )}
                    {pendingTasks > 0 && (
                      <div
                        className="h-full bg-[#3b82f6]"
                        style={{ width: `${(pendingTasks / person.assigned) * 100}%` }}
                        title={`Pending: ${pendingTasks}`}
                      />
                    )}
                  </div>
                  <div className="flex justify-between mt-2 text-xs text-charcoal-muted">
                    <span>0%</span>
                    <span>100%</span>
                  </div>

                  {/* Warning for overdue */}
                  {hasOverdue && (
                    <div className="mt-3 flex items-center gap-2 px-3 py-2 rounded-lg bg-[#ef4444]/10 text-[#ef4444] text-sm">
                      <span className="material-symbols-outlined text-[16px]">warning</span>
                      {person.overdue} overdue task{person.overdue > 1 ? 's' : ''}
                    </div>
                  )}

                  {/* View Details Link */}
                  <Link
                    to={`/performance/${person.userId}`}
                    className="mt-4 flex items-center justify-center gap-1 text-sm text-terracotta hover:text-terracotta-dark transition-colors"
                  >
                    View full performance
                    <span className="material-symbols-outlined text-[16px]">arrow_forward</span>
                  </Link>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* Empty State */}
      {analytics && analytics.byAssignee.length === 0 && (
        <div className="card text-center py-12">
          <span className="material-symbols-outlined text-[48px] text-charcoal-light mb-4">
            person_off
          </span>
          <p className="text-body-lg text-charcoal-muted">
            No task assignments found for this period.
          </p>
          <p className="text-sm text-charcoal-muted mt-1">
            Try adjusting the date range.
          </p>
        </div>
      )}
    </div>
  );
}
