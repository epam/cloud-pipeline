import {Input, Row} from 'antd';
import LoadingView from '../../../../special/LoadingView.tsx';
import styles from './data-storage-path-input.module.css';
import type {DataStoragePathInputProps} from './types.ts';
import type {useDataStoragePathInput} from './use-data-storage-path-input.ts';

type ObjectStoragePathInputProps = Pick<
  DataStoragePathInputProps,
  'disabled' | 'isNew' | 'addExistingStorageFlag' | 'onPressEnter'
> & {
  ctrl: ReturnType<typeof useDataStoragePathInput>;
};

function ObjectStoragePathInput({
  ctrl,
  disabled,
  isNew,
  addExistingStorageFlag,
  onPressEnter,
}: ObjectStoragePathInputProps) {
  const {
    state,
    preferencePending,
    storageObjectPrefix,
    validatePath,
    onPathChanged,
    initializeNameInput,
  } = ctrl;

  if (isNew && !addExistingStorageFlag) {
    if (preferencePending) {
      return <LoadingView />;
    }
    if (storageObjectPrefix) {
      return (
        <Row>
          <div
            style={{
              backgroundColor: '#eee',
              border: '1px solid #ccc',
              borderRadius: '4px 0px 0px 4px',
              height: 32,
              maxWidth: '50%',
            }}
          >
            <span style={{padding: '0 7px'}}>{storageObjectPrefix}</span>
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
  }

  return (
    <Input
      id="edit-storage-storage-path-input"
      ref={!disabled ? initializeNameInput : undefined}
      onPressEnter={(e) => onPressEnter?.(e)}
      value={state.storagePath}
      onChange={(e) => onPathChanged(e.target.value)}
      disabled={disabled}
    />
  );
}

export {ObjectStoragePathInput};
