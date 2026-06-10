import {memo, useCallback, useEffect, useMemo, useRef} from 'react';
import type {CSSProperties, ReactNode} from 'react';
import {useVirtualizer} from '@tanstack/react-virtual';
import classNames from 'classnames';
import {CommonProps} from '../../../@types/common.ts';

export type VirtualListItemKey<Item> = keyof Item | ((item: Item, idx: number) => string);

export type VirtualListItemHeight<Item> = number | ((item: Item, index: number) => number);

export type VirtualListProps<Item, Opts extends object> = CommonProps &
  Opts & {
    overscan?: number;
    items: Item[];
    itemsToken?: unknown;
    itemKey?: VirtualListItemKey<Item>;
    itemHeight?: VirtualListItemHeight<Item>;
    estimatedItemHeight?: number;
    footer?: ReactNode;
    focusedItem?: Item;
    render: (item: Item, opts: Opts & {index: number}) => ReactNode;
  };

const DEFAULT_ESTIMATED_ITEM_HEIGHT = 28;

function resolveItemKey<Item>(
  itemKey: VirtualListItemKey<Item> | undefined,
  item: Item,
  index: number,
): string {
  if (!itemKey) {
    return String(index);
  }
  if (typeof itemKey === 'function') {
    return itemKey(item, index);
  }
  return String(item[itemKey]);
}

function _VirtualList<Item, Opts extends object>(props: VirtualListProps<Item, Opts>) {
  const {
    overscan = 50,
    items,
    className,
    style,
    render,
    itemsToken,
    itemKey,
    itemHeight,
    estimatedItemHeight = DEFAULT_ESTIMATED_ITEM_HEIGHT,
    footer,
    focusedItem,
    ...renderProps
  } = props;
  const containerRef = useRef<HTMLDivElement | null>(null);
  const listItems = items ?? [];
  const measureItems = itemHeight === undefined;
  const token = useMemo(() => itemsToken ?? items, [items, itemsToken]);
  const getItemKey = useCallback(
    (index: number) => resolveItemKey(itemKey, listItems[index], index),
    [itemKey, listItems],
  );
  const estimateSize = useCallback(
    (index: number) => {
      if (typeof itemHeight === 'number') {
        return itemHeight;
      }
      if (typeof itemHeight === 'function') {
        return itemHeight(listItems[index], index);
      }
      return estimatedItemHeight;
    },
    [estimatedItemHeight, itemHeight, listItems],
  );
  const rowVirtualizer = useVirtualizer({
    count: listItems.length,
    overscan,
    getScrollElement: () => containerRef.current,
    estimateSize,
    getItemKey,
  });
  const focusedItemIndexInfo = useMemo(() => {
    if (focusedItem) {
      if (!itemKey) {
        return {index: listItems.indexOf(focusedItem)};
      }
      if (typeof itemKey === 'function') {
        const matches = listItems
          .map((o, idx) => ({item: o, idx, key: itemKey(o, idx)}))
          .filter((o) => itemKey(o.item, o.idx) === itemKey(focusedItem, o.idx));
        return matches.length === 1 ? {index: matches[0].idx, key: matches[0].key} : undefined;
      }
      return {
        index: listItems.findIndex((o) => o[itemKey] === focusedItem[itemKey]),
        key: focusedItem[itemKey],
      };
    }
    return undefined;
  }, [focusedItem, listItems, itemKey]);
  const focusedItemIndexRef = useRef<{index: number; key?: unknown} | undefined>(undefined);
  useEffect(() => {
    if (focusedItemIndexInfo && focusedItemIndexInfo.index >= 0) {
      const {current} = focusedItemIndexRef;
      if (
        !current ||
        (focusedItemIndexInfo.key && focusedItemIndexInfo.key !== current.key) ||
        (!focusedItemIndexInfo.key && focusedItemIndexInfo.index !== current.index)
      ) {
        rowVirtualizer.scrollToIndex(focusedItemIndexInfo.index, {
          align: 'auto',
          behavior: 'smooth',
        });
        focusedItemIndexRef.current = focusedItemIndexInfo;
      }
    }
  }, [focusedItemIndexInfo, rowVirtualizer]);
  useEffect(() => {
    containerRef.current?.scrollTo({top: 0});
  }, [token]);
  const fixedItemHeight = typeof itemHeight === 'number' ? itemHeight : undefined;
  return (
    <div
      ref={containerRef}
      className={classNames('min-h-0 overflow-auto', className)}
      style={style}
    >
      <div
        style={{
          height: rowVirtualizer.getTotalSize(),
          position: 'relative',
          width: '100%',
        }}
      >
        {rowVirtualizer.getVirtualItems().map((virtualRow) => {
          const item = listItems[virtualRow.index];
          const rowStyle: CSSProperties = {
            position: 'absolute',
            top: 0,
            left: 0,
            width: '100%',
            transform: `translateY(${virtualRow.start}px)`,
          };
          if (fixedItemHeight !== undefined) {
            rowStyle.height = fixedItemHeight;
          }
          return (
            <div
              key={virtualRow.key}
              data-index={virtualRow.index}
              data-item-key={virtualRow.key}
              ref={measureItems ? rowVirtualizer.measureElement : undefined}
              style={rowStyle}
            >
              {render(item, {
                ...(renderProps as Opts),
                index: virtualRow.index,
              })}
            </div>
          );
        })}
      </div>
      {footer}
    </div>
  );
}

const VirtualList = memo(_VirtualList) as typeof _VirtualList;

export default VirtualList;
