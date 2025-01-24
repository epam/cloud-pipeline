import { Breadcrumb } from 'antd';
import { Link } from 'react-router-dom';
import { HomeIcon } from '@heroicons/react/24/solid';
import { ItemLayout } from '../../shared/ui';
import {
  AppRoutes,
  generatePipelineRoutePath,
  PipelineTabs,
  RoutePath,
} from '../../shared/constants/routes';
import { usePipelineVersions } from './hooks';
import {
  AclClass,
  type Pipeline,
  type PipelineVersion,
} from '@cloud-pipeline/core';
import { PipelineHeader, PipelineRunsList } from './components';
import { useMemo } from 'react';
import { LayoutCard } from '../../shared/ui/item-layout/layout-card';
import { PipelineFiles } from './components/pipeline-files';
import { Permissions } from '../../features/permissions';
import { useNgsTabs } from '../../shared/hooks';

type Props = {
  pipeline: Pipeline;
  versions: PipelineVersion[];
};

export function PipelinePage({ pipeline, versions }: Props) {
  const { onChangeVersion, version } = usePipelineVersions(versions);

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
          <LayoutCard key="permissions">
            <Permissions entityId={pipeline.id} aclClass={AclClass.pipeline} />
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
      },
      {
        key: PipelineTabs.RunHistory,
        label: <span className="px-4">Runs History</span>,
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

  const { activeTab, handleChangeTab } = useNgsTabs({
    entityId: pipeline.id,
    tabs,
    generatePath: generatePipelineRoutePath,
  });

  return (
    <div className="flex flex-col h-full">
      <Breadcrumb
        items={[
          {
            title: (
              <Link to="/">
                <HomeIcon className="w-5 h-5" />
              </Link>
            ),
          },
          { title: <Link to={RoutePath[AppRoutes.PIPELINES]}>Pipelines</Link> },
          { title: pipeline.name },
        ]}
      />

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
