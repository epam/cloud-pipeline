import classNames from 'classnames';
import { SearchInput } from '@epam/uui';
import type { ListHeaderProps } from './types';

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
      <div
        className="flex items-center no-wrap p-2"
        style={{ color: 'var(--uui-text-secondary)' }}>
        <b>{title}</b>
        {controls ? <div className="ml-auto">{controls}</div> : null}
      </div>
      {onSearch ? (
        <div className="p-0.5">
          <SearchInput
            value={search}
            onValueChange={onSearch}
            placeholder={searchPlaceholder ?? 'Search'}
            debounceDelay={300}
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
