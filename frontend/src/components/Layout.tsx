import { useEffect, useState } from 'react';
import { Outlet, Link, useNavigate, useLocation } from 'react-router-dom';
import { useAuthStore } from '@/store/authStore';
import { useLogout } from '@/hooks/useAuth';
import { isAdminUser } from '@/auth/roles';
import Logo from '@/components/Logo';

const SIDEBAR_KEY = 'dinacharya-sidebar-open';

const ADMIN_LINKS = [
  { to: '/tasks', label: 'Tasks', icon: 'assignment' },
  { to: '/task-analytics', label: 'Task Analytics', icon: 'bar_chart' },
  { to: '/performance', label: 'Performance', icon: 'monitoring' },
  { to: '/moderator', label: 'Moderation', icon: 'shield' },
  { to: '/people', label: 'People', icon: 'badge' },
  { to: '/profile', label: 'Profile', icon: 'person' },
] as const;

const EMPLOYEE_LINKS = [
  { to: '/work', label: 'Daily tasks', icon: 'assignment' },
  { to: 'attendance', label: 'Attendance', icon: 'calendar_month', dynamic: true as const },
  { to: '/profile', label: 'Profile', icon: 'person' },
] as const;

export default function Layout() {
  const user = useAuthStore((state) => state.user);
  const role = useAuthStore((state) => state.getUserRole());
  const admin = isAdminUser(user, role);
  const navigate = useNavigate();
  const location = useLocation();
  const logoutMutation = useLogout();
  const [expanded, setExpanded] = useState(() => {
    const saved = localStorage.getItem(SIDEBAR_KEY);
    return saved === null ? true : saved === 'true';
  });

  useEffect(() => {
    localStorage.setItem(SIDEBAR_KEY, String(expanded));
  }, [expanded]);

  const handleLogout = async () => {
    await logoutMutation.mutateAsync();
    navigate('/login');
  };

  const handleNewTask = () => {
    if (admin) {
      if (location.pathname === '/tasks') {
        document.getElementById('new-task-form')?.scrollIntoView({ behavior: 'smooth', block: 'center' });
        document.getElementById('new-task-title')?.focus();
        return;
      }
      navigate('/tasks', { state: { openNewTask: Date.now() } });
      return;
    }
    navigate('/work', { state: { openNewTask: Date.now() } });
  };

  const isActive = (path: string) => {
    if (path === '/performance') return location.pathname.startsWith('/performance');
    if (path === '/task-analytics') return location.pathname === '/task-analytics';
    if (path.startsWith('/attendance')) return location.pathname.startsWith('/attendance');
    return location.pathname === path;
  };

  const resolveNavPath = (item: (typeof ADMIN_LINKS)[number] | (typeof EMPLOYEE_LINKS)[number]) => {
    if ('dynamic' in item && item.dynamic && user?.id) {
      return `/attendance/${user.id}`;
    }
    return item.to;
  };

  const navLinks = admin ? ADMIN_LINKS : EMPLOYEE_LINKS;

  return (
    <div className="flex h-screen overflow-hidden bg-linen">
      <nav
        className={`app-sidebar flex h-full shrink-0 flex-col transition-[width] duration-200 ${
          expanded ? 'w-[260px]' : 'w-[72px]'
        }`}
      >
        <div className={`sidebar-brand shrink-0 ${expanded ? 'px-5 pt-6 pb-4' : 'px-3 pt-5 pb-4'}`}>
          <div className={expanded ? '' : 'flex justify-center'}>
            <Logo size={expanded ? 'lg' : 'sm'} showText={expanded} stacked={expanded} />
          </div>
        </div>

        <div className="flex min-h-0 flex-1 flex-col overflow-y-auto custom-scroll px-3 pb-3 pt-4">
          <button
            type="button"
            onClick={handleNewTask}
            title="New Task"
            className={`btn btn-primary mb-5 flex items-center justify-center gap-2 ${
              expanded ? 'w-full' : 'w-full px-0'
            }`}
          >
            <span className="material-symbols-outlined text-[18px]">add</span>
            {expanded && <span>New Task</span>}
          </button>

          {expanded && (
            <p className="text-[11px] font-semibold tracking-[0.12em] uppercase text-charcoal-muted px-2 mb-2">
              Menu
            </p>
          )}

          <ul className="flex flex-col gap-1">
            {navLinks.map((item) => {
              const itemPath = resolveNavPath(item);
              const active = isActive(itemPath);
              return (
                <li key={itemPath}>
                  <Link
                    to={itemPath}
                    title={item.label}
                    className={`nav-item ${active ? 'active' : ''} ${expanded ? '' : 'justify-center px-0'}`}
                  >
                    <span className={`material-symbols-outlined ${active ? 'filled' : ''}`}>
                      {item.icon}
                    </span>
                    {expanded && <span>{item.label}</span>}
                  </Link>
                </li>
              );
            })}
          </ul>
        </div>

        <div className="sidebar-footer shrink-0 border-t border-warm-border px-3 py-4">
          <button
            type="button"
            onClick={handleLogout}
            title="Logout"
            className={`nav-item w-full text-[#9A4A32] hover:text-[#7A3422] hover:bg-[#F4F1EA] ${
              expanded ? 'text-left' : 'justify-center px-0'
            }`}
          >
            <span className="material-symbols-outlined">logout</span>
            {expanded && <span>Logout</span>}
          </button>
        </div>
      </nav>

      <div className="flex min-h-0 min-w-0 flex-1 flex-col">
        <header className="app-topbar flex h-14 shrink-0 items-center gap-3 border-b border-warm-border bg-white px-4">
          <button
            type="button"
            className="p-2 text-charcoal-muted hover:bg-sand rounded-lg transition-colors"
            aria-label={expanded ? 'Collapse sidebar' : 'Expand sidebar'}
            onClick={() => setExpanded((open) => !open)}
          >
            <span className="material-symbols-outlined text-[22px]">
              {expanded ? 'left_panel_close' : 'left_panel_open'}
            </span>
          </button>

          <div className="ml-auto flex items-center gap-1">
            <Link to="/profile" className="ml-1">
              <div className="avatar cursor-pointer bg-terracotta border-terracotta-dark">
                {user?.name?.charAt(0).toUpperCase()}
              </div>
            </Link>
          </div>
        </header>

        <main className="custom-scroll flex-1 overflow-auto bg-linen p-6">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
