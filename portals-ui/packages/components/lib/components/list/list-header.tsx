import classNames from 'classnames';
import { SearchInput } from '@epam/uui';
import type { ListHeaderProps } from './types';
import './style.css';

const ListHeader = (props: ListHeaderProps) => {
  const {
    className,
    style,
    title,
    controls,
    search,
    onSearch,
    searchPlaceholder,
  } = props;
  return (
    <div className={classNames(className, 'divide-y')} style={style}>
      <div className={classNames('flex text items-center no-wrap list-header')}>
        <b>{title}</b>
        {controls ? <div className="ml-auto">{controls}</div> : null}
      </div>
      {onSearch ? (
        <div
          className={classNames('list-header-search-container')}>
          <SearchInput
            value={search}
            onValueChange={onSearch}
            placeholder={searchPlaceholder ?? 'Search'}
            disableDebounce
            mode="inline"
            size="30"
          />
        </div>
      ) : null}
    </div>
  );
};

export default ListHeader;
