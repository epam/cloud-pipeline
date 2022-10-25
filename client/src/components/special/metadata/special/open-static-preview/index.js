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
import {inject, observer} from 'mobx-react';
import classNames from 'classnames';
import {Icon} from 'antd';
import dataStorages from '../../../../../models/dataStorage/DataStorages';
import {getStaticResourceUrl} from '../../../../../models/static-resources';

@inject('preferences')
@observer
class OpenStaticPreview extends React.Component {
  state = {
    pending: false,
    storagePath: undefined
  };

  get fileName () {
    const {path} = this.props;
    return (path || '').split(/[\\/]/g).pop();
  }

  get staticResourceUrl () {
    const {
      storagePath
    } = this.state;
    const {
      path,
      preferences
    } = this.props;
    if (
      !storagePath ||
      !path ||
      !preferences.loaded ||
      !preferences.dataStorageItemPreviewMasks.some(o => o.test(path))
    ) {
      return null;
    }
    return getStaticResourceUrl(storagePath, path);
  }

  componentDidMount () {
    this.fetchStorageInfo();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (this.props.storageId !== prevProps.storageId) {
      this.fetchStorageInfo();
    }
  }

  fetchStorageInfo = () => {
    const {
      storageId
    } = this.props;
    if (storageId) {
      this.setState({
        pending: true
      }, async () => {
        const state = {
          pending: false,
          storagePath: undefined
        };
        try {
          const request = dataStorages.load(storageId);
          await request.fetchIfNeededOrWait();
          if (request.error || !request.loaded) {
            throw new Error(request.error || 'Error loading storage info');
          }
          const {
            path
          } = request.value;
          state.storagePath = path;
        } catch (_) {
        } finally {
          this.setState(state);
        }
      });
    } else {
      this.setState({
        pending: false,
        storagePath: undefined
      });
    }
  };

  render () {
    const staticResourceUrl = this.staticResourceUrl;
    if (!staticResourceUrl) {
      return null;
    }
    const {
      pending
    } = this.state;
    const {
      className,
      style
    } = this.props;
    return (
      <a
        className={
          classNames(
            className,
            {
              'cp-text': pending
            }
          )
        }
        href={pending ? undefined : staticResourceUrl}
        target="_blank"
        style={style}
      >
        <Icon type="export" /> Open <b>{this.fileName}</b> in a separate tab
      </a>
    );
  }
}

OpenStaticPreview.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  path: PropTypes.string,
  storageId: PropTypes.oneOfType([PropTypes.string, PropTypes.number])
};

export default OpenStaticPreview;
