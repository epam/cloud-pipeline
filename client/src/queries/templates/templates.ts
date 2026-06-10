import {queryOptions} from '@tanstack/react-query';
import {loadFolderTemplates, loadTemplates} from '../../api/infra/infra-api.ts';
import {queryClient} from '../query-client.ts';
import {QueryOptionsParams} from '../types.ts';

export const folderTemplatesKeys = {
  all: ['folder-templates'] as const,
};

export const pipelineTemplatesKeys = {
  all: ['pipeline-templates'] as const,
};

export function folderTemplatesQueryOptions(opts?: QueryOptionsParams) {
  return queryOptions({
    ...(opts ?? {}),
    queryKey: folderTemplatesKeys.all,
    queryFn: loadFolderTemplates,
    placeholderData: [],
  });
}

export function pipelineTemplatesQueryOptions(opts?: QueryOptionsParams) {
  return queryOptions({
    ...(opts ?? {}),
    queryKey: pipelineTemplatesKeys.all,
    queryFn: loadTemplates,
    placeholderData: [],
  });
}

export function fetchFolderTemplates(opts?: QueryOptionsParams) {
  return queryClient.fetchQuery(folderTemplatesQueryOptions(opts));
}

export function fetchPipelineTemplates(opts?: QueryOptionsParams) {
  return queryClient.fetchQuery(pipelineTemplatesQueryOptions(opts));
}
