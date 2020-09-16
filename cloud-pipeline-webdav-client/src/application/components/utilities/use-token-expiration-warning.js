import React, {useEffect} from 'react';
import {Modal} from 'antd';
import moment from 'moment-timezone';
import electron from 'electron';
import parse from '../../models/file-systems/utilities/jwt-token-parser';

export default function useTokenExpirationWarning (...dependencies) {
  useEffect(() => {
    const cfg = electron.remote.getGlobal('webdavClient');
    const {config: webdavClientConfig = {}} = cfg || {};
    const token = parse(webdavClientConfig.password);
    if (token) {
      const days = token.exp.diff(moment(), 'days');
      if (token.exp < moment()) {
        Modal.error({
          title: 'Access token is expired',
          content: (
            <div>
              <div>Issued to <b>{token.sub}</b></div>
              <div>Issued at <b>{token.iat.format('d MMMM YYYY, HH:mm')}</b></div>
              <div>Expired at <b>{token.exp.format('d MMMM YYYY, HH:mm')}</b></div>
            </div>
          )
        });
      } else if (days < 1) {
        Modal.warn({
          title: 'Access token will expire soon',
          content: (
            <div>
              <div>Issued to <b>{token.sub}</b></div>
              <div>Issued at <b>{token.iat.format('d MMMM YYYY, HH:mm')}</b></div>
              <div>Expires at <b>{token.exp.format('d MMMM YYYY, HH:mm')}</b></div>
            </div>
          )
        });
      } else if (days <= 7) {
        Modal.warn({
          title: `Access token will expire in ${days} days`,
          content: (
            <div>
              <div>Issued to <b>{token.sub}</b></div>
              <div>Issued at <b>{token.iat.format('d MMMM YYYY, HH:mm')}</b></div>
              <div>Expires at <b>{token.exp.format('d MMMM YYYY, HH:mm')}</b></div>
            </div>
          )
        });
      }
    }
  }, dependencies);
}
