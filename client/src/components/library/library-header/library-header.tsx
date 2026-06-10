import {Fragment, useCallback, useMemo} from 'react';
import {CommonProps} from '../../../@types/common.ts';
import './library-header.css';
import classNames from 'classnames';
import {findLibraryItemPath, parseLibraryItemId} from '../model/tree.ts';
import {useLibrarySubTree} from '../../../queries';
import {useLibraryTreePlainList} from '../model/hooks.ts';
import {LibraryItem, LibraryItemType} from '../types.ts';
import {LibraryBreadcrumbItem} from './library-breadcrumb-item.tsx';
import {CaretRightOutlined} from '@ant-design/icons';
import {LibraryItemIcon} from '../library-item-icon.tsx';
import {LibraryEditableItem} from './library-editable-item.tsx';
import {LibraryItemOwner} from './library-item-owner.tsx';

function LibraryHeader(
  props: CommonProps & {
    activeItemId?: string;
    onActiveItemIdChange?: (activeItemId: string) => void;
    root?: number;
  },
) {
  const {className, style, activeItemId, onActiveItemIdChange, root} = props;
  const tree = useLibrarySubTree({parentFolderId: root});
  const plain = useLibraryTreePlainList(tree, {
    includeRootFolder: true,
    includeBackItem: false,
  });
  const item = useMemo(() => parseLibraryItemId(activeItemId), [activeItemId]);
  const displayedItem = useMemo(
    () =>
      item && item.type === LibraryItemType.pipelineVersion
        ? {type: LibraryItemType.pipeline, identifier: item.identifier}
        : item,
    [item],
  );
  const libraryItem = useMemo(
    () => (item ? findLibraryItemPath(plain, item.type, item.identifier, item.sub) : [])[0],
    [plain, item],
  );
  const breadcrumbsPath = useMemo(
    () =>
      (displayedItem
        ? findLibraryItemPath(
            plain,
            displayedItem.type,
            displayedItem.identifier,
            displayedItem.sub,
          )
        : []
      ).reverse(),
    [plain, displayedItem],
  );
  const onItemClick = useCallback(
    (item: LibraryItem) => {
      if (onActiveItemIdChange) {
        onActiveItemIdChange(item.id);
      }
    },
    [onActiveItemIdChange],
  );
  return (
    <div className={classNames(className, 'library-header')} style={style}>
      {libraryItem && <LibraryItemIcon item={libraryItem} className="library-header-item-icon" />}
      {libraryItem &&
        breadcrumbsPath.length > 0 &&
        breadcrumbsPath.map((o, idx) => (
          <Fragment key={o.id}>
            {idx === breadcrumbsPath.length - 1 ? (
              <LibraryEditableItem item={libraryItem} />
            ) : (
              <LibraryBreadcrumbItem
                className="library-header-breadcrumb-item"
                item={o}
                onClick={onActiveItemIdChange ? onItemClick : undefined}
              >
                {o.name}
              </LibraryBreadcrumbItem>
            )}
            {idx < breadcrumbsPath.length - 1 && (
              <CaretRightOutlined className="library-header-breadcrumb-arrow" />
            )}
          </Fragment>
        ))}
      {libraryItem && <LibraryItemOwner className="library-header-owner" item={libraryItem} />}
    </div>
  );
}

export {LibraryHeader};
