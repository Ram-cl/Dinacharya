import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/api/client';
import { Task, CreateTaskRequest, UpdateTaskRequest, UpdateTaskStatusRequest, Page, TaskStatus, TaskPriority } from '@/types';
import { toast } from 'react-toastify';
import { AxiosError } from 'axios';

function getApiErrorMessage(error: unknown, fallback: string) {
  if (error instanceof AxiosError) {
    const detail = error.response?.data?.detail;
    const message = error.response?.data?.message;
    const errors = error.response?.data?.errors as Record<string, string> | undefined;
    if (typeof detail === 'string' && detail) return detail;
    if (typeof message === 'string' && message) return message;
    if (errors) return Object.values(errors).join(', ');
  }
  return fallback;
}

interface TaskFilters {
  teamId?: string;
  status?: TaskStatus;
  priority?: TaskPriority;
  assignedToId?: string;
  page?: number;
  size?: number;
}

export const useTasks = (filters: TaskFilters) => {
  return useQuery({
    queryKey: ['tasks', filters],
    queryFn: async () => {
      const params = new URLSearchParams();
      if (filters.teamId) params.append('teamId', filters.teamId);
      if (filters.status) params.append('status', filters.status);
      if (filters.priority) params.append('priority', filters.priority);
      if (filters.assignedToId) params.append('assignedToId', filters.assignedToId);
      params.append('page', String(filters.page || 0));
      params.append('size', String(filters.size || 50));

      const response = await apiClient.get<Page<Task>>(`/tasks?${params}`);
      return response.data;
    },
  });
};

export const useMyTasks = (page = 0, size = 100, enabled = true) => {
  return useQuery({
    queryKey: ['tasks', 'mine', page, size],
    queryFn: async () => {
      const response = await apiClient.get<Page<Task>>(`/tasks/my-tasks?page=${page}&size=${size}`);
      return response.data;
    },
    enabled,
    refetchInterval: enabled ? 12000 : false,
    refetchOnWindowFocus: true,
  });
};

export const useTask = (id: string) => {
  return useQuery({
    queryKey: ['tasks', id],
    queryFn: async () => {
      const response = await apiClient.get<Task>(`/tasks/${id}`);
      return response.data;
    },
    enabled: !!id,
  });
};

export const useCreateTask = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (data: CreateTaskRequest) => {
      const payload: CreateTaskRequest = { ...data };
      if (!payload.deadline) {
        delete payload.deadline;
      }
      if (!payload.teamId) {
        delete payload.teamId;
      }
      const response = await apiClient.post<Task>('/tasks', payload);
      return response.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tasks'] });
      toast.success('Task created');
    },
    onError: (error) => {
      toast.error(getApiErrorMessage(error, 'Failed to create task'));
    },
  });
};

export const useUpdateTask = (id: string) => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (data: UpdateTaskRequest) => {
      const response = await apiClient.put<Task>(`/tasks/${id}`, data);
      return response.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tasks', id] });
      queryClient.invalidateQueries({ queryKey: ['tasks'] });
      toast.success('Task updated');
    },
    onError: () => {
      toast.error('Failed to update task');
    },
  });
};

export const useChangeTaskStatus = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ id, status }: { id: string; status: TaskStatus }) => {
      const response = await apiClient.patch<Task>(`/tasks/${id}/status`, { status });
      return response.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tasks'] });
      toast.success('Status updated');
    },
    onError: () => {
      toast.error('Failed to update status');
    },
  });
};

export const useUpdateTaskStatus = (id: string) => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (data: UpdateTaskStatusRequest) => {
      const response = await apiClient.patch<Task>(`/tasks/${id}/status`, data);
      return response.data;
    },
    onMutate: async (newStatus) => {
      await queryClient.cancelQueries({ queryKey: ['tasks'] });
      
      const previousTasks = queryClient.getQueryData(['tasks']);
      
      queryClient.setQueriesData({ queryKey: ['tasks'] }, (old: any) => {
        if (!old?.content) return old;
        return {
          ...old,
          content: old.content.map((task: Task) =>
            task.id === id ? { ...task, status: newStatus.status } : task
          ),
        };
      });

      return { previousTasks };
    },
    onError: (_err, _newStatus, context) => {
      queryClient.setQueryData(['tasks'], context?.previousTasks);
      toast.error('Failed to update status');
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ['tasks'] });
    },
  });
};

export const useDeleteTask = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (id: string) => {
      await apiClient.delete(`/tasks/${id}`);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tasks'] });
      toast.success('Task deleted');
    },
    onError: (error) => {
      toast.error(getApiErrorMessage(error, 'Failed to delete task'));
    },
  });
};

export const useAssignTask = (id: string) => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (assigneeId: string) => {
      const response = await apiClient.post<Task>(`/tasks/${id}/assign`, { assigneeId });
      return response.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tasks', id] });
      queryClient.invalidateQueries({ queryKey: ['tasks'] });
      toast.success('Task assigned');
    },
    onError: () => {
      toast.error('Failed to assign task');
    },
  });
};

export const useDeleteAllTasks = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (teamId?: string) => {
      const url = teamId ? `/tasks/all?teamId=${teamId}&confirm=true` : '/tasks/all?confirm=true';
      const response = await apiClient.delete<{ message: string; deletedCount: number }>(url);
      return response.data;
    },
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: ['tasks'] });
      toast.success(`${data.deletedCount} task(s) deleted successfully`);
    },
    onError: (error) => {
      toast.error(getApiErrorMessage(error, 'Failed to delete all tasks'));
    },
  });
};
