import { useMemo, useCallback, useEffect } from 'react';
import type { Pipeline } from '@cloud-pipeline/core';
import { LayoutCard } from '../../../shared/ui/item-layout/layout-card';
import { useNavigate, useParams } from 'react-router-dom';
import {
  generatePipelineRoutePath,
  PipelineTabs,
} from '../../../shared/constants/routes';
import { PipelineRunsList } from '../components';
import { PipelineFiles } from '../components/pipeline-files';

export const usePipelineTabs = (pipeline: Pipeline, version: string) => {
  const { tabId } = useParams();
  const navigate = useNavigate();

  useEffect(() => {
    const isValidTab =
      !tabId || Object.values(PipelineTabs).includes(tabId as PipelineTabs);

    if (!isValidTab) {
      navigate(generatePipelineRoutePath(pipeline.id, PipelineTabs.Documents));
    }
  }, [navigate, pipeline.id, tabId]);

  const handleChangeTab = useCallback(
    (key: string) => {
      navigate(generatePipelineRoutePath(pipeline.id, key as PipelineTabs));
    },
    [navigate, pipeline.id],
  );

  const tabs = useMemo(
    () => [
      {
        key: PipelineTabs.Documents,
        label: <span className="px-4">Documents</span>,
        content: <PipelineFiles pipelineId={pipeline.id} version={version} />,
        aside: [
          <LayoutCard key="runs">
            <PipelineRunsList pipelineId={pipeline.id} version={version} />
          </LayoutCard>,
          <LayoutCard key="permissions">Permissions</LayoutCard>,
        ],
      },
      {
        key: PipelineTabs.Code,
        label: <span className="px-4">Code</span>,
        content: <div>Code</div>,
      },
      {
        key: PipelineTabs.Configuration,
        label: <span className="px-4">Configuration</span>,
        content: <div>Configuration</div>,
      },
      {
        key: PipelineTabs.RunHistory,
        label: <span className="px-4">RunHistory</span>,
        content: (
          <PipelineRunsList
            pipelineId={pipeline.id}
            version={version}
            extended
          />
        ),
      },
    ],
    [pipeline.id, version],
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
