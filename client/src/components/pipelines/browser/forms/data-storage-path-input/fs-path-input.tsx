import classNames from 'classnames';
import {Button, Dropdown, Input, Row} from 'antd';
import {FileShareMountHostDisplay} from './file-share-mount-host-display.tsx';
import styles from './data-storage-path-input.module.css';
import type {DataStoragePathInputProps} from './types.ts';
import type {useDataStoragePathInput} from './use-data-storage-path-input.ts';

type FsPathInputProps = Pick<DataStoragePathInputProps, 'disabled' | 'onPressEnter'> & {
  ctrl: ReturnType<typeof useDataStoragePathInput>;
};

function FsPathInput({ctrl, disabled, onPressEnter}: FsPathInputProps) {
  const {
    state,
    fileShareMountsList,
    currentFileShareMount,
    validatePath,
    onSelectFileShareMount,
    onPathChanged,
    initializeNameInput,
  } = ctrl;

  const alternateMounts = fileShareMountsList.filter(
    (r) => !currentFileShareMount || r.id !== currentFileShareMount.id,
  );

  const mountLabel = currentFileShareMount ? (
    <FileShareMountHostDisplay fileShareMount={currentFileShareMount} showMountType />
  ) : (
    (state.path && state.path.split(':')[0]) || 'None'
  );

  return (
    <Row>
      <div
        className={classNames(styles.pathInputContainer, 'cp-input-group-addon')}
        style={{
          borderRadius: '4px 0px 0px 4px',
          height: 32,
          maxWidth: '50%',
        }}
      >
        {disabled || alternateMounts.length === 0 ? (
          <Button
            id="edit-storage-storage-path-nfs-mount"
            size="small"
            style={{
              border: 'none',
              fontWeight: 'bold',
              backgroundColor: 'transparent',
              width: '100%',
            }}
            onClick={undefined}
          >
            {mountLabel}
          </Button>
        ) : (
          <span id="edit-storage-storage-path-nfs-mount">
            <Dropdown
              popupRender={() => (
                <div className={styles.navigationDropdownContainer}>
                  {alternateMounts.map((fileShareMount) => (
                    <Row key={fileShareMount.id}>
                      <Button
                        style={{textAlign: 'left', width: '100%', border: 'none'}}
                        onClick={() => onSelectFileShareMount(fileShareMount)}
                      >
                        <FileShareMountHostDisplay fileShareMount={fileShareMount} showMountType />
                      </Button>
                    </Row>
                  ))}
                </div>
              )}
            >
              <Button
                size="small"
                style={{
                  border: 'none',
                  fontWeight: 'bold',
                  backgroundColor: 'transparent',
                  width: '100%',
                }}
              >
                {mountLabel}
              </Button>
            </Dropdown>
          </span>
        )}
      </div>
      <Input
        id="edit-storage-storage-path-input"
        className={styles.pathInput}
        disabled={disabled}
        ref={initializeNameInput}
        size="large"
        value={state.storagePath}
        onBlur={validatePath}
        onPressEnter={(e) => onPressEnter?.(e)}
        style={{
          width: 200,
          flex: 1,
          borderRadius: '0px 4px 4px 0px',
          marginLeft: -1,
        }}
        onChange={(e) => onPathChanged(e.target.value)}
      />
    </Row>
  );
}

export {FsPathInput};
