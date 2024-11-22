import type { CSSProperties, ReactNode } from 'react';

export type CommonProps = {
  className?: string;
  style?: CSSProperties;
};

export type CommonParentProps = CommonProps & {
  children?: ReactNode;
};
