import React from 'react';
import PropTypes from 'prop-types';
import {Button, Checkbox, Icon, Table} from 'antd';
import {getSIDKey, normalizePermissions, parseSIDKey} from './utilities';
import UserName from '../../../../../special/UserName';
import PickUserModal from './pick-user-modal';
import PickGroupsModal from './pick-group-modal';
import GroupName from './group-name';
import {alphabeticalSorter} from '../../../../../../utils/sorting';

/**
 * `StorageItemPermissionsList` is used for rendering a permissions list modifications controls.
 * This component does not update permissions (i.e., does not perform API call), instead it accepts
 * permissions list and onChange callback.
 *
 * Use `StorageItemPermissions` component for auto-updated permissions.
 */
class StorageItemPermissionsList extends React.PureComponent {
  state = {
    data: [],
    active: undefined,
    pickUserModalVisible: false,
    pickGroupModalVisible: false
  };

  componentDidMount () {
    this.updateFromProps();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (prevProps.permissions !== this.props.permissions) {
      this.updateFromProps();
    }
  }

  updateFromProps = () => {
    const {permissions = []} = this.props;
    const sids = new Set();
    for (const p of permissions) {
      sids.add(getSIDKey(p.sid));
    }
    const sidsArray = [...sids];
    sidsArray.sort(alphabeticalSorter);
    const data = [];
    for (const sidKey of sidsArray) {
      const sid = parseSIDKey(sidKey);
      const sidPermissions = permissions
        .filter((p) => p.sid.isPrincipal === sid.isPrincipal && p.sid.name === sid.name);
      data.push({
        sid,
        sidKey,
        permissions: sidPermissions,
        ...normalizePermissions(sidPermissions)
      });
    }
    let {active: currentActive} = this.state;
    if (!currentActive || !data.some((d) => d.sidKey === currentActive)) {
      if (data.length > 0) {
        currentActive = data[0].sidKey;
      } else {
        currentActive = undefined;
      }
    }
    this.setState({data, active: currentActive});
  };

  onChange = () => {
    const {
      onPermissionsChange
    } = this.props;
    if (typeof onPermissionsChange === 'function') {
      const {data = []} = this.state;
      const payload = data.reduce((acc, current) => acc.concat(current.permissions), []);
      onPermissionsChange(payload);
    }
  };

  /**
   * @param {StorageItemPermissionSID} sid
   * @param {StorageItemsNormalizedPermissions} normalized
   */
  onChangePermission = (sid, normalized) => {
    const {data = []} = this.state;
    const {storageId, storagePaths} = this.props;
    const mask = normalized.writeAllowed ? 0b0011 : 0b0001;
    const permissions = storagePaths.map((sp) => ({
      sid,
      storageId,
      storagePath: sp.path,
      type: sp.type,
      mask
    }));
    const sidKey = getSIDKey(sid);
    const updated = data
      .filter((d) => d.sidKey !== sidKey)
      .concat([{
        sid,
        sidKey: getSIDKey(sid),
        permissions,
        writeAllowed: normalized.writeAllowed,
        writeAllowedIndeterminate: false
      }]);
    this.setState({data: updated, active: sidKey}, this.onChange);
  };

  /**
   * @param {StorageItemPermissionSID} sid
   */
  onAddPermission = (sid) => {
    const {data = []} = this.state;
    const {storageId, storagePaths} = this.props;
    const sidKey = getSIDKey(sid);
    const currentIdx = data
      .findIndex((d) => d.sidKey === sidKey);
    if (currentIdx === -1) {
      this.onChangePermission(sid, {writeAllowed: false});
    } else {
      const current = data[currentIdx];
      const modified = current.permissions.slice();
      for (const sp of storagePaths) {
        const spCurrent = modified
          .find((p) => p.storageId === storageId &&
            p.storagePath === sp.path &&
            p.type === sp.type);
        if (!spCurrent) {
          modified.push({
            storageId,
            storagePath: sp.path,
            type: sp.type,
            mask: 0b0001, // read only
            sid
          });
        }
      }
      const updated = data.slice();
      updated.splice(currentIdx, 1, {...current, permissions: modified});
      this.setState({data: updated, active: sidKey}, this.onChange);
    }
  };

