import type { RunLog } from '@cloud-pipeline/core';
import { LogsViewer, type CommonProps } from '@cloud-pipeline/components';

type Props = CommonProps & {
  logs?: RunLog[];
};

export function RunLogsTab({ logs }: Props) {
  return <LogsViewer className="h-full rounded-md" logs={logs} />;
}
