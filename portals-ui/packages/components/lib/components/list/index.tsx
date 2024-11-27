import type { ReactNode } from 'react';
import { useMemo, useCallback, useState } from 'react';
import type { VirtualListState } from '@epam/uui-core';
import classNames from 'classnames';
import { VirtualList } from '@epam/uui';
import type { ListProps } from './types';
import ListHeader from './list-header';

const MIN_VISIBLE_COUNT = 20;

export default function List<Item>(props: ListProps<Item>): ReactNode {
  const {
    header,
    footer,
    data,
    renderItem,
    virtualized = false,
    style,
    className,
    itemKey,
    sliced,
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
  const [listState, setListState] = useState<VirtualListState>({
    topIndex: 0,
    visibleCount: itemsToDisplayCount,
  });
  const { topIndex = 0, visibleCount = itemsToDisplayCount } = listState;
  const visibleData = useMemo(
    () => data.slice(topIndex, topIndex + visibleCount),
    [data, topIndex, visibleCount],
  );
  const getItemKey = useCallback(
    (item: Item, index: number): string => {
      if (itemKey && typeof itemKey === 'function') {
        return String(itemKey(item, index));
      }
      if (itemKey && Object.hasOwnProperty.call(item, itemKey)) {
        return String(item[itemKey]);
      }
      return `key_${index}`;
    },
    [itemKey],
  );
  const rows = useMemo(
    () =>
      visibleData.map((item, index) => (
        <div key={getItemKey(item, index)}>{renderItem(item, index)}</div>
      )),
    [getItemKey, renderItem, visibleData],
  );
  const listComponent = virtualized ? (
    <VirtualList
      cx="max-h-full"
      rows={rows}
      value={listState}
      onValueChange={setListState}
      rowsCount={data.length}
    />
  ) : (
    <div className="overflow-y-auto">{rows}</div>
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
