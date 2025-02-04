import classNames from 'classnames';
import { Input, Spin } from 'antd';
import type { ListHeaderProps } from './types';
import './style.css';
import { useInputChange } from '../../hooks/use-input-change.ts';
import { MagnifyingGlassIcon } from '@heroicons/react/24/outline';

const ListHeader = (props: ListHeaderProps) => {
  const {
    className,
    searchClassName,
    searchInputClassName,
    style,
    title,
    controls,
    search,
    onSearch,
    searchPlaceholder,
    afterSearch,
    beforeSearch,
    pending,
  } = props;
  const onChange = useInputChange(onSearch);
  return (
    <div className={classNames(className, 'divide-y')} style={style}>
      {title || controls ? (
        <div
          className={classNames('flex text items-center no-wrap list-header')}>
          <b>{title}</b>
          {pending && <Spin size="small" className="ml-1" />}
          {controls ? <div className="ml-auto">{controls}</div> : null}
        </div>
      ) : null}
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
            style={{ boxShadow: 'none', borderColor: 'transparent' }}
            className={classNames(searchInputClassName, 'flex-1')}
          />
          {afterSearch ?? null}
        </div>
      ) : null}
    </div>
  );
};

export default ListHeader;
