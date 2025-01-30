import { useParams } from 'react-router';
import { Alert, Spin } from 'antd';
import RunLogsHeader from './run-logs-header.tsx';
import { useRunLogsTabs } from './hooks/use-run-logs-tabs.tsx';
import { ItemLayout } from '../../shared/ui/index.ts';
import useRunWithLogsInfo from './hooks/use-run-with-logs-info.tsx';
import './style.css';

export function RunLogsPage() {
  const { runId } = useParams();
  const { pending, error, run, logs, refreshRun } = useRunWithLogsInfo(
    Number(runId),
  );
  const { activeTab, tabs, handleChangeTab } = useRunLogsTabs(run, logs);
  if (error) {
    return <Alert message={error} type="error" />;
  }
  return (
    <div className="overflow-hidden gap-4 h-full w-full flex flex-col">
      <Spin wrapperClassName="spin-container" spinning={pending || !run}>
        <ItemLayout
          classes={{
            content: 'overflow-hidden flex grow',
            layoutCard: 'overflow-hidden',
          }}
          header={
            <RunLogsHeader
              activeKey={activeTab.key}
              tabs={tabs}
              onChangeTab={handleChangeTab}
              run={run}
              refresh={refreshRun}
            />
          }
          main={activeTab.content}
        />
      </Spin>
    </div>
  );
}
