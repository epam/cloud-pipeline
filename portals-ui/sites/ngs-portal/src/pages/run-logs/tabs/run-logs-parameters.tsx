import type { Run } from '@cloud-pipeline/core';
import { type CommonProps } from '@cloud-pipeline/components';
import RunParametersTable from '../../../widgets/run-parameters-table';

type Props = CommonProps & {
  run?: Run;
};

export function RunLogsParametersTab({ run }: Props) {
  return (
    <div className="flex flex-col pag-2">
      <RunParametersTable run={run} />
    </div>
  );
}
