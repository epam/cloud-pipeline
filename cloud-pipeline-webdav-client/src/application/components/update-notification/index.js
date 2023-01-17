import React, { useCallback, useEffect, useState } from 'react';
import electron from 'electron';
import {CloseOutlined} from '@ant-design/icons';
import checker from '../../../auto-update';
import './update-notification.css';

export default function UpdateNotification () {
  const config = (() => {
    const cfg = (electron.remote === undefined)
      ? global.webdavClient
      : electron.remote.getGlobal('webdavClient');
    return (cfg || {}).config || {};
  })();
  const {
    name: appName = 'Cloud Data'
  } = config;

  const [notificationVisible, setNotificationVisible] = useState(false);
  const [updating, setUpdating] = useState(false);
  const [error, setError] = useState(undefined);

  const handle = useCallback((options = {}) => {
    const {
      available = false
    } = options;
    setNotificationVisible(available);
  }, [setNotificationVisible]);

  const closeNotification = useCallback(() => setNotificationVisible(false), [setNotificationVisible]);

  useEffect(() => {
    checker.addEventListener(handle);
    return () => checker.removeEventListener(handle);
  }, [checker, handle]);

  const onUpdateApplication = useCallback(async () => {
    setUpdating(true);
    setError(false);
    try {
      await checker.update();
      // Normally, update script should kill current process.
      // If for some reason it is finished, but current process is alive,
      // we need to warn user that something went wrong.
      setError(`Unknown error updating and re-launching ${appName || 'Cloud Data'}.`);
      setUpdating(false);
    } catch (e) {
      setError(e.message);
      setUpdating(false);
    }
  }, [setUpdating, setError, appName]);
  return (
    <div
      className="update-notification"
      style={notificationVisible ? {} : {display: 'none'}}
    >
      <CloseOutlined
        className="close"
        onClick={closeNotification}
      />
      {
        updating && (
          <div>
            <b>{appName}</b> is updating now. Do not close the window.
          </div>
        )
      }
      {
        !updating && (
          <div>
            New version of <b>{appName}</b> is available. <a onClick={onUpdateApplication}>Install updates</a>
          </div>
        )
      }
      {
        error && (
          <div
            className="update-error"
          >
            {error}
          </div>
        )
      }
    </div>
  );
}
