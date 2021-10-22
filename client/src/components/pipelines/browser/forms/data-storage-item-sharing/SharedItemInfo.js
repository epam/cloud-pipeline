/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import React from 'react';
import {
  Button,
  Icon,
  Input,
  Modal,
  Row,
  Spin,
  Alert,
  message,
  Popover
} from 'antd';
import PropTypes from 'prop-types';
import {inject, observer} from 'mobx-react';
import DataStorageItemPermissionsForm from './DataStorageItemPermissionsForm';
import {
  shareStorageItem,
  getSharedStorageItemInfo
} from '../../../../../utils/share-storage-item';
import UserName from '../../../../special/UserName';
import styles from './SharedItemInfo.css';
import roleModel from "../../../../../utils/roleModel";

const MAX_SIDS_TO_PREVIEW = 10;

const SidInfo = ({sid, style}) => {
  if (sid.principal) {
    return (
      <UserName
        showIcon
        userName={sid.name}
        style={style}
      />
    );
  }
  return (
    <span
      style={style}
    >
      <Icon type="team" />
      {sid.name}
    </span>
  );
};

@inject('preferences')
@observer
class SharedItemInfo extends React.Component {
  state = {
    usersToShare: [],
    sharedLink: undefined,
    mask: 0,
    pending: false,
    editPermissionsMode: false,
    initialized: false,
    permissionsModificationAllowed: true
  }

  componentDidMount () {
    this.updateFromProps();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (
      prevProps.storage !== this.props.storage ||
      prevProps.path !== this.props.path ||
      (
        prevProps.visible !== this.props.visible && this.props.visible
      )
    ) {
      this.updateFromProps();
    }
  }

  updateFromProps () {
    if (this.props.storage && this.props.path) {
      const writeAvailable = roleModel.writeAllowed(this.props.storage);
      const extraMask = writeAvailable ? 0b1111 : 0b0011;
      this.setState({pending: true, initialized: false}, async () => {
        const newState = {
          editPermissionsMode: true,
          alreadyShared: false,
          pending: false,
          error: undefined,
          initialized: true,
          permissionsModificationAllowed: true
        };
        try {
          await this.props.preferences.fetchIfNeededOrWait();
          const info = await getSharedStorageItemInfo(
            this.props.preferences,
            this.props.storage,
            this.props.path
          );
          if (info) {
            const {
              url,
              permissions = {},
              permissionsModificationAllowed
            } = info;
            const {
              mask = 0,
              permissions: usersToShare = []
            } = permissions;
            newState.sharedLink = url;
            newState.mask = mask;
            newState.usersToShare = usersToShare.slice();
            newState.editPermissionsMode = false;
            newState.permissionsModificationAllowed = permissionsModificationAllowed;
          } else {
            newState.sharedLink = undefined;
            const parseGroups = o => {
              if (!o) {
                return [];
              }
              if (Array.isArray(o)) {
                return o;
              }
              if (typeof o === 'string') {
                return o.split(',').map(g => g.trim());
              }
              return [];
            };
            const {
              mask = 1,
              groups = []
            } = this.props.preferences.sharedStoragesDefaultPermissions;
            newState.mask = mask & extraMask;
            newState.usersToShare = parseGroups(groups)
              .map(g => ({name: g, principal: false}));
          }
        } catch (e) {
          newState.error = e.message;
        } finally {
          this.setState(newState);
        }
      });
    } else {
      this.setState({
        pending: false,
        mask: 0,
        usersToShare: [],
        sharedLink: undefined,
        error: undefined,
        initialized: true,
        permissionsModificationAllowed: true
      });
    }
  }

  get itemName () {
    const {
      path,
      storage
    } = this.props;
    if (path) {
      const {
        delimiter = '/'
      } = storage || {};
      return path.split(delimiter).pop();
    }
    return '';
  }

  copyUrlToClipboard = (event) => {
    event && event.stopPropagation();
    if (navigator?.clipboard?.writeText) {
      const {
        sharedLink = ''
      } = this.state;
      navigator.clipboard.writeText(sharedLink).then(() => {
        message.info('Share link copied to clipboard', 3);
      });
    }
  };

  closeShareDialog = () => {
    const {close} = this.props;
    this.setState({editPermissionsMode: false});
    if (close) {
      close();
    }
  }

  onShare = async (options = {}) => {
    const {
      mask = 0,
      sids = []
    } = options;
    const {
      storage,
      path,
      preferences
    } = this.props;
    this.setState({
      pending: true
    });
    const newState = {
      pending: false
    };
    try {
      newState.sharedLink = await shareStorageItem(
        preferences,
        storage,
        path,
        {
          mask,
          permissions: (sids).slice()
        }
      );
      newState.mask = mask;
      newState.usersToShare = sids.slice();
      newState.editPermissionsMode = false;
    } catch (e) {
      newState.error = e.message;
    } finally {
      this.setState(newState);
    }
  }

  onEditPermissions = () => {
    if (this.state.permissionsModificationAllowed) {
      this.setState({editPermissionsMode: true});
    }
  }

