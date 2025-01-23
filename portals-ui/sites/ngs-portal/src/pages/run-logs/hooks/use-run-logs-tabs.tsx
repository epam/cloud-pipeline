import { useMemo, useCallback, useEffect } from 'react';
import type { Run } from '@cloud-pipeline/core';
import { useNavigate, useParams } from 'react-router-dom';
import {
  generateRunLogsRoutePath,
  RunLogsTabs,
} from '../../../shared/constants/routes';
import { RunLogsTab } from '../tabs/run-logs-tab';
import { RunLogsParametersTab } from '../tabs/run-logs-parameters';

export const useRunLogsTabs = (run: Run | undefined) => {
  const { tabId } = useParams();
  const navigate = useNavigate();
  useEffect(() => {
    if (!run) {
      return;
    }
    const isValidTab =
      !tabId || Object.values(RunLogsTabs).includes(tabId as RunLogsTabs);
    if (!isValidTab) {
      navigate(generateRunLogsRoutePath(run.id, RunLogsTabs.Logs));
    }
  }, [navigate, run, run?.id, tabId]);
  const handleChangeTab = useCallback(
    (key: string) => {
      if (run) {
        navigate(generateRunLogsRoutePath(run.id, key as RunLogsTabs));
      }
    },
    [navigate, run],
  );
  const tabs = useMemo(
    () => [
      {
        key: RunLogsTabs.Logs,
        label: <span className="px-4">Logs</span>,
        content: run ? <RunLogsTab run={run} /> : null,
      },
      {
        key: RunLogsTabs.Parameters,
        label: <span className="px-4">Parameters</span>,
        content: <RunLogsParametersTab run={run} />,
      },
    ],
    [run],
  );
  const activeTab = useMemo(
    () => tabs.find((tab) => tab.key === tabId) ?? tabs[0],
    [tabId, tabs],
  );
  return {
    activeTab,
    tabs,
    handleChangeTab,
  };
};
