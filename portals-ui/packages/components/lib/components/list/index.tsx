import type { Key, ReactNode } from 'react';
import React, { useState } from 'react';
import type { VirtualListState } from '@epam/uui-core';
import type { CommonProps } from '../..';
import { VirtualList } from '@epam/uui';
import classNames from 'classnames';

const MIN_VISIBLE_COUNT = 20;

export type ListProps<Item> = CommonProps & {
  data: Item[];
  renderItem: (item: Item, index: number) => ReactNode | string;
  header?: ReactNode;
  footer?: ReactNode;
  virtualized?: boolean;
  fieldKey?: string;
};

export default function List<Item>(props: ListProps<Item>): ReactNode {
  const {
    header,
    footer,
    data,
    renderItem,
    fieldKey,
    virtualized = false,
    style,
    className,
  } = props;
  const [listState, setListState] = useState<VirtualListState>({
    topIndex: 0,
    visibleCount: MIN_VISIBLE_COUNT,
  });
  const visibleData = data.slice(
    listState.topIndex,
    (listState.topIndex ?? 0) + (listState.visibleCount ?? MIN_VISIBLE_COUNT),
  );
  const rows = visibleData.map((item, index) => (
    <React.Fragment key={index}>{renderItem(item, index)}</React.Fragment>
  ));
  const listComponent = virtualized ? (
    <VirtualList
      cx="max-h-full"
      rows={rows}
      value={listState}
      onValueChange={setListState}
      rowsCount={data.length}
    />
  ) : (
    <div className="overflow-y-auto">
      {data.map((item, index) => {
        let key: Key = `key_${index}`;
        if (item && typeof item === 'object' && fieldKey) {
          key = (item[fieldKey as keyof typeof item] as string | number) ?? key;
        }
        return (
          <React.Fragment key={key}>{renderItem(item, index)}</React.Fragment>
        );
      })}
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
