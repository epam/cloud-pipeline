import {Button, Row} from 'antd';
import {DeleteOutlined, LeftOutlined, PlusOutlined} from '@ant-design/icons';
import {isSpecialItem} from './utilities.ts';
import type {MetadataItem} from './types.ts';
import type {CSSProperties, ReactNode} from 'react';

type MetadataHeaderProps = {
  editable: boolean;
  showMetadata: boolean;
  removeAllAvailable: boolean;
  addKeyVisible: boolean;
  items: MetadataItem[];
  isReadOnlyTag: (key: string) => boolean;
  entityName?: string;
  title?: string;
  titleStyle?: CSSProperties;
  canNavigateBack?: boolean;
  onNavigateBack?: () => void;
  onAddKey: () => void;
  onRemoveAll: () => void;
};

function MetadataHeader({
  editable,
  showMetadata,
  removeAllAvailable,
  addKeyVisible,
  items,
  isReadOnlyTag,
  entityName,
  title,
  titleStyle,
  canNavigateBack,
  onNavigateBack,
  onAddKey,
  onRemoveAll,
}: MetadataHeaderProps) {
  const renderTitle = () => {
    if (entityName && onNavigateBack && canNavigateBack) {
      return (
        <>
          <Button
            id="back-button"
            key="back-button"
            style={{marginRight: 5}}
            size="small"
            onClick={onNavigateBack}
          >
            <LeftOutlined />
          </Button>
          <b key="entity name">{entityName}</b>
        </>
      );
    }
    if (title) {
      return <span style={titleStyle}>{title || '\u00A0'}</span>;
    }
    return null;
  };

  const actions: ReactNode[] = [];
  if (editable && !addKeyVisible && showMetadata) {
    actions.push(
      <Button id="add-key-button" key="add button" size="small" onClick={onAddKey}>
        <PlusOutlined /> Add
      </Button>,
    );
  }
  if (
    editable &&
    items.filter((item) => !isReadOnlyTag(item.key) && !isSpecialItem(item.key)).length > 0 &&
    removeAllAvailable
  ) {
    actions.push(
      <Button
        id="remove-all-keys-button"
        key="remove all keys button"
        size="small"
        danger
        onClick={onRemoveAll}
      >
        <DeleteOutlined /> Remove all
      </Button>,
    );
  }

  return (
    <thead className="metadata-header">
      <tr>
        <td colSpan={3} style={{padding: 5}}>
          <Row justify="space-between" align="middle">
            <div>{renderTitle()}</div>
            <div>{actions}</div>
          </Row>
        </td>
      </tr>
    </thead>
  );
}

export {MetadataHeader};
