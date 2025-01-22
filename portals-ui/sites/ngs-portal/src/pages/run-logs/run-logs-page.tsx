import { useRunInfo } from '../../shared/hooks/use-run-info.ts';
import { useParams } from 'react-router';
import { Spin } from 'antd';
import RunLogsHeader from './run-logs-header.tsx';
import { useRunLogsTabs } from './hooks/use-run-logs-tabs.tsx';
import { ItemLayout } from '../../shared/ui/index.ts';

export function RunLogsPage() {
  const { runId } = useParams();
  const { run, pending, refresh } = useRunInfo(runId);
  const { activeTab, tabs, handleChangeTab } = useRunLogsTabs(run);
  if (!run || pending) {
    return <Spin spinning />;
  }
  return (
    <div className="overflow-hidden gap-4 h-full w-full flex flex-col">
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
            refresh={refresh}
          />
        }
        main={activeTab.content}
      />
    </div>
  );
}
