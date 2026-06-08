import React from 'react';
import PropTypes from 'prop-types';
import {
  fetchStorageItemsPermissions,
  getStoragePathsDescription,
  saveStorageItemPermissions,
  storageItemPermissionsSetsEqual,
  storagePathsAreEqual
} from './utilities';
import styles from './storage-item-permissions.css';
import StorageItemPermissionsList from './storage-item-permissions-list';
import {Alert, Button, Modal} from 'antd';

class StorageItemPermissions extends React.PureComponent {
  state = {
    permissions: [],
    initial: [],
    modified: false,
    pending: false,
    pendingSave: false,
    error: undefined
  };
  _token = {};

  componentDidMount () {
    if (!this.props.modal || this.props.visible) {
      this.updateFromProps();
    }
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    const storageItemsChanged = (
      prevProps.storageId !== this.props.storageId ||
      !storagePathsAreEqual(prevProps.storagePaths, this.props.storagePaths)
    );
    if (
      this.props.modal &&
      this.props.visible &&
      (
        prevProps.visible !== this.props.visible ||
        storageItemsChanged
      )
    ) {
      this.updateFromProps();
    } else if (!this.props.modal && storageItemsChanged) {
      this.updateFromProps();
    }
  }

  componentWillUnmount () {
    this.invalidateFetchToken();
  }

  invalidateFetchToken = () => {
    this._token = {};
    return this._token;
  };

  updateFromProps = () => {
    const {storageId, storagePaths = []} = this.props;
    const token = this.invalidateFetchToken();
    const commitState = (state, cb) => {
      if (token === this._token) {
        this.setState(state, cb);
      }
    };
    (async () => {
      commitState({pending: true, pendingSave: false, error: undefined});
      try {
        if (storageId === undefined) {
          throw new Error('storage is not specified');
        }
        if (storagePaths.length === 0) {
          throw new Error('storage path is not specified');
        }
        const permissions = await fetchStorageItemsPermissions(storageId, storagePaths);
        commitState({permissions, initial: permissions.slice(), modified: false});
      } catch (e) {
        commitState({error: e.message, permissions: [], initial: [], modified: false});
      } finally {
        commitState({pending: false, pendingSave: false});
      }
    })();
  };

  onRevert = () => this.setState({
    permissions: this.state.initial.slice(),
    modified: false
  });

  onClose = () => {
    const {modal, onClose} = this.props;
    if (modal && typeof onClose === 'function') {
      onClose();
    }
  };

  onSave = () => {
    const token = this.invalidateFetchToken();
    const {storageId} = this.props;
    const commitState = (state, cb) => {
      if (token === this._token) {
        this.setState(state, cb);
      }
    };
    (async () => {
      let succeeded = false;
      const {permissions, initial} = this.state;
      try {
        if (!storageId) {
          throw new Error('storage is not specified');
        }
        commitState({pending: true, pendingSave: true, error: undefined});
        await saveStorageItemPermissions(storageId, permissions, initial);
        succeeded = true;
      } catch (e) {
        commitState({error: e.message});
      } finally {
        commitState({pending: false, pendingSave: false});
      }
      const {modal} = this.props;
      if (succeeded) {
        if (modal) {
          this.onClose();
        } else if (token === this._token) {
          this.updateFromProps();
        }
      }
    })();
  };

  onPermissionsChange = (permissions) => this.setState({
    permissions,
    modified: !storageItemPermissionsSetsEqual(this.state.initial, permissions)
  });

  render () {
    const {
      className,
      style,
      storageId,
      storagePaths = [],
      modal,
      visible,
      buttonsSize
    } = this.props;
    const {
      pending,
      pendingSave,
      error,
      permissions,
      modified
    } = this.state;
    const errorComponent = error && !pending ? (
      <Alert title={error} showIcon type="error" />
    ) : undefined;

    const titleComponent = (
      <span>
        <b style={{marginRight: 5}}>
          {getStoragePathsDescription(storagePaths, true)}
        </b>
        <span>permissions</span>
      </span>
    );
    if (modal) {
      return (
        <Modal
          className={className}
          style={style}
          open={visible}
          title={titleComponent}
          onCancel={this.onClose}
          footer={(
            <div className={styles.storageItemPermissionsActions}>
              <Button
                size={buttonsSize}
                onClick={this.onClose}
                style={{marginLeft: 'auto'}}
              >
                Cancel
              </Button>
              <Button
                size={buttonsSize}
                disabled={!modified}
                onClick={this.onRevert}
                style={{marginLeft: 15}}
              >
                Revert
              </Button>
              <Button
                size={buttonsSize}
                disabled={!modified}
                onClick={this.onSave}
                type="primary"
                loading={pendingSave}
                style={{marginLeft: 5}}
              >
                Save
              </Button>
            </div>
          )}
        >
          {errorComponent}
          <StorageItemPermissionsList
            storageId={storageId}
            storagePaths={storagePaths}
            disabled={pending}
            permissions={permissions}
            onPermissionsChange={this.onPermissionsChange}
          />
        </Modal>
      );
    }
    return (
      <div className={className} style={style}>
        <div>
          {titleComponent}
        </div>
        {errorComponent}
        <StorageItemPermissionsList
          storageId={storageId}
          storagePaths={storagePaths}
          disabled={pending}
          permissions={permissions}
          onPermissionsChange={this.onPermissionsChange}
        />
        <div className={styles.storageItemPermissionsActions}>
          <Button
            size={buttonsSize}
            disabled={!modified}
            onClick={this.onRevert}
            style={{marginLeft: 'auto'}}
          >
            Revert
          </Button>
          <Button
            size={buttonsSize}
            disabled={!modified}
            onClick={this.onRevert}
            type="primary"
            loading={pendingSave}
            style={{marginLeft: 5}}
          >
            Save
          </Button>
        </div>
      </div>
    );
  }
}

StorageItemPermissions.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  storageId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  storagePaths: PropTypes.oneOfType([PropTypes.object, PropTypes.array]),
  modal: PropTypes.bool,
  visible: PropTypes.bool,
  onClose: PropTypes.func,
  buttonsSize: PropTypes.oneOf(['small', 'medium', 'large'])
};

export default StorageItemPermissions;
