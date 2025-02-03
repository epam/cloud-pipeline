import type { CommonProps } from '@cloud-pipeline/components';
import { Breadcrumb, Skeleton } from 'antd';
import type {
  BreadcrumbItemType,
  BreadcrumbSeparatorType,
} from 'antd/es/breadcrumb/Breadcrumb';
import { useMemo } from 'react';

type Props = CommonProps & {
  items: Partial<BreadcrumbItemType & BreadcrumbSeparatorType>[];
  showSkeleton?: boolean;
};

export default function NgsBreadcrumbs({
  items: itemsProp,
  showSkeleton,
}: Props) {
  const items = useMemo(() => {
    if (showSkeleton) {
      return itemsProp.map((_, index) => ({
        title: (
          <Skeleton.Node
            active
            style={{ width: index === 0 ? 20 : 80, height: 18 }}
            className="pb-1.5"
          />
        ),
      }));
    }
    return itemsProp;
  }, [itemsProp, showSkeleton]);
  return <Breadcrumb items={items} />;
}
