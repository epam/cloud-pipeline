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
import {Icon, message} from 'antd';
import Dropdown from 'rc-dropdown';
import Menu, {MenuItem} from 'rc-menu';
import GenerateDownloadUrlRequest from '../../../../../models/dataStorage/GenerateDownloadUrl';
import DataStorageTags from '../../../../../models/dataStorage/tags/DataStorageTags';
import auditStorageAccessManager from '../../../../../utils/audit-storage-access';

class DownloadFileButton extends React.Component {
  state = {
    pending: false,
    visible: false,
    downloadOtherFilePath: undefined,
    downloadOtherFileFetched: false
  };

  componentDidMount () {
    this.resetDownloadFilePath();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (
      prevProps.storageId !== this.props.storageId ||
      prevProps.path !== this.props.path ||
      prevProps.version !== this.props.version
    ) {
      this.resetDownloadFilePath();
    }
  }

  resetDownloadFilePath = () => {
    this.setState({
      pending: false,
      visible: false,
      downloadOtherFilePath: undefined,
      downloadOtherFileFetched: false
    });
  };

  get fileName () {
    const {path} = this.props;
    if (path) {
      return path.split('/').pop();
    }
    return 'File';
  }

  handleClick = (event) => {
    event.stopPropagation();
    event.preventDefault();
    const {
      downloadOtherFileFetched
    } = this.state;
    const {
      storageId,
      path,
      version,
      preferences
    } = this.props;
    if (downloadOtherFileFetched) {
      return this.downloadSingleFile(path, version);
    }
    if (!storageId || !path) {
      return;
    }
    this.setState({
      pending: true,
      visible: false
    }, async () => {
      const state = {
        pending: false,
        downloadOtherFileFetched: true
      };
      try {
        await preferences.fetchIfNeededOrWait();
        const downloadTag = preferences.facetedFilterDownloadFileTag;
        if (downloadTag) {
          const tags = new DataStorageTags(storageId, path, version);
          await tags.fetch();
          if (
            tags.loaded &&
            tags.value &&
            tags.value[downloadTag] &&
            tags.value[downloadTag] !== path
          ) {
            state.downloadOtherFilePath = tags.value[downloadTag];
            state.visible = true;
          }
        }
      } catch (error) {
        message.error(error.message, 5);
      } finally {
        this.setState(state);
      }
      if (!state.downloadOtherFilePath) {
        this.downloadSingleFile(path, version);
      }
    });
  };

  downloadSingleFile = async (path, version) => {
    const {
      storageId
    } = this.props;
    if (!path || !storageId) {
      return;
    }
    const name = path.split('/').pop();
    const hide = message.loading(`Fetching ${name} url...`, 0);
    const request = new GenerateDownloadUrlRequest(storageId, path, version);
    await request.fetch();
    if (request.error) {
      hide();
      message.error(request.error);
    } else {
      auditStorageAccessManager.reportReadAccess({
        storageId,
        path,
        reportStorageType: 'S3'
      });
      hide();
      const a = document.createElement('a');
      a.href = request.value.url;
      a.download = name;
      a.style.display = 'none';
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
    }
    return false;
  };

  handleDropdownVisibility = (visible) => this.setState({visible});

  handleMenuClick = ({key}) => {
    this.setState({
      visible: false
    }, () => {
      if (key === 'original') {
        const {path, version} = this.props;
        (this.downloadSingleFile)(path, version);
      } else {
        const {downloadOtherFilePath} = this.state;
        (this.downloadSingleFile)(downloadOtherFilePath);
      }
    });
  };

  render () {
    const {
      className,
      style,
      storageId,
      path,
      version
    } = this.props;
    const {
      pending,
      downloadOtherFileFetched,
      downloadOtherFilePath,
      visible
    } = this.state;
    if (!downloadOtherFileFetched || !downloadOtherFilePath) {
      return (
        <a
          id={`download-${path}`}
          className={classNames('cp-button', className)}
          href={
            GenerateDownloadUrlRequest.getRedirectUrl(storageId, path, version)
          }
          target="_blank"
          download={this.fileName}
          onClick={(e) => this.handleClick(e)}
          style={style}
        >
          <Icon
            type={pending ? 'loading' : 'download'}
          />
        </a>
      );
    }
    return (
      <Dropdown
        trigger={['click']}
        visible={visible}
        onVisibleChange={this.handleDropdownVisibility}
        overlay={
          <Menu
            selectedKeys={[]}
            onClick={this.handleMenuClick}
            style={{minWidth: 150, cursor: 'pointer'}}>
            <MenuItem
              id={`menu-item-download-${path}`}
              className={`menu-item-download-${path}`}
              key="original"
            >
              {this.fileName}
            </MenuItem>
            <MenuItem
              id={`menu-item-download-${downloadOtherFilePath}`}
              className={`menu-item-download-${downloadOtherFilePath}`}
              key="other"
            >
              {(downloadOtherFilePath || '').split('/').pop()}
            </MenuItem>
          </Menu>
        }
      >
        <a
          id={`download-${path}`}
          className={classNames('cp-button', className)}
          style={style}
          href={
            GenerateDownloadUrlRequest.getRedirectUrl(storageId, path, version)
          }
          onClick={event => event.preventDefault()}
        >
          <Icon
            type={pending ? 'loading' : 'download'}
          />
        </a>
      </Dropdown>
    );
  }
}

DownloadFileButton.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  storageId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  path: PropTypes.string,
  version: PropTypes.string
};

export default inject('preferences')(observer(DownloadFileButton));
