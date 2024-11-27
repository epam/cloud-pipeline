import type { ListProps } from '@cloud-pipeline/components';
import type { ReactNode } from 'react';
import type {
  SearchNamedItem,
  SearchOptions,
} from '../../shared/hooks/use-search.ts';

export type ViewAllItemsConfiguration = {
  title?: ReactNode;
  link: string;
};

export type ItemsPanelProps<Item> = Omit<
  ListProps<Item>,
  'renderItem' | 'data' | 'header'
> & {
  renderItem: (item: Item, search: string) => ReactNode | string;
  items?: Item[];
  title?: ReactNode;
  actions?: ReactNode;
  search?: Item extends SearchNamedItem
    ? boolean | Omit<SearchOptions<Item>, 'items'>
    : Omit<SearchOptions<Item>, 'items'>;
  viewAll?: ViewAllItemsConfiguration;
};
