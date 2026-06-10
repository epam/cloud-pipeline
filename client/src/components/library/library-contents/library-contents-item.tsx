import {LibraryItem, LibraryItemType} from '../types.ts';
import {CommonProps} from '../../../@types/common.ts';
import {type MouseEvent, useCallback} from 'react';
import {LibraryItemLink} from '../library-item-link.tsx';
import classNames from 'classnames';
import {LibraryItemIcon} from '../library-item-icon.tsx';
import {LoadingMessage} from '../../shared/loading-message/loading-message.tsx';
import Markdown from '../../special/markdown';
import './library-contents.css';
import {MetadataTags} from '../../shared/metadata-tag/metadata-tags.tsx';
import {FolderRemoveButton} from '../../shared/object-actions/folder/remove/folder-remove-button.tsx';
import {isConfiguration, isFolder, isPipeline} from '../../../utilities/guards.ts';
import {Button} from 'antd';
import {MessageOutlined} from '@ant-design/icons';
import LibraryItemInlineActions from './inline-actions';

export type LibraryContentsItemPresentationProps = {
  onItemClick?: (item: LibraryItem) => void;
  onItemIssuesClick?: (item: LibraryItem | undefined) => void;
  showDetails?: boolean;
  actions?: boolean;
};

function LibraryContentsItemPresentation(
  props: CommonProps &
    LibraryContentsItemPresentationProps & {
      item: LibraryItem;
    },
) {
  const {
    className,
    style,
    item,
    onItemClick,
    onItemIssuesClick,
    showDetails = false,
    actions = false,
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
  const onIssuesClick = useCallback(() => {
    if (onItemIssuesClick) {
      onItemIssuesClick(item);
    }
  }, [item, onItemIssuesClick]);
  const displayIssues =
    !!onItemIssuesClick &&
    ((isFolder(item.object) &&
      [LibraryItemType.folder, LibraryItemType.project].includes(item.type)) ||
      isConfiguration(item.object) ||
      isPipeline(item.object));
  return (
    <div
      className={classNames(className, 'library-contents-item', {
        expanded: item.expanded,
        interactive: item.interactive,
      })}
      style={style}
    >
      <LibraryItemLink className="library-contents-item-info" onClick={onClick} item={item}>
        <div className="library-contents-item-title">
          <div className="inline mr-1 text-center" style={{width: '1.25em'}}>
            <LibraryItemIcon item={item} />
          </div>
          <LoadingMessage
            className={classNames('library-contents-item-name', {
              'text-faded': item.type === LibraryItemType.loading,
            })}
            loading={item.pending}
          >
            {item.name}
          </LoadingMessage>
          {item.details && <span className="library-contents-item-details">{item.details}</span>}
        </div>
        {showDetails && (item.object?.description || Object.keys(item.metadata).length > 0) && (
          <div className="library-contents-item-details">
            {item.object?.description && (
              <Markdown
                className="wrap w-full whitespace-normal overflow-auto"
                md={item.object.description}
              />
            )}
            <MetadataTags
              className="w-full overflow-auto"
              metadata={item.metadata}
              mode="vertical"
              skipSystem
              skipSecrets
            />
          </div>
        )}
      </LibraryItemLink>
      {actions && (
        <div className="library-contents-item-actions">
          <LibraryItemInlineActions
            item={item}
            onIssuesClick={onItemIssuesClick ? onIssuesClick : undefined}
          />
        </div>
      )}
    </div>
  );
}

export {LibraryContentsItemPresentation};
