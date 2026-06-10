import React from 'react';
import PropTypes from 'prop-types';
import StorageItemPermissionsModal from './modal';
import {Button} from 'antd';
import {getStoragePathsDescription} from './utilities';

class StorageItemPermissionsButton extends React.PureComponent {
  state = {visible: false};

  onOpen = () => this.setState({visible: true});
  onClose = () => this.setState({visible: false});
  handleOpen = () => this.onOpen();
  handleClose = () => this.onClose();

  render() {
    const {
      storageId,
      storagePaths = [],
      className,
      style,
      disabled,
      size,
      type,
      children,
      asLink,
    } = this.props;
    const {visible} = this.state;
    let content = children;
    if (!content) {
      content = (
        <span>
          <span style={{marginRight: 5}}>Manage</span>
          <b style={{marginRight: 5}}>{getStoragePathsDescription(storagePaths, true)}</b>
          <span>permissions</span>
        </span>
      );
    }
    return (
      <div
        className={className}
        style={{
          display: 'inline',
          ...(style ?? {}),
        }}
      >
        {asLink && <a onClick={this.handleOpen}>{content}</a>}
        {!asLink && (
          <Button disabled={disabled} size={size} type={type} onClick={this.handleOpen}>
            {content}
          </Button>
        )}
        <StorageItemPermissionsModal
          storageId={storageId}
          storagePaths={storagePaths}
          visible={visible}
          onClose={this.handleClose}
        />
      </div>
    );
  }
}

StorageItemPermissionsButton.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  disabled: PropTypes.bool,
  storageId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  storagePaths: PropTypes.oneOfType([PropTypes.object, PropTypes.array]),
  size: PropTypes.oneOf(['small', 'medium', 'large']),
  type: PropTypes.oneOf(['primary', 'success', 'warning', 'danger']),
  children: PropTypes.node,
  asLink: PropTypes.bool,
};

export default StorageItemPermissionsButton;
