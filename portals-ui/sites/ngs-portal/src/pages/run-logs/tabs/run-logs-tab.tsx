import { type Run } from '@cloud-pipeline/core';
import { LogsViewer, type CommonProps } from '@cloud-pipeline/components';
import { useRunsLogs } from '../../../shared/hooks/use-runs-logs';

type Props = CommonProps & {
  run: Run;
};

export function RunLogsTab({ run }: Props) {
  const { logs } = useRunsLogs(run.id, { task: 'Console' });
  return <LogsViewer className="h-full rounded-md" logs={logs} />;
}
