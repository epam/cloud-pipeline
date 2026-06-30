import {useQuery} from '@tanstack/react-query';

import type {CommonProps} from '../../../../@types/common.ts';
import {pipelineQueryOptions} from '../../../../queries';
import {GitRepositoryPopover} from '../shared/git-repository-popover.tsx';

type GitRepositoryActionProps = CommonProps & {
  pipelineId?: number | string;
  version?: string;
};

function GitRepositoryAction({pipelineId}: GitRepositoryActionProps) {
  const numericId = pipelineId !== undefined ? Number(pipelineId) : undefined;
  const {data: pipeline} = useQuery(pipelineQueryOptions(numericId));

  return <GitRepositoryPopover https={pipeline?.repository} ssh={pipeline?.repositorySsh} />;
}

export {GitRepositoryAction};
