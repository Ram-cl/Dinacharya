import { useMemo, useState, useEffect } from 'react';
import { useLocation } from 'react-router-dom';
import { useAuthStore } from '@/store/authStore';
import { useTasks, useCreateTask, useUpdateTask, useUpdateTaskStatus, useDeleteTask, useDeleteAllTasks } from '@/hooks/useTasks';
import { useUsers, useDepartments } from '@/hooks/useUsers';
import { useTeams, useTeam } from '@/hooks/useTeams';
import { Task, TaskPriority, TaskStatus, User } from '@/types';
import { toast } from 'react-toastify';
import TaskImport from '@/components/TaskImport';

interface TaskFormState {
  assignedToId: string;
  department: string;
  deadline: string;
  title: string;
  description: string;
  priority: TaskPriority;
  status: TaskStatus;
  teamId: string;
}

const EMPTY_FORM: TaskFormState = {
  assignedToId: '',
  department: '',
  deadline: '',  // Will be set dynamically
  title: '',
  description: '',
  priority: TaskPriority.MEDIUM,
  status: TaskStatus.TODO,
  teamId: '',
};

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

const PAGE_SIZE_OPTIONS = [5, 10, 20, 50];

function isOverdue(task: Task) {
  if (!task.deadline || task.status === TaskStatus.DONE) return false;
  return new Date(task.deadline) < new Date();
}

function formatDate(value?: string) {
  if (!value) return '—';
  const date = new Date(value);
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const target = new Date(date);
  target.setHours(0, 0, 0, 0);
  const diff = (target.getTime() - today.getTime()) / (1000 * 60 * 60 * 24);
  if (diff === 0) return 'Today';
  if (diff === 1) return 'Tomorrow';
  if (diff === -1) return 'Yesterday';
  return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
}

