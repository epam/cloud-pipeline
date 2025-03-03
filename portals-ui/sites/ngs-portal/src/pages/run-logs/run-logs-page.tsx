import { useParams } from 'react-router';
import { Alert, Spin } from 'antd';
import RunLogsHeader from './run-logs-header.tsx';
import { useRunLogsTabs } from './hooks/use-run-logs-tabs.tsx';
import { ItemLayout } from '../../shared/ui/index.ts';
import useRunWithLogsInfo from './hooks/use-run-with-logs-info.tsx';
import './style.css';
import NgsBreadcrumbs from '../../widgets/ngs-breadcrumbs/ngs-breadcrumbs.tsx';
import { useProjectsStore } from '../../state/projects/hooks.ts';
import useRunBreadcrumbs from './hooks/use-run-breadcrumbs.tsx';

export function RunLogsPage() {
  const { runId } = useParams();
  const { pending: projectsPending, getProjectById } = useProjectsStore();
  const { pending: runPending, error, run, logs, refreshRun } = useRunWithLogsInfo(Number(runId));
  const parentProject = getProjectById(Number(run?.projectId));
  const { activeTab, tabs, handleChangeTab } = useRunLogsTabs(run, logs);
  const breadcrumbs = useRunBreadcrumbs(run, parentProject);

  if (error) {
    return <Alert message={error} type="error" />;
  }

  return (
    <div className="overflow-hidden h-full w-full flex flex-col">
      <NgsBreadcrumbs items={breadcrumbs} showSkeleton={runPending || projectsPending} />
      <Spin wrapperClassName="spin-container" spinning={runPending || projectsPending || !run}>
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
