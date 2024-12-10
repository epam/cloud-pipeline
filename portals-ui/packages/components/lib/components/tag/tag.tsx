import { ReactNode } from 'react';
import classNames from 'classnames';
import { Tag as AntdTag } from 'antd';
import type { TagProps as AntdTagProps } from 'antd/es/tag';

export type TagProps = AntdTagProps & {
  icon?: ReactNode;
};

export function Tag(props: TagProps) {
  const { icon, children, className, ...antdTagProps } = props;
  return (
    <AntdTag
      className={classNames(className, 'inline-flex', 'leading-4', 'gap-0.5')}
      {...antdTagProps}>
      {icon}
      {children}
    </AntdTag>
  );
}
