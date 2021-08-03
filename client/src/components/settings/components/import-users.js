/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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
  Upload,
  Dropdown,
  Menu
} from 'antd';
import {inject, observer} from 'mobx-react';
import classNames from 'classnames';
import displaySize from '../../../utils/displaySize';
import importUsersUrl from '../../../models/user/Import';
import styles from './import-users.css';
import UserIntegrityCheck from '../user-integrity-check';

const DROPDOWN_KEYS = {
  checkUsers: 'checkUsers'
};

@inject('systemDictionaries', 'users')
@observer
class ImportUsersButton extends React.Component {
  state = {
    attributes: [],
    users: [],
    metadata: [],
    file: undefined,
    pending: false,
    dialogVisible: false,
    createUsers: false,
    createGroups: false,
    fixUsers: []
  };

  onFileSelected = file => {
    const {systemDictionaries} = this.props;
    const dictionaries = systemDictionaries.loaded
      ? new Set((systemDictionaries.value || []).map(d => d.key))
      : new Set([]);
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
          attributes: [],
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
        const attrs = (lines[0] || '')
          .slice(2)
          .filter(Boolean);
        const metadata = attrs.filter(a => dictionaries.has(a));
        const attributes = attrs.filter(a => !dictionaries.has(a));
        this.setState({
          attributes: attributes.map(m => ({name: m, create: false})),
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

  checkUsers = async () => {
    return new Promise((resolve) => {
      const {
        users: usersRequest,
        systemDictionaries: systemDictionariesRequest
      } = this.props;
      Promise.all([
        usersRequest.fetchIfNeededOrWait(),
        systemDictionariesRequest.fetchIfNeededOrWait()
      ])
        .then(() => {
          if (usersRequest.loaded && systemDictionariesRequest.loaded) {
            const users = (usersRequest.value || []).slice();
            const systemDictionaries = (systemDictionariesRequest.value || []).slice();
            return UserIntegrityCheck.check(users, systemDictionaries);
          } else {
            return Promise.resolve();
          }
        })
        .then(resolve)
        .catch(() => resolve());
    });
  };

  checkUsersIntegrityClick = () => {
    const hide = message.loading('Checking users integrity...', 0);
    this.checkUsers()
      .then(checkResult => {
        this.setState({
          fixUsers: checkResult || []
        });
      })
      .catch(() => {})
      .then(() => hide());
  };

  closeUsersIntegrityModal = () => {
    this.setState({
      fixUsers: []
    });
  };

  onDropdownMenuClick = ({key}) => {
    if (key === DROPDOWN_KEYS.checkUsers) {
      this.checkUsersIntegrityClick();
    }
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

  onCreateAttributesChanged = name => e => {
    const {attributes} = this.state;
    const m = (attributes || []).find(mm => mm.name === name);
    if (m) {
      m.create = e.target.checked;
      this.setState({
        attributes
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
      attributes,
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
        [
          ...metadata.filter(m => m.create).map(m => m.name),
          ...attributes.filter(m => m.create).map(m => m.name)
        ]
      );
      const hide = message.loading('Importing...', 0);
      const resolve = ({error, logs}) => {
        hide();
        if (error) {
          message.error(error, 5);
        }
        this.setState({
          attributes: [],
          users: [],
          metadata: [],
          file: undefined,
          pending: false,
          dialogVisible: false,
          createUsers: false,
          createGroups: false
        }, () => {
          onImportDone && onImportDone({error, logs});
        });
      };
      const request = new XMLHttpRequest();
      request.upload.onabort = (event) => {
        resolve({error: 'Aborted'});
      };
      request.onreadystatechange = () => {
        if (request.readyState !== 4) return;

        if (request.status !== 200) {
          resolve({error: `Error importing users: ${request.statusText}`});
        } else {
          try {
            const json = JSON.parse(request.responseText);
            if (json.status === 'ERROR') {
              resolve({error: json.message});
            } else {
              resolve({logs: json.payload});
            }
          } catch (_) {
            resolve({logs: []});
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
      disabled: d,
      size,
      style,
      systemDictionaries
    } = this.props;
    const {
      attributes,
      file,
      pending,
      dialogVisible,
      users,
      metadata,
      createGroups,
      createUsers,
      fixUsers
    } = this.state;
    const disabled = d || (systemDictionaries.pending && !systemDictionaries.loaded);
    const dropdownMenu = (
      <Menu
        onClick={this.onDropdownMenuClick}
      >
        <Menu.Item key={DROPDOWN_KEYS.checkUsers}>
          Check users integrity
        </Menu.Item>
      </Menu>
    );
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
          <div onClick={(event) => {
            if (event?.target?.classList.contains('ant-dropdown-trigger')) {
              event.stopPropagation();
            }
          }}>
            <Dropdown.Button
              disabled={disabled || pending}
              size={size}
              overlay={dropdownMenu}
            >
              Import users
            </Dropdown.Button>
          </div>
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
            metadata.length > 0 && (
              <div style={{fontWeight: 'bold', marginTop: 5, marginBottom: 5}}>
                Create elements for dictionaries:
              </div>
            )
          }
          {
            metadata.map((m, i) => (
              <div key={`${m}_${i}`} className={styles.formItem}>
                <Checkbox
                  checked={m.create}
                  onChange={this.onCreateMetadataChanged(m.name)}
                >
                  {m.name}
                </Checkbox>
              </div>
            ))
          }
          {
            attributes.length > 0 && (
              <div style={{fontWeight: 'bold', marginTop: 5, marginBottom: 5}}>
                Create user attributes:
              </div>
            )
          }
          {
            attributes.map((m, i) => (
              <div key={`${m}_${i}`} className={styles.formItem}>
                <Checkbox
                  checked={m.create}
                  onChange={this.onCreateAttributesChanged(m.name)}
                >
                  {m.name}
                </Checkbox>
              </div>
            ))
          }
        </Modal>
        <UserIntegrityCheck
          visible={(fixUsers || []).length > 0}
          users={fixUsers}
          onClose={this.closeUsersIntegrityModal}
        />
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
