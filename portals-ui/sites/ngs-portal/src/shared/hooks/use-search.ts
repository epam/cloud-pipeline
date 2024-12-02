import { useMemo, useState } from 'react';

export type SearchBaseOptions<Item> = {
  items: Item[];
};

export type SearchCallback<Item> = (item: Item, search: string) => boolean;

export type SearchGeneralOptions<Item> = SearchBaseOptions<Item> & {
  searchCallback: SearchCallback<Item>;
};

export type SearchNamedItem = {
  name: string;
};

export type SearchNamedItemOptions<Item extends SearchNamedItem> =
  SearchBaseOptions<Item> & {
    searchCallback?: SearchCallback<Item>;
  };

export type SearchOptions<Item> = Item extends SearchNamedItem
  ? SearchNamedItemOptions<Item>
  : SearchGeneralOptions<Item>;
function isSearchNamedItem(item: unknown): item is SearchNamedItem {
  return (
    item !== undefined &&
    item !== null &&
    typeof item === 'object' &&
    'name' in item &&
    typeof item.name === 'string'
  );
}

function defaultSearchCallback<Item>(item: Item, search: string): boolean {
  if (isSearchNamedItem(item)) {
    return item.name.toLowerCase().includes(search.toLowerCase());
  }
  return true;
}

export type SearchResult<Item> = {
  filtered: Item[];
  search: string;
  onSearchChange: (search: string) => void;
};

export function useSearch<Item>(
  options: SearchOptions<Item>,
): SearchResult<Item> {
  const { items, searchCallback = defaultSearchCallback } = options;
  const [search, setSearch] = useState('');

  const filtered = useMemo(() => {
    return items.filter((item) => searchCallback(item, search));
  }, [items, searchCallback, search]);

  return useMemo(
    () => ({
      filtered,
      search,
      onSearchChange: setSearch,
    }),
    [filtered, search, setSearch],
  );
}
