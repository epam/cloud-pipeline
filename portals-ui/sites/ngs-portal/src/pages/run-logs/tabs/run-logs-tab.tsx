import { type Run } from '@cloud-pipeline/core';
import { LogsViewer, type CommonProps } from '@cloud-pipeline/components';
import { useRunLogs } from '../../../shared/hooks/use-run-logs';

type Props = CommonProps & {
  run: Run;
};

export function RunLogsTab({ run }: Props) {
  const { logs } = useRunLogs(run.id, { task: 'Console' });
  return <LogsViewer className="h-full rounded-md" logs={logs} />;
}
