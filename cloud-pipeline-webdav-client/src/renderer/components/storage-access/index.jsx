import React, { useCallback, useMemo, useState } from 'react';
import PropTypes from 'prop-types';
import {
  Alert,
  Button,
  ConfigProvider,
  Input,
  Spin,
} from 'antd';
import useStorages from './hooks/use-storages';
import useRequestAccess from './hooks/use-request-access';
import './storage-access.css';

const WRITE_PERMISSION = 0b10;

function writeAllowed(mask) {
  // eslint-disable-next-line no-bitwise
  return (mask & WRITE_PERMISSION) === WRITE_PERMISSION;
}

function Storage(
  {
    disabled,
    storage,
    onClick,
  },
) {
  const {
    mask = 1,
    davAccessInfo,
  } = storage || {};
  const handleClick = useCallback(() => {
    if (typeof onClick === 'function') {
      onClick(storage);
    }
  }, [storage, onClick]);
  if (!storage) {
    return null;
  }
  const buttonDisabled = disabled
    || (davAccessInfo && davAccessInfo.available)
    || !writeAllowed(mask);
  return (
    <div
      className="storage"
    >
      <span
        className="storage-title"
      >
        {storage.name}
      </span>
      <Button
        type="link"
        disabled={buttonDisabled}
        className="storage-action"
        onClick={handleClick}
      >
        {
          (!davAccessInfo || !davAccessInfo.available) && 'Request access'
        }
        {
          davAccessInfo
          && davAccessInfo.available
          && !davAccessInfo.expiresAt
          && 'Enabled'
        }
        {
          davAccessInfo
          && davAccessInfo.available
          && davAccessInfo.expiresAt
          && `Enabled till ${davAccessInfo.expiresAt}`
        }
      </Button>
    </div>
  );
}

Storage.propTypes = {
  storage: PropTypes.shape({
    name: PropTypes.string,
  }).isRequired,
  disabled: PropTypes.bool,
  onClick: PropTypes.func,
};

Storage.defaultProps = {
  disabled: false,
  onClick: undefined,
};

function generateFilter(filter) {
  const lowercased = (filter || '').toLowerCase();
  return (aStorage) => {
    if (!lowercased.length) {
      return true;
    }
    return (aStorage.name || '').toLowerCase().includes(lowercased);
  };
}

function StorageAccess() {
  const state = useStorages();
  const {
    pending,
    error,
    storages,
    reload,
  } = state;
  const [filter, setFilter] = useState(undefined);
  const onChangeFilter = useCallback((event) => setFilter(event.target.value), [setFilter]);
  const filterFn = useMemo(() => generateFilter(filter), [filter]);
  const filteredStorages = useMemo(
    () => (storages || []).filter(filterFn),
    [storages, filterFn],
  );
  const {
    storageIsDisabled,
    requestAccess,
  } = useRequestAccess(reload);
  return (
    <ConfigProvider componentSize="small">
      <div className="storage-access-container">
        <div className="header">
          <Input
            value={filter}
            onChange={onChangeFilter}
            className="input"
            placeholder="Filter storages"
          />
          <Button
            onClick={reload}
            disabled={pending}
            className="reload"
          >
            Refresh
          </Button>
        </div>
        {
          error && (
            <Alert type="error" message={error} />
          )
        }
        <Spin spinning={pending} wrapperClassName="storage-list">
          {
            filteredStorages.map((aStorage) => (
              <Storage
                storage={aStorage}
                key={aStorage.id}
                disabled={pending || storageIsDisabled(aStorage)}
                onClick={requestAccess}
              />
            ))
          }
          {
            !filteredStorages.length && filter && (
              <Alert
                type="warning"
                message="Nothing found"
              />
            )
          }
        </Spin>
      </div>
    </ConfigProvider>
  );
}

export default StorageAccess;
