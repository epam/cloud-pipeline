import { useMemo } from 'react';
import { evaluateRunPrice } from '@cloud-pipeline/core';
import { RunPriceProps } from './types';
import { Price } from './price';

export function RunPrice(props: RunPriceProps) {
  const { run, ...rest } = props;
  const amount = useMemo(
    () => (run ? evaluateRunPrice(run).total : undefined),
    [run],
  );
  return <Price {...rest} amount={amount} />;
}
