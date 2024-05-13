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
import {Icon, Button} from 'antd';
import {downloadStorageItems} from '../../../../special/download-omics-storage-items';

export default class DownloadOmicsButton extends React.Component {

  static propTypes = {
    className: PropTypes.string,
    style: PropTypes.object,
    storageInfo: PropTypes.object,
    region: PropTypes.string,
    item: PropTypes.object
  };

  state = {
    pending: false
  };

  get item () {
    return this.props.item;
  }

  get storageId () {
    return (this.props.storageInfo || {}).id;
  }

  handleClick = (event) => {
    if (event) {
      event.stopPropagation();
      event.preventDefault();
    }
    this.setState({
      pending: true
    }, async () => {
      await this.downloadOmicsItems();
      this.setState({pending: false});
    });
  };

  downloadOmicsItems = async () => {
    const items = [{
      storageId: this.props.storageInfo.id,
      path: this.item.path,
      name: this.item.name,
      type: this.item.type,
      labels: {
        fileName: this.item.labels.fileName,
        fileType: this.item.labels.fileType
      }
    }];
    const config = {
      region: this.props.region,
      storageInfo: this.props.storageInfo
    };
    await downloadStorageItems(items, config);
  }

  render () {
    const {className, style} = this.props;
    const {pending} = this.state;
    return (
      <Button
        id="download omics files"
        key="download"
        size="small"
        style={style}
        className={classNames('cp-button', className)}
        onClick={(e) => this.handleClick(e)}>
        <Icon type={pending ? 'loading' : 'download'} />
      </Button>
    );
  }
}
