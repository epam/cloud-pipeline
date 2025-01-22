import type { RunLog } from '@cloud-pipeline/core';

export type MappedLog = RunLog & {
  html: string;
  index: number;
};
