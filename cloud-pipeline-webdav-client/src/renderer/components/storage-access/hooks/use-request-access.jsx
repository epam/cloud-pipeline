import React, { useCallback, useMemo, useState } from 'react';
import { message } from 'antd';
import ipcResponse from '../../../common/ipc-response';

function requestAccessCallback(aStorage) {
  return ipcResponse('requestDavAccess', aStorage.id);
}

export default function useRequestAccess(reload) {
  const [requests, setRequest] = useState([]);
  const addRequest = useCallback(
    (storage) => setRequest((current) => [...current, storage.id]),
    [setRequest],
  );
  const removeRequest = useCallback(
    (storage) => setRequest((current) => current.filter((s) => s !== storage.id)),
    [setRequest],
  );
  const requestAccess = useCallback((aStorage) => {
    const hide = message.loading(
      (
        <span>
          {'Requesting '}
          <b>{aStorage.name}</b>
          {' access...'}
        </span>
      ),
      0,
    );
    addRequest(aStorage);
    requestAccessCallback(aStorage)
      .then(() => {
        (message.info)(
          (
            <span>
              <b>{aStorage.name}</b>
              {' will be available in few minutes'}
            </span>
          ),
          5,
        );
        if (typeof reload === 'function') {
          reload();
        }
        return Promise.resolve();
      })
      .catch((error) => {
        (message.error)(error.message, 5);
        return Promise.resolve();
      })
      .then(() => {
        hide();
        removeRequest(aStorage);
      });
  }, [
    reload,
    addRequest,
    removeRequest,
  ]);
  const storageIsDisabled = useCallback((aStorage) => requests.includes(aStorage.id), [requests]);
  return useMemo(() => ({
    storageIsDisabled,
    requestAccess,
  }), [
    storageIsDisabled,
    requestAccess,
  ]);
}