  renderShareInfo = () => {
    const {path, storage} = this.props;
    if (!path || !storage) {
      return null;
    }
    const {
      editPermissionsMode,
      usersToShare: sids = [],
      sharedLink
    } = this.state;
    if (editPermissionsMode) {
      return null;
    }
    const plural = (o, word) => `${o} ${word}${o > 1 ? 's' : ''}`;
    let info;
    if (sharedLink && sids.length > 0) {
      const users = sids.filter(o => o.principal);
      const groups = sids.filter(o => !o.principal);
      const sharedWith = [
        users.length > 0 ? plural(users.length, 'user') : undefined,
        groups.length > 0 ? plural(groups.length, 'group') : undefined
      ].filter(Boolean);
      if (sids.length > MAX_SIDS_TO_PREVIEW) {
        info = (
          <Popover
            content={(
              <div className={styles.sharedWithPopover}>
                {
                  sids
                    .sort((a, b) => Number(b.principal) - Number(a.principal))
                    .map((sid) => (
                      <div
                        key={`${sid.principal ? 'user' : 'group'}-${sid.name}`}
                      >
                        <SidInfo sid={sid} />
                      </div>
                    ))
                }
              </div>
            )}
          >
            <a className={styles.noLink}>
              Folder shared with {sharedWith.join(' and ')}
            </a>
          </Popover>
        );
      } else if (sids.length > 0) {
        info = (
          <div
            className={styles.sharedWith}
          >
            <span>Folder shared with:</span>
            {
              sids
                .sort((a, b) => Number(b.principal) - Number(a.principal))
                .map((sid) => (
                  <SidInfo
                    key={`${sid.principal ? 'user' : 'group'}-${sid.name}`}
                    sid={sid}
                    style={{marginLeft: 5}}
                  />
                ))
            }
          </div>
        )
      }
    }
    return (
      <div className={styles.container}
      >
        {
          sharedLink && (
            <span className={styles.mainText}>
              {`Link ${this.itemName ? `to ${this.itemName}` : ''} shared folder`}
            </span>
          )
        }
        {
          sharedLink && (
            <span className={styles.hint}>
              Make sure you copy the link below.
            </span>
          )
        }
        {
          sharedLink && (
            <div style={{width: '100%'}}>
              {this.renderSharedLink()}
            </div>
          )
        }
        <div className={styles.shareInfo}>
          {info}
        </div>
      </div>
    );
  }

  renderSharedLink = () => {
    const {sharedLink} = this.state;
    return (
      <div className={styles.urlSection}>
        <Input
          value={sharedLink}
          readOnly
          style={{flex: 1}}
          size="large"
        />
        <Button
          type="primary"
          onClick={this.copyUrlToClipboard}
          style={{
            marginLeft: 5
          }}
          icon="copy"
        />
      </div>
    );
  }

  renderUserSelectForm = () => {
    const {
      editPermissionsMode,
      sharedLink,
      initialized
    } = this.state;
    if (!editPermissionsMode || !initialized) {
      return null;
    }
    const onCancel = () => {
      if (sharedLink) {
        this.setState({editPermissionsMode: false});
      } else {
        this.closeShareDialog();
      }
    };
    return (
      <DataStorageItemPermissionsForm
        sids={this.state.usersToShare}
        mask={this.state.mask}
        onSave={this.onShare}
        onCancel={onCancel}
        saveEnabled={!this.state.sharedLink}
        writeAvailable={roleModel.writeAllowed(this.props.storage)}
      />
    );
  }

  renderModalFooter = () => {
    const {
      editPermissionsMode,
      initialized,
      permissionsModificationAllowed
    } = this.state;
    if (editPermissionsMode || !initialized) {
      return null;
    }
    if (!permissionsModificationAllowed) {
      return (
        <Row type="flex" justify="end">
          <Button
            onClick={this.closeShareDialog}
          >
            CLOSE
          </Button>
        </Row>
      );
    }
    return (
      <Row type="flex" justify="space-between">
        <Button
          onClick={this.closeShareDialog}
        >
          CLOSE
        </Button>
        <Button
          type="primary"
          onClick={this.onEditPermissions}
        >
          EDIT PERMISSIONS
        </Button>
      </Row>
    );
  };

  render () {
    const {
      error,
      editPermissionsMode
    } = this.state;
    const selectUsersTitle = (
      <span>
        Select users / groups to share <b>{this.itemName}</b> folder
      </span>
    );
    const defaultTitle = (
      <span>
        Share <b>{this.itemName}</b> folder
      </span>
    );
    return (
      <Modal
        visible={this.props.visible}
        title={
          editPermissionsMode ? selectUsersTitle : defaultTitle
        }
        onCancel={this.closeShareDialog}
        footer={this.renderModalFooter()}
        bodyStyle={{
          padding: 10
        }}
      >
        <div>
          {
            error && (
              <Alert
                type="error"
                message={error}
                style={{marginBottom: 5}}
              />
            )
          }
          <Spin spinning={this.state.pending}>
            {this.renderShareInfo()}
            {this.renderUserSelectForm()}
          </Spin>
        </div>
      </Modal>
    );
  }
}

SharedItemInfo.PropTypes = {
  storage: PropTypes.object,
  path: PropTypes.string,
  close: PropTypes.func,
  visible: PropTypes.bool
};

export default SharedItemInfo;
