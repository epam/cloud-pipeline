// constants.ts
import { EngineTaskStatus } from '@cloud-pipeline/core';
import {
  ClockIcon,
  PlusCircleIcon,
  PlayCircleIcon,
  CheckCircleIcon,
  ExclamationCircleIcon,
  XCircleIcon,
  MinusCircleIcon,
} from '@heroicons/react/24/outline';
import type { ComponentType, SVGProps } from 'react';

export const TASKS_PAGE_SIZE = 20;

export const orderedStatuses: EngineTaskStatus[] = [
  EngineTaskStatus.CREATED,
  EngineTaskStatus.SUBMITTED,
  EngineTaskStatus.RUNNING,
  EngineTaskStatus.COMPLETED,
  EngineTaskStatus.FAILED,
  EngineTaskStatus.ABORTED,
  EngineTaskStatus.CACHED,
];

export const statusColors: Record<EngineTaskStatus, string> = {
  [EngineTaskStatus.CREATED]: 'var(--ant-color-text)',
  [EngineTaskStatus.SUBMITTED]: 'var(--ant-cyan-6)',
  [EngineTaskStatus.RUNNING]: 'var(--ant-blue-6)',
  [EngineTaskStatus.COMPLETED]: 'var(--ant-green-6)',
  [EngineTaskStatus.FAILED]: 'var(--ant-red-6)',
  [EngineTaskStatus.ABORTED]: 'var(--ant-orange-6)',
  [EngineTaskStatus.CACHED]: 'var(--ant-purple-6)',
};

export const statusIcons: Record<EngineTaskStatus, ComponentType<SVGProps<SVGSVGElement>>> = {
  [EngineTaskStatus.CREATED]: ClockIcon,
  [EngineTaskStatus.SUBMITTED]: PlusCircleIcon,
  [EngineTaskStatus.RUNNING]: PlayCircleIcon,
  [EngineTaskStatus.COMPLETED]: CheckCircleIcon,
  [EngineTaskStatus.FAILED]: ExclamationCircleIcon,
  [EngineTaskStatus.ABORTED]: XCircleIcon,
  [EngineTaskStatus.CACHED]: MinusCircleIcon,
};
