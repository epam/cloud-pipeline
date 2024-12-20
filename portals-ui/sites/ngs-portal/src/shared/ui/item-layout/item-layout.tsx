import type { CommonProps } from '@cloud-pipeline/components';
import { Children, ReactNode } from 'react';
import { LayoutCard } from './layout-card';
import cn from 'classnames';
import classNames from 'classnames';
import './styles.css';

type Props = CommonProps & {
  header: ReactNode;
  main: ReactNode;
  aside?: ReactNode;
  classes?: {
    container?: string;
    content?: string;
  };
};

export const ItemLayout = ({
  aside,
  header,
  main,
  style,
  classes = {},
}: Props) => {
  const asidePanels = Children.toArray(aside);
  const hasAsidePanels = asidePanels.length > 0;
  return (
    <div
      className={cn(
        'flex flex-col space-y-4 h-full overflow-hidden',
        classes.container,
      )}
      style={style}>
      <LayoutCard>{header}</LayoutCard>
      <div className={cn('flex flex-1 space-x-4 flex-grow', classes.content)}>
        <LayoutCard
          className={classNames(
            'overflow-auto',
            hasAsidePanels ? 'w-2/3' : 'w-full',
          )}>
          {main}
        </LayoutCard>
        {asidePanels.length > 0 && (
          <div className="item-layout-aside">{asidePanels}</div>
        )}
      </div>
    </div>
  );
};
