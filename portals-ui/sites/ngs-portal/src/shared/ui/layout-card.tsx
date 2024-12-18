import type { CommonProps } from '@cloud-pipeline/components';
import type { ReactNode } from 'react';

type Props = CommonProps & { children: ReactNode };

export const LayoutCard = ({ children, className }: Props) => {
  return (
    <div className={`p-4 bg-white rounded-md shadow-lg ${className}`}>
      {children}
    </div>
  );
};
