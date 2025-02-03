import { Select, Tabs } from 'antd';
import type { TabsProps } from 'antd';
import type { CommonProps } from '@cloud-pipeline/components';
import type { Pipeline, PipelineVersion } from '@cloud-pipeline/core';
import { usePipelineDisplayName } from '../../../shared/hooks/use-pipeline-display-name.ts';

type Props = CommonProps & {
  pipeline: Pipeline;
  tabs: TabsProps['items'];
  onChangeTab: (tabKey: string) => void;
  activeKey: string;
  versions: PipelineVersion[];
  onChangeVersion: (index: number) => void;
  versionName: string;
};

export const PipelineHeader = (props: Props) => {
  const {
    pipeline,
    versions,
    tabs,
    onChangeTab,
    activeKey,
    onChangeVersion,
    versionName,
  } = props;

  const options = versions.map(({ name }, i) => ({
    value: i,
    label: name,
  }));

  const currentVersion =
    options.find(({ label }) => label === versionName)?.value ??
    options[0]?.value;

  const pipelineName = usePipelineDisplayName(pipeline);

  return (
    <div className="flex flex-col gap-2">
      <div className="flex flex-nowrap gap-1 items-center">
        <b className="text-lg mr-1">{pipelineName}</b>
        <Select
          className="ml-4 min-w-[150px]"
          disabled={versions.length === 1}
          value={currentVersion}
          onChange={onChangeVersion}
          placeholder="Select version"
          options={options}
        />
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