  /**
   * @param {StorageItemPermissionSID} sid
   */
  onRemovePermission = (sid) => {
    const {data = []} = this.state;
    const sidKey = getSIDKey(sid);
    const updated = data
      .filter((d) => d.sidKey !== sidKey);
    const active = updated.length > 0 ? updated[0].sidKey : undefined;
    this.setState({data: updated, active}, this.onChange);
  };

  onPickUserClicked = () => this.setState({pickUserModalVisible: true});

  onClosePickUserModal = () => this.setState({pickUserModalVisible: false});

  onPickGroupClicked = () => this.setState({pickGroupModalVisible: true});

  onClosePickGroupModal = () => this.setState({pickGroupModalVisible: false});

  renderSidsTable = () => {
    const {
      data = []
    } = this.state;
    const columns = [
      {
        key: 'name',
        render: (item) => {
          return (
            <div style={{display: 'inline-flex', alignItems: 'center', gap: 5}}>
              {
                item.sid.isPrincipal && <UserName userName={item.sid.name} showIcon />
              }
              {
                !item.sid.isPrincipal && <GroupName group={item.sid.name} showIcon />
              }
            </div>
          );
        }
      },
      {
        key: 'permissions',
        width: 150,
        render: (item) => (
          <Checkbox
            checked={item.writeAllowed}
            indeterminate={item.writeAllowedIndeterminate}
            onChange={(e) => this.onChangePermission(
              item.sid,
              {writeAllowed: e.target.checked})
            }
          >
            Write access
          </Checkbox>
        )
      },
      {
        key: 'actions',
        width: 40,
        render: (item) => (
          <Button
            onClick={() => this.onRemovePermission(item.sid)}
            size="small"
            type="danger"
          >
            <Icon type="delete" />
          </Button>
        )
      }
    ];
    return (
      <Table
        title={() => (
          <div style={{display: 'inline-flex', alignItems: 'center', width: '100%'}}>
            <b>Groups and users</b>
            <div
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: 5,
                marginLeft: 'auto'
              }}
            >
              <Button
                size="small"
                onClick={this.onPickUserClicked}
              >
                <Icon type="user-add" />
              </Button>
              <Button
                size="small"
                onClick={this.onPickGroupClicked}
              >
                <Icon type="usergroup-add" />
              </Button>
            </div>
          </div>
        )}
        columns={columns}
        rowKey="sidKey"
        dataSource={data}
        pagination={false}
        showHeader={false}
        size="small"
      />
    );
  };

  onUserPicked = (userName) => {
    this.onAddPermission({name: userName, isPrincipal: true});
    this.onClosePickUserModal();
  };

  onGroupPicked = (groupName) => {
    this.onAddPermission({name: groupName, isPrincipal: false});
    this.onClosePickGroupModal();
  };

  render () {
    const {data = []} = this.state;
    const usersSids = data
      .filter((o) => o.sid.isPrincipal)
      .map((o) => o.sid.name);
    const groupsSids = data
      .filter((o) => !o.sid.isPrincipal)
      .map((o) => o.sid.name);
    return (
      <div>
        {this.renderSidsTable()}
        <PickUserModal
          visible={this.state.pickUserModalVisible}
          onClose={this.onClosePickUserModal}
          onUserPicked={this.onUserPicked}
          exclude={usersSids}
        />
        <PickGroupsModal
          visible={this.state.pickGroupModalVisible}
          onClose={this.onClosePickGroupModal}
          onGroupPicked={this.onGroupPicked}
          exclude={groupsSids}
        />
      </div>
    );
  }
}

StorageItemPermissionsList.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  disabled: PropTypes.bool,
  storageId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  storagePaths: PropTypes.oneOfType([PropTypes.object, PropTypes.array]),
  /**
   * Array of objects `StorageItemPermission`:
   * ```js
   * {
   *   "storageId": number;
   *   "storagePath": string;
   *   "sid": {"name": string; "isPrincipal": boolean};
   *   "mask": number
   * }
   * ```
   */
  permissions: PropTypes.oneOfType([PropTypes.object, PropTypes.array]),
  onPermissionsChange: PropTypes.func
};

export default StorageItemPermissionsList;
