import { useQuery, useMutation, useQueryClient, keepPreviousData } from '@tanstack/react-query';
import { apiClient } from '@/api/client';
import { Page, User, CreateMemberRequest, UpdateMemberRequest, UpdateUserRequest } from '@/types';
import { AxiosError } from 'axios';
import { toast } from 'react-toastify';
import { useAuthStore } from '@/store/authStore';

function apiError(error: AxiosError<{ detail?: string; message?: string; errors?: Record<string, string> }>, fallback: string) {
  const data = error.response?.data;
  if (data?.errors) {
    const messages = Object.values(data.errors).filter(Boolean);
    if (messages.length) return messages.join('. ');
  }
  return data?.detail || data?.message || fallback;
}

export const useUsers = (page = 0, size = 100) => {
  return useQuery({
    queryKey: ['users', page, size],
    queryFn: async () => {
      const response = await apiClient.get<Page<User>>(`/users?page=${page}&size=${size}`);
      return response.data;
    },
    staleTime: 5 * 60 * 1000,
    placeholderData: keepPreviousData,
  });
};

export const useDepartments = () => {
  return useQuery({
    queryKey: ['departments'],
    queryFn: async () => {
      const response = await apiClient.get<string[]>('/users/departments');
      return response.data;
    },
    staleTime: 5 * 60 * 1000,
    placeholderData: keepPreviousData,
  });
};

export const useCreateDepartment = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (name: string) => {
      const response = await apiClient.post<{ name: string }>('/moderator/departments', { name });
      return response.data.name;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['departments'] });
      toast.success('Department added');
    },
    onError: (error: AxiosError<{ detail?: string }>) => {
      const message = error.response?.data?.detail || 'Failed to add department';
      toast.error(message);
    },
  });
};

export const useDeleteDepartment = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (name: string) => {
      await apiClient.delete('/moderator/departments', { params: { name } });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['departments'] });
      queryClient.invalidateQueries({ queryKey: ['users'] });
      queryClient.invalidateQueries({ queryKey: ['attendance'] });
      toast.success('Department deleted');
    },
    onError: (error: AxiosError<{ detail?: string }>) => {
      const message = error.response?.data?.detail || 'Failed to delete department';
      toast.error(message);
    },
  });
};

export const useUpdateEmploymentType = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ userId, employmentType }: { userId: string; employmentType: import('@/types').EmploymentType }) => {
      const response = await apiClient.patch<User>(`/users/${userId}/employment-type`, { employmentType });
      return response.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users'] });
      queryClient.invalidateQueries({ queryKey: ['teams'] });
      toast.success('Role updated');
    },
    onError: (error: AxiosError<{ detail?: string }>) => {
      const message = error.response?.data?.detail || 'Failed to update role';
      toast.error(message);
    },
  });
};

export const useDirectoryDepartments = () => {
  return useQuery({
    queryKey: ['user-departments'],
    queryFn: async () => {
      const response = await apiClient.get<string[]>('/users/departments');
      return response.data;
    },
    staleTime: 5 * 60 * 1000,
    placeholderData: keepPreviousData,
  });
};

export const useUpdateMember = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ userId, data }: { userId: string; data: UpdateMemberRequest }) => {
      const response = await apiClient.patch<User>(`/users/${userId}`, data);
      return response.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users'] });
      toast.success('Employee updated');
    },
    onError: (error: AxiosError<{ detail?: string; message?: string; errors?: Record<string, string> }>) => {
      toast.error(apiError(error, 'Failed to update employee'));
    },
  });
};

export const useUpdateEmployeeStatus = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ userId, employeeStatus }: { userId: string; employeeStatus: import('@/types').EmployeeStatus }) => {
      const response = await apiClient.patch<User>(`/users/${userId}/employee-status`, { employeeStatus });
      return response.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users'] });
      toast.success('Status updated');
    },
    onError: (error: AxiosError<{ detail?: string }>) => {
      const message = error.response?.data?.detail || 'Failed to update status';
      toast.error(message);
    },
  });
};

export const useEnrollMember = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (data: CreateMemberRequest) => {
      const response = await apiClient.post<User>('/users/enroll', data);
      return response.data;
    },
    onSuccess: (user) => {
      queryClient.invalidateQueries({ queryKey: ['users'] });
      queryClient.invalidateQueries({ queryKey: ['user-departments'] });
      queryClient.invalidateQueries({ queryKey: ['departments'] });
      if (user.temporaryPassword) {
        toast.success(`Enrolled ${user.name}. Temporary password: ${user.temporaryPassword}`);
      } else {
        toast.success(`${user.name} enrolled. A welcome email will be sent if mail is configured.`);
      }
    },
    onError: (error: AxiosError<{ detail?: string; message?: string; errors?: Record<string, string> }>) => {
      toast.error(apiError(error, 'Failed to enroll member'));
    },
  });
};

export const useDeleteMember = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (userId: string) => {
      await apiClient.delete(`/users/${userId}`);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users'] });
      queryClient.invalidateQueries({ queryKey: ['teams'] });
      queryClient.invalidateQueries({ queryKey: ['attendance'] });
      toast.success('Member deleted');
    },
    onError: (error: AxiosError<{ detail?: string; message?: string; errors?: Record<string, string> }>) => {
      toast.error(apiError(error, 'Failed to delete member'));
    },
  });
};

export const useCreateMember = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (data: CreateMemberRequest) => {
      const response = await apiClient.post<User>('/moderator/members', data);
      return response.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users'] });
      queryClient.invalidateQueries({ queryKey: ['departments'] });
      toast.success('Member created');
    },
    onError: (error: AxiosError<{ detail?: string }>) => {
      const message = error.response?.data?.detail || 'Failed to create member';
      toast.error(message);
    },
  });
};

export const useUpdateProfile = () => {
  const setUser = useAuthStore((state) => state.setUser);

  return useMutation({
    mutationFn: async (data: UpdateUserRequest) => {
      const response = await apiClient.put<User>('/users/me', data);
      return response.data;
    },
    onSuccess: (updatedUser) => {
      setUser(updatedUser);
      toast.success('Profile updated');
    },
    onError: (error: AxiosError<{ detail?: string; message?: string; errors?: Record<string, string> }>) => {
      toast.error(apiError(error, 'Failed to update profile'));
    },
  });
};
