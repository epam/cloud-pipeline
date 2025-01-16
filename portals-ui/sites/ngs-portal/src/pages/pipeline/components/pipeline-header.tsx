import { Tabs } from 'antd';
import type { TabsProps } from 'antd';
import type { CommonProps } from '@cloud-pipeline/components';
import type { Pipeline } from '@cloud-pipeline/core';

type Props = CommonProps & {
  pipeline: Pipeline;
  tabs: TabsProps['items'];
  onChangeTab: (tabKey: string) => void;
  activeKey: string;
};

export const PipelineHeader = (props: Props) => {
  const { pipeline, tabs, onChangeTab, activeKey } = props;

  return (
    <div className="flex flex-col gap-2">
      <div className="flex flex-nowrap gap-1 items-center">
        <b className="text-lg mr-1">{pipeline.name}</b>
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
