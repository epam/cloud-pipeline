import {useMemo} from 'react';
import {MetadataLoadResponseItem} from '../../@types/metadata.ts';
import {useMemoizedArray} from '../../hooks/common/memo.ts';
import {Revision} from '../../@types/pipeline.ts';
import {PipelineVersionsInfo} from '../../stores/pipelines/types.ts';
import {useQueriesByPrefix} from '../utils.ts';
import {pipelineVersionKeys} from './pipeline-version.ts';

export function usePipelineVersionsForAllPipelines(): PipelineVersionsInfo[] {
  const queries = useQueriesByPrefix(pipelineVersionKeys.all);
  return useMemo(
    () =>
      queries
        .filter((query) => query.state.data !== undefined)
        .map((query) => {
          const pipelineId = query.queryKey[2] as number;
          return {
            pending: query.state.fetchStatus === 'fetching',
            loaded: query.state.status === 'success',
            error: query.state.error ? String(query.state.error) : undefined,
            pipelineId,
            versions: (query.state.data ?? []) as Revision[],
          };
        }),
    [queries],
  );
}
