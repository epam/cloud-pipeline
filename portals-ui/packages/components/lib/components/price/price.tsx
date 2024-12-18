import { PriceProps } from './types.ts';

export function Price(props: PriceProps) {
  const { className, style, amount, currency = '$' } = props;
  if (amount === undefined) {
    return null;
  }
  return (
    <span className={className} style={style}>
      {`${currency} ${(Math.ceil(Math.max(1, amount * 100)) / 100).toFixed(2)}`}
    </span>
  );
}
