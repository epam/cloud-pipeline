import type { Run } from '@cloud-pipeline/core';
import { RunStatuses } from '@cloud-pipeline/core';
import type { BadgeProps } from '@epam/uui';

type BadgeStyle = {
  fill?: BadgeProps['fill'];
  color?: BadgeProps['color'];
};

const statusBadgeStyleMap: Record<Run['status'], BadgeStyle> = {
  [RunStatuses.success]: { fill: 'solid', color: 'success' },
  [RunStatuses.running]: { fill: 'outline', color: 'success' },
  [RunStatuses.failure]: { fill: 'solid', color: 'critical' },
  [RunStatuses.stopped]: { fill: 'outline', color: 'critical' },
  [RunStatuses.resuming]: { fill: 'outline', color: 'info' },
  [RunStatuses.paused]: { fill: 'outline', color: 'neutral' },
  [RunStatuses.pausing]: { fill: 'outline', color: 'info' },
};

export const getStatusBadgeStyle = (
  status: Run['status'],
): BadgeStyle | null => {
  return statusBadgeStyleMap[status] || null;
};
