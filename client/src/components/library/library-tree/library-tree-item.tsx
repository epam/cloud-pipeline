import {useCallback} from 'react';
import type {MouseEvent} from 'react';
import {CommonProps} from '../../../@types/common.ts';
import {LibraryItemType, LibraryItem} from '../types.ts';
import classNames from 'classnames';
import {CaretRightOutlined} from '@ant-design/icons';
import {LoadingMessage} from '../../shared/loading-message/loading-message.tsx';
import {LibraryItemIcon} from '../library-item-icon.tsx';
import {LibraryItemLink} from '../library-item-link.tsx';
import './library-tree.css';

export type LibraryTreeItemPresentationProps = {
  activeItemId?: string;
  onClick?: (item: LibraryItem) => void;
  onExpand?: (item: LibraryItem, expanded: boolean) => void;
  offsetSize?: number | string;
};

function LibraryTreeItemPresentation(
  props: CommonProps &
    LibraryTreeItemPresentationProps & {
      item: LibraryItem;
    },
) {
  const {
    className,
    style,
    item,
    onClick: onItemClick,
    onExpand: onItemExpand,
    offsetSize = '1.5em',
    activeItemId,
  } = props;
  const onClick = useCallback(
    (event: MouseEvent) => {
      event.stopPropagation();
      if (onItemClick) {
        onItemClick(item);
      }
    },
    [item, onItemClick],
  );
  const onExpand = useCallback(
    (event: MouseEvent<HTMLDivElement>) => {
      event.stopPropagation();
      event.preventDefault();
      if (onItemExpand) {
        onItemExpand(item, !item.expanded);
      }
    },
    [item, onItemExpand],
  );
  return (
    <LibraryItemLink
      className={classNames(className, 'library-tree-item', `library-item-type-${item.type}`, {
        expanded: item.expanded,
        active: item.id === activeItemId,
        interactive: item.interactive,
      })}
      style={style}
      onClick={onClick}
      item={item}
    >
      <div
        className="library-tree-item-offset"
        style={{
          marginLeft:
            typeof offsetSize === 'number'
              ? item.level * offsetSize
              : `calc(${item.level} * ${offsetSize})`,
        }}
      />
      <div className="library-tree-item-container">
        <div
          className="inline text-center"
          style={{width: offsetSize}}
          onClick={item.expandable ? onExpand : undefined}
        >
          {item.expandable && <CaretRightOutlined className="expand-icon" />}
        </div>
        <div className="inline mr-1 text-center" style={{width: '1.25em'}}>
          <LibraryItemIcon item={item} />
        </div>
        <LoadingMessage
          className={classNames('library-tree-item-name', {
            'text-faded': item.type === LibraryItemType.loading,
          })}
          loading={item.pending}
        >
          {item.name}
        </LoadingMessage>
        {item.details && (
          <span className="library-tree-item-details text-faded">{item.details}</span>
        )}
      </div>
    </LibraryItemLink>
  );
}

export {LibraryTreeItemPresentation};
