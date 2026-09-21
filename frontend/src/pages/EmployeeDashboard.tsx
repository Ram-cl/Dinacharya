import { FormEvent, useEffect, useMemo, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { useAuthStore } from '@/store/authStore';
import { useChangeTaskStatus, useCreateTask, useMyTasks } from '@/hooks/useTasks';
import { useTeams } from '@/hooks/useTeams';
import { useDepartments } from '@/hooks/useUsers';
import { Task, TaskPriority, TaskStatus } from '@/types';
import { toast } from 'react-toastify';

const STATUS_LABELS: Record<TaskStatus, string> = {
  [TaskStatus.TODO]: 'Pending',
  [TaskStatus.IN_PROGRESS]: 'In Progress',
  [TaskStatus.IN_REVIEW]: 'In Review',
  [TaskStatus.DONE]: 'Completed',
};

const PRIORITY_LABELS: Record<TaskPriority, string> = {
  [TaskPriority.LOW]: 'Low',
  [TaskPriority.MEDIUM]: 'Medium',
  [TaskPriority.HIGH]: 'High',
  [TaskPriority.URGENT]: 'Urgent',
};

function todayKey() {
  const now = new Date();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${now.getFullYear()}-${month}-${day}`;
}

function dateKey(value?: string) {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

function formatDate(value?: string) {
  if (!value) return '—';
  const key = dateKey(value);
  if (key === todayKey()) return 'Today';
  return new Date(value).toLocaleDateString('en-GB', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
  });
}

function priorityBadgeClass(priority: TaskPriority) {
  if (priority === TaskPriority.HIGH || priority === TaskPriority.URGENT) {
    return 'tms-badge tms-badge-priority-high';
  }
  if (priority === TaskPriority.MEDIUM) return 'tms-badge tms-badge-priority-medium';
  return 'tms-badge tms-badge-priority-low';
}

function statusBadgeClass(status: TaskStatus) {
  if (status === TaskStatus.TODO) return 'tms-badge tms-badge-status-pending';
  if (status === TaskStatus.IN_PROGRESS) return 'tms-badge tms-badge-status-progress';
  if (status === TaskStatus.IN_REVIEW) return 'tms-badge tms-badge-status-review';
  return 'tms-badge tms-badge-status-done';
}

export default function EmployeeDashboard() {
  const user = useAuthStore((state) => state.user);
  const location = useLocation();
  const { data: teamsPage } = useTeams(0, 100);
  const { data: departments = [] } = useDepartments();
  const { data: tasksPage, isLoading } = useMyTasks();
  const createTask = useCreateTask();
  const changeStatus = useChangeTaskStatus();
  const teams = teamsPage?.content || [];
  const tasks = tasksPage?.content || [];
  const today = todayKey();
  const [showCreateModal, setShowCreateModal] = useState(false);

  const [form, setForm] = useState({
    workDate: today,
    teamId: '',
    department: '',
    title: '',
    description: '',
    priority: TaskPriority.MEDIUM,
    status: TaskStatus.IN_PROGRESS,
  });

  useEffect(() => {
    setForm((prev) => {
      const preferred = teams.find((team) => team.name !== 'Daily work') || teams[0];
      return {
        ...prev,
        teamId: prev.teamId || preferred?.id || '',
        department: prev.department || user?.department || departments[0] || '',
      };
    });
  }, [teams, departments, user?.department]);

  useEffect(() => {
    if (!(location.state as { openNewTask?: number } | null)?.openNewTask) return;
    setShowCreateModal(true);
  }, [location.state]);

  useEffect(() => {
    if (!(location.state as { focusAssigned?: number } | null)?.focusAssigned) return;
    document.getElementById('assigned-tasks')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }, [location.state]);

  const assignedTasks = useMemo(
    () =>
      tasks.filter(
        (task) =>
          task.assignedTo?.id === user?.id &&
          task.createdBy?.id !== user?.id
      ),
    [tasks, user?.id]
  );

  const openAssigned = useMemo(
    () => assignedTasks.filter((task) => task.status !== TaskStatus.DONE),
    [assignedTasks]
  );

  const todaysTasks = useMemo(
    () =>
      tasks.filter(
        (task) =>
          task.createdBy?.id === user?.id &&
          (dateKey(task.deadline) === today || (!task.deadline && dateKey(task.createdAt) === today))
      ),
    [tasks, today, user?.id]
  );

  const earlierTasks = useMemo(
    () =>
      tasks.filter(
        (task) =>
          task.createdBy?.id === user?.id &&
          !todaysTasks.some((item) => item.id === task.id)
      ),
    [tasks, todaysTasks, user?.id]
  );

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    if (!form.title.trim()) {
      toast.error('What did you work on? Add a task title.');
      return;
    }

    try {
      await createTask.mutateAsync({
        title: form.title.trim(),
        description: form.description.trim() || undefined,
        priority: form.priority,
        status: form.status,
        teamId: form.teamId || undefined,
        assignedToId: user?.id,
        labels: form.department ? [form.department] : undefined,
        deadline: form.workDate ? `${form.workDate}T00:00:00` : undefined,
      });
      setForm((prev) => ({
        ...prev,
        title: '',
        description: '',
        priority: TaskPriority.MEDIUM,
        status: TaskStatus.IN_PROGRESS,
      }));
      setShowCreateModal(false);
    } catch {
      // toast handled by hook
    }
  };

  return (
    <div className="tms-page -m-6">
      <header className="tms-header tms-header-light">
        <div className="tms-header-inner">
          <div>
            <h1 className="tms-header-title">Daily tasks</h1>
            <p className="tms-header-subtitle">
              Tasks your admin assigns appear here. You can also log your own daily work.
            </p>
          </div>
        </div>
      </header>

      <div className="tms-body space-y-6">
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          <div className="tms-stat-card">
            <span className="tms-stat-label tms-stat-label-pending">Assigned to you</span>
            <span className="tms-stat-value">{openAssigned.length}</span>
          </div>
          <div className="tms-stat-card">
            <span className="tms-stat-label tms-stat-label-progress">Today's log</span>
            <span className="tms-stat-value">{todaysTasks.length}</span>
          </div>
          <div className="tms-stat-card">
            <span className="tms-stat-label tms-stat-label-completed">Done today</span>
            <span className="tms-stat-value">
              {todaysTasks.filter((task) => task.status === TaskStatus.DONE).length}
            </span>
          </div>
        </div>

        <section id="new-task-form" className="tms-panel">
          <div className="flex items-center justify-between gap-3 mb-4">
            <h2 className="tms-panel-title m-0">Upload today's work</h2>
            <button type="button" className="btn btn-primary" onClick={() => setShowCreateModal(true)}>
              Log a task
            </button>
          </div>
          <p className="text-charcoal-muted">
            Use <strong>New Task</strong> in the sidebar or <strong>Log a task</strong> to add what you worked on today.
          </p>
        </section>

        <TaskList
          id="assigned-tasks"
          title="Assigned to you"
          empty="No tasks assigned by an admin yet."
          tasks={assignedTasks}
          loading={isLoading}
          showAssigner
          onStatusChange={(id, status) => changeStatus.mutate({ id, status })}
        />
        <TaskList
          title="Today's log"
          empty="Nothing logged for today yet."
          tasks={todaysTasks}
          loading={false}
          onStatusChange={(id, status) => changeStatus.mutate({ id, status })}
        />
        <TaskList
          title="Earlier days"
          empty="No earlier tasks."
          tasks={earlierTasks}
          loading={false}
          onStatusChange={(id, status) => changeStatus.mutate({ id, status })}
        />
      </div>

      {showCreateModal && (
        <div className="modal-overlay" onClick={() => setShowCreateModal(false)}>
          <div className="modal p-6 w-full max-w-lg" onClick={(event) => event.stopPropagation()}>
            <h2 className="font-display text-2xl text-charcoal mb-6">Log today's work</h2>
            <form onSubmit={handleSubmit} className="space-y-4">
              <div>
                <label className="tms-label">Work date</label>
                <input
                  type="date"
                  className="input"
                  value={form.workDate}
                  onChange={(e) => setForm((prev) => ({ ...prev, workDate: e.target.value }))}
                />
              </div>
              {departments.length > 0 && (
                <div>
                  <label className="tms-label">Department</label>
                  <select
                    className="input"
                    value={form.department}
                    onChange={(e) => setForm((prev) => ({ ...prev, department: e.target.value }))}
                  >
                    <option value="">Select department</option>
                    {departments.map((dept) => (
                      <option key={dept} value={dept}>{dept}</option>
                    ))}
                  </select>
                </div>
              )}
              <div>
                <label className="tms-label">Team</label>
                <select
                  className="input"
                  value={form.teamId}
                  onChange={(e) => setForm((prev) => ({ ...prev, teamId: e.target.value }))}
                >
                  {teams.length === 0 && <option value="">No teams yet</option>}
                  {teams.map((team) => (
                    <option key={team.id} value={team.id}>{team.name}</option>
                  ))}
                </select>
              </div>
              <div>
                <label className="tms-label">What did you work on?</label>
                <input
                  id="new-task-title"
                  autoFocus
                  className="input"
                  placeholder="e.g. Reviewed API tests, wrote daily report"
                  value={form.title}
                  onChange={(e) => setForm((prev) => ({ ...prev, title: e.target.value }))}
                />
              </div>
              <div>
                <label className="tms-label">Details</label>
                <textarea
                  className="input min-h-[88px] resize-y"
                  placeholder="Add notes, blockers, or results from today"
                  rows={3}
                  value={form.description}
                  onChange={(e) => setForm((prev) => ({ ...prev, description: e.target.value }))}
                />
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="tms-label">Priority</label>
                  <select
                    className="input"
                    value={form.priority}
                    onChange={(e) => setForm((prev) => ({ ...prev, priority: e.target.value as TaskPriority }))}
                  >
                    {Object.values(TaskPriority).map((priority) => (
                      <option key={priority} value={priority}>{PRIORITY_LABELS[priority]}</option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="tms-label">Status</label>
                  <select
                    className="input"
                    value={form.status}
                    onChange={(e) => setForm((prev) => ({ ...prev, status: e.target.value as TaskStatus }))}
                  >
                    {Object.values(TaskStatus).map((status) => (
                      <option key={status} value={status}>{STATUS_LABELS[status]}</option>
                    ))}
                  </select>
                </div>
              </div>
              <div className="flex justify-end gap-3 pt-2">
                <button type="button" className="btn btn-secondary" onClick={() => setShowCreateModal(false)}>
                  Cancel
                </button>
                <button type="submit" className="btn btn-primary" disabled={createTask.isPending}>
                  {createTask.isPending ? 'Saving…' : 'Save task'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}

function TaskList({
  id,
  title,
  empty,
  tasks,
  loading,
  showAssigner,
  onStatusChange,
}: {
  id?: string;
  title: string;
  empty: string;
  tasks: Task[];
  loading: boolean;
  showAssigner?: boolean;
  onStatusChange: (id: string, status: TaskStatus) => void;
}) {
  return (
    <section id={id} className="tms-panel tms-table-panel overflow-hidden p-0">
      <div className="px-5 py-4 border-b border-gray-100">
        <h2 className="tms-panel-title m-0">{title} ({tasks.length})</h2>
      </div>
      {loading ? (
        <div className="flex items-center justify-center py-16">
          <span className="material-symbols-outlined text-[40px] text-charcoal-light animate-spin">refresh</span>
        </div>
      ) : tasks.length === 0 ? (
        <p className="text-center text-charcoal-muted py-12">{empty}</p>
      ) : (
        <div className="overflow-x-auto">
          <table className="tms-table w-full">
            <thead>
              <tr>
                <th>Task</th>
                {showAssigner && <th>Assigned by</th>}
                <th>Department</th>
                <th>Date</th>
                <th>Priority</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {tasks.map((task) => (
                <tr key={task.id} className="tms-table-row">
                  <td>
                    <p className="font-semibold">{task.title}</p>
                    <p className="text-sm text-charcoal-muted truncate max-w-xs">{task.description || '—'}</p>
                  </td>
                  {showAssigner && <td>{task.createdBy?.name || 'Admin'}</td>}
                  <td>{task.assignedTo?.department || '—'}</td>
                  <td>{formatDate(task.deadline || task.createdAt)}</td>
                  <td><span className={priorityBadgeClass(task.priority)}>{PRIORITY_LABELS[task.priority]}</span></td>
                  <td>
                    <select
                      className="input py-1 px-2 text-sm w-auto min-w-[140px]"
                      value={task.status}
                      onChange={(e) => onStatusChange(task.id, e.target.value as TaskStatus)}
                    >
                      {Object.values(TaskStatus).map((status) => (
                        <option key={status} value={status}>{STATUS_LABELS[status]}</option>
                      ))}
                    </select>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

