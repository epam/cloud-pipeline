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
      <b
        className="flex no-wrap px-6 py-4"
        style={{ color: 'var(--uui-text-secondary)' }}>
        {title} {controls ? <div className="ml-auto">{controls}</div> : null}
      </b>
      {onSearch ? (
        <div className="px-6 py-2">
          <SearchInput
            value={search}
            onValueChange={onSearch}
            placeholder={searchPlaceholder ?? 'Search'}
            debounceDelay={300}
            size="30"
          />
        </div>
      ) : null}
    </div>
  );
};

export default ListHeader;
