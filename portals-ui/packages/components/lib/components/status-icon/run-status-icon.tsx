import { useMemo } from 'react';
import classNames from 'classnames';
import { Tooltip } from 'antd';
import { RunStatuses } from '@cloud-pipeline/core';
import type { CommonProps } from '../common.types';
import {
  PlayCircleIcon,
  PauseCircleIcon,
  StopCircleIcon,
} from '@heroicons/react/24/outline';

type StatusIconProps = CommonProps & {
  status: RunStatuses;
  showTooltip?: boolean;
};

const icons = {
  [RunStatuses.success]: PlayCircleIcon,
  [RunStatuses.running]: PlayCircleIcon,
  [RunStatuses.failure]: StopCircleIcon,
  [RunStatuses.stopped]: StopCircleIcon,
  [RunStatuses.resuming]: PlayCircleIcon,
  [RunStatuses.paused]: PauseCircleIcon,
  [RunStatuses.pausing]: PauseCircleIcon,
};

const statusClassNames: Record<RunStatuses, string> = {
  [RunStatuses.success]: 'stroke-sky-600',
  [RunStatuses.running]: 'stroke-sky-600',
  [RunStatuses.failure]: 'stroke-red-600',
  [RunStatuses.stopped]: 'stroke-yellow-500',
  [RunStatuses.resuming]: 'stroke-sky-600',
  [RunStatuses.paused]: 'stroke-yellow-500',
  [RunStatuses.pausing]: 'stroke-yellow-500',
};

export function RunStatusIcon({
  className,
  showTooltip = false,
  style,
  status,
}: StatusIconProps) {
  const StatusIcon = useMemo(() => icons[status], [status]);
  return (
    <Tooltip
      title={showTooltip ? <span className="text-xs">{status}</span> : null}
      overlayClassName="min-w-2">
      <StatusIcon
        className={classNames(statusClassNames[status], className)}
        style={style}
      />
    </Tooltip>
  );
}
