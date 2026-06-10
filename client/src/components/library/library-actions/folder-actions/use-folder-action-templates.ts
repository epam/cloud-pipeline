import {useMemo} from 'react';
import {useQuery} from '@tanstack/react-query';
import {folderTemplatesQueryOptions, pipelineTemplatesQueryOptions} from '../../../../queries';
import {TemplateDescription} from '../../../../@types/app.ts';

type FolderActionTemplatesState = {
  pipelineTemplates: TemplateDescription[];
  folderTemplates: TemplateDescription[];
  pending: boolean;
  error?: string;
};

export function useFolderActionTemplates(): FolderActionTemplatesState {
  const {data: pipelineTemplates = [], isFetching: pipelineTemplatesPending} = useQuery(
    pipelineTemplatesQueryOptions(),
  );
  const {data: folderTemplates = [], isFetching: folderTemplatesPending} = useQuery(
    folderTemplatesQueryOptions(),
  );

  return useMemo(
    () => ({
      pipelineTemplates,
      folderTemplates,
      pending: pipelineTemplatesPending || folderTemplatesPending,
    }),
    [pipelineTemplates, folderTemplates, pipelineTemplatesPending, folderTemplatesPending],
  );
}
