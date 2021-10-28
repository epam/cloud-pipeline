import React, {useCallback, useEffect, useState} from 'react';
import PropTypes from 'prop-types';
import {Alert, Button, Input, Modal, message, Spin} from 'antd';
import moment from 'moment-timezone';
import requestStorageAccessApi from '../../models/request-storage-access-api';
import './request-storage-access.css';

function davAccessInfo (value) {
  if (!value) {
    return undefined;
  }
  if (
    (typeof value === 'string' && !Number.isNaN(Number(value))) ||
    typeof value === 'number'
  ) {
    const time = moment.utc(new Date(Number(value) * 1000));
    const now = moment.utc();
    return {
      available: now < time,
      expiresAt: time.format('D MMM YYYY, HH:mm')
    };
  }
  if (typeof value === 'boolean') {
    return {
      available: Boolean(value)
    };
  }
  return {
    available: /^true$/i.test(value)
  };
}

function StorageAccess(
  {
    disabled = false,
    onRequest,
    storage
  }
) {
  const {
    metadata = {}
  } = storage || {};
  const {
    'dav-mount': davMount
  } = metadata;
  const info = davAccessInfo(davMount);
  let infoString = 'Request access';
  if (info && info.available) {
    infoString = 'Enabled';
    if (info.expiresAt) {
      infoString = `Enabled till ${info.expiresAt}`;
    }
  }
  return (
    <Button
      type="link"
      disabled={disabled || (info && info.available)}
      onClick={onRequest}
    >
      {infoString}
    </Button>
  );
}

function fetchDuration() {
  return new Promise((resolve) => {
    requestStorageAccessApi.getPreference('storage.webdav.access.duration.seconds')
      .then(preference => {
        if (preference && !Number.isNaN(Number(preference))) {
          resolve(Number(preference));
        } else {
          resolve(0);
        }
      })
      .catch(() => resolve(0));
  });
}

function RequestStorageAccess (
  {
    onClose,
    visible
  }
) {
  const [pending, setPending] = useState(false);
  const [error, setError] = useState(undefined);
  const [storages, setStorages] = useState([]);
  const [duration, setDuration] = useState(0);
  const [fetchId, setFetchId] = useState(0);
  const [search, setSearch] = useState(undefined);
  const [requests, setRequests] = useState([]);
  const reload = useCallback(() => setFetchId(o => o + 1), [setFetchId]);
  const onFilterChange = useCallback((e) => {
    setSearch(e.target.value);
  }, [setSearch]);
  const addRequest = useCallback((id) => {
    setRequests(o => [...(o || []).filter(o => o !== id), id]);
  }, [setRequests]);
  const removeRequest = useCallback((id) => {
    setRequests(o => (o || []).filter(o => o !== id));
  }, [setRequests]);
  const requestAccess = useCallback((storage) => {
    if (storage) {
      const hide = message.loading(
        (<span>Requesting <b>{storage.name}</b> access...</span>),
        0
      );
      addRequest(storage.id);
      requestStorageAccessApi
        .requestDavAccess(storage.id, duration)
        .then(() => {
          message.info(
            <span><b>{storage.name}</b> will be available in few minutes</span>,
            5
          );
          reload();
        })
        .catch((e) => {
          message.error(e.message, 5);
        })
        .then(() => {
          hide();
          removeRequest(storage.id);
        })
    }
  }, [
    addRequest,
    removeRequest,
    duration,
    reload
  ]);
  useEffect(() => {
    if (visible) {
      setPending(true);
      requestStorageAccessApi
        .initialize()
        .getStoragesWithMetadata()
        .then((storages = []) => setStorages(
          (storages || [])
            .filter(o => !/^nfs$/i.test(o.type))
            .sort((a, b) => {
              const aName = (a.name || '').toLowerCase();
              const bName = (b.name || '').toLowerCase();
              if (aName < bName) {
                return -1;
              }
              if (aName > bName) {
                return 1;
              }
              return 0;
            })
        ))
        .then(() => setError(undefined))
        .catch((error) => setError(error.message))
        .then(() => fetchDuration())
        .then(setDuration)
        .then(() => setPending(false));
    }
  }, [visible, fetchId, setError, setPending, setStorages, setDuration]);
  return (
    <Modal
      visible={visible}
      title="Request storage access"
      onCancel={onClose}
      footer={false}
      width="80%"
    >
      <Spin spinning={pending}>
        {
          error && !pending && (
            <Alert type="error" message={error} />
          )
        }
        <div className="storage-search">
          <Input
            className="search-input"
            value={search}
            onChange={onFilterChange}
            placeholder="Filter storages"
          />
          <Button onClick={reload}>
            Refresh
          </Button>
        </div>
        <div className="storages-list">
          {
            storages
              .filter(storage => !search || (storage.name || '').toLowerCase().includes(search.toLowerCase()))
              .map(storage => (
                <div
                  key={storage.id}
                  className="storage"
                >
                  <span>{storage.name}</span>
                  <StorageAccess
                    disabled={requests.includes(storage.id)}
                    onRequest={() => requestAccess(storage)}
                    storage={storage}
                  />
                </div>
              ))
          }
        </div>
      </Spin>
    </Modal>
  );
}

RequestStorageAccess.propTypes = {
  onClose: PropTypes.func,
  visible: PropTypes.bool
}

export default RequestStorageAccess;
