import {queryOptions} from '@tanstack/react-query';
import {loadConfigurationSchedule} from '../../api/configuration/configuration-schedule-api.ts';
import type {QueryOptionsParams} from '../types.ts';

export const configurationScheduleKeys = {
  all: ['configuration-schedule'] as const,
  details: () => [...configurationScheduleKeys.all, 'detail'] as const,
  detail: (id: number) => [...configurationScheduleKeys.details(), id] as const,
};

export function configurationScheduleQueryOptions(
  id: number | undefined,
  opts?: QueryOptionsParams,
) {
  const {enabled = true, ...queryOpts} = opts ?? {};
  return queryOptions({
    ...queryOpts,
    queryKey: id !== undefined ? configurationScheduleKeys.detail(id) : configurationScheduleKeys.all,
    queryFn: () => loadConfigurationSchedule(id as number),
    enabled: enabled && id !== undefined,
  });
}
