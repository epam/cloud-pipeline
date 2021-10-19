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
  Checkbox,
  Row,
  Table,
  Button
} from 'antd';

import roleModel from '../../../../../utils/roleModel';
import UsersRolesSelect from '../../../../special/users-roles-select';
import styles from './DataStorageItemPermissionsForm.css';

export function sidsAreEqual (sidA, sidB) {
  if (!sidA && !sidB) {
    return true;
  }
  if (!sidA || !sidB) {
    return false;
  }
  const {
    name: aName,
    principal: aPrincipal
  } = sidA;
  const {
    name: bName,
    principal: bPrincipal
  } = sidB;
  return aName === bName && aPrincipal === bPrincipal;
}

export function sidsArraysAreEqual (sidsA = [], sidsB = []) {
  if (sidsA.length !== sidsB.length) {
    return false;
  }
  for (const sidA of sidsA) {
    if (!sidsB.find(sidB => sidsAreEqual(sidA, sidB))) {
      return false;
    }
  }
  return true;
}

class DataStorageItemPermissionsForm extends React.Component {
  state = {
    sids: [],
    mask: 0
  };

  componentDidMount () {
    this.updateFromProps();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (prevProps.sids !== this.props.sids || prevProps.mask !== this.props.mask) {
      this.updateFromProps();
    }
  }

  updateFromProps = () => {
    const {
      sids = [],
      mask = 0
    } = this.props;
    this.setState({
      sids: sids.slice(),
      mask
    });
  };

  get modified () {
    const {sids: initialSIDs, mask: initialMask} = this.props;
    const {sids: currentSIDs, mask: currentMask} = this.state;
    return initialMask !== currentMask || !sidsArraysAreEqual(initialSIDs, currentSIDs);
  }

  onAllowDenyValueChanged = (permissionMask, allowDenyMask, allowRead = false) => async (event) => {
    const mask = (1 | 1 << 1 | 1 << 2 | 1 << 3) ^ permissionMask;
    let newValue = 0;
    if (event.target.checked) {
      newValue = allowDenyMask;
    }
    const {
      mask: currentMask
    } = this.state;
    let newMask = (currentMask & mask) | newValue;
    if (allowRead && event.target.checked) {
      newMask = (newMask & (1 << 1 | 1 << 2 | 1 << 3)) | 1;
    }
    this.setState({mask: newMask});
  };

  renderUserPermission = () => {
    const {
      mask = 0,
      sids = []
    } = this.state;
    const {
      readonly
    } = this.props;
    if (sids.length > 0) {
      const columns = [
        {
          title: 'Permissions',
          dataIndex: 'permission',
          render: (name, item) => {
            if (!item.allowed && !item.denied) {
              return (<span>{name} <i style={{fontSize: 'smaller'}}>(inherit)</i></span>);
            }
            return name;
          }
        },
        {
          title: 'Allow',
          width: 50,
          className: styles.userAllowDenyActions,
          render: (item) => (
            <Checkbox
              disabled={item.allowMask === 0 || readonly}
              checked={item.allowed}
              onChange={
                this.onAllowDenyValueChanged(
                  item.allowMask | item.denyMask,
                  item.allowMask,
                  !item.isRead
                )
              }
            />
          )
        },
        {
          title: 'Deny',
          width: 50,
          className: styles.userAllowDenyActions,
          render: (item) => (
            <Checkbox
              disabled={item.denyMask === 0 || readonly}
              checked={item.denied}
              onChange={
                this.onAllowDenyValueChanged(
                  item.allowMask | item.denyMask,
                  item.denyMask
                )
              }
            />
          )
        }
      ];
      const data = [
        {
          permission: 'Read',
          allowMask: 1,
          denyMask: 1 << 1,
          allowed: roleModel.readAllowed({mask}, true),
          denied: roleModel.readDenied({mask}, true),
          isRead: true
        },
        {
          permission: 'Write',
          allowMask: 1 << 2,
          denyMask: 1 << 3,
          allowed: roleModel.writeAllowed({mask}, true),
          denied: roleModel.writeDenied({mask}, true)
        }
      ];
      return (
        <Table
          style={{marginTop: 10}}
          showHeader
          size="small"
          columns={columns}
          pagination={false}
          rowKey={(item) => item.permission}
          dataSource={data}
        />
      );
    }
    return undefined;
  };

  renderUsers = () => {
    const {
      sids = []
    } = this.state;
    const {
      readonly
    } = this.props;
    const onChangeSIDs = (newSids = []) => {
      this.setState({
        sids: newSids.slice()
      });
    };
    return (
      <div>
        <div
          style={{
            width: '100%',
            position: 'relative'
          }}
        >
          <UsersRolesSelect
            disabled={readonly}
            value={sids}
            onChange={onChangeSIDs}
            style={{flex: 1, width: '100%'}}
          />
        </div>
        {this.renderUserPermission()}
      </div>
    );
  };

  onSaveAndShareClicked = () => {
    const {
      sids = [],
      mask
    } = this.state;
    const {
      onSave
    } = this.props;
    if (onSave) {
      onSave({
        sids,
        mask
      });
    }
  };

  render () {
    return (
      <div>
        <Row>
          {this.renderUsers()}
        </Row>
        <Row
          type="flex"
          justify="space-between"
          style={{marginTop: '10px', paddingRight: 6}}
        >
          <Button
            onClick={this.props.onCancel}
          >
            CANCEL
          </Button>
          <Button
            type="primary"
            disabled={!this.modified}
            onClick={this.onSaveAndShareClicked}
          >
            SAVE AND SHARE
          </Button>
        </Row>
      </div>
    );
  }
}

const SIDPropType = PropTypes.shape({
  name: PropTypes.string,
  principal: PropTypes.bool
});

DataStorageItemPermissionsForm.propTypes = {
  sids: PropTypes.arrayOf(SIDPropType),
  mask: PropTypes.number,
  onSave: PropTypes.func,
  onCancel: PropTypes.func,
  readonly: PropTypes.bool
};

export default DataStorageItemPermissionsForm;
