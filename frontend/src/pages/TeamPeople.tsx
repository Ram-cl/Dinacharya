import { FormEvent, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuthStore } from '@/store/authStore';
import { EmployeeStatus, User } from '@/types';
import {
  useDeleteMember,
  useDirectoryDepartments,
  useEnrollMember,
  useUpdateMember,
  useUsers,
} from '@/hooks/useUsers';
import { toast } from 'react-toastify';
import { DEFAULT_DEPARTMENT, DEPARTMENTS } from '@/constants/departments';

const PAGE_SIZE = 8;
const STATUS_OPTIONS: { value: EmployeeStatus; label: string }[] = [
  { value: EmployeeStatus.ACTIVE, label: 'Active' },
  { value: EmployeeStatus.ONBOARDING, label: 'Onboarding' },
  { value: EmployeeStatus.AWAY, label: 'Away' },
];

const EMPTY_FORM = {
  name: '',
  professionalRole: '',
  email: '',
  githubProfile: '',
  department: DEFAULT_DEPARTMENT,
  joiningDate: new Date().toISOString().slice(0, 10),
};

function statusOf(user: User): EmployeeStatus {
  return user.employeeStatus || EmployeeStatus.ACTIVE;
}

function statusClass(status: EmployeeStatus) {
  switch (status) {
    case EmployeeStatus.ONBOARDING:
      return 'tms-badge tms-badge-status-onboarding';
    case EmployeeStatus.AWAY:
      return 'tms-badge tms-badge-status-away';
    default:
      return 'tms-badge tms-badge-status-active';
  }
}

