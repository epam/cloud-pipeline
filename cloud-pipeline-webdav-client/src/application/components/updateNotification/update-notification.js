import React, {useEffect} from 'react';
import electron from 'electron';
import {
  Button,
  notification
} from 'antd';
import './update-notification.css';

function UpdateNotification () {
  const config = (() => {
    const cfg = (electron.remote === undefined)
      ? global.webdavClient
      : electron.remote.getGlobal('webdavClient');
    return (cfg || {}).config;
  })();

  useEffect(() => {
    const {
      api,
      password,
      version: currentVersion
    } = config;
    if (api && password) {
      (async () => {
        const endpoint = 'app/info';
        const version = await fetch(`${api}${endpoint}`, {
          headers: {
            "Authorization": `Bearer ${password}`,
            "Content-type": "application/json",
            "Accept": "application/json",
            "Accept-Charset": "utf-8"
          }
        })
          .then(res => res.json())
          .then(res => res.payload.version || '');
        if (version !== currentVersion) {
          showNotification();
        }
      })();
    }
  }, [config]);

  const showNotification = () => {
    const button = (
      <div className='update-button'>
        <Button
          type="primary"
          onClick={() => console.log('install')}
        >
          Install Update
        </Button>
      </div>
    )
    const args = {
      message: 'New version is available',
      description: button,
      duration: 0,
    };
    notification.config({
      placement: 'bottomRight'
    })
    notification.open(args);
  };
  return null;
}

export default UpdateNotification;
