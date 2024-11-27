import type { ItemsPanelProps, ViewAllItemsConfiguration } from './types.ts';
import type { SearchOptions } from '../../shared/hooks/use-search.ts';
import { useSearch } from '../../shared/hooks/use-search.ts';
import { useMemo } from 'react';
import type { CommonProps } from '@cloud-pipeline/components';
import { List, ListHeader } from '@cloud-pipeline/components';
import classNames from 'classnames';
import { LinkButton } from '@epam/uui';

function ItemsPanelFooter(
  props: CommonProps & { viewAll?: ViewAllItemsConfiguration },
) {
  const { className, style, viewAll } = props;
  const { title: viewAllTitle = 'View all', link } = viewAll ?? {};
  if (link) {
    return (
      <div
        className={classNames(
          'p-1',
          'flex',
          'items-center',
          'justify-around',
          className,
        )}
        style={style}>
        <LinkButton caption={viewAllTitle} link={{ pathname: link }} />
      </div>
    );
  }
  return null;
}

export function ItemsPanel<Item>(props: ItemsPanelProps<Item>) {
  const {
    items,
    search: searchConfig,
    className,
    style,
    title,
    actions,
    renderItem,
    itemKey,
    virtualized,
    sliced,
    viewAll,
  } = props;
  const searchEnabled = Boolean(searchConfig);
  const searchOptions = useMemo<SearchOptions<Item>>(() => {
    if (searchConfig) {
      return {
        ...(typeof searchConfig === 'object' ? searchConfig : {}),
        items: items ?? [],
      } as SearchOptions<Item>;
    }
    return {
      items: items ?? [],
    } as SearchOptions<Item>;
  }, [items, searchConfig]);
  const { filtered, search, onSearchChange } = useSearch(searchOptions);

  return (
    <div
      className={classNames('flex', 'flex-col', 'overflow-auto', className)}
      style={style}>
      <ListHeader
        title={title}
        className="shrink-0 border-b"
        search={search}
        onSearch={searchEnabled ? onSearchChange : undefined}
        controls={actions}
      />
      {filtered.length > 0 && (
        <List
          className="overflow-auto flex-1 py-2"
          data={filtered}
          renderItem={(item, i) => renderItem(item, search, i)}
          itemKey={itemKey}
          virtualized={virtualized}
          sliced={sliced}
        />
      )}
      {filtered.length == 0 && (
        <div className="p-2 text-faded text-xs">Nothing found</div>
      )}
      <ItemsPanelFooter className="border-t" viewAll={viewAll} />
    </div>
  );
}
