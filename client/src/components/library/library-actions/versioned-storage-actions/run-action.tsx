import {Button} from 'antd';
import {useQuery} from '@tanstack/react-query';

import type {CommonProps} from '../../../../@types/common.ts';
import {pipelineQueryOptions} from '../../../../queries/pipelines/pipeline.ts';
import roleModel from '../../../../utils/roleModel.jsx';

type RunActionProps = CommonProps & {
  storageId?: number | string;
  readOnly?: boolean;
  onRun?: () => void;
};

function RunAction(props: RunActionProps) {
  const {storageId, readOnly = false, onRun} = props;

  const numericId = storageId !== undefined ? Number(storageId) : undefined;
  const {data: pipeline} = useQuery(
    pipelineQueryOptions(numericId, {enabled: numericId !== undefined}),
  );

  if (!pipeline || !roleModel.executeAllowed(pipeline)) {
    return null;
  }

  return (
    <Button
      size="small"
      type="primary"
      disabled={readOnly}
      onClick={() => onRun?.()}
    >
      RUN
    </Button>
  );
}

export {RunAction};
