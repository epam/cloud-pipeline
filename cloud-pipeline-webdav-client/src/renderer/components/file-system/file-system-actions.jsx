import React from 'react';
import PropTypes from 'prop-types';
import {
  Button,
  ConfigProvider,
  Divider,
} from 'antd';
import {
  useCopyOperation,
  useCreateDirectoryOperation,
  useMoveOperation,
  useRefreshOperation,
  useRemoveOperation,
} from './hooks/use-operations';
import OperationName from '../../operations/components/operation-name';
import { OperationTypes } from '../../operations';
import { usePlatformSpecificHotKeyCode } from '../../operations/hooks/use-platform-specific-hotkeys';
import { useFileSystemRestricted } from "./hooks/use-file-system";

function ActionButton(
  {
    type,
    danger,
    operationType,
    operation,
    disabled,
  },
) {
  return (
    <Button
      tabIndex={-1}
      disabled={disabled}
      type={type}
      danger={danger}
      onClick={operation}
    >
      <OperationName
        type={operationType}
      />
    </Button>
  );
}

ActionButton.propTypes = {
  type: PropTypes.string,
  danger: PropTypes.bool,
  operationType: PropTypes.string.isRequired,
  operation: PropTypes.func.isRequired,
  disabled: PropTypes.bool,
};

ActionButton.defaultProps = {
  type: undefined,
  danger: false,
  disabled: false,
};

function CreateDirectory() {
  const hotKey = usePlatformSpecificHotKeyCode(OperationTypes.createDirectory);
  const {
    operation,
    allowed,
  } = useCreateDirectoryOperation(hotKey);
  return (
    <ActionButton
      disabled={!allowed}
      type="primary"
      operation={operation}
      operationType={OperationTypes.createDirectory}
    />
  );
}

function Copy() {
  const hotKey = usePlatformSpecificHotKeyCode(OperationTypes.copy);
  const {
    operation,
    allowed,
  } = useCopyOperation(hotKey);
  return (
    <ActionButton
      disabled={!allowed}
      operation={operation}
      operationType={OperationTypes.copy}
    />
  );
}

function Move() {
  const hotKey = usePlatformSpecificHotKeyCode(OperationTypes.move);
  const {
    operation,
    allowed,
  } = useMoveOperation(hotKey);
  return (
    <ActionButton
      disabled={!allowed}
      operation={operation}
      operationType={OperationTypes.move}
    />
  );
}

function Remove() {
  const hotKey = usePlatformSpecificHotKeyCode(OperationTypes.remove);
  const {
    operation,
    allowed,
  } = useRemoveOperation(hotKey);
  return (
    <ActionButton
      disabled={!allowed}
      operation={operation}
      operationType={OperationTypes.remove}
      type="primary"
      danger
    />
  );
}

function Refresh() {
  const hotKey = usePlatformSpecificHotKeyCode(OperationTypes.refresh);
  const {
    operation,
    allowed,
  } = useRefreshOperation(hotKey);
  return (
    <ActionButton
      disabled={!allowed}
      operation={operation}
      operationType={OperationTypes.refresh}
    />
  );
}

function FileSystemActions(
  {
    className,
    style,
  },
) {
  const restricted = useFileSystemRestricted();
  return (
    <div
      className={className}
      style={style}
    >
      <ConfigProvider componentSize="small">
        {
          !restricted && (
            <>
              <CreateDirectory />
              <Divider type="vertical" />
              <Copy />
              <Move />
              <Divider type="vertical" />
              <Remove />
              <Divider type="vertical" />
            </>
          )
        }
        <Refresh />
      </ConfigProvider>
    </div>
  );
}

FileSystemActions.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
};

FileSystemActions.defaultProps = {
  className: undefined,
  style: {},
};

export default FileSystemActions;
