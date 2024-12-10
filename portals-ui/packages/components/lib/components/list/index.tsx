import type { ReactNode } from 'react';
import { useMemo } from 'react';
import classNames from 'classnames';
import VirtualList from '../virtual-list';
import type { ListProps } from './types';
import ListHeader from './list-header';
import { useItemKey } from '../virtual-list/utilities.ts';

const MIN_VISIBLE_COUNT = 20;

export default function List<Item>(props: ListProps<Item>): ReactNode {
  const {
    header,
    footer,
    items: items,
    render,
    virtualized = false,
    style,
    className,
    itemKey,
    sliced,
    pageSize,
  } = props;
  const itemsToDisplayCount = useMemo(() => {
    if (typeof sliced === 'boolean') {
      return sliced ? MIN_VISIBLE_COUNT : Infinity;
    }
    if (typeof sliced === 'number') {
      return sliced;
    }
    return Infinity;
  }, [sliced]);
  const slicedData = useMemo(
    () => items.slice(0, itemsToDisplayCount),
    [items, itemsToDisplayCount],
  );
  const getItemKey = useItemKey(itemKey);
  const listComponent = virtualized ? (
    <VirtualList
      className="max-h-full"
      items={slicedData}
      itemKey={itemKey}
      render={render}
      pageSize={pageSize}
    />
  ) : (
    <div className="overflow-y-auto">
      {slicedData.map((item, idx) => (
        <div key={getItemKey(item, idx)}>{render(item, idx)}</div>
      ))}
    </div>
  );
  return (
    <div
      style={style}
      className={classNames('overflow-hidden flex flex-col', className)}>
      {header ?? null}
      {listComponent}
      {footer ?? null}
    </div>
  );
}

export { ListHeader };
