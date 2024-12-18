import type { CommonProps } from '@cloud-pipeline/components';
import type { ReactNode } from 'react';
import { LayoutCard } from './layout-card';

type Props = CommonProps & {
  header: ReactNode;
  main: ReactNode;
  asideTop?: ReactNode;
  asideBottom?: ReactNode;
};

export const ItemLayout = ({
  asideTop,
  asideBottom,
  header,
  main,
  className,
  style,
}: Props) => {
  return (
    <div className={`flex flex-col space-y-4 ${className}`} style={style}>
      <LayoutCard>{header}</LayoutCard>

      <div className="flex flex-1 space-x-4 flex-grow">
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
