import { useState, useEffect, useMemo } from 'react';
import { createPortal } from 'react-dom';
import { useSearchParams } from 'react-router-dom';
import {
  AttendanceRecord,
  AttendanceStatus,
  CreateAttendanceRequest,
  UpdateAttendanceRequest,
} from '@/types';
import {
  useAttendance,
  useCreateAttendance,
  useUpdateAttendance,
} from '@/hooks/useAttendance';
import { useUsers, useDepartments, useCreateMember, useCreateDepartment, useDeleteDepartment, useDeleteMember } from '@/hooks/useUsers';
import { toast } from 'react-toastify';

const CUSTOM_DEPARTMENT = '__custom__';

interface NewMemberFormState {
  name: string;
  email: string;
  password: string;
  department: string;
  customDepartment: string;
}

interface BreakFormRow {
  startTime: string;
  endTime: string;
}

interface AttendanceFormState {
  userId: string;
  workDate: string;
  entryTime: string;
  exitTime: string;
  status: AttendanceStatus;
  breaks: BreakFormRow[];
}

function todayString() {
  return new Date().toISOString().split('T')[0];
}

function formatDisplayTime(value?: string) {
  if (!value) return '-- : -- --';
  return new Date(value).toLocaleTimeString('en-US', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: true,
  });
}

function formatDisplayDate(date: string) {
  const d = new Date(date + 'T00:00:00');
  return d.toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric' });
}

function extractTime(value?: string) {
  if (!value) return '';
  const d = new Date(value);
  const hours = String(d.getHours()).padStart(2, '0');
  const minutes = String(d.getMinutes()).padStart(2, '0');
  return `${hours}:${minutes}`;
}

function combineDateAndTime(workDate: string, time: string): string | undefined {
  if (!time) return undefined;
  return `${workDate}T${time}:00`;
}

function isPresent(status: AttendanceStatus) {
  return (
    status === AttendanceStatus.PRESENT ||
    status === AttendanceStatus.ONLINE ||
    status === AttendanceStatus.ON_BREAK
  );
}

function statusBadgeClass(status: AttendanceStatus) {
  return isPresent(status) ? 'badge-success' : 'badge-neutral';
}

function statusLabel(status: AttendanceStatus) {
  return isPresent(status) ? 'Present' : 'Absent';
}

function toPresentAbsent(status: AttendanceStatus) {
  return isPresent(status) ? AttendanceStatus.PRESENT : AttendanceStatus.ABSENT;
}

function emptyNewMember(): NewMemberFormState {
  return {
    name: '',
    email: '',
    password: '',
    department: '',
    customDepartment: '',
  };
}

function emptyForm(workDate: string): AttendanceFormState {
  return {
    userId: '',
    workDate,
    entryTime: '',
    exitTime: '',
    status: AttendanceStatus.ABSENT,
    breaks: [{ startTime: '', endTime: '' }],
  };
}

function recordToForm(record: AttendanceRecord): AttendanceFormState {
  return {
    userId: record.userId,
    workDate: record.workDate,
    entryTime: extractTime(record.entryTime),
    exitTime: extractTime(record.exitTime),
    status: toPresentAbsent(record.status),
    breaks:
      record.breaks.length > 0
        ? record.breaks.map((b) => ({
            startTime: extractTime(b.startTime),
            endTime: extractTime(b.endTime),
          }))
        : [{ startTime: '', endTime: '' }],
  };
}

