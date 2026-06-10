import {useEffect} from 'react';
import {QueryKey, useQueryClient} from '@tanstack/react-query';

export type DetailQueryKeyFactory = (id: number) => QueryKey;

export function useInvalidateDetailQueryOnOpen(
  open: boolean,
  detailKey: DetailQueryKeyFactory,
  id: number | undefined,
): void {
  const queryClient = useQueryClient();
  useEffect(() => {
    if (open && id !== undefined) {
      void queryClient.invalidateQueries({queryKey: detailKey(id)});
    }
  }, [open, id, queryClient, detailKey]);
}
