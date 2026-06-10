import {AutoComplete, Button, Checkbox, Input, Select} from 'antd';
import {CheckOutlined, CloseOutlined} from '@ant-design/icons';
import {SystemDictionary} from '../../@types/system-dictionaries.ts';
import type {MetadataAddKeyState} from './types.ts';

type AddMetadataKeyRowProps = {
  addKey: MetadataAddKeyState;
  readOnly: boolean;
  availableDictionaries: string[];
  dictionary?: SystemDictionary;
  onChange: (field: keyof MetadataAddKeyState, value: string | boolean) => void;
  onSave: () => void | Promise<void>;
  onCancel: () => void;
};

function AddMetadataKeyRow({
  addKey,
  readOnly,
  availableDictionaries,
  dictionary,
  onChange,
  onSave,
  onCancel,
}: AddMetadataKeyRowProps) {
  const valueItem = dictionary ? (
    <Select
      allowClear
      showSearch
      style={{width: '100%'}}
      filterOption={(input, option) =>
        String(option?.label ?? '')
          .toLowerCase()
          .includes(input.toLowerCase())
      }
      value={addKey.value}
      onChange={(value) => onChange('value', value ?? '')}
      disabled={readOnly}
      options={(dictionary.values ?? []).map((entry) => ({
        value: entry.value,
        label: entry.value,
      }))}
    />
  ) : (
    <Input.TextArea
      disabled={readOnly}
      onPressEnter={
        readOnly
          ? undefined
          : (e) => {
              e.stopPropagation();
              void onSave();
            }
      }
      onKeyDown={(e) => {
        if (e.key === 'Escape') {
          onCancel();
        }
      }}
      value={addKey.value}
      onChange={(e) => onChange('value', e.target.value)}
      size="small"
      autoSize
    />
  );

  return (
    <>
      <tr>
        <td colSpan={6}>
          <div className="metadata-divider cp-divider horizontal" />
        </td>
      </tr>
      <tr className="metadata-new-key-row">
        <td style={{textAlign: 'right', width: 80}}>Key:</td>
        <td colSpan={2}>
          <AutoComplete
            disabled={readOnly}
            allowClear
            backfill
            autoFocus
            value={addKey.key}
            onChange={(value) => onChange('key', value)}
            size="small"
            style={{width: '100%'}}
            filterOption={(input, option) =>
              String(option?.label ?? '')
                .toLowerCase()
                .includes(input.toLowerCase())
            }
            options={availableDictionaries.map((dict) => ({value: dict, label: dict}))}
          />
        </td>
      </tr>
      <tr className="metadata-new-key-row">
        <td style={{textAlign: 'right', width: 80}}>Value:</td>
        <td colSpan={2}>{valueItem}</td>
      </tr>
      <tr className="metadata-new-key-row">
        <td style={{textAlign: 'right', width: 80}}>{'\u00A0'}</td>
        <td colSpan={2}>
          <Checkbox checked={addKey.secret} onChange={(e) => onChange('secret', e.target.checked)}>
            Secret
          </Checkbox>
        </td>
      </tr>
      <tr className="metadata-new-key-row">
        <td colSpan={3} style={{textAlign: 'right'}}>
          <Button
            id="add-metadata-item-button"
            size="small"
            type="primary"
            onClick={() => void onSave()}
            disabled={readOnly}
          >
            <CheckOutlined /> Add
          </Button>
          <Button id="cancel-add-metadata-item-button" size="small" onClick={onCancel}>
            <CloseOutlined /> Cancel
          </Button>
        </td>
      </tr>
    </>
  );
}

export {AddMetadataKeyRow};
