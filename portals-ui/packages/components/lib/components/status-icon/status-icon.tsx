import { RunStatuses } from '@cloud-pipeline/core';
import type { CommonProps } from '../common.types';
import classNames from 'classnames';

type StatusIconProps = CommonProps & {
  status: RunStatuses;
  radius?: number;
  strokeWidth?: number;
};

const statusClassNames: Record<RunStatuses, string> = {
  [RunStatuses.success]: 'fill-green-600 stroke-green-600',
  [RunStatuses.running]: 'fill-transparent stroke-green-600',
  [RunStatuses.failure]: 'fill-red-600 stroke-red-600',
  [RunStatuses.stopped]: 'fill-transparent stroke-red-600',
  [RunStatuses.resuming]: 'fill-sky-600 stroke-sky-600',
  [RunStatuses.paused]: 'fill-slate-400 stroke-slate-400',
  [RunStatuses.pausing]: 'fill-sky-600 stroke-sky-600',
};

export function StatusIcon({
  radius = 5,
  strokeWidth = 3,
  className,
  style,
  status,
}: StatusIconProps) {
  return (
    <svg
      className={classNames(
        className,
        statusClassNames[status],
        'rounded-full',
      )}
      style={style}
      height={radius * 2}
      width={radius * 2}>
      <circle cx={radius} cy={radius} r={radius} strokeWidth={strokeWidth} />
    </svg>
  );
}
