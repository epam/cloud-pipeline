import { ItemLayout } from '../../shared/ui';
import { generatePipelineRoutePath, PipelineTabs } from '../../shared/constants/routes';
import { usePipelineLanguage, usePipelineVersions } from './hooks';
import type { Project } from '@cloud-pipeline/core';
import { AclClass, PipelineLanguages, type Pipeline, type PipelineVersion } from '@cloud-pipeline/core';
import { PipelineHeader, PipelineRunsList } from './components';
import { useMemo } from 'react';
import { LayoutCard } from '../../shared/ui/item-layout/layout-card';
import { PipelineFiles } from './components/pipeline-files';
import { Permissions } from '../../features/permissions';
import { useNgsTabs } from '../../shared/hooks';
import { NgsBreadcrumbs } from '../../widgets/ngs-breadcrumbs';
import usePipelineBreadcrumbs from './hooks/use-pipeline-breadcrumbs';
import PipelineWorkflow from './components/pipeline-workflow';

type Props = {
  pipeline: Pipeline;
  versions: PipelineVersion[];
  parentProject?: Project;
  pending?: boolean;
};

export function PipelinePage({ pipeline, pending, parentProject, versions }: Props) {
  const { onChangeVersion, version } = usePipelineVersions(versions);
  const { language } = usePipelineLanguage(pipeline.id, version);
  console.log('lang', language);
  const breadcrumbs = usePipelineBreadcrumbs(pipeline, parentProject);
  const tabs = useMemo(
    () =>
      [
        {
          key: PipelineTabs.Documents,
          label: <span className="px-4">Documents</span>,
          content: <PipelineFiles pipelineId={pipeline.id} version={version} />,
          aside: [
            <LayoutCard key="runs">
              <PipelineRunsList pipelineId={pipeline.id} version={version} />
            </LayoutCard>,
            <LayoutCard key="permissions">
              <Permissions entityId={pipeline.id} aclClass={AclClass.pipeline} />
            </LayoutCard>,
          ],
        },
        {
          key: PipelineTabs.Code,
          label: <span className="px-4">Code</span>,
          content: <div>Code</div>,
          disabled: true,
        },
        {
          key: PipelineTabs.Configuration,
          label: <span className="px-4">Configuration</span>,
          content: <div>Configuration</div>,
          disabled: true,
        },
        {
          key: PipelineTabs.RunHistory,
          label: <span className="px-4">Runs History</span>,
          content: <PipelineRunsList pipelineId={pipeline.id} version={version} extended />,
        },
        {
          key: PipelineTabs.Workflow,
          label: <span className="px-4">Workflow</span>,
          content: <PipelineWorkflow pipeline={pipeline} version={version} />,
          visible: () => language === PipelineLanguages.cwl,
        },
      ].filter((tab) => tab.visible?.() ?? true),
    [language, pipeline, version],
  );
  const { activeTab, handleChangeTab } = useNgsTabs({
    entityId: pipeline.id,
    tabs,
    generatePath: generatePipelineRoutePath,
  });
  return (
    <div className="flex flex-col h-full">
      <NgsBreadcrumbs items={breadcrumbs} showSkeleton={pending} />
      <ItemLayout
        classes={{ content: 'overflow-hidden' }}
        header={
          <PipelineHeader
            pipeline={pipeline}
            tabs={tabs}
            onChangeTab={handleChangeTab}
            activeKey={activeTab.key}
            versions={versions}
            onChangeVersion={onChangeVersion}
            versionName={version}
          />
        }
        main={activeTab.content}
        aside={activeTab.aside}
      />
    </div>
  );
}
