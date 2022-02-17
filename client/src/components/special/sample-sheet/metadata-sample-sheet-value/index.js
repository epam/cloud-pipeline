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
import {
  Button,
  Icon,
  message,
  Modal,
  Upload
} from 'antd';
import styles from './metadata-sample-sheet-value.css';
import SampleSheetEditDialog from '../edit-dialog';
import {isSampleSheetContent, buildEmptySampleSheet} from '../utilities';
import DataStorageItemContent from '../../../../models/dataStorage/DataStorageItemContent';

function fetchSampleSheetContents (options = {}) {
  return new Promise(async (resolve, reject) => {
    const {
      dataStorageAvailable,
      path
    } = options;
    try {
      if (dataStorageAvailable) {
        await dataStorageAvailable.fetchIfNeededOrWait();
        const match = storage => storage.pathMask &&
          (new RegExp(`^${storage.pathMask}/`, 'i')).test(path);
        const storage = (dataStorageAvailable.value || []).find(match);
        if (storage) {
          let relativePath = path.slice(storage.pathMask.length);
          if (relativePath.startsWith('/')) {
            relativePath = relativePath.slice(1);
          }
          const request = new DataStorageItemContent(storage.id, relativePath);
          await request.fetch();
          if (request.error || !request.loaded) {
            throw new Error(request.error || 'Error fetching file content');
          }
          const {content} = request.value;
          if (!content) {
            throw new Error(request.error || 'Error fetching file content');
          }
          resolve(atob(content));
          return;
        }
      }
      resolve('');
    } catch (e) {
      reject(e);
    }
  });
}

function readFileContents (file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.readAsText(file, 'UTF-8');
    reader.onload = function (evt) {
      resolve(evt.target.result);
    };
    reader.onerror = function (evt) {
      reject(new Error('Error reading file content'));
    };
  });
}

@inject('dataStorageAvailable')
@observer
class MetadataSampleSheetValue extends React.Component {
  state = {
    editDialogVisible: false,
    sampleSheetContent: undefined
  };

  get sampleSheetAvailable () {
    const {value} = this.props;
    return !!value;
  }

  get sampleSheetFileName () {
    const {value} = this.props;
    if (value) {
      return value.split('/').pop();
    }
    return 'SampleSheet.csv';
  }

  onEditClick = async (e) => {
    if (e) {
      e.stopPropagation();
    }
    const {
      dataStorageAvailable,
      value
    } = this.props;
    let hide;
    if (value) {
      hide = message.loading(`Reading ${this.sampleSheetFileName || 'SampleSheet'}...`);
    }
    try {
      let contents = buildEmptySampleSheet();
      if (value) {
        contents = await fetchSampleSheetContents({dataStorageAvailable, path: value});
      }
      this.setState({
        editDialogVisible: true,
        sampleSheetContent: contents
      });
    } catch (e) {
      message.error(e.message, 5);
    } finally {
      if (typeof hide === 'function') {
        hide();
      }
    }
  };

  onCloseEditDialog = () => {
    this.setState({
      editDialogVisible: false
    });
  };

  onChangeSampleSheetContent = (content) => {
    const {onChange} = this.props;
    if (onChange) {
      onChange(content);
    }
    this.onCloseEditDialog();
  };

  onRemoveClick = (e) => {
    if (e) {
      e.stopPropagation();
    }
    if (this.sampleSheetAvailable) {
      const {onRemove} = this.props;
      Modal.confirm({
        // eslint-disable-next-line
        title: `Are you sure you want to remove ${this.sampleSheetFileName} and associated samples?`,
        okText: 'YES',
        onOk: () => onRemove && onRemove()
      });
    }
  };

  onUpload = (file) => {
    readFileContents(file)
      .then((contents) => {
        if (isSampleSheetContent(contents)) {
          const {onChange} = this.props;
          if (onChange) {
            onChange(contents);
          }
        } else {
          throw new Error(`${file.name} is not a valid SampleSheet`);
        }
      })
      .catch(e => message.error(e.message, 5));
    return false;
  };

  renderEditActions () {
    return [
      (
        <Button
          key="edit"
          className={styles.button}
          size="small"
          onClick={this.onEditClick}
          style={{width: 78}}
        >
          <Icon type="edit" /> {this.sampleSheetAvailable ? 'Edit' : 'Create'}
        </Button>
      ),
      (
        <Upload
          fileList={[]}
          key="upload"
          beforeUpload={this.onUpload}
          multiple={false}
        >
          <Button
            key="edit"
            className={styles.button}
            size="small"
          >
            <Icon type="upload" /> Upload
          </Button>
        </Upload>
      )
    ];
  }

  renderRemoveAction () {
    if (this.sampleSheetAvailable) {
      return (
        <Button
          key="delete"
          className={styles.button}
          size="small"
          type="danger"
          onClick={this.onRemoveClick}
        >
          <Icon type="delete" /> Remove
        </Button>
      );
    }
    return null;
  }

  render () {
    const {
      className,
      style
    } = this.props;
    const {
      editDialogVisible,
      sampleSheetContent
    } = this.state;
    return (
      <div
        className={
          classNames(
            className,
            styles.buttons
          )
        }
        style={style}
        onClick={e => e.stopPropagation()}
        onMouseDown={e => e.stopPropagation()}
        onMouseUp={e => e.stopPropagation()}
      >
        <div style={{marginRight: 5}}>
          {this.renderEditActions()}
        </div>
        <div>
          {this.renderRemoveAction()}
        </div>
        <SampleSheetEditDialog
          content={sampleSheetContent}
          visible={editDialogVisible}
          onClose={this.onCloseEditDialog}
          onSave={this.onChangeSampleSheetContent}
        />
      </div>
    );
  }
}

MetadataSampleSheetValue.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  value: PropTypes.string,
  onRemove: PropTypes.func,
  onChange: PropTypes.func
};

export default MetadataSampleSheetValue;
