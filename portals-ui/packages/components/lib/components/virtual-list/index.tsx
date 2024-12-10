import { useEffect } from 'react';
import { useState, useRef, useCallback, useMemo } from 'react';
import type { UIEvent } from 'react';
import classNames from 'classnames';
import type { VirtualListProps } from './types';
import { useItemKey } from './utilities.ts';

export default function VirtualList<Item>(props: VirtualListProps<Item>) {
  const { pageSize = 50, items, className, style, render, itemKey } = props;
  const [limitChildrenTo, setLimitChildrenTo] = useState(pageSize);
  const increaseLimit = useCallback(() => {
    setLimitChildrenTo((c) => c + pageSize);
  }, [pageSize, setLimitChildrenTo]);
  const containerRef = useRef<HTMLDivElement | null>(null);
  const slicedItems = useMemo(
    () => (items ?? []).slice(0, limitChildrenTo),
    [limitChildrenTo, items],
  );
  useEffect(() => {
    setLimitChildrenTo(pageSize);
    containerRef.current?.scrollTo({ top: 0 });
  }, [items, pageSize]);
  const hasMore = slicedItems.length < items.length;
  const onScroll = useCallback(
    (event: UIEvent): void => {
      if (
        hasMore &&
        event.currentTarget.scrollHeight <=
          event.currentTarget.scrollTop +
            event.currentTarget.clientHeight +
            10.0
      ) {
        increaseLimit();
      }
    },
    [hasMore, increaseLimit],
  );
  const getItemKey = useItemKey(itemKey);
  return (
    <div
      ref={containerRef}
      className={classNames('flex flex-col overflow-y-auto', className)}
      onScroll={onScroll}
      style={style}>
      {slicedItems.map((item, idx) => (
        <div key={getItemKey(item, idx)}>{render(item, idx)}</div>
      ))}
      {hasMore && (
        <div className="h-6 text-faded text-center text-sm">Loading...</div>
      )}
    </div>
  );
}
