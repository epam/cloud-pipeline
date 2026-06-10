import {useCallback, useEffect, useMemo, useState} from 'react';
import type {ChangeEvent} from 'react';
import {useQuery} from '@tanstack/react-query';
import {CommonProps} from '../../../@types/common.ts';
import {getQueryErrorMessage, libraryTreeQueryOptions, useLibrarySubTree} from '../../../queries';
import {Alert, Input, Skeleton, Spin} from 'antd';
import {
  useLibraryTreePlainList,
  useLibraryItemLoader,
  useLibraryTreePlainListPresentation,
  useLibraryItemPathByItemId,
  useLibraryTreeSearchResults,
} from '../model/hooks.ts';
import {
  getLibraryFolderTreeItemId,
  LibraryTreePlainListPresentationOptions,
} from '../model/tree.ts';
import VirtualList from '../../shared/virtual-list';
import {LibraryItem} from '../types.ts';
import {
  LibraryTreeItemPresentation,
  LibraryTreeItemPresentationProps,
} from './library-tree-item.tsx';
import classNames from 'classnames';
import {useUiHiddenObjects} from '../../../stores/preferences/named-preferences/ui-hidden-objects.ts';
import {useChangeCallback} from '../../../hooks/common/callbacks.ts';

const itemRender = (item: LibraryItem, options: LibraryTreeItemPresentationProps) => (
  <LibraryTreeItemPresentation item={item} {...options} />
);

function LibraryTree(
  props: CommonProps & {
    root?: number;
    displayRoot?: boolean;
    activeItemId?: string;
    onActiveItemIdChange?: (activeItemId: string) => void;
  },
) {
  const {
    className,
    style,
    root,
    displayRoot = root === undefined,
    activeItemId: _activeItemId,
    onActiveItemIdChange: _onActiveItemIdChange,
  } = props;
  const [activeItemId, onActiveItemIdChange] = useChangeCallback(
    _activeItemId,
    _onActiveItemIdChange,
  );
  const tree = useLibrarySubTree({parentFolderId: root});
  const [expandedKeys, setExpandedKeys] = useState<Set<string>>(new Set([]));
  useEffect(() => {
    if (tree) {
      setExpandedKeys(
        (current) => new Set([...current].concat([getLibraryFolderTreeItemId(tree)])),
      );
    }
  }, [tree, setExpandedKeys]);
  const {initialized: hiddenObjectsInitialized} = useUiHiddenObjects();
  const plain = useLibraryTreePlainList(tree, {
    includeRootFolder: displayRoot,
    includeBackItem: false,
  });
  const [search, setSearch] = useState('');
  const onSearchChange = useCallback(
    (event: ChangeEvent<HTMLInputElement>) => {
      setSearch(event.target.value);
      if (!tree || event.target.value.length > 0) {
        setExpandedKeys(new Set([]));
      } else {
        setExpandedKeys(new Set([getLibraryFolderTreeItemId(tree)]));
      }
    },
    [setSearch, setExpandedKeys, tree],
  );
  const {
    results: searchResults,
    pending: searchPending,
    searchMode,
  } = useLibraryTreeSearchResults(plain, search);
  const searchResultIds = useMemo(
    () => (searchResults ? searchResults.map((item) => item.id) : []),
    [searchResults],
  );
  const plainTreePresentationOptions = useMemo<LibraryTreePlainListPresentationOptions>(
    () => ({
      expandedKeys: [...expandedKeys],
      searchResultIds,
      searchMode,
    }),
    [expandedKeys, searchResultIds, searchMode],
  );
  const presentationList = useLibraryTreePlainListPresentation(plain, plainTreePresentationOptions);
  const visible = useMemo(() => presentationList.filter((o) => o.visible), [presentationList]);
  const {isSuccess: loaded, error: treeError} = useQuery(libraryTreeQueryOptions());
  const error = getQueryErrorMessage(treeError);
  const onLoadItemDetails = useLibraryItemLoader();
  useEffect(() => {
    if (tree) {
      onLoadItemDetails(tree);
    }
  }, [tree, onLoadItemDetails]);
  const onExpandItems = useCallback(
    (items: Array<{item: LibraryItem; expand: boolean}>) => {
      items.forEach((cfg) => {
        if (cfg.expand) {
          onLoadItemDetails(cfg.item.object);
        }
      });
      setExpandedKeys((current) => {
        let changed = false;
        const newSet = new Set(current);
        items.forEach((cfg) => {
          const currentState = current.has(cfg.item.id);
          if (currentState !== cfg.expand) {
            changed = true;
            if (newSet.has(cfg.item.id)) {
              newSet.delete(cfg.item.id);
            } else {
              newSet.add(cfg.item.id);
            }
          }
        });
        if (changed) {
          return newSet;
        }
        return current;
      });
    },
    [setExpandedKeys, onLoadItemDetails],
  );
  const onExpandSingleItem = useCallback(
    (item: LibraryItem, expand: boolean) => {
      onExpandItems([{item, expand}]);
    },
    [onExpandItems],
  );
  const activeItemPath = useLibraryItemPathByItemId(plain, activeItemId);
  useEffect(() => {
    if (!searchMode) {
      const expandedItems = activeItemPath
        .filter((o) => !expandedKeys.has(o.id) && o.expandable)
        .map((o) => ({
          item: o,
          expand: true,
        }));
      if (expandedItems.length > 0) {
        onExpandItems(expandedItems);
      }
    }
  }, [activeItemPath, expandedKeys, searchMode, onExpandItems]);
  const onItemClick = useCallback(
    (item: LibraryItem) => {
      onActiveItemIdChange(item.id);
      onExpandItems([{item, expand: true}]);
    },
    [onExpandItems, onActiveItemIdChange],
  );
  const activeItem = useMemo(() => plain.find((o) => o.id === activeItemId), [plain, activeItemId]);
  if (loaded && !tree) {
    return (
      <div className={classNames(className, 'library-tree')} style={style}>
        <span className="text-faded">Library root not found</span>
      </div>
    );
  }
  if (error) {
    return (
      <div className={classNames(className, 'library-tree')} style={style}>
        <Alert type="error" title="Error occurred" />
      </div>
    );
  }
  if (!loaded || !hiddenObjectsInitialized) {
    return (
      <div className={classNames(className, 'library-tree')} style={style}>
        <Skeleton className="h-full" active paragraph={{rows: 10}} />
      </div>
    );
  }
  return (
    <div className={classNames(className, 'library-tree')} style={style}>
      <div className="library-tree-search">
        <Input
          value={search}
          onChange={onSearchChange}
          placeholder="Search"
          allowClear
          suffix={searchPending ? <Spin size="small" /> : null}
        />
      </div>
      <VirtualList
        className="min-h-0 flex-1"
        items={visible}
        itemKey="id"
        focusedItem={activeItem}
        render={itemRender}
        onClick={onItemClick}
        onExpand={onExpandSingleItem}
        itemsToken={tree}
        itemHeight={28}
        activeItemId={activeItemId}
      />
    </div>
  );
}

export {LibraryTree};
