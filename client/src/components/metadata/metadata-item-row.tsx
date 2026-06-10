import {useMemo, useRef} from 'react';
import classNames from 'classnames';
import {Button, Input, Select} from 'antd';
import type {InputRef} from 'antd';
import {DeleteOutlined} from '@ant-design/icons';
import ItemsTable, {isJson} from '../special/metadata/items-table';
import {SystemDictionary} from '../../@types/system-dictionaries.ts';
import {MetadataDisplayOptions} from './constants.ts';
import {confirmDeleteKey} from './special-tag-row.tsx';
import type {MetadataEditField, MetadataItem} from './types.ts';

type MetadataItemRowProps = {
  item: MetadataItem;
  readOnly: boolean;
  editableKeyIndex: number | null;
  editableValueIndex: number | null;
  editableText: string | null;
  dictionary?: SystemDictionary;
  onEditStarted: (field: MetadataEditField, index: number, value: string) => void;
  onEditableTextChange: (value: string) => void;
  onDiscardChanges: () => void;
  onSave: (opts: {index?: number; field?: MetadataEditField}) => () => void | Promise<void>;
  onSaveDictionary: (index: number) => (value: string) => void | Promise<void>;
  onSaveValue: (index: number) => (value: string) => Promise<boolean | void>;
  onDelete: (item: MetadataItem) => Promise<void>;
};

function MetadataDivider({itemKey}: {itemKey: string}) {
  return (
    <tr key={`${itemKey}_divider`}>
      <td colSpan={6}>
        <div className={classNames('metadata-divider', 'cp-divider', 'horizontal')} />
      </td>
    </tr>
  );
}

function useInputOptions({
  editableText,
  isSecret,
  item,
  onDiscardChanges,
  onEditableTextChange,
  onSave,
  readOnly,
}: {
  editableText: string | null;
  isSecret: boolean;
  item: MetadataItem;
  onDiscardChanges: () => void;
  onEditableTextChange: (value: string) => void;
  onSave: (opts: {index?: number; field?: MetadataEditField}) => () => void | Promise<void>;
  readOnly: boolean;
}) {
  const passwordInputRef = useRef<HTMLInputElement | null>(null);
  return useMemo(
    () => (field: MetadataEditField) => ({
      id: `${field}-input-${item.key}`,
      ref: (input: InputRef | null) => {
        if (input) {
          input.focus();
          if (field === 'value' && isSecret && input.input) {
            input.input.type = 'password';
            passwordInputRef.current = input.input;
          }
        }
      },
      onBlur: field === 'value' && isSecret ? onDiscardChanges : onSave({index: item.index, field}),
      onPressEnter: onSave({index: item.index, field}),
      size: 'small' as const,
      disabled: readOnly,
      value: editableText ?? '',
      onChange: (e: {target: {value: string}}) => onEditableTextChange(e.target.value),
      onKeyDown: (e: {key?: string; preventDefault?: () => void}) => {
        if (e.key === 'Escape') {
          onDiscardChanges();
        } else if (e.key === 'Enter') {
          e.preventDefault?.();
        }
      },
    }),
    [
      editableText,
      isSecret,
      item.index,
      item.key,
      onDiscardChanges,
      onEditableTextChange,
      onSave,
      readOnly,
    ],
  );
}

