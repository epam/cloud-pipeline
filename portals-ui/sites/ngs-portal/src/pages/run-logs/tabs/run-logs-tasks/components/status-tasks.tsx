import { Progress } from 'antd';
import { statusColors } from '../constants';
import type { EngineTaskStatus } from '@cloud-pipeline/core';
import { StatusPill } from './status-pill';

type Props = {
  percentage: number;
  statusTotal: number;
  status: EngineTaskStatus;
};

export const StatusTasks = ({ percentage, statusTotal, status }: Props) => {
  const color = statusColors[status];

  return (
    <div className="flex gap-2">
      <div className="flex min-w-[120px]">
        <StatusPill status={status} />
      </div>

      <p className="w-20 text-center">{statusTotal}</p>
      <Progress className="flex-grow" strokeColor={color} percent={percentage} showInfo={false} />
    </div>
  );
};
