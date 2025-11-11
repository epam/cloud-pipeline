import React from 'react';
import PropTypes from 'prop-types';
import {AutoComplete, Button, Modal} from 'antd';
import GroupFind from '../../../../../../models/user/GroupFind';
import Roles from '../../../../../../models/user/Roles';
import GroupName, {getGroupName} from './group-name';

class PickGroupsModal extends React.PureComponent {
  state = {
    selection: undefined,
    filter: undefined,
    roles: [],
    rolesPending: false,
    rolesError: undefined,
    groups: [],
    groupsPending: false
  };
  _token = {};
  _rolesToken = {};

  componentDidMount () {
    this.updateFromProps();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (prevProps.visible !== this.props.visible) {
      this.updateFromProps();
    }
  }

  componentWillUnmount () {
    this.invalidateToken();
    this.invalidateRolesToken();
  }

  updateFromProps = () => {
    this.setState({
      selection: undefined
    });
    const token = this.invalidateRolesToken();
    const commitState = (state, cb) => {
      if (token === this._rolesToken) {
        this.setState(state, cb);
      }
    };
    (async () => {
      try {
        commitState({
          rolesPending: true,
          rolesError: undefined
        });
        const roles = new Roles();
        await roles.fetch();
        if (roles.error) {
          throw new Error(roles.error);
        }
        commitState({
          roles: roles.value || []
        });
      } catch (e) {
        commitState({
          rolesError: e.message,
          roles: []
        });
      } finally {
        commitState({
          rolesPending: false
        });
      }
    })();
  };

  invalidateToken = () => {
    this._token = {};
    return this._token;
  };

  invalidateRolesToken = () => {
    this._rolesToken = {};
    return this._rolesToken;
  };

  performGroupsSearch = () => {
    const token = this.invalidateToken();
    const {
      filter
    } = this.state;
    const commitState = (state, cb) => {
      if (token === this._token) {
        this.setState(state, cb);
      }
    };
    (async () => {
      try {
        if (filter && filter.length > 2) {
          commitState({
            groupsPending: true
          });
          const groups = await GroupFind.findGroups(filter);
          commitState({
            groups
          });
        } else {
          commitState({
            groups: []
          });
        }
      } catch {
        commitState({
          groups: []
        });
      } finally {
        commitState({
          groupsPending: false
        });
      }
    })();
  };

  get roles () {
    const {
      exclude = []
    } = this.props;
    const {
      roles = [],
      filter
    } = this.state;
    const filtered = roles
      .filter((r) => !filter ||
        filter.length === 0 ||
        r.name.toLowerCase().includes(filter.toLowerCase()));
    const excludeLowered = exclude.map((e) => e.toLowerCase());
    return filtered.filter((u) => !excludeLowered.includes(u.name.toLowerCase()));
  }

  get groups () {
    const {
      exclude = []
    } = this.props;
    const {
      groups = []
    } = this.state;
    const adGroups = groups.map((g) => ({name: g}));
    const excludeLowered = exclude.map((e) => e.toLowerCase());
    return adGroups.filter((u) => !excludeLowered.includes(u.name.toLowerCase()));
  }

  onSearchChanged = (value) => {
    this.setState({
      filter: value && value.length ? value : undefined,
      selection: undefined
    }, this.performGroupsSearch);
  };

  onClearSearch = () => this.setState({filter: undefined}, this.invalidateToken)

  onGroupChanged = (value) => {
    this.invalidateToken();
    this.setState({
      selection: value,
      filter: undefined,
      groups: [],
      rolesPending: false
    });
  };

  onSelectClicked = () => {
    const {
      onGroupPicked
    } = this.props;
    const {selection} = this.state;
    if (selection && typeof onGroupPicked === 'function') {
      onGroupPicked(selection);
    }
  };

  render () {
    const {
      className,
      style,
      visible,
      onClose
    } = this.props;
    const {
      selection,
      filter = '',
      rolesPending
    } = this.state;
    return (
      <Modal
        className={className}
        style={style}
        visible={visible}
        title="Select group"
        onCancel={onClose}
        footer={(
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'flex-end',
              gap: 5
            }}
          >
            <Button
              onClick={onClose}
            >
              Cancel
            </Button>
            <Button
              type="primary"
              disabled={!selection}
              onClick={this.onSelectClicked}
            >
              Select
            </Button>
          </div>
        )}
      >
        <AutoComplete
          disabled={rolesPending}
          placeholder="Select group"
          style={{width: '100%'}}
          value={filter || selection}
          showSearch
          onSearch={this.onSearchChanged}
          onSelect={this.onGroupChanged}
          onBlur={this.onClearSearch}
          optionLabelProp="title"
        >
          <AutoComplete.OptGroup label="Roles">
            {
              this.roles.map((r) => (
                <AutoComplete.Option
                  key={r.name}
                  value={r.name}
                  title={getGroupName(r.name, !r.predefined)}
                >
                  <GroupName group={r.name} removePrefix={!r.predefined} />
                </AutoComplete.Option>
              ))
            }
          </AutoComplete.OptGroup>
          {
            this.groups.length > 0 && (
              <AutoComplete.OptGroup label="Groups">
                {
                  this.groups.map((r) => (
                    <AutoComplete.Option
                      key={r.name}
                      value={r.name}
                      title={getGroupName(r.name, false)}
                    >
                      <GroupName group={r.name} />
                    </AutoComplete.Option>
                  ))
                }
              </AutoComplete.OptGroup>
            )
          }
        </AutoComplete>
      </Modal>
    );
  }
}

PickGroupsModal.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  visible: PropTypes.bool,
  onClose: PropTypes.func,
  onGroupPicked: PropTypes.func,
  exclude: PropTypes.oneOfType([PropTypes.array, PropTypes.object])
};

export default PickGroupsModal;
