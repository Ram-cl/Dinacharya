import { Routes, Route, Navigate } from 'react-router-dom';
import { useAuthStore } from './store/authStore';
import { isAdminUser, homePath } from './auth/roles';
import Layout from './components/Layout';
import Login from './pages/Login';
import Register from './pages/Register';
import ForgotPassword from './pages/ForgotPassword';
import ResetPassword from './pages/ResetPassword';
import Profile from './pages/Profile';
import ModeratorPanel from './pages/ModeratorPanel';
import TaskManagement from './pages/TaskManagement';
import TeamPeople from './pages/TeamPeople';
import EmployeeDashboard from './pages/EmployeeDashboard';
import EmployeePerformance from './pages/EmployeePerformance';
import EmployeePerformanceDetail from './pages/EmployeePerformanceDetail';
import EmployeeAttendanceDashboard from './pages/EmployeeAttendanceDashboard';
import TaskAnalytics from './pages/TaskAnalytics';

function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated());
  return isAuthenticated ? <>{children}</> : <Navigate to="/login" />;
}

function AdminRoute({ children }: { children: React.ReactNode }) {
  const user = useAuthStore((state) => state.user);
  const role = useAuthStore((state) => state.getUserRole());
  if (!isAdminUser(user, role)) {
    return <Navigate to="/work" replace />;
  }
  return <>{children}</>;
}

function EmployeeRoute({ children }: { children: React.ReactNode }) {
  const user = useAuthStore((state) => state.user);
  const role = useAuthStore((state) => state.getUserRole());
  if (isAdminUser(user, role)) {
    return <Navigate to="/tasks" replace />;
  }
  return <>{children}</>;
}

function HomeRedirect() {
  const user = useAuthStore((state) => state.user);
  const role = useAuthStore((state) => state.getUserRole());
  return <Navigate to={homePath(user, role)} replace />;
}

function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />
      <Route path="/forgot-password" element={<ForgotPassword />} />
      <Route path="/reset-password" element={<ResetPassword />} />

      <Route
        path="/"
        element={
          <ProtectedRoute>
            <Layout />
          </ProtectedRoute>
        }
      >
        <Route index element={<HomeRedirect />} />
        <Route
          path="work"
          element={
            <EmployeeRoute>
              <EmployeeDashboard />
            </EmployeeRoute>
          }
        />
        <Route
          path="tasks"
          element={
            <AdminRoute>
              <TaskManagement />
            </AdminRoute>
          }
        />
        <Route
          path="people"
          element={
            <AdminRoute>
              <TeamPeople />
            </AdminRoute>
          }
        />
        <Route
          path="task-analytics"
          element={
            <AdminRoute>
              <TaskAnalytics />
            </AdminRoute>
          }
        />
        <Route
          path="performance"
          element={
            <AdminRoute>
              <EmployeePerformance />
            </AdminRoute>
          }
        />
        <Route
          path="performance/:userId"
          element={
            <AdminRoute>
              <EmployeePerformanceDetail />
            </AdminRoute>
          }
        />
        <Route path="attendance/:userId" element={<EmployeeAttendanceDashboard />} />
        <Route path="profile" element={<Profile />} />
        <Route
          path="moderator"
          element={
            <AdminRoute>
              <ModeratorPanel />
            </AdminRoute>
          }
        />
      </Route>
    </Routes>
  );
}

export default App;
