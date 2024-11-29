import classNames from 'classnames';
import { SearchInput } from '@epam/uui';
import type { ListHeaderProps } from './types';

const controlsCx = {
  standard: 'px-3 py-3',
  compact: 'p-2',
} as Record<string, string>;

const searchCx = {
  standard: 'py-0.5 px-1',
  compact: 'p-0.5',
} as Record<string, string>;

const ListHeader = (props: ListHeaderProps) => {
  const {
    className,
    style,
    title,
    controls,
    search,
    onSearch,
    searchPlaceholder,
    mode = 'compact',
  } = props;
  return (
    <div className={classNames(className, 'divide-y')} style={style}>
      <div
        className={classNames(
          'flex text items-center no-wrap',
          controlsCx[mode],
        )}>
        <b>{title}</b>
        {controls ? <div className="ml-auto">{controls}</div> : null}
      </div>
      {onSearch ? (
        <div className={searchCx[mode]}>
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
