import { Progress } from 'antd';
import { statusIcons, statusColors } from './constants';
import type { EngineTaskStatus } from '@cloud-pipeline/core';

type Props = {
  percentage: number;
  statusTotal: number;
  status: EngineTaskStatus;
};

export const StatusTasks = ({ percentage, statusTotal, status }: Props) => {
  const Icon = statusIcons[status];
  const color = statusColors[status];

  return (
    <div className="flex gap-2">
      <div className="flex min-w-[120px]">
        <div
          className="inline-flex shrink items-center gap-0.5 px-1 text-white font-bold rounded-md"
          style={{ backgroundColor: color }}>
          <Icon className="w-4 h-4 text-white" />
          <p>{status}</p>
        </div>
      </div>

      <p className="w-20 text-center">{statusTotal}</p>
      <Progress className="flex-grow" strokeColor={color} percent={percentage} showInfo={false} />
    </div>
  );
};
