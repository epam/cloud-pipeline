import React, { useCallback, useEffect, useState } from 'react';
import electron from 'electron';
import {CloseOutlined} from '@ant-design/icons';
import cloudPipelineAPI from '../../models/cloud-pipeline-api';
import { log } from '../../models/log';
import autoUpdateApplication, {autoUpdateAvailable} from '../../../auto-update';
import './update-notification.css';

const UPDATE_INTERVAL_MS = 5 * 60 * 1000; // 5 minutes

export default function UpdateNotification () {
  const config = (() => {
    const cfg = (electron.remote === undefined)
      ? global.webdavClient
      : electron.remote.getGlobal('webdavClient');
    return (cfg || {}).config || {};
  })();
  const {
    name: appName = 'Cloud Data',
    componentVersion: currentVersion
  } = config;

  const [newVersion, setNewVersion] = useState(undefined);
  const [available, setAvailable] = useState(undefined);
  const [userClosedNotification, setUserClosedNotification] = useState(undefined);
  const [notificationVisible, setNotificationVisible] = useState(false);
  const [updating, setUpdating] = useState(false);
  const [error, setError] = useState(undefined);

  useEffect(() => {
    let handler;
    let stopped = false;
    const check = () => {
      clearTimeout(handler);
      Promise.resolve()
        .then(() => cloudPipelineAPI.initialize())
        .then(autoUpdateAvailable)
        .then(autoUpdateAvailableForOS => {
            setAvailable(autoUpdateAvailableForOS);
            return cloudPipelineAPI.getAppInfo();
          }
        )
        .then(version => {
          const updateAvailable = version &&
            // currentVersion &&
            version !== currentVersion;
          if (updateAvailable && newVersion !== version) {
            log(`New version ${version} available. Current version: ${currentVersion || 'unknown'}`);
          }
          if (newVersion !== version) {
            setNewVersion(version);
          }
        })
        .catch(e => log(`Error fetching app version: ${e.message}`))
        .then(() => {
          if (!stopped) {
            handler = setTimeout(check, UPDATE_INTERVAL_MS);
          }
        });
    };
    check();
    return () => {
      stopped = true;
      clearTimeout(handler);
    };
  }, [
    newVersion,
    setNewVersion,
    currentVersion,
    setAvailable
  ]);

  useEffect(() => {
    setNotificationVisible(
      available &&
      !!newVersion &&
      currentVersion !== newVersion &&
      userClosedNotification !== newVersion
    );
  }, [
    available,
    userClosedNotification,
    newVersion,
    currentVersion,
    setNotificationVisible
  ]);
  const onUpdateApplication = useCallback(async () => {
    setUpdating(true);
    setError(false);
    try {
      await autoUpdateApplication();
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
  const onCloseNotification = useCallback(
    () => setUserClosedNotification(newVersion),
    [setUserClosedNotification, newVersion]
  );
  return (
    <div
      className="update-notification"
      style={notificationVisible ? {} : {display: 'none'}}
    >
      <CloseOutlined
        className="close"
        onClick={onCloseNotification}
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
            New version of <b>{appName}</b> is available. <a onClick={onUpdateApplication}>Install update</a>
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
