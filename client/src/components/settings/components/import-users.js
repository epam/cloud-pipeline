/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
import {
  Button,
  Checkbox,
  message,
  Modal,
  Upload
} from 'antd';
import classNames from 'classnames';
import displaySize from '../../../utils/displaySize';
import importUsersUrl from '../../../models/user/Import';
import styles from './import-users.css';

class ImportUsersButton extends React.Component {
  state = {
    users: [],
    metadata: [],
    file: undefined,
    pending: false,
    dialogVisible: false,
    createUsers: false,
    createGroups: false
  };

  onFileSelected = file => {
    const hide = message.loading('Analyzing...', 0);
    this.setState({
      file,
      pending: true,
      dialogVisible: false
    }, () => {
      const fileReader = new FileReader();
      const onError = (e) => {
        hide();
        message.error(e || 'Error reading file', 5);
        this.setState({
          file: undefined,
          pending: false,
          users: [],
          metadata: [],
          dialogVisible: false,
          createUsers: false,
          createGroups: false
        });
      };
      const onLoad = (data) => {
        hide();
        const lines = (data || '')
          .split('\n')
          .map(line => line.split(/[,;\t]/));
        if (
          lines.length < 1 ||
          lines[0].length < 2 ||
          !/^username$/i.test(lines[0][0]) ||
          !/^groups$/i.test(lines[0][1])
        ) {
          onError('Wrong file format');
          return;
        }
        const users = lines.slice(1).map(line => line[0]).filter(Boolean);
        const metadata = (lines[0] || '').slice(2).filter(Boolean);
        this.setState({
          users,
          metadata: metadata.map(m => ({name: m, create: false})),
          pending: false,
          dialogVisible: true,
          createUsers: false,
          createGroups: false
        });
      };
      fileReader.onload = function () {
        onLoad(this.result);
      };
      fileReader.onerror = function () {
        onError();
      };
      fileReader.readAsText(file);
    });
    return false;
  };

  onCreateUsersChanged = e => {
    this.setState({
      createUsers: e.target.checked
    });
  };

  onCreateGroupsChanged = e => {
    this.setState({
      createGroups: e.target.checked
    });
  };

  onCreateMetadataChanged = name => e => {
    const {metadata} = this.state;
    const m = (metadata || []).find(mm => mm.name === name);
    if (m) {
      m.create = e.target.checked;
      this.setState({
        metadata
      });
    }
  };

  onCancel = () => {
    this.setState({
      dialogVisible: false,
      file: undefined,
      metadata: [],
      pending: false
    });
  }

  onImport = () => {
    const {
      file,
      metadata,
      createGroups,
      createUsers
    } = this.state;
    const {
      onImportDone
    } = this.props;
    this.setState({
      pending: true
    }, () => {
      const url = importUsersUrl(
        createUsers,
        createGroups,
        metadata.filter(m => m.create).map(m => m.name)
      );
      const hide = message.loading('Importing...', 0);
      const resolve = (error) => {
        hide();
        if (error) {
          message.error(error, 5);
        }
        this.setState({
          users: [],
          metadata: [],
          file: undefined,
          pending: false,
          dialogVisible: false,
          createUsers: false,
          createGroups: false
        }, () => {
          onImportDone && onImportDone(!error);
        });
      };
      const request = new XMLHttpRequest();
      request.upload.onabort = (event) => {
        resolve('Aborted');
      };
      request.onreadystatechange = () => {
        if (request.readyState !== 4) return;

        if (request.status !== 200) {
          resolve(`Error importing users: ${request.statusText}`);
        } else {
          try {
            const json = JSON.parse(request.responseText);
            if (json.status === 'ERROR') {
              resolve(json.message);
            } else {
              resolve();
            }
          } catch (_) {
            resolve();
          }
        }
      };
      request.open('POST', url, true);
      request.withCredentials = true;
      const formData = new FormData();
      formData.append('file', file);
      request.send(formData);
    });
  };

  render () {
    const {
      className,
      disabled,
      size,
      style
    } = this.props;
    const {
      file,
      pending,
      dialogVisible,
      users,
      metadata,
      createGroups,
      createUsers
    } = this.state;
    return (
      <div
        className={classNames(className)}
        style={Object.assign({}, style, {display: 'inline'})}
      >
        <Upload
          fileList={[]}
          disabled={disabled || pending}
          multiple={false}
          beforeUpload={this.onFileSelected}
          showUploadList={false}
        >
          <Button
            disabled={disabled || pending}
            size={size}
          >
            Import users
          </Button>
        </Upload>
        <Modal
          visible={dialogVisible && !!file}
          title="Import settings"
          closable={!pending}
          maskClosable={!pending}
          onCancel={this.onCancel}
          footer={(
            <div
              className={styles.footer}
            >
              <Button
                onClick={this.onCancel}
                disabled={pending}
              >
                CANCEL
              </Button>
              <Button
                disabled={pending}
                type="primary"
                onClick={this.onImport}
              >
                IMPORT
              </Button>
            </div>
          )}
        >
          {
            file && (
              <div>
                <b>{file.name}</b>
                <span style={{marginLeft: 5}}>
                  ({displaySize(file.size)}, {users.length} user{users.length > 1 ? 's' : ''})
                </span>
              </div>
            )
          }
          <div className={styles.formItem}>
            <Checkbox
              checked={createUsers}
              onChange={this.onCreateUsersChanged}
            >
              Create users
            </Checkbox>
          </div>
          <div className={styles.formItem}>
            <Checkbox
              checked={createGroups}
              onChange={this.onCreateGroupsChanged}
            >
              Create groups
            </Checkbox>
          </div>
          {
            metadata.map((m, i) => (
              <div key={`${m}_${i}`} className={styles.formItem}>
                <Checkbox
                  checked={m.create}
                  onChange={this.onCreateMetadataChanged(m.name)}
                >
                  Create <b>{m.name}</b> dictionary
                </Checkbox>
              </div>
            ))
          }
        </Modal>
      </div>
    );
  }
}

ImportUsersButton.propTypes = {
  className: PropTypes.string,
  disabled: PropTypes.bool,
  size: PropTypes.oneOf(['small', 'default', 'large']),
  style: PropTypes.object,
  onImportDone: PropTypes.func
};

export default ImportUsersButton;
