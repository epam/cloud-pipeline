import { Divider, Tabs } from 'antd';
import type { TabsProps } from 'antd';
import type { CommonProps } from '@cloud-pipeline/components';
import { Tag } from '@cloud-pipeline/components';
import type { Project } from '@cloud-pipeline/core';
import { NgsUserCard } from '../../../widgets/cards';
import { NgsTag } from '../../../widgets/ngs-tag';
import { EditProjectTagsButton } from './edit-project-tags';
import { useProjectTags } from '../../../shared/tags/use-project-tags.ts';

type Props = CommonProps & {
  project: Project;
  tabs: TabsProps['items'];
  onChangeTab: (tabKey: string) => void;
  activeKey: string;
};

export const ProjectHeader = (props: Props) => {
  const { project, tabs, onChangeTab, activeKey } = props;
  const tags = useProjectTags(project?.data);
  return (
    <div className="flex flex-col gap-2">
      <div className="flex flex-nowrap gap-1 items-center">
        <b className="text-lg mr-1" data-cy="project-details-title">
          {project.name}
        </b>
        <Tag className="mr-0">
          <NgsUserCard userName={project.owner} showIcon />
        </Tag>
        <Divider className="h-6 mx-2 border-slate-200 dark:border-neutral-700" type="vertical" />
        <div className="flex flex-wrap gap-1 items-center">
          {tags.map((tag) => (
            <NgsTag key={tag.key} tag={tag.key} value={tag.value} className="shrink-0 m-0" />
          ))}
          <EditProjectTagsButton project={project} />
        </div>
      </div>

      <Tabs
        items={tabs}
        size="middle"
        tabBarStyle={{ fontWeight: 'bold', marginBottom: 0 }}
        onChange={onChangeTab}
        activeKey={activeKey}
        tabBarGutter={0}
      />
    </div>
  );
};
