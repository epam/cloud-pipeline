import {Alert, Button} from 'antd';
import {DeleteOutlined} from '@ant-design/icons';
import classNames from 'classnames';
import styles from './add-docker-registry-control.module.css';
import {DockerImageSelect} from './docker-image-select.tsx';
import {MultipleVersionsSelector} from './multiple-versions-selector.tsx';
import {VersionSelector} from './version-selector.tsx';
import type {AddDockerRegistryControlProps} from './types.ts';
import {useAddDockerRegistryControl} from './use-add-docker-registry-control.ts';

function AddDockerRegistryControl(props: AddDockerRegistryControlProps) {
  const ctrl = useAddDockerRegistryControl(props);

  if (ctrl.errorMessage) {
    if (ctrl.showError) {
      return <Alert type="error" title={ctrl.errorMessage} />;
    }
    return null;
  }

  return (
    <div className={ctrl.className} style={ctrl.style}>
      <div className={classNames(styles.container)} style={ctrl.containerStyle}>
        <DockerImageSelect ctrl={ctrl} />
        {ctrl.multipleMode ? (
          <MultipleVersionsSelector ctrl={ctrl} />
        ) : (
          <VersionSelector ctrl={ctrl} />
        )}
        {ctrl.showDelete && (
          <Button
            disabled={ctrl.disabled}
            size="small"
            danger
            onClick={ctrl.onRemove}
            className={styles.action}
          >
            <DeleteOutlined />
          </Button>
        )}
      </div>
    </div>
  );
}

export {AddDockerRegistryControl};
export default AddDockerRegistryControl;
