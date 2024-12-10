import classNames from 'classnames';
import { Input } from 'antd';
import type { ListHeaderProps } from './types';
import './style.css';
import { useInputChange } from '../../hooks/use-input-change.ts';
import { MagnifyingGlassIcon } from '@heroicons/react/24/outline';

const ListHeader = (props: ListHeaderProps) => {
  const {
    className,
    searchClassName,
    style,
    title,
    controls,
    search,
    onSearch,
    searchPlaceholder,
    beforeSearch,
  } = props;
  const onChange = useInputChange(onSearch);
  return (
    <div className={classNames(className, 'divide-y')} style={style}>
      <div className={classNames('flex text items-center no-wrap list-header')}>
        <b>{title}</b>
        {controls ? <div className="ml-auto">{controls}</div> : null}
      </div>
      {onSearch ? (
        <div
          className={classNames(
            'list-header-search-container',
            searchClassName,
          )}>
          {beforeSearch ?? null}
          <Input
            prefix={<MagnifyingGlassIcon className="w-4 h-4" />}
            value={search}
            onChange={onChange}
            placeholder={searchPlaceholder ?? 'Search'}
            size="middle"
            variant="borderless"
          />
        </div>
      ) : null}
    </div>
  );
};

export default ListHeader;
