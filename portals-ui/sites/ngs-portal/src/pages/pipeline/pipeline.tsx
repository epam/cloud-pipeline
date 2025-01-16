import { Breadcrumb } from 'antd';
import { Link } from 'react-router-dom';
import { HomeIcon } from '@heroicons/react/24/solid';
import { ItemLayout } from '../../shared/ui';
import { AppRoutes, RoutePath } from '../../shared/constants/routes';
import { usePipelineTabs } from './hooks';
import type { Pipeline } from '@cloud-pipeline/core';
import { PipelineHeader } from './components';

type Props = {
  pipeline: Pipeline;
};

export function PipelinePage({ pipeline }: Props) {
  const { activeTab, tabs, handleChangeTab } = usePipelineTabs(pipeline);

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
          />
        }
        main={activeTab.content}
        aside={activeTab.aside}
      />
    </div>
  );
}
