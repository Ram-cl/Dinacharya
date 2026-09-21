// Enums
export enum UserRole {
  MEMBER = 'MEMBER',
  TEAM_LEAD = 'TEAM_LEAD',
  MODERATOR = 'MODERATOR',
  ADMIN = 'ADMIN',
}

export enum TaskStatus {
  TODO = 'TODO',
  IN_PROGRESS = 'IN_PROGRESS',
  IN_REVIEW = 'IN_REVIEW',
  DONE = 'DONE',
}

export enum TaskPriority {
  LOW = 'LOW',
  MEDIUM = 'MEDIUM',
  HIGH = 'HIGH',
  URGENT = 'URGENT',
}

export enum AttendanceStatus {
  PRESENT = 'PRESENT',
  ABSENT = 'ABSENT',
  ONLINE = 'ONLINE',
  ON_BREAK = 'ON_BREAK',
  OFFLINE = 'OFFLINE',
}

export enum EmploymentType {
  INTERN = 'INTERN',
  FULL_TIME = 'FULL_TIME',
  LEAD = 'LEAD',
}

export enum EmployeeStatus {
  ACTIVE = 'ACTIVE',
  ONBOARDING = 'ONBOARDING',
  AWAY = 'AWAY',
}

// User types
export interface User {
  id: string;
  email: string;
  name: string;
  role: UserRole;
  profilePicture?: string;
  bio?: string;
  skills?: string[];
  department?: string;
  professionalRole?: string;
  githubProfile?: string;
  employeeStatus?: EmployeeStatus;
  employmentType?: EmploymentType;
  isActive: boolean;
  lastActive?: string;
  joiningDate?: string;   // ISO date string YYYY-MM-DD
  createdAt: string;
  updatedAt: string;
  temporaryPassword?: string;
}

// Team types
export interface Team {
  id: string;
  name: string;
  description?: string;
  lead: User;
  members: User[];
  taskCount?: number;
  createdAt: string;
  updatedAt: string;
}

// Task types
export interface Task {
  id: string;
  title: string;
  description?: string;
  remark?: string;
  status: TaskStatus;
  priority: TaskPriority;
  deadline?: string;
  assignedTo?: User;
  createdBy: User;
  teamId: string;
  teamName: string;
  labels?: string[];
  commentCount?: number;
  attachmentCount?: number;
  version: number;
  createdAt: string;
  updatedAt: string;
}

// Comment types
export interface Comment {
  id: string;
  content: string;
  author: User;
  taskId: string;
  flagged: boolean;
  createdAt: string;
  updatedAt: string;
}

// Attachment types
export interface Attachment {
  id: string;
  fileUrl: string;
  fileName: string;
  fileType?: string;
  taskId: string;
  uploadedBy: User;
  uploadedAt: string;
}

// Analytics types
export interface TaskAnalytics {
  statusCounts: Record<string, number>;
  priorityCounts: Record<string, number>;
  totalTasks: number;
  overdueTasks: number;
  completedTasks: number;
}

export interface Workload {
  userId: string;
  userName: string;
  assignedTasks: number;
  completedTasks: number;
  inProgressTasks: number;
  overdueTasks: number;
}

export interface EmployeePerformance {
  userId: string;
  userName: string;
  department?: string;
  periodStart: string;
  periodEnd: string;
  productivityScore: number;
  efficiencyScore: number;
  disciplineScore: number;
  performanceIndex: number;
  rollingIndex?: number;
  tasksCompleted: number;
  tasksAssigned: number;
  onTimeTasks: number;
  attendanceDays: number;
  workingDays: number;
}

export interface EmployeePerformanceOverview {
  periodStart: string;
  periodEnd: string;
  employees: EmployeePerformance[];
}

export interface EmployeePerformanceTrend {
  userId: string;
  userName: string;
  points: {
    periodStart: string;
    periodEnd: string;
    productivityScore: number;
    efficiencyScore: number;
    disciplineScore: number;
    performanceIndex: number;
    rollingIndex?: number;
  }[];
}

export interface AttendanceBreak {
  id?: string;
  startTime?: string;
  endTime?: string;
  duration?: string;
}

