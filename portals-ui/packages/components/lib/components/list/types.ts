import type { ReactNode } from 'react';
import type { CommonProps } from '../common.types';
import { VirtualListProps } from '../virtual-list/types.ts';

export type ListProps<Item> = VirtualListProps<Item> & {
  header?: ReactNode;
  footer?: ReactNode;
  virtualized?: boolean;
  sliced?: number | boolean;
};

export type ListHeaderProps = CommonProps & {
  title: string | ReactNode;
  logo?: string;
  search?: string;
  searchPlaceholder?: string;
  onSearch?: (search: string) => void;
  controls?: ReactNode;
  beforeSearch?: ReactNode;
  searchClassName?: string;
};
