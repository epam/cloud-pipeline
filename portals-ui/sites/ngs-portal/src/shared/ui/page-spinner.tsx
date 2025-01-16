import type { CommonProps } from '@cloud-pipeline/components';
import { Spin } from 'antd';
import cn from 'classnames';

export const PageSpinner = ({ className, style }: CommonProps) => {
  return (
    <div
      className={cn('size-full flex items-center justify-center', className)}
      style={style}>
      <Spin size="large" />
    </div>
  );
};
