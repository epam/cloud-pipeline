import {ReactNode, useCallback, useEffect, useState} from 'react';
import type {KeyboardEvent, ChangeEvent, MouseEvent} from 'react';
import {EditOutlined} from '@ant-design/icons';
import {Input} from 'antd';
import classNames from 'classnames';
import {CommonProps} from '../../../@types/common.ts';
import './library-header.css';
import {LoadingMessage} from '../../shared/loading-message/loading-message.tsx';

function EditableBreadcrumb(
  props: CommonProps & {
    value: string;
    pending?: boolean;
    editable?: boolean;
    disabled?: boolean;
    onChange?: (value: string) => void;
    display?: ReactNode | ((name: string) => ReactNode);
  },
) {
  const {
    className,
    style,
    disabled = false,
    value,
    onChange: onValueChange,
    display = value,
    editable = true,
    pending = false,
  } = props;
  const [editMode, setEditMode] = useState(false);
  const [name, setName] = useState(value);
  const [touched, setTouched] = useState(false);

  useEffect(() => {
    setEditMode(false);
    setName(value);
    setTouched(false);
  }, [value, setEditMode, setTouched]);

  const onChange = useCallback(
    (event: ChangeEvent<HTMLInputElement>) => {
      if (editable) {
        setName(event.target.value);
      }
    },
    [setName, editable],
  );
  const onClick = useCallback(
    (event: KeyboardEvent | MouseEvent) => {
      event.stopPropagation();
      event.preventDefault();
      if (editable) {
        setEditMode(true);
      }
    },
    [setEditMode, editable],
  );
  const onBlur = useCallback(() => {
    setEditMode(false);
    setTouched(name !== value);
    if (name !== value && onValueChange) {
      onValueChange(name);
    }
  }, [setEditMode, onValueChange, setTouched, name, value]);
  return (
    <div
      className={classNames(className, 'library-header-edit', {editable, 'edit-mode': editMode})}
      style={style}
      onClick={onClick}
    >
      {editMode ? (
        <Input
          className="library-header-edit-input"
          disabled={disabled}
          style={{width: 300}}
          autoFocus
          onBlur={onBlur}
          value={name}
          onChange={onChange}
          onPressEnter={onBlur}
        />
      ) : (
        <LoadingMessage className="library-header-display-name" loading={pending}>
          {touched || typeof display === 'function'
            ? typeof display === 'function'
              ? display(name)
              : name
            : display}
        </LoadingMessage>
      )}
      {editable && <EditOutlined className="library-header-edit-icon text-faded" />}
    </div>
  );
}

export {EditableBreadcrumb};