function MetadataItemRow(props: MetadataItemRowProps) {
  const {
    item,
    readOnly,
    editableKeyIndex,
    editableValueIndex,
    editableText,
    dictionary,
    onEditStarted,
    onEditableTextChange,
    onDiscardChanges,
    onSave,
    onSaveDictionary,
    onSaveValue,
    onDelete,
  } = props;

  const isSecret = (item.type || '').toLowerCase() === 'secret';

  const inputOptions = useInputOptions({
    editableText,
    isSecret,
    item,
    onDiscardChanges,
    onEditableTextChange,
    onSave,
    readOnly,
  });

  const keyElement =
    editableKeyIndex === item.index ? (
      <tr key={`${item.key}_key`} className={classNames('cp-metadata-item-row', 'key', 'editable')}>
        <td colSpan={6}>
          <Input
            {...inputOptions('key')}
            style={{minHeight: '28px'}}
            className={classNames(
              'qa-metadata-item-key-input',
              `qa-metadata-item-key-input-${item.key}`,
            )}
          />
        </td>
      </tr>
    ) : (
      <tr
        key={`${item.key}_key`}
        className={classNames('cp-metadata-item-row', 'key', {'read-only': readOnly})}
      >
        <td
          id={`key-column-${item.key}`}
          colSpan={readOnly ? 6 : 5}
          className={classNames('cp-metadata-item-key', 'cp-ellipsis-text')}
          onClick={readOnly ? undefined : () => onEditStarted('key', item.index, item.key)}
        >
          <span style={{display: 'inline-block'}}>{item.key}</span>
        </td>
        {readOnly ? null : (
          <td style={{minWidth: 30, textAlign: 'right'}}>
            <Button
              id={`delete-metadata-key-${item.key}-button`}
              danger
              size="small"
              onClick={() => confirmDeleteKey(item, onDelete)}
            >
              <DeleteOutlined />
            </Button>
          </td>
        )}
      </tr>
    );

  let valueElement;
  if (dictionary) {
    valueElement = (
      <tr
        key={`${item.key}_value`}
        className={classNames('cp-metadata-item-row', 'value', 'editable')}
      >
        <td colSpan={6}>
          <Select
            disabled={readOnly}
            showSearch
            style={{width: '100%'}}
            filterOption={(input, option) =>
              String(option?.label ?? '')
                .toLowerCase()
                .includes(input.toLowerCase())
            }
            value={item.value}
            onChange={onSaveDictionary(item.index)}
            options={(dictionary.values ?? []).map((entry) => ({
              value: entry.value,
              label: entry.value,
            }))}
          />
        </td>
      </tr>
    );
  } else if (item.value && isJson(item.value)) {
    valueElement = (
      <tr
        key={`${item.key}_value`}
        className={classNames('cp-metadata-item-row', 'value', 'editable')}
      >
        <td colSpan={6}>
          <ItemsTable
            title={item.key}
            disabled={readOnly}
            value={item.value}
            onChange={onSaveValue(item.index)}
          />
        </td>
      </tr>
    );
  } else if (editableValueIndex === item.index) {
    valueElement = (
      <tr
        key={`${item.key}_value`}
        className={classNames('cp-metadata-item-row', 'value', 'editable')}
      >
        <td colSpan={6}>
          <div>
            {isSecret ? (
              <Input
                {...inputOptions('value')}
                style={{height: '24px'}}
                className={classNames(
                  'qa-metadata-item-value-input',
                  `qa-metadata-item-value-input-${item.index}`,
                )}
              />
            ) : (
              <Input.TextArea
                {...inputOptions('value')}
                autoSize={MetadataDisplayOptions.edit.autoSize}
                className={classNames(
                  'qa-metadata-item-value-input',
                  `qa-metadata-item-value-input-${item.index}`,
                )}
              />
            )}
          </div>
          {isSecret ? (
            <div
              className="cp-text-not-important"
              style={{fontSize: 'smaller', textAlign: 'center'}}
            >
              Press Enter to save new secret
            </div>
          ) : null}
        </td>
      </tr>
    );
  } else {
    valueElement = (
      <tr key={`${item.key}_value`} className={classNames('cp-metadata-item-row', 'value')}>
        <td
          id={`value-column-${item.key}`}
          colSpan={6}
          onClick={
            readOnly
              ? undefined
              : () => onEditStarted('value', item.index, isSecret ? '' : (item.value ?? ''))
          }
        >
          <span style={{display: 'inline-block'}}>
            {isSecret ? '*****' : MetadataDisplayOptions.preview.display(item.value)}
          </span>
        </td>
      </tr>
    );
  }

  return (
    <>
      <MetadataDivider itemKey={item.key} />
      {keyElement}
      {valueElement}
    </>
  );
}

export {MetadataItemRow};
