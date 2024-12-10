import type { ReactNode } from 'react';
import { CommonProps } from '../common.types.ts';

export type ItemKey<Item> = keyof Item | ((item: Item, index: number) => string | number);

export type ItemKeyResolved<Item> = (item: Item, index: number) => string;

export type VirtualListProps<Item> = CommonProps & {
  items: Item[];
  render: (item: Item, idx: number) => ReactNode;
  itemKey?: ItemKey<Item>;
  pageSize?: number;
};