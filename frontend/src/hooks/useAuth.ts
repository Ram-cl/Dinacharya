import { useMutation, useQuery } from '@tanstack/react-query';
import { apiClient } from '@/api/client';
import { AuthResponse, LoginRequest, RegisterRequest } from '@/types';
import { useAuthStore } from '@/store/authStore';
import { toast } from 'react-toastify';
import { AxiosError } from 'axios';

function getApiErrorMessage(error: unknown, fallback: string) {
  if (error instanceof AxiosError) {
    if (!error.response) {
      return 'Cannot reach the API. Set VITE_API_URL or the Cloudflare Worker variable API_ORIGIN to your Render backend URL (https://dinacharya-backend.onrender.com).';
    }
    const status = error.response.status;
    if (status === 503) {
      const message = error.response.data?.message;
      if (typeof message === 'string' && message) return message;
    }
    const detail = error.response.data?.detail;
    const message = error.response.data?.message;
    const errors = error.response.data?.errors as Record<string, string> | undefined;
    if (typeof detail === 'string' && detail) return detail;
    if (typeof message === 'string' && message) return message;
    if (errors) return Object.values(errors).join(', ');
    if (status === 401 || status === 403) return fallback;
  }
  return fallback;
}

export const useLogin = () => {
  const setAuth = useAuthStore((state) => state.setAuth);

  return useMutation({
    mutationFn: async (data: LoginRequest) => {
      const response = await apiClient.post<AuthResponse>('/auth/login', data);
      return response.data;
    },
    onSuccess: (data) => {
      setAuth(data.accessToken, data.refreshToken, data.user);
      toast.success('Welcome back!');
    },
    onError: (error) => {
      toast.error(getApiErrorMessage(error, 'Invalid credentials'));
    },
  });
};

export const useRegister = () => {
  const setAuth = useAuthStore((state) => state.setAuth);

  return useMutation({
    mutationFn: async (data: RegisterRequest) => {
      const response = await apiClient.post<AuthResponse>('/auth/register', data);
      return response.data;
    },
    onSuccess: (data) => {
      setAuth(data.accessToken, data.refreshToken, data.user);
      toast.success('Account created successfully!');
    },
    onError: (error) => {
      toast.error(getApiErrorMessage(error, 'Registration failed'));
    },
  });
};

export const useLogout = () => {
  const logout = useAuthStore((state) => state.logout);

  return useMutation({
    mutationFn: async () => {
      await apiClient.post('/auth/logout');
    },
    onSuccess: () => {
      logout();
      toast.info('Logged out');
      window.location.href = '/login';
    },
    onError: (error) => {
      console.error('Logout error:', error);
      // Force logout even if backend fails
      logout();
      window.location.href = '/login';
    },
  });
};

export const useForgotPassword = () => {
  return useMutation({
    mutationFn: async (email: string) => {
      const response = await apiClient.post<{ message: string; mailReady?: boolean }>(
        '/auth/forgot-password',
        { email }
      );
      return response.data;
    },
    onError: (error) => {
      toast.error(getApiErrorMessage(error, 'Could not send a reset link. Restart the backend if this is a new feature.'));
    },
  });
};

export const useValidateResetToken = (token: string) => {
  return useQuery({
    queryKey: ['reset-token', token],
    queryFn: async () => {
      const response = await apiClient.get<{ valid: boolean }>(
        `/auth/reset-password/validate?token=${encodeURIComponent(token)}`
      );
      return response.data;
    },
    enabled: !!token,
    retry: false,
  });
};

export const useResetPassword = () => {
  return useMutation({
    mutationFn: async (data: { token: string; newPassword: string }) => {
      const response = await apiClient.post<{ message: string }>('/auth/reset-password', data);
      return response.data;
    },
    onSuccess: () => {
      toast.success('Password updated. Sign in with your new password.');
    },
    onError: () => {
      toast.error('This reset link is invalid or has expired.');
    },
  });
};
