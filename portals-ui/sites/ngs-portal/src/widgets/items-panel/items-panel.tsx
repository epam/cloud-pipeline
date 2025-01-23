import type { ItemsPanelProps, ViewAllItemsConfiguration } from './types.ts';
import type { CommonProps } from '@cloud-pipeline/components';
import { List, ListHeader } from '@cloud-pipeline/components';
import classNames from 'classnames';
import { Link } from 'react-router-dom';
import { PageSpinner } from '../../shared/ui/page-spinner.tsx';
import { useCallback } from 'react';

function ItemsPanelFooter(
  props: CommonProps & { viewAll?: ViewAllItemsConfiguration },
) {
  const { className, style, viewAll } = props;
  const { title: viewAllTitle = 'View all', link } = viewAll ?? {};

  if (!link) {
    return null;
  }

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
      <Link to={link} className="font-semibold">
        {viewAllTitle}
      </Link>
    </div>
  );
}

export function ItemsPanel<Item>(props: ItemsPanelProps<Item>) {
  const {
    items,
    search,
    className,
    style,
    title,
    actions,
    render,
    itemKey,
    virtualized,
    sliced,
    viewAll,
    afterSearch,
    beforeSearch,
    extraHeader,
    searchClassName,
    onSearchChange,
    isItemsLoading,
    errorText,
  } = props;

  const renderContent = useCallback(() => {
    if (isItemsLoading) {
      return <PageSpinner />;
    }

    if (errorText) {
      return <div className="p-2 text-faded text-xs">{errorText}</div>;
    }

    if (items.length === 0) {
      return <div className="p-2 text-faded text-xs">Nothing found</div>;
    }

    return (
      <List
        className="overflow-auto flex-1"
        items={items}
        render={(item, i) => render(item, search ?? '', i)}
        itemKey={itemKey}
        virtualized={virtualized}
        sliced={sliced}
      />
    );
  }, [
    errorText,
    isItemsLoading,
    itemKey,
    items,
    render,
    search,
    sliced,
    virtualized,
  ]);

  return (
    <div
      className={classNames('flex', 'flex-col', 'overflow-auto', className)}
      style={style}>
      <ListHeader
        title={title}
        className={classNames('shrink-0', {
          'border-b':
            title ?? beforeSearch ?? afterSearch ?? Boolean(onSearchChange),
        })}
        search={search}
        onSearch={onSearchChange}
        controls={actions}
        beforeSearch={beforeSearch}
        afterSearch={afterSearch}
        searchClassName={searchClassName}
      />
      {extraHeader}
      {renderContent()}
      <ItemsPanelFooter className="border-t mt-auto" viewAll={viewAll} />
    </div>
  );
}
