import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/api/client';
import { TaskCompletionAnalytics } from '@/types';

export function useTaskCompletionAnalytics(from?: string, to?: string, department?: string) {
  return useQuery({
    queryKey: ['task-completion-analytics', from, to, department],
    queryFn: async () => {
      const params = new URLSearchParams();
      if (from) params.set('from', from);
      if (to) params.set('to', to);
      if (department) params.set('department', department);
      const query = params.toString();
      const response = await apiClient.get<TaskCompletionAnalytics>(
        `/analytics/tasks${query ? `?${query}` : ''}`
      );
      return response.data;
    },
    staleTime: 60 * 1000,
  });
}
