import type { EngineTaskStatus } from '@cloud-pipeline/core';
import { statusIcons, statusColors } from '../constants';

type Props = {
  status: EngineTaskStatus;
};

export const StatusPill = ({ status }: Props) => {
  const Icon = statusIcons[status];
  const color = statusColors[status];

  return (
    <div
      className="inline-flex items-center gap-0.5 px-1 text-white font-bold rounded-md"
      style={{ backgroundColor: color }}>
      <Icon className="w-4 h-4 text-white" />
      <p>{status}</p>
    </div>
  );
};
