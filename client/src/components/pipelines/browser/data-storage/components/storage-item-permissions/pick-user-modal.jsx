import React from 'react';
import PropTypes from 'prop-types';
import {Button, Modal, Select} from 'antd';
import {inject, observer} from 'mobx-react';
import UserName from '../../../../../special/UserName';

@inject('usersInfo')
@observer
class PickUserModal extends React.Component {
  state = {
    selection: undefined,
    filter: undefined
  };

  componentDidMount () {
    this.updateFromProps();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (prevProps.visible !== this.props.visible) {
      this.updateFromProps();
    }
  }

  updateFromProps = () => {
    this.setState({
      selection: undefined
    });
  };

  get users () {
    const {
      usersInfo,
      exclude = []
    } = this.props;
    if (!usersInfo || !usersInfo.loaded) {
      return [];
    }
    const all = usersInfo.value || [];
    const excludeLowered = exclude.map((e) => e.toLowerCase());
    return all.filter((u) => !excludeLowered.includes(u.name.toLowerCase()));
  }

  onSearchChanged = (value) => this.setState({filter: value});

  onUserFindInputChanged = (value) => {
    this.setState({selection: value});
  };

  onSelectClicked = () => {
    const {
      onUserPicked
    } = this.props;
    const {selection} = this.state;
    if (selection && typeof onUserPicked === 'function') {
      onUserPicked(selection);
    }
  };

  render () {
    const {
      className,
      style,
      visible,
      onClose,
      usersInfo
    } = this.props;
    const {
      selection,
      filter = ''
    } = this.state;
    return (
      <Modal
        className={className}
        style={style}
        open={visible}
        title="Select user"
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
        <Select
          disabled={!usersInfo || !usersInfo.loaded}
          placeholder="Select user"
          style={{width: '100%'}}
          showSearch
          value={selection}
          onSelect={this.onUserFindInputChanged}
          filterOption={(input, option) =>
            !input ||
            input.length === 0 ||
            option.props.attributes
              .map(o => o.toLowerCase())
              .find(o => o.includes((input || '').toLowerCase()))
          }
          onSearch={this.onSearchChanged}
          onFocus={() => this.setState({filter: undefined})}
          notFoundContent={filter && filter.length > 2
            ? 'Not found'
            : 'Start typing to filter users...'
          }
        >
          {
            filter && filter.length > 2 ? (
              this.users
                .map(user => (
                  <Select.Option
                    key={user.name}
                    value={user.name}
                    attributes={
                      [
                        user.name,
                        ...Object.values(user.attributes || {})
                      ]
                    }
                  >
                    <UserName userName={user.name} />
                  </Select.Option>
                ))
            ) : null
          }
        </Select>
      </Modal>
    );
  }
}

PickUserModal.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  visible: PropTypes.bool,
  onClose: PropTypes.func,
  onUserPicked: PropTypes.func,
  exclude: PropTypes.oneOfType([PropTypes.array, PropTypes.object])
};

export default PickUserModal;
