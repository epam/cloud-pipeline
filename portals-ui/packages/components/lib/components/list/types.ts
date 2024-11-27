import type { ReactNode } from 'react';
import type { CommonProps } from '../common.types';

export type ListProps<Item> = CommonProps & {
  data: Item[];
  renderItem: (item: Item, index: number) => ReactNode | string;
  header?: ReactNode;
  footer?: ReactNode;
  virtualized?: boolean;
  itemKey?: keyof Item | ((item: Item, index: number) => string | number);
  sliced?: number | boolean;
};

export type ListHeaderProps = CommonProps & {
  title: string | ReactNode;
  logo?: string;
  search?: string;
  searchPlaceholder?: string;
  onSearch?: (search: string) => void;
  controls?: ReactNode;
};
