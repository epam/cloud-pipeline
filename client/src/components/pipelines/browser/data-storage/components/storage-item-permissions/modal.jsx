import React from 'react';
import PropTypes from 'prop-types';
import StorageItemPermissions from './storage-item-permissions';

function StorageItemPermissionsModal(props) {
  return <StorageItemPermissions {...props} modal />;
}

StorageItemPermissionsModal.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  storageId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  storagePaths: PropTypes.oneOfType([PropTypes.object, PropTypes.array]),
  visible: PropTypes.bool,
  onClose: PropTypes.func,
};

export default StorageItemPermissionsModal;
