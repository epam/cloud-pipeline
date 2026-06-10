import {QueryClient} from '@tanstack/react-query';
import {DEFAULT_GC_TIME, DEFAULT_STALE_TIME} from './constants.ts';

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: DEFAULT_STALE_TIME,
      gcTime: DEFAULT_GC_TIME,
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
});
