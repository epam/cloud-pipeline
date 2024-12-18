import { Run } from '@cloud-pipeline/core';
import { CommonProps } from '../common.types.ts';

export type PriceProps = CommonProps & {
  amount?: number;
  currency?: string;
};

export type RunPriceProps = Omit<PriceProps, 'amount'> & {
  run?: Run;
};
