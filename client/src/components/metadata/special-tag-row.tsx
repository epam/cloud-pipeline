import classNames from 'classnames';
import {Button, message, Modal} from 'antd';
import {DeleteOutlined} from '@ant-design/icons';
import {SpecialTags} from './constants.ts';
import type {MetadataItem} from './types.ts';

type SpecialTagRowProps = {
  item: MetadataItem;
  readOnly: boolean;
  specialTagsProperties?: Record<string, unknown>;
  onSaveValue: (index: number) => (value: string) => Promise<boolean | void>;
  onRemove: (item: MetadataItem) => Promise<void>;
  onReload: () => void;
};

function SpecialTagRow({
  item,
  readOnly,
  specialTagsProperties,
  onSaveValue,
  onRemove,
  onReload,
}: SpecialTagRowProps) {
  const Component = SpecialTags[item.key];
  if (!Component) {
    return null;
  }
  return (
    <tr
      key={`${item.key}_special`}
      className={classNames('cp-metadata-item-row', 'special', {'read-only': readOnly})}
    >
      <td id={`value-column-${item.key}`} colSpan={6}>
        <Component
          key={item.key}
          metadata={item}
          readOnly={readOnly}
          onChange={onSaveValue(item.index)}
          onRemove={async () => {
            await onRemove(item);
          }}
          info={specialTagsProperties}
          reload={onReload}
        />
      </td>
    </tr>
  );
}

export function confirmDeleteKey(
  item: MetadataItem,
  onConfirm: (item: MetadataItem) => Promise<void>,
) {
  Modal.confirm({
    title: `Do you want to delete key "${item.key}"?`,
    content: null,
    style: {wordWrap: 'break-word'},
    okText: 'OK',
    cancelText: 'Cancel',
    onOk: async () => {
      try {
        await onConfirm(item);
      } catch (e) {
        message.error(e instanceof Error ? e.message : String(e), 5);
      }
    },
  });
}

export {SpecialTagRow};
