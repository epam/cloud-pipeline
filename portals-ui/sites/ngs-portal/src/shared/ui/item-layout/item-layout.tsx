import type { CommonProps } from '@cloud-pipeline/components';
import type { ReactNode } from 'react';
import { LayoutCard } from './layout-card';
import cn from 'classnames';

type Props = CommonProps & {
  header: ReactNode;
  main: ReactNode;
  asideTop?: ReactNode;
  asideBottom?: ReactNode;
  classes?: {
    container?: string;
    content?: string;
  };
};

export const ItemLayout = ({
  asideTop,
  asideBottom,
  header,
  main,
  style,
  classes = {},
}: Props) => {
  return (
    <div
      className={cn(
        'flex flex-col space-y-4 h-full overflow-hidden',
        classes.container,
      )}
      style={style}>
      <LayoutCard>{header}</LayoutCard>
      <div className={cn('flex flex-1 space-x-4 flex-grow', classes.content)}>
        <LayoutCard className={asideTop ? 'w-2/3' : 'w-full'}>
          {main}
        </LayoutCard>
        {asideTop && (
          <div className="w-1/3 flex flex-col space-y-4">
            <LayoutCard className="flex-grow">{asideTop}</LayoutCard>
            {asideBottom && (
              <LayoutCard className="flex-grow">{asideBottom}</LayoutCard>
            )}
          </div>
        )}
      </div>
    </div>
  );
};