function githubUsername(value?: string) {
  if (!value) return '';
  return value
    .trim()
    .replace(/^https?:\/\//i, '')
    .replace(/^www\./i, '')
    .replace(/^github\.com\//i, '')
    .replace(/^@/, '')
    .split('/')[0];
}

function githubHref(value?: string) {
  const username = githubUsername(value);
  return username ? `https://github.com/${username}` : '';
}

function githubLabel(value?: string) {
  const username = githubUsername(value);
  return username ? `github.com/${username}` : '';
}

export default function TeamPeople() {
  const navigate = useNavigate();
  const currentUser = useAuthStore((state) => state.user);
  const { data: usersPage, isLoading } = useUsers(0, 200);
  const { data: departmentsFromApi } = useDirectoryDepartments();
  const enroll = useEnrollMember();
  const updateMember = useUpdateMember();
  const deleteMember = useDeleteMember();
  const [form, setForm] = useState(EMPTY_FORM);
  const [page, setPage] = useState(1);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [draft, setDraft] = useState({
    professionalRole: '',
    email: '',
    githubProfile: '',
    employeeStatus: EmployeeStatus.ACTIVE,
    joiningDate: '',
  });

  const employees = useMemo(
    () =>
      [...(usersPage?.content || [])]
        .filter((user) => user.isActive !== false)
        .sort((a, b) => a.name.localeCompare(b.name)),
    [usersPage]
  );

  const departments = useMemo(() => {
    const merged = new Set([
      ...DEPARTMENTS,
      ...(departmentsFromApi || []),
      ...employees.map((user) => user.department).filter(Boolean) as string[],
    ]);
    return Array.from(merged);
  }, [departmentsFromApi, employees]);

  const totalPages = Math.max(1, Math.ceil(employees.length / PAGE_SIZE));
  const currentPage = Math.min(page, totalPages);
  const pageEmployees = employees.slice((currentPage - 1) * PAGE_SIZE, currentPage * PAGE_SIZE);

  const startEdit = (person: User) => {
    setEditingId(person.id);
    setDraft({
      professionalRole: person.professionalRole || '',
      email: person.email,
      githubProfile: githubUsername(person.githubProfile),
      employeeStatus: statusOf(person),
      joiningDate: person.joiningDate || '',
    });
  };

  const cancelEdit = () => {
    setEditingId(null);
  };

  const saveEdit = async (userId: string) => {
    if (!draft.email.trim()) {
      toast.error('Work email is required');
      return;
    }
    try {
      await updateMember.mutateAsync({
        userId,
        data: {
          professionalRole: draft.professionalRole.trim(),
          email: draft.email.trim(),
          githubProfile: draft.githubProfile.trim(),
          employeeStatus: draft.employeeStatus,
          joiningDate: draft.joiningDate || undefined,
        },
      });
      setEditingId(null);
    } catch {
      // toast handled by hook
    }
  };

  const handleEnroll = async (event: FormEvent) => {
    event.preventDefault();
    if (!form.name.trim() || !form.email.trim() || !form.department.trim()) {
      toast.error('Full name, work email, and department are required');
      return;
    }

    try {
      await enroll.mutateAsync({
        name: form.name.trim(),
        email: form.email.trim(),
        professionalRole: form.professionalRole.trim() || undefined,
        githubProfile: form.githubProfile.trim() || undefined,
        department: form.department.trim(),
        joiningDate: form.joiningDate || undefined,
      });
      setForm({ ...EMPTY_FORM, department: form.department });
    } catch {
      // toast handled by hook
    }
  };

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-full">
        <span className="material-symbols-outlined text-[48px] text-charcoal-light animate-spin">refresh</span>
      </div>
    );
  }

  return (
    <div className="tms-page -m-6">
      <header className="tms-header tms-header-light">
        <div className="tms-header-inner">
          <div>
            <h1 className="tms-header-title">Employee Directory</h1>
            <p className="tms-header-subtitle">Enroll new teammates and manage the Employees</p>
          </div>
        </div>
      </header>

      <div className="tms-body">
        <div className="grid grid-cols-1 xl:grid-cols-[360px_minmax(0,1fr)] gap-6 items-start">
          <section className="tms-panel">
              <h2 className="tms-panel-title">Add New Employee</h2>
              <form onSubmit={handleEnroll} className="flex flex-col gap-4">
                <div>
                  <label className="tms-label">Full Name</label>
                  <input
                    className="input"
                    placeholder="e.g. Alex Vance"
                    value={form.name}
                    onChange={(e) => setForm((prev) => ({ ...prev, name: e.target.value }))}
                  />
                </div>
                <div>
                  <label className="tms-label">Professional Role</label>
                  <input
                    className="input"
                    placeholder="e.g. Senior Developer"
                    value={form.professionalRole}
                    onChange={(e) => setForm((prev) => ({ ...prev, professionalRole: e.target.value }))}
                  />
                </div>
                <div>
                  <label className="tms-label">Work Email</label>
                  <input
                    className="input"
                    type="text"
                    placeholder="alex.v@dinacharya.io"
                    value={form.email}
                    onChange={(e) => setForm((prev) => ({ ...prev, email: e.target.value }))}
                  />
                </div>
                <div>
                  <label className="tms-label">GitHub Profile</label>
                  <div className="relative">
                    <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-[18px] text-charcoal-muted">
                      code
                    </span>
                    <input
                      className="input pl-10"
                      placeholder="username or github.com/username"
                      value={form.githubProfile}
                      onChange={(e) => setForm((prev) => ({ ...prev, githubProfile: e.target.value }))}
                    />
                  </div>
                </div>
                <div>
                  <label className="tms-label">Department</label>
                  <select
                    className="input"
                    value={form.department}
                    onChange={(e) => setForm((prev) => ({ ...prev, department: e.target.value }))}
                  >
                    {departments.map((dept) => (
                      <option key={dept} value={dept}>{dept}</option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="tms-label">Joining Date</label>
                  <input
                    className="input"
                    type="date"
                    value={form.joiningDate}
                    onChange={(e) => setForm((prev) => ({ ...prev, joiningDate: e.target.value }))}
                  />
                </div>
                <button type="submit" className="dir-enroll-btn" disabled={enroll.isPending}>
                  {enroll.isPending ? 'Enrolling…' : '+ Enroll Member'}
                </button>
              </form>
            </section>

          <section className="tms-table-panel overflow-hidden">
            <div className="px-5 py-4 border-b border-gray-100 flex items-center justify-between">
              <h2 className="tms-panel-title m-0">Active Employees ({employees.length})</h2>
            </div>
            <div className="overflow-x-auto">
              <table className="tms-table">
                <thead>
                  <tr>
                    <th>Employee</th>
                    <th>Contact</th>
                    <th>Status</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {pageEmployees.length === 0 ? (
                    <tr>
                      <td colSpan={4} className="text-center py-12 text-charcoal-muted">
                        No employees yet. Enroll the first member to build the roster.
                      </td>
                    </tr>
                  ) : (
                    pageEmployees.map((person) => {
                      const status = statusOf(person);
                      const editing = editingId === person.id;
                      return (
                        <tr key={person.id} className="tms-table-row">
                          <td>
                            <div className="flex items-center gap-3">
                              {person.profilePicture ? (
                                <img
                                  src={person.profilePicture}
                                  alt=""
                                  className="w-10 h-10 rounded-full object-cover"
                                />
                              ) : (
                                <div className="w-10 h-10 rounded-full bg-violet-100 text-violet-700 flex items-center justify-center font-semibold">
                                  {person.name.charAt(0).toUpperCase()}
                                </div>
                              )}
                              <div className="min-w-0 flex-1">
                                {editing ? (
                                  <p className="font-semibold text-charcoal truncate">{person.name}</p>
                                ) : (
                                  <Link
                                    to={`/performance/${person.id}`}
                                    className="font-semibold text-charcoal truncate hover:text-terracotta hover:underline block"
                                  >
                                    {person.name}
                                  </Link>
                                )}
                                {editing ? (
                                  <input
                                    className="input py-1 px-2 text-sm mt-1"
                                    placeholder="Professional role"
                                    value={draft.professionalRole}
                                    onChange={(e) => setDraft((prev) => ({ ...prev, professionalRole: e.target.value }))}
                                  />
                                ) : (
                                  <p className="dir-role truncate">
                                    {person.professionalRole || person.department || person.role}
                                  </p>
                                )}
                              </div>
                            </div>
                          </td>
                          <td>
                            {editing ? (
                              <div className="dir-contact gap-2">
                                <input
                                  className="input py-1 px-2 text-sm"
                                  placeholder="Work email"
                                  value={draft.email}
                                  onChange={(e) => setDraft((prev) => ({ ...prev, email: e.target.value }))}
                                />
                                <input
                                  className="input py-1 px-2 text-sm"
                                  placeholder="GitHub username"
                                  value={draft.githubProfile}
                                  onChange={(e) => setDraft((prev) => ({ ...prev, githubProfile: e.target.value }))}
                                />
                              </div>
                            ) : (
                              <div className="dir-contact">
                                <span className="inline-flex items-center gap-1">
                                  <span className="material-symbols-outlined text-[16px]">mail</span>
                                  {person.email}
                                </span>
                                <span className="inline-flex items-center gap-1">
                                  <span className="material-symbols-outlined text-[16px]">code</span>
                                  {githubLabel(person.githubProfile) ? (
                                    <a
                                      href={githubHref(person.githubProfile)}
                                      target="_blank"
                                      rel="noreferrer"
                                    >
                                      {githubLabel(person.githubProfile)}
                                    </a>
                                  ) : (
                                    <span className="text-charcoal-light">No GitHub</span>
                                  )}
                                </span>
                              </div>
                            )}
                          </td>
                          <td>
                            {editing ? (
                              <div className="flex flex-col gap-2">
                                <select
                                  className="input py-1 px-2 text-sm min-w-[140px]"
                                  value={draft.employeeStatus}
                                  onChange={(e) =>
                                    setDraft((prev) => ({
                                      ...prev,
                                      employeeStatus: e.target.value as EmployeeStatus,
                                    }))
                                  }
                                >
                                  {STATUS_OPTIONS.map((option) => (
                                    <option key={option.value} value={option.value}>
                                      {option.label}
                                    </option>
                                  ))}
                                </select>
                                <input
                                  className="input py-1 px-2 text-sm"
                                  type="date"
                                  title="Joining date"
                                  value={draft.joiningDate}
                                  onChange={(e) => setDraft((prev) => ({ ...prev, joiningDate: e.target.value }))}
                                />
                              </div>
                            ) : (
                              <div className="flex flex-col gap-1">
                                <span className={statusClass(status)}>{status}</span>
                                {person.joiningDate && (
                                  <span className="text-xs text-charcoal-muted">
                                    Joined {new Date(person.joiningDate).toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' })}
                                  </span>
                                )}
                              </div>
                            )}
                          </td>
                          <td>
                            <div className="flex items-center gap-2">
                              {editing ? (
                                <>
                                  <button
                                    type="button"
                                    className="tms-action-btn tms-action-complete"
                                    title="Save"
                                    disabled={updateMember.isPending}
                                    onClick={() => saveEdit(person.id)}
                                  >
                                    <span className="material-symbols-outlined text-[16px]">check</span>
                                  </button>
                                  <button
                                    type="button"
                                    className="tms-action-btn tms-action-progress"
                                    title="Cancel"
                                    onClick={cancelEdit}
                                  >
                                    <span className="material-symbols-outlined text-[16px]">close</span>
                                  </button>
                                </>
                              ) : (
                                <>
                                <button
                                  type="button"
                                  className="tms-action-btn tms-action-remark"
                                  title="Edit employee"
                                  onClick={() => startEdit(person)}
                                >
                                  <span className="material-symbols-outlined text-[16px]">edit</span>
                                </button>
                                <button
                                  type="button"
                                  className="tms-action-btn tms-action-remark"
                                  title="View attendance"
                                  onClick={() => navigate(`/attendance/${person.id}`)}
                                >
                                  <span className="material-symbols-outlined text-[16px]">calendar_month</span>
                                </button>
                                </>
                              )}
                              <button
                                type="button"
                                className="tms-action-btn tms-action-delete"
                                title="Delete member"
                                disabled={deleteMember.isPending || person.id === currentUser?.id}
                                onClick={() => {
                                  if (person.id === currentUser?.id) {
                                    toast.error('You cannot delete your own account');
                                    return;
                                  }
                                  if (window.confirm(`Delete ${person.name} from the directory?`)) {
                                    deleteMember.mutate(person.id);
                                  }
                                }}
                              >
                                <span className="material-symbols-outlined text-[16px]">delete</span>
                              </button>
                            </div>
                          </td>
                        </tr>
                      );
                    })
                  )}
                </tbody>
              </table>
            </div>
            <div className="tms-table-footer">
              <span className="text-sm text-charcoal-muted">
                Page {currentPage} of {totalPages}
              </span>
              <div className="tms-pagination-controls">
                {Array.from({ length: totalPages }, (_, index) => index + 1).map((pageNumber) => (
                  <button
                    key={pageNumber}
                    type="button"
                    className={`tms-page-btn ${pageNumber === currentPage ? 'is-current' : ''}`}
                    onClick={() => setPage(pageNumber)}
                  >
                    {pageNumber}
                  </button>
                ))}
              </div>
            </div>
          </section>
        </div>
      </div>
    </div>
  );
}
