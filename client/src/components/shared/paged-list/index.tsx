import {useCallback, useEffect, useMemo, useState} from 'react';
import type {ReactNode} from 'react';
import {Pagination} from 'antd';
import {CommonProps} from '../../../@types/common.ts';
import classNames from 'classnames';

export type PagedListItemKey<Item> = keyof Item | ((item: Item, idx: number) => string);

export type PagedListProps<Item, Opts extends object = object> = CommonProps &
  Opts & {
    items: Item[];
    render: (item: Item, opts: Opts & {index: number}) => ReactNode;
    itemKey?: PagedListItemKey<Item>;
    footer?: ReactNode;
    pageSize?: number;
  };

const DEFAULT_PAGE_SIZE = 10;

function resolveItemKey<Item>(
  itemKey: PagedListItemKey<Item> | undefined,
  item: Item,
  index: number,
): string {
  if (!itemKey) {
    return String(index);
  }
  if (typeof itemKey === 'function') {
    return itemKey(item, index);
  }
  return String(item[itemKey]);
}

function PagedList<Item, Opts extends object = object>(props: PagedListProps<Item, Opts>) {
  const {
    items,
    className,
    style,
    render,
    itemKey,
    footer,
    pageSize: pageSizeProp,
    ...renderProps
  } = props;
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(pageSizeProp ?? DEFAULT_PAGE_SIZE);
  const listItems = items ?? [];
  const maxPage = Math.max(1, Math.ceil(listItems.length / pageSize) || 1);
  const currentPage = Math.min(page, maxPage);
  const pageStartIndex = (currentPage - 1) * pageSize;
  const pagedItems = useMemo(
    () => listItems.slice(pageStartIndex, pageStartIndex + pageSize),
    [listItems, currentPage, pageSize, pageStartIndex],
  );
  useEffect(() => {
    if (page > maxPage) {
      setPage(maxPage);
    }
  }, [page, maxPage]);
  const handleChange = useCallback((nextPage: number, nextPageSize: number) => {
    setPage(nextPage);
    setPageSize(nextPageSize);
  }, []);
  const handlePageSizeChange = useCallback((_current: number, nextPageSize: number) => {
    setPageSize(nextPageSize);
  }, []);
  return (
    <div className={classNames(className, 'flex flex-col overflow-auto')} style={style}>
      <div className="flex-1 shrink overflow-auto">
        {pagedItems.map((item, idx) => {
          const index = pageStartIndex + idx;
          return (
            <div key={resolveItemKey(itemKey, item, index)}>
              {render(item, {
                ...(renderProps as Opts),
                index,
              })}
            </div>
          );
        })}
      </div>
      <div className="shrink-0 flex items-center justify-end py-1 px-4">
        <Pagination
          current={currentPage}
          hideOnSinglePage
          onChange={handleChange}
          onShowSizeChange={handlePageSizeChange}
          pageSize={pageSize}
          total={listItems.length}
          size="small"
        />
      </div>
      {footer && <div className="shrink-0">{footer}</div>}
    </div>
  );
}

export default PagedList;
