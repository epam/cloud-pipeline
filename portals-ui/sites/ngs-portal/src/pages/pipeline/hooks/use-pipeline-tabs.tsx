import { useMemo, useCallback, useEffect } from 'react';
import { Markdown } from '@cloud-pipeline/components';
import type { Pipeline } from '@cloud-pipeline/core';
import { LayoutCard } from '../../../shared/ui/item-layout/layout-card';
import { dummyDescription } from '../dummy.description';
import { useNavigate, useParams } from 'react-router-dom';
import {
  generatePipelineRoutePath,
  PipelineTabs,
} from '../../../shared/constants/routes';
import { PipelineRunsList } from '../components';

export const usePipelineTabs = (pipeline: Pipeline) => {
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
        label: <span className="px-4">Info</span>,
        content: <Markdown>{dummyDescription}</Markdown>,
        aside: [
          <LayoutCard key="runs">
            <PipelineRunsList pipelineId={pipeline.id} />
          </LayoutCard>,
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
        // content: <ProjectPipelines project={pipeline} />,
      },
      {
        key: PipelineTabs.RunHistory,
        label: <span className="px-4">RunHistory</span>,
        content: <PipelineRunsList pipelineId={pipeline.id} extended />,
      },
    ],
    [pipeline.id],
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
