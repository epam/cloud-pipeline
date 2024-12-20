import type { Run } from '@cloud-pipeline/core';
import { RunStatuses } from '@cloud-pipeline/core';
import type { TagProps } from 'antd/es/tag';

type BadgeStyle = Pick<TagProps, 'color' | 'bordered' | 'icon'>;

const statusBadgeStyleMap: Record<Run['status'], BadgeStyle> = {
  [RunStatuses.success]: { color: 'success' },
  [RunStatuses.running]: { color: 'success' },
  [RunStatuses.failure]: { color: 'error' },
  [RunStatuses.stopped]: { color: 'orange' },
  [RunStatuses.resuming]: { color: 'processing' },
  [RunStatuses.paused]: { color: 'processing' },
  [RunStatuses.pausing]: { color: 'processing' },
};

export const getStatusBadgeStyle = (
  status: Run['status'],
): BadgeStyle | null => {
  return statusBadgeStyleMap[status] || null;
};
