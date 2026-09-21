import { create } from 'zustand';
import { User } from '@/types';
import { jwtDecode } from 'jwt-decode';

interface JWTPayload {
  sub: string;
  userId: string;
  role: string;
  exp: number;
}

interface AuthState {
  user: User | null;
  accessToken: string | null;
  refreshToken: string | null;
  setAuth: (accessToken: string, refreshToken: string, user: User) => void;
  setTokens: (accessToken: string, refreshToken: string) => void;
  setUser: (user: User) => void;
  logout: () => void;
  isAuthenticated: () => boolean;
  getUserRole: () => string | null;
}

const AUTH_KEY = 'dinacharya-auth';

function loadAuth(): Pick<AuthState, 'user' | 'accessToken' | 'refreshToken'> {
  try {
    const raw = localStorage.getItem(AUTH_KEY);
    if (!raw) {
      return { user: null, accessToken: null, refreshToken: null };
    }
    const parsed = JSON.parse(raw) as { user?: User; accessToken?: string; refreshToken?: string };
    return {
      user: parsed.user ?? null,
      accessToken: parsed.accessToken ?? null,
      refreshToken: parsed.refreshToken ?? null,
    };
  } catch {
    return { user: null, accessToken: null, refreshToken: null };
  }
}

function persistAuth(state: { user: User | null; accessToken: string | null; refreshToken: string | null }) {
  localStorage.setItem(AUTH_KEY, JSON.stringify(state));
}

const initial = loadAuth();

export const useAuthStore = create<AuthState>((set, get) => ({
  user: initial.user,
  accessToken: initial.accessToken,
  refreshToken: initial.refreshToken,

  setAuth: (accessToken, refreshToken, user) => {
    persistAuth({ accessToken, refreshToken, user });
    set({ accessToken, refreshToken, user });
  },

  setTokens: (accessToken, refreshToken) => {
    persistAuth({ accessToken, refreshToken, user: get().user });
    set({ accessToken, refreshToken });
  },

  setUser: (user) => {
    persistAuth({ accessToken: get().accessToken, refreshToken: get().refreshToken, user });
    set({ user });
  },

  logout: () => {
    localStorage.removeItem(AUTH_KEY);
    set({ user: null, accessToken: null, refreshToken: null });
  },

  isAuthenticated: () => {
    const { accessToken } = get();
    if (!accessToken) return false;

    try {
      const decoded = jwtDecode<JWTPayload>(accessToken);
      return decoded.exp * 1000 > Date.now();
    } catch {
      return false;
    }
  },

  getUserRole: () => {
    const { user, accessToken } = get();
    if (user?.role) return user.role;
    if (!accessToken) return null;

    try {
      const decoded = jwtDecode<JWTPayload>(accessToken);
      return decoded.role;
    } catch {
      return null;
    }
  },
}));
