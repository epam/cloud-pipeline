import type { ListProps } from '@cloud-pipeline/components';
import type { ReactNode } from 'react';

export type ViewAllItemsConfiguration = {
  title?: ReactNode;
  link: string;
};

export type ItemsPanelProps<Item> = Omit<
  ListProps<Item>,
  'render' | 'items' | 'header'
> & {
  render: (item: Item, search: string, index: number) => ReactNode | string;
  items?: Item[];
  title?: ReactNode;
  actions?: ReactNode;
  viewAll?: ViewAllItemsConfiguration;
  beforeSearch?: ReactNode;
  searchClassName?: string;
  onSearchChange?: (search: string) => void;
  search?: string;
};
