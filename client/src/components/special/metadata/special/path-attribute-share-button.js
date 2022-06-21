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
import {Button, Icon} from 'antd';
import {getStorageFileAccessInfo} from '../../../../utils/object-storage';
// eslint-disable-next-line max-len
import SharedItemInfo from '../../../pipelines/browser/forms/data-storage-item-sharing/SharedItemInfo';

const parseJSONArray = (o) => {
  try {
    const array = JSON.parse(o);
    if (array && Array.isArray(array)) {
      return array;
    }
  } catch (_) {}
  return undefined;
};

class PathAttributeShareButton extends React.PureComponent {
  /**
   * @param {{key: string?, value: *, pathKeys: string[]?, testValue: boolean?}} options
   */
  static shareButtonAvailable = (options = {}) => {
    const {
      key,
      value,
      pathKeys = [],
      testValue = false
    } = options;
    if (typeof pathKeys.includes === 'function' && pathKeys.includes(key)) {
      return true;
    }
    return testValue && /^(s3|http|https):\/\//i.test(value);
  };

  state = {
    itemsToShare: [],
    shareDialogVisible: false
  };

  componentDidMount () {
    this.updatePathInfo();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (prevProps.path !== this.props.path) {
      this.updatePathInfo();
    }
  }

  updatePathInfo = () => {
    const {path} = this.props;
    let pathArray = parseJSONArray(path);
    if (pathArray === undefined && typeof path === 'string') {
      pathArray = [path];
    } else if (pathArray === undefined && typeof path === 'object' && path.length !== undefined) {
      pathArray = path;
    }
    if (pathArray && pathArray.length) {
      Promise.all(pathArray.map(getStorageFileAccessInfo))
        .then(infos => {
          const itemsToShare = infos
            .filter(info => info && info.objectStorage && info.path)
            .map(info => ({
              path: info.path,
              storageId: info.objectStorage.id,
              type: /\.(.+)$/.test(info.path) ? 'file' : 'folder'
            }));
          this.setState({
            shareDialogVisible: false,
            itemsToShare
          });
        });
    } else {
      this.setState({shareDialogVisible: false, itemsToShare: []});
    }
  };

  share = (event) => {
    event.stopPropagation();
    const {itemsToShare} = this.state;
    if (itemsToShare.length === 0) {
      return;
    }
    this.setState({
      shareDialogVisible: true
    });
  }

  closeShareItemDialog = () => {
    return this.setState({
      shareDialogVisible: false
    });
  };

  render () {
    const {
      shareDialogVisible,
      itemsToShare = []
    } = this.state;
    if (itemsToShare.length === 0) {
      return null;
    }
    const {
      className,
      style,
      id
    } = this.props;
    return (
      <Button
        id={id}
        size="small"
        onClick={this.share}
        style={style}
        className={className}
      >
        <Icon type="share-alt" />
        <SharedItemInfo
          visible={shareDialogVisible}
          shareItems={itemsToShare}
          close={this.closeShareItemDialog}
        />
      </Button>
    );
  }
}

PathAttributeShareButton.propTypes = {
  id: PropTypes.string,
  className: PropTypes.string,
  style: PropTypes.object,
  path: PropTypes.string
};

export default PathAttributeShareButton;