export interface AttendanceRecord {
  id?: string;
  userId: string;
  memberName: string;
  memberEmail: string;
  department?: string;
  profilePicture?: string;
  workDate: string;
  entryTime?: string;
  exitTime?: string;
  status: AttendanceStatus;
  hoursToday: string;
  hoursTodayMinutes: number;
  weeklyAvgHours: string;
  weeklyAvgMinutes: number;
  breaks: AttendanceBreak[];
  createdAt: string;
  updatedAt: string;
}

export interface CreateAttendanceRequest {
  userId: string;
  workDate?: string;
  entryTime?: string;
  exitTime?: string;
  status?: AttendanceStatus;
  breaks?: { startTime?: string; endTime?: string }[];
}

export interface UpdateAttendanceRequest {
  entryTime?: string;
  exitTime?: string;
  status?: AttendanceStatus;
  breaks?: { startTime?: string; endTime?: string }[];
}

export interface EmployeeAttendanceDay {
  workDate: string;
  dayLabel: string;
  status: 'PRESENT' | 'ABSENT' | 'LATE';
  note: string;
}

export interface EmployeeAttendanceMonth {
  monthKey: string;
  monthLabel: string;
  attendancePercent: number;
  presentCount: number;
  absentCount: number;
  lateCount: number;
  totalSessions: number;
  days: EmployeeAttendanceDay[];
}

export interface EmployeeAttendanceDashboard {
  userId: string;
  userName: string;
  department?: string;
  periodStart: string;
  periodEnd: string;
  overallPercent: number;
  totalSessions: number;
  presentCount: number;
  absentCount: number;
  lateCount: number;
  months: EmployeeAttendanceMonth[];
}

export interface CreateMemberRequest {
  email: string;
  password?: string;
  name: string;
  department: string;
  professionalRole?: string;
  githubProfile?: string;
  joiningDate?: string;   // ISO date YYYY-MM-DD
}

export interface UpdateMemberRequest {
  email?: string;
  professionalRole?: string;
  githubProfile?: string;
  department?: string;
  employeeStatus?: EmployeeStatus;
  joiningDate?: string;   // ISO date YYYY-MM-DD
}

export interface AttendanceFilters {
  date?: string;
  department?: string;
  status?: AttendanceStatus;
  search?: string;
  page?: number;
  size?: number;
}

// Auth types
export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  user: User;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  name: string;
  department?: string;
}

// Request types
export interface CreateTaskRequest {
  title: string;
  description?: string;
  remark?: string;
  status?: TaskStatus;
  priority: TaskPriority;
  deadline?: string;
  assignedToId?: string;
  teamId?: string;
  labels?: string[];
}

export interface UpdateTaskRequest {
  title?: string;
  description?: string;
  remark?: string;
  priority?: TaskPriority;
  deadline?: string;
  assignedToId?: string;
  labels?: string[];
}

export interface UpdateTaskStatusRequest {
  status: TaskStatus;
}

export interface CreateTeamRequest {
  name: string;
  description?: string;
}

export interface UpdateUserRequest {
  name?: string;
  bio?: string;
  skills?: string[];
  department?: string;
  profilePicture?: string;
}

export interface CreateCommentRequest {
  content: string;
}

// Paginated response
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}


// Task Completion Analytics types
export interface TaskCompletionAnalytics {
  periodStart: string;
  periodEnd: string;
  totalTasks: number;
  completedTasks: number;
  inProgressTasks: number;
  todoTasks: number;
  inReviewTasks: number;
  completionRate: number;
  onTimeRate: number;
  overdueTasks: number;
  avgCompletionTimeHours?: number;
  avgTimeInProgressHours?: number;
  byStatus: StatusBreakdown[];
  byPriority: PriorityBreakdown[];
  byAssignee: AssigneeBreakdown[];
  dailyTrend: DailyTrend[];
  weeklyTrend: WeeklyTrend[];
}

export interface StatusBreakdown {
  status: string;
  count: number;
  percentage: number;
}

export interface PriorityBreakdown {
  priority: string;
  total: number;
  completed: number;
  completionRate: number;
}

export interface AssigneeBreakdown {
  userId: string;
  userName: string;
  assigned: number;
  completed: number;
  completionRate: number;
  overdue: number;
}

export interface DailyTrend {
  date: string;
  created: number;
  completed: number;
  netChange: number;
}

export interface WeeklyTrend {
  weekStart: string;
  weekLabel: string;
  created: number;
  completed: number;
  completionRate: number;
}
