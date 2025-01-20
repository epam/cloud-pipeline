import { Breadcrumb } from 'antd';
import { Link } from 'react-router-dom';
import { HomeIcon } from '@heroicons/react/24/solid';
import { ItemLayout } from '../../shared/ui';
import { AppRoutes, RoutePath } from '../../shared/constants/routes';
import { usePipelineTabs, usePipelineVersions } from './hooks';
import type { Pipeline, PipelineVersion } from '@cloud-pipeline/core';
import { PipelineHeader } from './components';

type Props = {
  pipeline: Pipeline;
  versions: PipelineVersion[];
};

export function PipelinePage({ pipeline, versions }: Props) {
  const { onChangeVersion, version } = usePipelineVersions(versions);

  const { activeTab, tabs, handleChangeTab } = usePipelineTabs(
    pipeline,
    version,
  );

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
