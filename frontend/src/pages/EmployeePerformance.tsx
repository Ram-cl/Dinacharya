import { useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { useDepartments } from '@/hooks/useUsers';
import {
  useEmployeePerformance,
  useRecomputePerformance,
} from '@/hooks/usePerformanceAnalytics';

function toLocalIsoDate(date: Date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function currentMonthRange() {
  const now = new Date();
  // Use last 60 days so imported historical data is always in range
  const start = new Date(now);
  start.setDate(start.getDate() - 60);
  return { from: toLocalIsoDate(start), to: toLocalIsoDate(now) };
}

export default function EmployeePerformancePage() {
  const navigate = useNavigate();
  const defaultRange = useMemo(() => currentMonthRange(), []);
  const [department, setDepartment] = useState('');

  const { data: departments } = useDepartments();
  const { data: overview, isLoading } = useEmployeePerformance(
    defaultRange.from,
    defaultRange.to,
    department || undefined
  );
  const recompute = useRecomputePerformance();

  const employees = overview?.employees ?? [];

  const summary = useMemo(() => {
    if (!employees.length) {
      return { avgIndex: 0, topPerformer: '—', avgProductivity: 0, avgCompletion: 0 };
    }
    const avgIndex =
      employees.reduce((sum, employee) => sum + employee.performanceIndex, 0) / employees.length;
    const avgProductivity =
      employees.reduce((sum, employee) => sum + employee.productivityScore, 0) / employees.length;
    const avgCompletion =
      employees.reduce((sum, employee) => sum + employee.disciplineScore, 0) / employees.length;
    const top = [...employees].sort((a, b) => b.performanceIndex - a.performanceIndex)[0];
    return {
      avgIndex: Math.round(avgIndex * 10) / 10,
      topPerformer: top?.userName ?? '—',
      avgProductivity: Math.round(avgProductivity * 10) / 10,
      avgCompletion: Math.round(avgCompletion * 10) / 10,
    };
  }, [employees]);

  const rankingData = useMemo(
    () =>
      [...employees]
        .sort((a, b) => b.performanceIndex - a.performanceIndex)
        .map((employee) => ({
          userId: employee.userId,
          name: employee.userName.split(' ')[0],
          performanceIndex: employee.performanceIndex,
          productivityScore: employee.productivityScore,
          efficiencyScore: employee.efficiencyScore,
          disciplineScore: employee.disciplineScore,
        })),
    [employees]
  );

  if (isLoading && !overview) {
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
      <div className="tms-header-light rounded-xl px-6 py-5 flex flex-col gap-4 md:flex-row md:items-center md:justify-between border border-warm-border">
        <div>
          <h1 className="text-2xl font-semibold text-charcoal">Employee Performance</h1>
          <p className="text-body-md text-charcoal-muted mt-1">
            Productivity, task completion, and on-time delivery for{' '}
            {overview ? `${overview.periodStart} to ${overview.periodEnd}` : 'this period'}
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-3">
          <select
            className="input-field min-w-[180px]"
            value={department}
            onChange={(event) => setDepartment(event.target.value)}
          >
            <option value="">All departments</option>
            {(departments ?? []).map((item) => (
              <option key={item} value={item}>
                {item}
              </option>
            ))}
          </select>
          <button
            type="button"
            className="btn-secondary"
            disabled={recompute.isPending}
            onClick={() => recompute.mutate()}
          >
            {recompute.isPending ? 'Refreshing…' : 'Refresh scores'}
          </button>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        <div className="card">
          <p className="text-label-md text-charcoal-muted mb-2">Avg Performance Index</p>
          <p className="text-2xl font-semibold text-charcoal">{summary.avgIndex}</p>
        </div>
        <div className="card">
          <p className="text-label-md text-charcoal-muted mb-2">Top Performer</p>
          <p className="text-lg font-semibold text-terracotta">{summary.topPerformer}</p>
        </div>
        <div className="card">
          <p className="text-label-md text-charcoal-muted mb-2">Avg Productivity</p>
          <p className="text-2xl font-semibold text-[#6B8F71]">{summary.avgProductivity}</p>
        </div>
        <div className="card">
          <p className="text-label-md text-charcoal-muted mb-2">Avg Task Completion</p>
          <p className="text-2xl font-semibold text-[#D4AF37]">{summary.avgCompletion}</p>
        </div>
      </div>

      {employees.length === 0 ? (
        <div className="card text-center py-12">
          <p className="text-body-lg text-charcoal-muted">
            No employee performance data yet. Click Refresh scores to compute.
          </p>
        </div>
      ) : (
        <>
          <div className="card">
            <h2 className="text-lg font-semibold text-charcoal mb-4">Performance Ranking</h2>
            <ResponsiveContainer width="100%" height={320}>
              <BarChart data={rankingData}>
                <CartesianGrid strokeDasharray="3 3" stroke="#E8E4DC" />
                <XAxis dataKey="name" tick={{ fill: '#5C5E58' }} />
                <YAxis domain={[0, 100]} tick={{ fill: '#5C5E58' }} />
                <Tooltip />
                <Legend />
                <Bar
                  dataKey="performanceIndex"
                  fill="#C57A44"
                  name="Performance Index"
                  className="cursor-pointer"
                  onClick={(data) => {
                    const payload = data?.payload as { userId?: string } | undefined;
                    if (payload?.userId) navigate(`/performance/${payload.userId}`);
                  }}
                />
              </BarChart>
            </ResponsiveContainer>
          </div>

          <div className="card overflow-x-auto">
            <h2 className="text-lg font-semibold text-charcoal mb-4">Score Breakdown</h2>
            <table className="w-full text-left">
              <thead>
                <tr className="border-b border-sand-dark text-label-md text-charcoal-muted">
                  <th className="py-3 pr-4">Employee</th>
                  <th className="py-3 pr-4">Department</th>
                  <th className="py-3 pr-4">PI</th>
                  <th className="py-3 pr-4">Productivity</th>
                  <th className="py-3 pr-4">Completion</th>
                  <th className="py-3 pr-4">On-time</th>
                  <th className="py-3 pr-4">Tasks</th>
                  <th className="py-3">Assigned</th>
                </tr>
              </thead>
              <tbody>
                {employees.map((employee) => (
                  <tr
                    key={employee.userId}
                    className="border-b border-sand-dark/60 hover:bg-sand/40 cursor-pointer"
                    onClick={() => navigate(`/performance/${employee.userId}`)}
                  >
                    <td className="py-3 pr-4 font-medium text-charcoal">
                      <Link
                        to={`/performance/${employee.userId}`}
                        className="text-terracotta hover:text-terracotta-dark hover:underline"
                        onClick={(event) => event.stopPropagation()}
                      >
                        {employee.userName}
                      </Link>
                    </td>
                    <td className="py-3 pr-4 text-charcoal-muted">{employee.department || '—'}</td>
                    <td className="py-3 pr-4 font-medium text-charcoal">{employee.performanceIndex}</td>
                    <td className="py-3 pr-4 font-medium text-charcoal">{employee.productivityScore}</td>
                    <td className="py-3 pr-4 font-medium text-charcoal">{employee.disciplineScore}%</td>
                    <td className="py-3 pr-4 font-medium text-charcoal">{employee.efficiencyScore}%</td>
                    <td className="py-3 pr-4 font-medium text-charcoal">{employee.tasksCompleted}</td>
                    <td className="py-3 font-medium text-charcoal">{employee.tasksAssigned}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </div>
  );
}
