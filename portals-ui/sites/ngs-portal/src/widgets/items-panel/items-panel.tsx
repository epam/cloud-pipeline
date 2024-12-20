import type { ItemsPanelProps, ViewAllItemsConfiguration } from './types.ts';
import type { CommonProps } from '@cloud-pipeline/components';
import { List, ListHeader } from '@cloud-pipeline/components';
import classNames from 'classnames';
import { Typography } from 'antd';
import { Link } from 'react-router-dom';

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
      <Link to={link} className="flex">
        <Typography.Text className="cursor-pointer text-blue-400 hover:text-blue-500  font-semibold">
          {viewAllTitle}
        </Typography.Text>
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
  } = props;

  return (
    <div
      className={classNames('flex', 'flex-col', 'overflow-auto', className)}
      style={style}>
      <ListHeader
        title={title}
        className={classNames('shrink-0', {
          'border-b':
            title || beforeSearch || afterSearch || Boolean(onSearchChange),
        })}
        search={search}
        onSearch={onSearchChange}
        controls={actions}
        beforeSearch={beforeSearch}
        afterSearch={afterSearch}
        searchClassName={searchClassName}
      />
      {extraHeader}
      {items.length > 0 && (
        <List
          className="overflow-auto flex-1"
          items={items}
          render={(item, i) => render(item, search ?? '', i)}
          itemKey={itemKey}
          virtualized={virtualized}
          sliced={sliced}
        />
      )}
      {items.length == 0 && (
        <div className="p-2 text-faded text-xs">Nothing found</div>
      )}
      <ItemsPanelFooter className="border-t" viewAll={viewAll} />
    </div>
  );
}
