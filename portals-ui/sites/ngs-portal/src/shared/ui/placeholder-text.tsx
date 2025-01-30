import type { CommonProps } from '@cloud-pipeline/components';
import cn from 'classnames';
import type { PropsWithChildren } from 'react';

type Props = PropsWithChildren<CommonProps>;

export const PlaceholderText = ({ children, className, style }: Props) => {
  return (
    <p className={cn(`text-faded text-xs ${className}`)} style={style}>
      {children}
    </p>
  );
};
