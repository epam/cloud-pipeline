import {useMemo} from 'react';
import {useQuery} from '@tanstack/react-query';
import {Popover} from 'antd';
import classNames from 'classnames';
import type {MetadataValueRendererProps} from './types.ts';
import {storagesQueryOptions} from '../../../../queries';
import {getStoragesByIdentifiers} from '../../../../utils/limit-mounts/get-limit-mounts-storages.js';
import {limitMountsValueIsNone} from '../../../../utils/limit-mounts/get-limit-mounts-storages.js';
import {plural, stringifyMetadataValue} from './utilities.ts';

const MAX_STORAGES_TO_SHOW = 3;

function LimitMountsRenderer(props: MetadataValueRendererProps) {
  const {className, style, value} = props;
  const rawValue = stringifyMetadataValue(value) ?? '';
  const {data: storages = []} = useQuery(storagesQueryOptions());
  const identifiers = useMemo(
    () =>
      rawValue
        .split(/[,;]/)
        .map((item) => item.trim())
        .filter(Boolean),
    [rawValue],
  );
  const isNone = limitMountsValueIsNone(rawValue);
  const matchedStorages = useMemo(
    () => getStoragesByIdentifiers(identifiers, storages),
    [identifiers, storages],
  );
  if (isNone) {
    return (
      <span className={className} style={style}>
        Do not mount storages
      </span>
    );
  }
  if (matchedStorages.length === 0) {
    const fallback = identifiers.length > 0 ? plural(identifiers.length, 'storage') : rawValue;
    return (
      <span className={className} style={style}>
        {fallback}
      </span>
    );
  }
  const visible = matchedStorages.slice(0, MAX_STORAGES_TO_SHOW);
  const hiddenCount = matchedStorages.length - visible.length;
  const content = (
    <span className={className} style={style}>
      {visible.map((storage, index) => (
        <span key={storage.id}>
          {index > 0 && ', '}
          {storage.name}
        </span>
      ))}
      {hiddenCount > 0 && <span className={classNames('cp-text')}> and {hiddenCount} more</span>}
    </span>
  );
  if (matchedStorages.length <= MAX_STORAGES_TO_SHOW) {
    return content;
  }
  return (
    <Popover
      content={
        <div style={{maxHeight: '50vh', overflow: 'auto'}}>
          {matchedStorages.map((storage) => (
            <div key={storage.id}>{storage.name}</div>
          ))}
        </div>
      }
    >
      {content}
    </Popover>
  );
}

export {LimitMountsRenderer};
