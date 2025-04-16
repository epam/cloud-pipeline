/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import {inject, observer} from 'mobx-react';
import auditStorageAccessManager from '../../../utils/audit-storage-access';
import {Button, message} from 'antd';
import {getStorageLinkInfo} from '../data-storage-link/utilities';
import GenerateDownloadUrlRequest from '../../../models/dataStorage/GenerateDownloadUrl';

@inject('dataStorageAvailable')
@observer
class DataStorageFileDownloadButton extends React.Component {
  state = {
    info: undefined
  };

  componentDidMount () {
    this.updateFileInfo();
  }

  componentDidUpdate (prevProps) {
    if (prevProps.filePath !== this.props.filePath) {
      this.updateFileInfo();
    }
  }

  componentWillUnmount () {
    this.token = {};
  }

  updateFileInfo = () => {
    const token = this.token = {};
    const commit = (fn) => {
      if (token === this.token) {
        fn();
      }
    };
    const {
      dataStorageAvailable,
      filePath
    } = this.props;
    (async () => {
      try {
        await dataStorageAvailable.fetchIfNeededOrWait();
        const storages = dataStorageAvailable.value || [];
        const info = getStorageLinkInfo({
          storages,
          path: filePath,
          isFolder: false
        });
        commit(() => this.setState({
          info
        }));
      } catch {
        // noop
      }
    })();
  };

  onClick = (event) => {
    if (event) {
      event.stopPropagation();
      event.preventDefault();
    }
    const {info} = this.state;
    const {
      storage,
      relativePath
    } = info ?? {};
    if (storage && relativePath) {
      (async () => {
        const name = relativePath.split('/').pop();
        const hide = message.loading(
          (<span>Downloading <b>{name}</b>...</span>),
          0
        );
        const request = new GenerateDownloadUrlRequest(storage.id, relativePath);
        try {
          await request.fetch();
          if (request.error) {
            throw new Error(request.error);
          } else {
            auditStorageAccessManager.reportReadAccess({
              storageId: storage.id,
              path: relativePath,
              reportStorageType: 'S3'
            });
            const a = document.createElement('a');
            a.href = request.value.url;
            a.download = name;
            a.style.display = 'none';
            a.click();
          }
        } catch (e) {
          message.error(
            (<span>Error downloading <b>{name}</b>: {e.message}</span>),
            5
          );
        } finally {
          hide();
        }
      })();
    }
  };

  render () {
    const {
      className,
      style,
      disabled,
      mode = 'button',
      children = 'Download file',
      size,
      primary = false
    } = this.props;
    const {
      info
    } = this.state;
    const {
      relativePath,
      storage
    } = info || {};
    if (!relativePath || !storage) {
      return null;
    }
    if (/^link$/i.test(mode)) {
      if (disabled) {
        return (
          <span
            className={classNames(className, 'cp-text-not-important')}
            style={style}
          >
            {children}
          </span>
        );
      }
      return (
        <a onClick={this.onClick}>
          {children}
        </a>
      );
    }
    return (
      <Button
        className={className}
        style={style}
        size={size}
        onClick={this.onClick}
        type={primary ? 'primary' : undefined}
        disabled={disabled}
      >
        {children}
      </Button>
    );
  }
}

DataStorageFileDownloadButton.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  children: PropTypes.node,
  disabled: PropTypes.bool,
  filePath: PropTypes.string,
  mode: PropTypes.oneOf(['link', 'button']),
  size: PropTypes.oneOf(['small', 'medium', 'large']), // only for 'button' mode
  primary: PropTypes.bool // only for 'button' mode
};

export default DataStorageFileDownloadButton;