function formatDateKey(value?: string) {
  if (!value) return '';
  const date = new Date(value);
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function priorityBadgeClass(priority: TaskPriority) {
  switch (priority) {
    case TaskPriority.URGENT:
    case TaskPriority.HIGH:
      return 'tms-badge tms-badge-priority-high';
    case TaskPriority.MEDIUM:
      return 'tms-badge tms-badge-priority-medium';
    default:
      return 'tms-badge tms-badge-priority-low';
  }
}

function statusBadgeClass(status: TaskStatus) {
  switch (status) {
    case TaskStatus.TODO:
      return 'tms-badge tms-badge-status-pending';
    case TaskStatus.IN_PROGRESS:
      return 'tms-badge tms-badge-status-progress';
    case TaskStatus.IN_REVIEW:
      return 'tms-badge tms-badge-status-review';
    case TaskStatus.DONE:
      return 'tms-badge tms-badge-status-done';
    default:
      return 'tms-badge tms-badge-neutral';
  }
}

function priorityAccent(priority: TaskPriority) {
  switch (priority) {
    case TaskPriority.URGENT:
    case TaskPriority.HIGH:
      return 'border-l-error';
    case TaskPriority.MEDIUM:
      return 'border-l-warning';
    default:
      return 'border-l-success';
  }
}

function uniqueUsers(users: User[]) {
  const seen = new Set<string>();
  return users.filter((user) => {
    if (seen.has(user.id)) return false;
    seen.add(user.id);
    return true;
  });
}

// Helper to get today's date in YYYY-MM-DD format (local timezone)
function getTodayDate() {
  const today = new Date();
  const year = today.getFullYear();
  const month = String(today.getMonth() + 1).padStart(2, '0');
  const day = String(today.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

export default function TaskManagement() {
  const user = useAuthStore((s) => s.user);
  const location = useLocation();
  const isModerator = user?.role === 'MODERATOR' || user?.role === 'ADMIN';

  const [form, setForm] = useState<TaskFormState>({ ...EMPTY_FORM, deadline: getTodayDate() });
  const [filterEmployee, setFilterEmployee] = useState('');
  const [filterDepartment, setFilterDepartment] = useState('');
  const [filterDate, setFilterDate] = useState('');  // No default date filter — show all tasks
  const [filterStatus, setFilterStatus] = useState('');
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [remarkTaskId, setRemarkTaskId] = useState<string | null>(null);
  const [remarkText, setRemarkText] = useState('');
  const [descriptionTaskId, setDescriptionTaskId] = useState<string | null>(null);
  const [descriptionText, setDescriptionText] = useState('');
  const [showDeleteAllModal, setShowDeleteAllModal] = useState(false);

  const { data: tasksPage, isLoading } = useTasks({ size: 1000 });
  const { data: usersPage } = useUsers();
  const { data: departmentsList = [] } = useDepartments();
  const { data: teamsPage } = useTeams(0, 50);

  const createTask = useCreateTask();
  const deleteTask = useDeleteTask();
  const deleteAllTasks = useDeleteAllTasks();

  const tasks = tasksPage?.content || [];
  const users = usersPage?.content || [];
  const teams = teamsPage?.content || [];
  const selectedTeamId = form.teamId || teams[0]?.id || '';
  const { data: selectedTeam } = useTeam(selectedTeamId);

  const assignableUsers = useMemo(
    () => uniqueUsers([
      ...(selectedTeam?.members || []),
      ...(selectedTeam?.lead ? [selectedTeam.lead] : []),
      ...users,
    ]),
    [selectedTeam, users]
  );

  const departments = useMemo(() => {
    const fromUsers = users
      .map((u) => u.department)
      .filter((d): d is string => Boolean(d));
    return [...new Set([...departmentsList, ...fromUsers])].sort();
  }, [departmentsList, users]);

  const formUsers = useMemo(() => {
    if (!form.department) return assignableUsers;
    return assignableUsers.filter((u) => u.department === form.department);
  }, [assignableUsers, form.department]);

  useEffect(() => {
    if (form.department && teams.length > 0) {
      const match = teams.find(
        (t) => t.name.toLowerCase() === form.department.toLowerCase()
      );
      if (match && form.teamId !== match.id) {
        setForm((prev) => ({ ...prev, teamId: match.id }));
      }
      return;
    }
    if (teams.length > 0 && !form.teamId) {
      setForm((prev) => ({ ...prev, teamId: teams[0].id }));
    }
  }, [teams, form.teamId, form.department]);

  useEffect(() => {
    if (!(location.state as { openNewTask?: number } | null)?.openNewTask) return;
    const timer = window.setTimeout(() => {
      document.getElementById('new-task-form')?.scrollIntoView({ behavior: 'smooth', block: 'center' });
      document.getElementById('new-task-title')?.focus();
    }, 50);
    return () => window.clearTimeout(timer);
  }, [location.state]);

  const filteredTasks = useMemo(() => {
    return tasks.filter((task) => {
      if (filterEmployee && task.assignedTo?.id !== filterEmployee) return false;
      if (filterDepartment && task.assignedTo?.department !== filterDepartment) return false;
      if (filterDate && formatDateKey(task.deadline) !== filterDate) return false;
      if (filterStatus && task.status !== filterStatus) return false;
      return true;
    });
  }, [tasks, filterEmployee, filterDepartment, filterDate, filterStatus]);

  const stats = useMemo(() => ({
    pending: filteredTasks.filter((t) => t.status === TaskStatus.TODO).length,
    inProgress: filteredTasks.filter((t) => t.status === TaskStatus.IN_PROGRESS || t.status === TaskStatus.IN_REVIEW).length,
    completed: filteredTasks.filter((t) => t.status === TaskStatus.DONE).length,
    overdue: filteredTasks.filter(isOverdue).length,
  }), [filteredTasks]);

  const totalPages = Math.max(1, Math.ceil(filteredTasks.length / pageSize));
  const currentPage = Math.min(page, totalPages);

  const paginatedTasks = useMemo(() => {
    const start = (currentPage - 1) * pageSize;
    return filteredTasks.slice(start, start + pageSize);
  }, [filteredTasks, currentPage, pageSize]);

  useEffect(() => {
    setPage(1);
  }, [filterEmployee, filterDepartment, filterDate, filterStatus, pageSize]);

  useEffect(() => {
    if (page > totalPages) {
      setPage(totalPages);
    }
  }, [page, totalPages]);

  const rangeStart = filteredTasks.length === 0 ? 0 : (currentPage - 1) * pageSize + 1;
  const rangeEnd = Math.min(currentPage * pageSize, filteredTasks.length);

  const handleFormChange = (field: keyof TaskFormState, value: string) => {
    setForm((prev) => {
      const next = { ...prev, [field]: value };

      if (field === 'assignedToId') {
        const selected = assignableUsers.find((u) => u.id === value);
        next.department = selected?.department || '';
      }

      if (field === 'department' && value && prev.assignedToId) {
        const selected = assignableUsers.find((u) => u.id === prev.assignedToId);
        if (selected?.department !== value) {
          next.assignedToId = '';
        }
      }

      return next;
    });
  };

  const handleAddTask = async (e: React.FormEvent) => {
    e.preventDefault();
    const departmentTeam = form.department
      ? teams.find((t) => t.name.toLowerCase() === form.department.toLowerCase())
      : undefined;
    const teamId = form.teamId || departmentTeam?.id || teams[0]?.id;
    if (!form.title.trim()) {
      toast.error('Task title is required');
      return;
    }
    if (!teamId) {
      toast.error('Select a department (each department is a team)');
      return;
    }

    try {
      await createTask.mutateAsync({
        title: form.title.trim(),
        description: form.description.trim() || undefined,
        priority: form.priority,
        status: form.status,
        teamId,
        assignedToId: form.assignedToId || undefined,
        deadline: form.deadline ? `${form.deadline}T00:00:00` : undefined,
      });

      setForm({
        ...EMPTY_FORM,
        teamId,
        deadline: getTodayDate(),
        priority: TaskPriority.MEDIUM,
        status: TaskStatus.TODO,
      });
    } catch {
      // Error toast is shown by useCreateTask
    }
  };

  const openRemark = (task: Task) => {
    setRemarkTaskId(task.id);
    setRemarkText(''); // Start with empty remark
  };

  const openDescription = (task: Task) => {
    setDescriptionTaskId(task.id);
    setDescriptionText(task.description || '');
  };

  return (
    <div className="tms-page -m-6">
      <header className="tms-header tms-header-light">
        <div className="tms-header-inner">
          <div>
            <h1 className="tms-header-title">Task Management System</h1>
            <p className="tms-header-subtitle">Manage and track all employee tasks across teams</p>
          </div>
          {isModerator && (
            <span className="tms-moderator-badge">
              <span className="material-symbols-outlined text-[16px]">verified_user</span>
              Moderator
            </span>
          )}
        </div>
      </header>

      <div className="tms-body space-y-6">
        <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
          <div className="tms-stat-card">
            <span className="tms-stat-label tms-stat-label-pending">Pending Tasks</span>
            <span className="tms-stat-value">{stats.pending}</span>
          </div>
          <div className="tms-stat-card">
            <span className="tms-stat-label tms-stat-label-progress">In Progress</span>
            <span className="tms-stat-value">{stats.inProgress}</span>
          </div>
          <div className="tms-stat-card">
            <span className="tms-stat-label tms-stat-label-completed">Completed</span>
            <span className="tms-stat-value">{stats.completed}</span>
          </div>
          <div className="tms-stat-card">
            <span className="tms-stat-label tms-stat-label-overdue">Overdue</span>
            <span className="tms-stat-value">{stats.overdue}</span>
          </div>
        </div>

        {/* Task Import — rows go to the department team from the sheet (ASE, UI, …) */}
        <section className="tms-panel">
            <div className="flex justify-between items-center mb-4">
              <h2 className="tms-panel-title">Import Tasks</h2>
              {isModerator && (
                <button
                  type="button"
                  className="btn btn-danger text-sm flex items-center gap-1.5"
                  onClick={() => setShowDeleteAllModal(true)}
                  title="Delete all tasks in the system (Admin only)"
                >
                  <span className="material-symbols-outlined text-[14px] leading-none">delete_sweep</span>
                  Delete All Tasks
                </button>
              )}
            </div>
            <TaskImport
              teamId={selectedTeamId}
              onImportSuccess={(result) => {
                toast.success(`Successfully imported ${result.successCount} task(s)!`);
                if (result.failureCount > 0) {
                  toast.warning(`${result.failureCount} task(s) failed to import. Check console for details.`);
                  console.error('Import errors:', result.errors);
                }
                setTimeout(() => window.location.reload(), 1500);
              }}
            />
        </section>

        <section id="new-task-form" className="tms-panel">
          <h2 className="tms-panel-title">Add New Task</h2>
          <form onSubmit={handleAddTask} className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label className="tms-label">Department</label>
              <select
                className="input"
                value={form.department}
                onChange={(e) => handleFormChange('department', e.target.value)}
              >
                <option value="">Select Department</option>
                {departments.map((dept) => (
                  <option key={dept} value={dept}>{dept}</option>
                ))}
              </select>
            </div>
            <div>
              <label className="tms-label">Employee</label>
              <select
                className="input"
                value={form.assignedToId}
                onChange={(e) => handleFormChange('assignedToId', e.target.value)}
              >
                <option value="">Select Employee</option>
                {formUsers.map((u) => (
                  <option key={u.id} value={u.id}>
                    {u.name}{u.department ? ` (${u.department})` : ''}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="tms-label">Date</label>
              <input
                type="date"
                className="input"
                value={form.deadline}
                onChange={(e) => handleFormChange('deadline', e.target.value)}
              />
            </div>
            <div className="md:col-span-2">
              <label className="tms-label">Task Title</label>
              <input
                id="new-task-title"
                type="text"
                className="input"
                placeholder="Enter task title"
                value={form.title}
                onChange={(e) => handleFormChange('title', e.target.value)}
              />
            </div>
            <div className="md:col-span-2">
              <label className="tms-label">Description</label>
              <textarea
                className="input min-h-[88px] resize-y"
                placeholder="Enter task description"
                rows={3}
                value={form.description}
                onChange={(e) => handleFormChange('description', e.target.value)}
              />
            </div>
            <div>
              <label className="tms-label">Priority</label>
              <select
                className="input"
                value={form.priority}
                onChange={(e) => handleFormChange('priority', e.target.value)}
              >
                {Object.values(TaskPriority).map((p) => (
                  <option key={p} value={p}>{PRIORITY_LABELS[p]}</option>
                ))}
              </select>
            </div>
            <div>
              <label className="tms-label">Status</label>
              <select
                className="input"
                value={form.status}
                onChange={(e) => handleFormChange('status', e.target.value)}
              >
                {Object.values(TaskStatus).map((s) => (
                  <option key={s} value={s}>{STATUS_LABELS[s]}</option>
                ))}
              </select>
            </div>
            <div className="md:col-span-2 flex justify-end">
              <button type="submit" className="btn btn-primary px-6" disabled={createTask.isPending}>
                {createTask.isPending ? 'Adding...' : 'Add Task'}
              </button>
            </div>
          </form>
        </section>

        <div className="flex flex-wrap gap-3">
          <select className="input w-auto min-w-[160px]" value={filterEmployee} onChange={(e) => setFilterEmployee(e.target.value)}>
            <option value="">All Employees</option>
            {users.map((u) => (
              <option key={u.id} value={u.id}>{u.name}</option>
            ))}
          </select>
          <select className="input w-auto min-w-[160px]" value={filterDepartment} onChange={(e) => setFilterDepartment(e.target.value)}>
            <option value="">All Departments</option>
            {departments.map((dept) => (
              <option key={dept} value={dept}>{dept}</option>
            ))}
          </select>
          <input
            type="date"
            className="input w-auto min-w-[160px]"
            value={filterDate}
            onChange={(e) => setFilterDate(e.target.value)}
            title="Filter by date"
          />
          {filterDate && (
            <button
              type="button"
              className="btn btn-secondary text-sm"
              onClick={() => setFilterDate('')}
            >
              Clear Date
            </button>
          )}
          <select className="input w-auto min-w-[140px]" value={filterStatus} onChange={(e) => setFilterStatus(e.target.value)}>
            <option value="">All Statuses</option>
            {Object.values(TaskStatus).map((s) => (
              <option key={s} value={s}>{STATUS_LABELS[s]}</option>
            ))}
          </select>
        </div>

        <section className="tms-panel tms-table-panel overflow-hidden p-0">
          {isLoading ? (
            <div className="flex items-center justify-center py-16">
              <span className="material-symbols-outlined text-[40px] text-charcoal-light animate-spin">refresh</span>
            </div>
          ) : filteredTasks.length === 0 ? (
            <p className="text-center text-charcoal-muted py-16">No tasks found. Add a task above to get started.</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="tms-table w-full">
                <thead>
                  <tr>
                    <th>Employee</th>
                    <th>Department</th>
                    <th>Task</th>
                    <th>Date</th>
                    <th>Status</th>
                    <th>Remark</th>
                    <th className="text-right">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {paginatedTasks.map((task) => (
                    <TaskRow
                      key={task.id}
                      task={task}
                      isModerator={isModerator}
                      onDelete={() => {
                        if (confirm(`Delete "${task.title}"? This cannot be undone.`)) {
                          deleteTask.mutate(task.id);
                        }
                      }}
                      onRemark={() => openRemark(task)}
                      onEditDescription={() => openDescription(task)}
                    />
                  ))}
                </tbody>
              </table>
            </div>
          )}
          <div className="tms-table-footer">
            <span>
              Showing {rangeStart}–{rangeEnd} of {filteredTasks.length} tasks
              {filteredTasks.length !== tasks.length && ` (${tasks.length} total)`}
            </span>
            <div className="tms-pagination">
              <label className="tms-pagination-size">
                <span className="sr-only">Rows per page</span>
                <select
                  className="input py-1 px-2 text-sm w-auto min-w-[72px]"
                  value={pageSize}
                  onChange={(e) => setPageSize(Number(e.target.value))}
                >
                  {PAGE_SIZE_OPTIONS.map((size) => (
                    <option key={size} value={size}>{size} / page</option>
                  ))}
                </select>
              </label>
              <div className="tms-pagination-controls">
                <button
                  type="button"
                  className="tms-page-btn"
                  onClick={() => setPage((p) => Math.max(1, p - 1))}
                  disabled={currentPage <= 1}
                  aria-label="Previous page"
                >
                  <span className="material-symbols-outlined text-[18px]">chevron_left</span>
                </button>
                <span className="tms-page-indicator">
                  Page {currentPage} of {totalPages}
                </span>
                <button
                  type="button"
                  className="tms-page-btn"
                  onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
                  disabled={currentPage >= totalPages}
                  aria-label="Next page"
                >
                  <span className="material-symbols-outlined text-[18px]">chevron_right</span>
                </button>
              </div>
            </div>
          </div>
        </section>
      </div>

      {remarkTaskId && (
        <RemarkModal
          taskId={remarkTaskId}
          value={remarkText}
          onChange={setRemarkText}
          onClose={() => setRemarkTaskId(null)}
        />
      )}

      {descriptionTaskId && (
        <DescriptionModal
          taskId={descriptionTaskId}
          value={descriptionText}
          onChange={setDescriptionText}
          onClose={() => setDescriptionTaskId(null)}
        />
      )}

      {showDeleteAllModal && (
        <DeleteAllTasksModal
          isOpen={showDeleteAllModal}
          isLoading={deleteAllTasks.isPending}
          onConfirm={async () => {
            await deleteAllTasks.mutateAsync();
            setShowDeleteAllModal(false);
            setTimeout(() => window.location.reload(), 1000);
          }}
          onClose={() => setShowDeleteAllModal(false)}
        />
      )}
    </div>
  );
}

function TaskRow({
  task,
  isModerator,
  onDelete,
  onRemark,
  onEditDescription,
}: {
  task: Task;
  isModerator: boolean;
  onDelete: () => void;
  onRemark: () => void;
  onEditDescription: () => void;
}) {
  const updateStatus = useUpdateTaskStatus(task.id);
  const overdue = isOverdue(task);

  return (
    <tr className={`tms-table-row border-l-4 ${priorityAccent(task.priority)}`}>
      <td>
        <div className="flex items-center gap-2">
          <div className="avatar-sm bg-charcoal shrink-0">
            {task.assignedTo?.name?.charAt(0).toUpperCase() || '?'}
          </div>
          <span className="text-body-md text-charcoal font-medium" title={task.assignedTo?.name || 'Unassigned'}>
            {task.assignedTo?.name || 'Unassigned'}
          </span>
        </div>
      </td>
      <td>
        {task.assignedTo?.department ? (
          <span className="tms-badge tms-badge-neutral">{task.assignedTo.department}</span>
        ) : (
          <span className="text-charcoal-muted">—</span>
        )}
      </td>
      <td className="font-medium text-charcoal">{task.title}</td>
      <td className={overdue ? 'text-error font-medium' : 'text-charcoal-muted'}>
        {formatDate(task.deadline)}
      </td>
      <td>
        <span className={statusBadgeClass(task.status)}>
          {STATUS_LABELS[task.status]}
        </span>
      </td>
      <td>
        <div className="flex items-center gap-2">
          <span className="text-charcoal-muted max-w-[160px] truncate">
            —
          </span>
          {isModerator && (
            <button
              type="button"
              className="tms-action-btn tms-action-remark shrink-0"
              title="Edit Remark (Admin Only)"
              onClick={onRemark}
            >
              <span className="material-symbols-outlined text-[14px]">edit</span>
            </button>
          )}
        </div>
      </td>
      <td>
        <div className="flex justify-end gap-1">
          <button
            type="button"
            className="tms-action-btn tms-action-complete"
            title="Complete"
            onClick={() => updateStatus.mutate({ status: TaskStatus.DONE })}
          >
            <span className="material-symbols-outlined text-[16px]">check</span>
          </button>
          <button
            type="button"
            className="tms-action-btn tms-action-progress"
            title="Progress"
            onClick={() => updateStatus.mutate({ status: TaskStatus.IN_PROGRESS })}
          >
            <span className="material-symbols-outlined text-[16px]">schedule</span>
          </button>
          <button
            type="button"
            className="tms-action-btn tms-action-delete"
            title="Delete"
            onClick={(event) => {
              event.preventDefault();
              event.stopPropagation();
              onDelete();
            }}
          >
            <span className="material-symbols-outlined text-[16px]">delete</span>
          </button>
        </div>
      </td>
    </tr>
  );
}

function RemarkModal({
  taskId,
  value,
  onChange,
  onClose,
}: {
  taskId: string;
  value: string;
  onChange: (v: string) => void;
  onClose: () => void;
}) {
  const updateTask = useUpdateTask(taskId);

  const handleSave = async () => {
    await updateTask.mutateAsync({ remark: value });
    onClose();
  };

  return (
    <div className="modal-overlay">
      <div className="modal p-6 w-full max-w-md">
        <h3 className="text-headline-sm text-charcoal mb-4">Edit Remark (Admin Only)</h3>
        <textarea
          className="input min-h-[100px] resize-y mb-4"
          placeholder="Enter remark..."
          value={value}
          onChange={(e) => onChange(e.target.value)}
        />
        <div className="flex justify-end gap-3">
          <button type="button" className="btn btn-secondary" onClick={onClose}>Cancel</button>
          <button
            type="button"
            className="btn btn-primary"
            onClick={handleSave}
            disabled={updateTask.isPending}
          >
            {updateTask.isPending ? 'Saving...' : 'Save Remark'}
          </button>
        </div>
      </div>
    </div>
  );
}

function DeleteAllTasksModal({
  isOpen,
  isLoading,
  onConfirm,
  onClose,
}: {
  isOpen: boolean;
  isLoading: boolean;
  onConfirm: () => void;
  onClose: () => void;
}) {
  if (!isOpen) return null;

  return (
    <div className="modal-overlay">
      <div className="modal p-6 w-full max-w-md">
        <h3 className="text-headline-sm text-charcoal mb-4 flex items-center gap-2">
          <span className="material-symbols-outlined text-error text-[24px]">warning</span>
          Delete All Tasks?
        </h3>
        <p className="text-body-md text-charcoal-muted mb-6">
          This action will permanently delete ALL tasks in the system. This cannot be undone.
        </p>
        <div className="bg-error bg-opacity-10 border border-error border-opacity-20 rounded p-3 mb-6">
          <p className="text-sm text-error font-medium">⚠️ Warning: This is a destructive action</p>
        </div>
        <div className="flex justify-end gap-3">
          <button
            type="button"
            className="btn btn-secondary"
            onClick={onClose}
            disabled={isLoading}
          >
            Cancel
          </button>
          <button
            type="button"
            className="btn btn-danger"
            onClick={onConfirm}
            disabled={isLoading}
          >
            {isLoading ? 'Deleting...' : 'Delete All Tasks'}
          </button>
        </div>
      </div>
    </div>
  );
}
function DescriptionModal({
  taskId,
  value,
  onChange,
  onClose,
}: {
  taskId: string;
  value: string;
  onChange: (v: string) => void;
  onClose: () => void;
}) {
  const updateTask = useUpdateTask(taskId);

  const handleSave = async () => {
    await updateTask.mutateAsync({ description: value });
    onClose();
  };

  return (
    <div className="modal-overlay">
      <div className="modal p-6 w-full max-w-md">
        <h3 className="text-headline-sm text-charcoal mb-4">Edit Description (Admin Only)</h3>
        <textarea
          className="input min-h-[100px] resize-y mb-4"
          placeholder="Enter description..."
          value={value}
          onChange={(e) => onChange(e.target.value)}
        />
        <div className="flex justify-end gap-3">
          <button type="button" className="btn btn-secondary" onClick={onClose}>Cancel</button>
          <button
            type="button"
            className="btn btn-primary"
            onClick={handleSave}
            disabled={updateTask.isPending}
          >
            {updateTask.isPending ? 'Saving...' : 'Save Description'}
          </button>
        </div>
      </div>
    </div>
  );
}
