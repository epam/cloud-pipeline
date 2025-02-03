import classNames from 'classnames';
import { Tabs } from 'antd';
import type { TabsProps } from 'antd';
import {
  RunPrice,
  RunStatusIcon,
  Tag,
  type CommonProps,
} from '@cloud-pipeline/components';
import {
  displayDate,
  displayDurationInSeconds,
  getRunDurationInfo,
  type Run,
} from '@cloud-pipeline/core';
import { NgsUserCard } from '../../widgets/cards';
import RunHeaderControls from './run-header-controls';
import { useRunDisplayName } from '../../shared/hooks';

type Props = CommonProps & {
  run?: Run;
  activeKey: string;
  tabs: TabsProps['items'];
  onChangeTab: (tabKey: string) => void;
  refresh: () => Promise<void>;
};

export default function RunLogsHeader({
  run,
  className,
  activeKey,
  tabs,
  onChangeTab,
  refresh,
}: Props) {
  const runName = useRunDisplayName(run, 'Run');
  const { totalDuration } = getRunDurationInfo(run) ?? {};
  if (!run) {
    return;
  }
  return (
    <div className={classNames('flex flex-col gap-2', className)}>
      <div className="flex flex-nowrap gap-1 items-center">
        <RunStatusIcon
          showTooltip
          status={run.status}
          className="shrink-0 w-5 h-5"
        />
        <b className="text-lg mr-1">{runName}</b>
        <Tag className="mr-0">
          <NgsUserCard userName={run.owner} showIcon />
        </Tag>
        <RunHeaderControls
          className="ml-auto"
          run={run}
          runName={runName}
          refresh={refresh}
        />
      </div>
      <div className="flex flex-nowrap gap-1 items-center">
        <span className="text-xs">
          <span className="mr-1 text-faded">Started:</span>
          <span>{displayDate(run.startDate ?? '')}</span>
        </span>
      </div>
      {run.endDate ? (
        <div className="flex flex-nowrap gap-1 items-center">
          <span className="text-xs">
            <span className="mr-1 text-faded">Ended:</span>
            <span>{displayDate(run.endDate ?? '')}</span>
            {totalDuration ? (
              <span className="ml-1">
                ({displayDurationInSeconds(totalDuration)})
              </span>
            ) : null}
          </span>
        </div>
      ) : null}
      <span className="text-xs">
        <span className="text-faded">Estimated price: </span>
        <RunPrice run={run} />
      </span>
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
}
