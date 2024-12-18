import { Divider, Tabs } from 'antd';
import type { TabsProps } from 'antd';
import type { CommonProps } from '@cloud-pipeline/components';
import { Tag } from '@cloud-pipeline/components';
import type { Project } from '@cloud-pipeline/core';
import { NgsUserCard, NgsTag } from '../../widgets';
import { useMemo } from 'react';
import { extractTags } from '../../shared/tags';
import { PencilIcon } from '@heroicons/react/24/solid';

type Props = CommonProps & {
  project: Project | undefined;
  tabs: TabsProps['items'];
  onChangeTab: (tabKey: string) => void;
  activeKey: string;
};

const ProjectHeader = (props: Props) => {
  const { project, tabs, onChangeTab, activeKey } = props;
  const { data } = project ?? {};
  const tags = useMemo(() => extractTags(data), [data]);
  return (
    <div className="flex flex-col gap-2">
      <div>Breadcrumbs</div>
      <div className="panel shadow p-3 gap-1 flex flex-col">
        {project ? (
          <div className="flex flex-nowrap gap-1 items-center">
            <b className="text-lg mr-1">{project.name}</b>
            <Tag className="mr-0">
              <NgsUserCard userName={project.owner} showIcon />
            </Tag>
            <Divider
              className="h-full mx-2 border-slate-200 dark:border-neutral-700"
              type="vertical"
            />
            <div className="flex flex-wrap gap-1 items-center">
              {tags.map((tag) => (
                <NgsTag
                  key={tag.key}
                  tag={tag.key}
                  value={tag.value}
                  className="shrink-0 m-0"
                />
              ))}
              <span className="ml-1 cursor-pointer flex flex-nowrap items-center gap-1 text-xs text-link">
                <PencilIcon className="w-4 h-4" />
                <span>Edit Tags</span>
              </span>
            </div>
          </div>
        ) : null}
        <Tabs
          items={tabs}
          size="middle"
          tabBarStyle={{ fontWeight: 'bold', marginBottom: 0 }}
          onChange={onChangeTab}
          activeKey={activeKey}
          tabBarGutter={0}
        />
      </div>
    </div>
  );
};

export default ProjectHeader;