export default function TeamAttendanceSection() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [selectedDate, setSelectedDate] = useState(todayString());
  const [department, setDepartment] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [searchInput, setSearchInput] = useState(searchParams.get('q') || '');
  const [search, setSearch] = useState(searchParams.get('q') || '');
  const [page, setPage] = useState(0);
  const [showModal, setShowModal] = useState(false);
  const [showDepartmentModal, setShowDepartmentModal] = useState(false);
  const [newDepartmentName, setNewDepartmentName] = useState('');
  const [memberTab, setMemberTab] = useState<'existing' | 'new'>('existing');
  const [newMember, setNewMember] = useState<NewMemberFormState>(emptyNewMember());
  const [editingRecord, setEditingRecord] = useState<AttendanceRecord | null>(null);
  const [form, setForm] = useState<AttendanceFormState>(emptyForm(selectedDate));

  const applySearch = (value = searchInput) => {
    const next = value.trim();
    setSearch(next);
    setPage(0);
    const nextParams = new URLSearchParams(searchParams);
    if (next) {
      nextParams.set('q', next);
    } else {
      nextParams.delete('q');
    }
    setSearchParams(nextParams, { replace: true });
  };

  useEffect(() => {
    const fromUrl = searchParams.get('q') || '';
    if (fromUrl !== searchInput) {
      setSearchInput(fromUrl);
      setSearch(fromUrl);
      setPage(0);
    }
  }, [searchParams]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      if (searchInput.trim() !== search) {
        applySearch(searchInput);
      }
    }, 300);
    return () => window.clearTimeout(timer);
  }, [searchInput]);

  const attendanceFilters = useMemo(
    () => ({
      date: selectedDate,
      department: department || undefined,
      status: (statusFilter as AttendanceStatus) || undefined,
      search: search || undefined,
      page,
      size: 10,
    }),
    [selectedDate, department, statusFilter, search, page]
  );

  const { data: attendancePage, isPending } = useAttendance(attendanceFilters);

  const { data: usersPage } = useUsers(0, 200);
  const { data: apiDepartments = [] } = useDepartments();
  const createMember = useCreateMember();
  const createDepartment = useCreateDepartment();
  const deleteDepartment = useDeleteDepartment();
  const createAttendance = useCreateAttendance();
  const updateAttendance = useUpdateAttendance();
  const deleteMember = useDeleteMember();

  const records = useMemo(() => {
    const content = attendancePage?.content || [];
    const query = search.trim().toLowerCase();
    if (!query) return content;
    return content.filter((record) =>
      [record.memberName, record.memberEmail, record.department]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(query))
    );
  }, [attendancePage?.content, search]);
  const totalElements = attendancePage?.totalElements ?? 0;
  const totalPages = attendancePage?.totalPages ?? 1;
  const users = usersPage?.content || [];

  const departments = useMemo(() => {
    const fromUsers = users.map((u) => u.department).filter(Boolean) as string[];
    return Array.from(new Set([...apiDepartments, ...fromUsers])).sort();
  }, [apiDepartments, users]);

  const isInitialLoading = isPending && !attendancePage;

  useEffect(() => {
    if (!showModal && !showDepartmentModal) return;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.body.style.overflow = previousOverflow;
    };
  }, [showModal, showDepartmentModal]);

  const openCreateModal = () => {
    setEditingRecord(null);
    setMemberTab('existing');
    setNewMember(emptyNewMember());
    setForm(emptyForm(selectedDate));
    setShowModal(true);
  };

  const openEditModal = (record: AttendanceRecord) => {
    setEditingRecord(record);
    setForm(recordToForm(record));
    setShowModal(true);
  };

  const closeModal = () => {
    setShowModal(false);
    setEditingRecord(null);
    setMemberTab('existing');
    setNewMember(emptyNewMember());
  };

  const handleCreateDepartment = async () => {
    const name = newDepartmentName.trim();
    if (name.length < 2) {
      toast.error('Department name must be at least 2 characters');
      return;
    }
    try {
      const createdName = await createDepartment.mutateAsync(name);
      setDepartment(createdName || name);
      setShowDepartmentModal(false);
      setNewDepartmentName('');
      setPage(0);
    } catch {
      // toast handled in hook
    }
  };

  const resolveDepartment = () => {
    if (newMember.department === CUSTOM_DEPARTMENT) {
      return newMember.customDepartment.trim();
    }
    return newMember.department.trim();
  };

  const handleExport = () => {
    if (records.length === 0) {
      toast.info('No records to export');
      return;
    }

    const header = 'Member,Department,Status,Entry,Exit,Hours Today,Weekly Avg,Breaks';
    const rows = records.map((r) => {
      const breaks = r.breaks
        .map((b) => `${formatDisplayTime(b.startTime)}-${formatDisplayTime(b.endTime)}`)
        .join('; ');
      return [
        r.memberName,
        r.department || '',
        statusLabel(r.status),
        formatDisplayTime(r.entryTime),
        formatDisplayTime(r.exitTime),
        r.hoursToday,
        r.weeklyAvgHours,
        breaks,
      ]
        .map((v) => `"${String(v).replace(/"/g, '""')}"`)
        .join(',');
    });

    const csv = [header, ...rows].join('\n');
    const blob = new Blob([csv], { type: 'text/csv' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `attendance-${selectedDate}.csv`;
    link.click();
    URL.revokeObjectURL(url);
  };

  const handleSubmit = async () => {
    let userId = form.userId;

    if (!editingRecord && memberTab === 'new') {
      if (!newMember.name.trim() || !newMember.email.trim() || !newMember.password) {
        toast.error('Fill in name, email, and password for the new member');
        return;
      }
      const departmentName = resolveDepartment();
      if (!departmentName) {
        toast.error('Select or enter a department');
        return;
      }
      try {
        const created = await createMember.mutateAsync({
          name: newMember.name.trim(),
          email: newMember.email.trim(),
          password: newMember.password,
          department: departmentName,
        });
        userId = created.id;
      } catch {
        return;
      }
    } else if (!editingRecord && !userId) {
      toast.error('Select a member');
      return;
    }

    const breaksPayload = form.breaks
      .filter((b) => b.startTime)
      .map((b) => ({
        startTime: combineDateAndTime(form.workDate, b.startTime),
        endTime: combineDateAndTime(form.workDate, b.endTime),
      }));

    const entryTime = combineDateAndTime(form.workDate, form.entryTime);
    const exitTime = combineDateAndTime(form.workDate, form.exitTime);

    try {
      if (editingRecord?.id) {
        const payload: UpdateAttendanceRequest = {
          entryTime,
          exitTime,
          status: form.status,
          breaks: breaksPayload,
        };
        await updateAttendance.mutateAsync({ id: editingRecord.id, data: payload });
      } else {
        const payload: CreateAttendanceRequest = {
          userId: editingRecord?.userId || userId,
          workDate: form.workDate,
          entryTime,
          exitTime,
          status: form.status,
          breaks: breaksPayload,
        };
        await createAttendance.mutateAsync(payload);
      }
      closeModal();
    } catch {
      // toast handled in hooks
    }
  };

  const handleDelete = (record: AttendanceRecord) => {
    if (!confirm(`Remove ${record.memberName} from the attendance roster?`)) return;
    deleteMember.mutate(record.userId);
  };

  const handleDeleteDepartment = () => {
    if (!department) {
      toast.info('Select a department from the dropdown first');
      return;
    }
    if (!confirm(`Delete department "${department}"? Members in this department will keep their attendance records but will no longer be assigned to it.`)) {
      return;
    }
    deleteDepartment.mutate(department, {
      onSuccess: () => {
        setDepartment('');
        setPage(0);
      },
    });
  };

  const weeklyBarWidth = (minutes: number) => Math.min(100, Math.round((minutes / (8 * 60)) * 100));

  // Calculate attendance statistics
  const stats = useMemo(() => {
    if (records.length === 0) {
      return { totalHours: 0, avgHours: 0, presentCount: 0, absentCount: 0, attendanceRate: 0 };
    }
    const totalMinutes = records.reduce((sum, r) => sum + (r.hoursTodayMinutes || 0), 0);
    const totalHours = totalMinutes / 60;
    const avgHours = totalHours / records.length;
    const presentCount = records.filter((r) => isPresent(r.status)).length;
    const absentCount = records.length - presentCount;
    const attendanceRate = Math.round((presentCount / records.length) * 100);

    return {
      totalHours: Math.round(totalHours * 10) / 10,
      avgHours: Math.round(avgHours * 10) / 10,
      presentCount,
      absentCount,
      attendanceRate,
    };
  }, [records]);

  return (
    <>
    {/* Attendance Summary Stats */}
    <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-3 mb-6">
      <div className="bg-gradient-to-br from-success/10 to-success/5 border border-success/20 rounded-lg p-3">
        <p className="text-label-xs text-charcoal-muted mb-1">Present</p>
        <p className="text-headline-md text-success font-semibold">{stats.presentCount}</p>
      </div>
      <div className="bg-gradient-to-br from-error/10 to-error/5 border border-error/20 rounded-lg p-3">
        <p className="text-label-xs text-charcoal-muted mb-1">Absent</p>
        <p className="text-headline-md text-error font-semibold">{stats.absentCount}</p>
      </div>
      <div className="bg-gradient-to-br from-primary/10 to-primary/5 border border-primary/20 rounded-lg p-3">
        <p className="text-label-xs text-charcoal-muted mb-1">Total Hours</p>
        <p className="text-headline-md text-primary font-semibold">{stats.totalHours}h</p>
      </div>
      <div className="bg-gradient-to-br from-info/10 to-info/5 border border-info/20 rounded-lg p-3">
        <p className="text-label-xs text-charcoal-muted mb-1">Avg Hours</p>
        <p className="text-headline-md text-info font-semibold">{stats.avgHours}h</p>
      </div>
      <div className="bg-gradient-to-br from-purple/10 to-purple/5 border border-purple/20 rounded-lg p-3">
        <p className="text-label-xs text-charcoal-muted mb-1">Attendance</p>
        <p className="text-headline-md text-purple font-semibold">{stats.attendanceRate}%</p>
      </div>
    </div>

    <div className="card">
      <div className="flex flex-col lg:flex-row lg:items-center lg:justify-between gap-4 mb-6">
        <div>
          <h2 className="text-headline-md text-charcoal">Team Attendance</h2>
          <p className="text-body-md text-charcoal-muted mt-1">
            Track member entry, exit, and breaks for {formatDisplayDate(selectedDate)}
          </p>
        </div>
        <div className="flex items-center gap-2 flex-wrap">
          <button
            type="button"
            className="btn btn-secondary flex items-center gap-2"
            title="Generate detailed attendance report"
          >
            <span className="material-symbols-outlined text-[18px]">assessment</span>
            Report
          </button>
          <button
            type="button"
            onClick={() => {
              setNewDepartmentName('');
              setShowDepartmentModal(true);
            }}
            className="btn btn-secondary flex items-center gap-2"
          >
            <span className="material-symbols-outlined text-[18px]">apartment</span>
            Add Department
          </button>
          <button type="button" onClick={openCreateModal} className="btn btn-primary flex items-center gap-2">
            <span className="material-symbols-outlined text-[18px]">person_add</span>
            Add Member
          </button>
        </div>
      </div>

      <div className="flex flex-col xl:flex-row xl:items-center gap-3 mb-4">
        <div className="flex flex-wrap items-center gap-3">
          <input
            type="date"
            value={selectedDate}
            onChange={(e) => {
              setSelectedDate(e.target.value);
              setPage(0);
            }}
            className="input max-w-[180px]"
          />
          <div className="flex items-center gap-1">
            <select
              value={department}
              onChange={(e) => {
                setDepartment(e.target.value);
                setPage(0);
              }}
              className="input max-w-[180px]"
            >
              <option value="">All Departments</option>
              {departments.map((dept) => (
                <option key={dept} value={dept}>{dept}</option>
              ))}
            </select>
            <button
              type="button"
              onClick={handleDeleteDepartment}
              disabled={!department || deleteDepartment.isPending}
              className="p-2 text-charcoal-muted hover:text-error rounded-lg hover:bg-error-container/30 disabled:opacity-40 disabled:hover:bg-transparent disabled:hover:text-charcoal-muted"
              title={department ? `Delete ${department}` : 'Select a department to delete'}
            >
              <span className="material-symbols-outlined text-[20px]">delete</span>
            </button>
          </div>
          <select
            value={statusFilter}
            onChange={(e) => {
              setStatusFilter(e.target.value);
              setPage(0);
            }}
            className="input max-w-[160px]"
          >
            <option value="">All Statuses</option>
            <option value={AttendanceStatus.PRESENT}>Present</option>
            <option value={AttendanceStatus.ABSENT}>Absent</option>
          </select>
          <form
            className="relative min-w-[240px] flex items-center"
            onSubmit={(e) => {
              e.preventDefault();
              applySearch();
            }}
          >
            <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-charcoal-light text-[18px] pointer-events-none">
              search
            </span>
            <input
              type="search"
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
              placeholder="Find member..."
              className="input pl-10 pr-20"
            />
            <button
              type="submit"
              className="absolute right-1 top-1/2 -translate-y-1/2 btn btn-primary px-3 py-1.5 text-label-sm"
            >
              Search
            </button>
          </form>
        </div>
        <button
          type="button"
          onClick={handleExport}
          className="btn btn-secondary flex items-center gap-2 xl:ml-auto"
        >
          <span className="material-symbols-outlined text-[18px]">download</span>
          Export Log
        </button>
      </div>

      <div className="overflow-x-auto border border-warm-border rounded-xl">
        <table className="w-full min-w-[900px]">
          <thead className="bg-surface-container-low text-label-md text-on-surface-variant uppercase tracking-wide">
            <tr>
              <th className="text-left px-4 py-3">Member</th>
              <th className="text-left px-4 py-3">Status</th>
              <th className="text-left px-4 py-3">Entry Time</th>
              <th className="text-left px-4 py-3">Exit Time</th>
              <th className="text-left px-4 py-3">Hours Today</th>
              <th className="text-left px-4 py-3">Weekly Avg</th>
              <th className="text-left px-4 py-3">Breaks</th>
              <th className="text-right px-4 py-3">Actions</th>
            </tr>
          </thead>
          <tbody>
            {isInitialLoading ? (
              <tr>
                <td colSpan={8} className="px-4 py-10 text-center text-charcoal-muted">
                  Loading attendance...
                </td>
              </tr>
            ) : records.length === 0 ? (
              <tr>
                <td colSpan={8} className="px-4 py-10 text-center text-charcoal-muted">
                  No attendance records for this date. Click &quot;Add Member&quot; to log entry, exit, and breaks.
                </td>
              </tr>
            ) : (
              records.map((record) => (
                <tr key={record.id || record.userId} className="border-t border-warm-border hover:bg-sand/40">
                  <td className="px-4 py-3">
                    <div className="flex items-center gap-3">
                      <div className="avatar-sm bg-primary text-on-primary">
                        {record.memberName.charAt(0).toUpperCase()}
                      </div>
                      <div>
                        <p className="font-medium text-charcoal">{record.memberName}</p>
                        <p className="text-label-sm text-charcoal-muted">
                          {record.department || 'No department'}
                        </p>
                      </div>
                    </div>
                  </td>
                  <td className="px-4 py-3">
                    <span className={`badge px-3 py-1 rounded-full text-label-sm ${statusBadgeClass(record.status)}`}>
                      {statusLabel(record.status)}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-body-md text-charcoal">{formatDisplayTime(record.entryTime)}</td>
                  <td className="px-4 py-3 text-body-md text-charcoal">{formatDisplayTime(record.exitTime)}</td>
                  <td className="px-4 py-3 text-body-md font-medium text-charcoal">{record.hoursToday}</td>
                  <td className="px-4 py-3">
                    <div className="flex items-center gap-2 min-w-[120px]">
                      <div className="flex-1 h-2 rounded-full bg-surface-container-high overflow-hidden">
                        <div
                          className="h-full bg-primary rounded-full"
                          style={{ width: `${weeklyBarWidth(record.weeklyAvgMinutes)}%` }}
                        />
                      </div>
                      <span className="text-label-sm text-charcoal-muted">{record.weeklyAvgHours}</span>
                    </div>
                  </td>
                  <td className="px-4 py-3 text-body-md text-charcoal-muted">
                    {record.breaks.length === 0
                      ? '—'
                      : record.breaks.map((b, i) => (
                          <span key={b.id || i} className="block text-label-sm">
                            {formatDisplayTime(b.startTime)} – {formatDisplayTime(b.endTime)}
                          </span>
                        ))}
                  </td>
                  <td className="px-4 py-3 text-right">
                    <div className="flex justify-end gap-1">
                      <button
                        type="button"
                        onClick={() => openEditModal(record)}
                        className="p-2 text-charcoal-muted hover:text-primary rounded-lg hover:bg-sand"
                        title="Edit"
                      >
                        <span className="material-symbols-outlined text-[18px]">edit</span>
                      </button>
                      <button
                        type="button"
                        onClick={() => handleDelete(record)}
                        className="p-2 text-charcoal-muted hover:text-error rounded-lg hover:bg-error-container/30"
                        title="Remove member"
                      >
                        <span className="material-symbols-outlined text-[18px]">delete</span>
                      </button>
                    </div>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 mt-4 text-body-md text-charcoal-muted">
        <p>
          Showing {records.length === 0 ? 0 : page * 10 + 1} to {page * 10 + records.length} of {totalElements} members
        </p>
        <div className="flex items-center gap-2">
          <button
            type="button"
            disabled={page === 0}
            onClick={() => setPage((p) => p - 1)}
            className="btn btn-secondary px-3 disabled:opacity-50"
          >
            <span className="material-symbols-outlined text-[18px]">chevron_left</span>
          </button>
          <span className="text-label-md px-2">Page {page + 1} of {totalPages}</span>
          <button
            type="button"
            disabled={page >= totalPages - 1}
            onClick={() => setPage((p) => p + 1)}
            className="btn btn-secondary px-3 disabled:opacity-50"
          >
            <span className="material-symbols-outlined text-[18px]">chevron_right</span>
          </button>
        </div>
      </div>

      {showModal &&
        createPortal(
          <div
            className="modal-overlay"
            onClick={closeModal}
            role="presentation"
          >
            <div
              className="modal p-6 w-full max-w-lg max-h-[90vh] overflow-y-auto custom-scroll"
              onClick={(e) => e.stopPropagation()}
              role="dialog"
              aria-modal="true"
              aria-labelledby="attendance-modal-title"
            >
            <h3 id="attendance-modal-title" className="font-display text-2xl text-charcoal mb-4">
              {editingRecord ? 'Edit Attendance' : 'Add Member Attendance'}
            </h3>

            {!editingRecord && (
              <div className="flex gap-2 mb-6 p-1 bg-sand rounded-lg border border-warm-border">
                <button
                  type="button"
                  onClick={() => setMemberTab('existing')}
                  className={`flex-1 py-2 px-3 rounded-md text-label-md transition-colors ${
                    memberTab === 'existing'
                      ? 'bg-ivory text-charcoal shadow-sm'
                      : 'text-charcoal-muted hover:text-charcoal'
                  }`}
                >
                  Existing Member
                </button>
                <button
                  type="button"
                  onClick={() => setMemberTab('new')}
                  className={`flex-1 py-2 px-3 rounded-md text-label-md transition-colors ${
                    memberTab === 'new'
                      ? 'bg-ivory text-charcoal shadow-sm'
                      : 'text-charcoal-muted hover:text-charcoal'
                  }`}
                >
                  New Member
                </button>
              </div>
            )}

            <div className="space-y-4">
              {!editingRecord && memberTab === 'existing' && (
                <div>
                  <label className="block text-label-md text-charcoal-muted mb-2">Member</label>
                  <select
                    value={form.userId}
                    onChange={(e) => setForm({ ...form, userId: e.target.value })}
                    className="input"
                  >
                    <option value="">Select member...</option>
                    {users.map((user) => (
                      <option key={user.id} value={user.id}>
                        {user.name} — {user.department || user.email}
                      </option>
                    ))}
                  </select>
                </div>
              )}

              {!editingRecord && memberTab === 'new' && (
                <div className="space-y-4 p-4 bg-sand/60 rounded-xl border border-warm-border">
                  <p className="text-label-md text-charcoal-muted">Create a new member account</p>
                  <div>
                    <label className="block text-label-md text-charcoal-muted mb-2">Full Name</label>
                    <input
                      type="text"
                      value={newMember.name}
                      onChange={(e) => setNewMember({ ...newMember, name: e.target.value })}
                      className="input"
                      placeholder="Jane Doe"
                    />
                  </div>
                  <div>
                    <label className="block text-label-md text-charcoal-muted mb-2">Email</label>
                    <input
                      type="email"
                      value={newMember.email}
                      onChange={(e) => setNewMember({ ...newMember, email: e.target.value })}
                      className="input"
                      placeholder="jane@company.com"
                    />
                  </div>
                  <div>
                    <label className="block text-label-md text-charcoal-muted mb-2">Password</label>
                    <input
                      type="password"
                      value={newMember.password}
                      onChange={(e) => setNewMember({ ...newMember, password: e.target.value })}
                      className="input"
                      placeholder="Min. 4 characters"
                    />
                  </div>
                  <div>
                    <label className="block text-label-md text-charcoal-muted mb-2">Department</label>
                    <select
                      value={newMember.department}
                      onChange={(e) => setNewMember({ ...newMember, department: e.target.value })}
                      className="input"
                    >
                      <option value="">Select department...</option>
                      {departments.map((dept) => (
                        <option key={dept} value={dept}>{dept}</option>
                      ))}
                      <option value={CUSTOM_DEPARTMENT}>+ Create new department</option>
                    </select>
                  </div>
                  {newMember.department === CUSTOM_DEPARTMENT && (
                    <div>
                      <label className="block text-label-md text-charcoal-muted mb-2">New Department Name</label>
                      <input
                        type="text"
                        value={newMember.customDepartment}
                        onChange={(e) => setNewMember({ ...newMember, customDepartment: e.target.value })}
                        className="input"
                        placeholder="e.g. Business Development, Engineering"
                      />
                    </div>
                  )}
                </div>
              )}

              {!editingRecord && (
                <div className="border-t border-warm-border pt-4">
                  <p className="text-label-md text-charcoal-muted mb-3">Attendance for this day</p>
                </div>
              )}

              <div>
                <label className="block text-label-md text-charcoal-muted mb-2">Work Date</label>
                <input
                  type="date"
                  value={form.workDate}
                  onChange={(e) => setForm({ ...form, workDate: e.target.value })}
                  className="input"
                  disabled={!!editingRecord}
                />
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-label-md text-charcoal-muted mb-2">Entry Time</label>
                  <input
                    type="time"
                    value={form.entryTime}
                    onChange={(e) => setForm({ ...form, entryTime: e.target.value })}
                    className="input"
                  />
                </div>
                <div>
                  <label className="block text-label-md text-charcoal-muted mb-2">Exit Time</label>
                  <input
                    type="time"
                    value={form.exitTime}
                    onChange={(e) => setForm({ ...form, exitTime: e.target.value })}
                    className="input"
                  />
                </div>
              </div>

              <div>
                <label className="block text-label-md text-charcoal-muted mb-2">Status</label>
                <select
                  value={form.status}
                  onChange={(e) => setForm({ ...form, status: e.target.value as AttendanceStatus })}
                  className="input"
                >
                  <option value={AttendanceStatus.PRESENT}>Present</option>
                  <option value={AttendanceStatus.ABSENT}>Absent</option>
                </select>
              </div>

              <div>
                <div className="flex items-center justify-between mb-2">
                  <label className="text-label-md text-charcoal-muted">Breaks</label>
                  <button
                    type="button"
                    onClick={() =>
                      setForm({
                        ...form,
                        breaks: [...form.breaks, { startTime: '', endTime: '' }],
                      })
                    }
                    className="text-label-sm text-primary hover:underline"
                  >
                    + Add break
                  </button>
                </div>
                <div className="space-y-3">
                  {form.breaks.map((breakRow, index) => (
                    <div key={index} className="grid grid-cols-[1fr_1fr_auto] gap-2 items-center">
                      <input
                        type="time"
                        value={breakRow.startTime}
                        onChange={(e) => {
                          const breaks = [...form.breaks];
                          breaks[index] = { ...breaks[index], startTime: e.target.value };
                          setForm({ ...form, breaks });
                        }}
                        className="input"
                        placeholder="Start"
                      />
                      <input
                        type="time"
                        value={breakRow.endTime}
                        onChange={(e) => {
                          const breaks = [...form.breaks];
                          breaks[index] = { ...breaks[index], endTime: e.target.value };
                          setForm({ ...form, breaks });
                        }}
                        className="input"
                        placeholder="End"
                      />
                      <button
                        type="button"
                        onClick={() => {
                          const breaks = form.breaks.filter((_, i) => i !== index);
                          setForm({
                            ...form,
                            breaks: breaks.length ? breaks : [{ startTime: '', endTime: '' }],
                          });
                        }}
                        className="p-2 text-charcoal-muted hover:text-error"
                      >
                        <span className="material-symbols-outlined text-[18px]">close</span>
                      </button>
                    </div>
                  ))}
                </div>
              </div>
            </div>

            <div className="flex justify-end gap-3 mt-6 pt-4 border-t border-warm-border">
              <button type="button" onClick={closeModal} className="btn btn-secondary">
                Cancel
              </button>
              <button
                type="button"
                onClick={handleSubmit}
                disabled={createAttendance.isPending || updateAttendance.isPending || createMember.isPending}
                className="btn btn-primary disabled:opacity-50"
              >
                {createAttendance.isPending || updateAttendance.isPending || createMember.isPending
                  ? 'Saving...'
                  : editingRecord
                    ? 'Save Changes'
                    : 'Add Record'}
              </button>
            </div>
            </div>
          </div>,
          document.body
        )}

      {showDepartmentModal &&
        createPortal(
          <div className="modal-overlay" onClick={() => setShowDepartmentModal(false)} role="presentation">
            <div
              className="modal p-6 w-full max-w-md"
              onClick={(e) => e.stopPropagation()}
              role="dialog"
              aria-modal="true"
              aria-labelledby="department-modal-title"
            >
              <h3 id="department-modal-title" className="font-display text-2xl text-charcoal mb-4">
                Add Department
              </h3>
              <p className="text-body-md text-charcoal-muted mb-4">
                Create a department so you can assign members to it.
              </p>
              <label className="block text-label-md text-charcoal-muted mb-2">Department Name</label>
              <input
                type="text"
                value={newDepartmentName}
                onChange={(e) => setNewDepartmentName(e.target.value)}
                className="input"
                placeholder="e.g. Business Development, Engineering"
                autoFocus
                onKeyDown={(e) => {
                  if (e.key === 'Enter') {
                    e.preventDefault();
                    handleCreateDepartment();
                  }
                }}
              />
              <div className="flex justify-end gap-3 mt-6 pt-4 border-t border-warm-border">
                <button
                  type="button"
                  onClick={() => setShowDepartmentModal(false)}
                  className="btn btn-secondary"
                >
                  Cancel
                </button>
                <button
                  type="button"
                  onClick={handleCreateDepartment}
                  disabled={createDepartment.isPending}
                  className="btn btn-primary disabled:opacity-50"
                >
                  {createDepartment.isPending ? 'Saving...' : 'Add Department'}
                </button>
              </div>
            </div>
          </div>,
          document.body
        )}
    </div>
    </>
  );
}
