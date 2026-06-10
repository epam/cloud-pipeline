import {useCallback, useMemo} from 'react';
import {useQuery} from '@tanstack/react-query';
import {CommonProps} from '../../../@types/common.ts';
import {Folder, LibraryRootFolder} from '../../../@types/library.ts';
import {
  folderQueryOptions,
  getQueryErrorMessage,
  libraryTreeQueryOptions,
  metadataFolderQueryOptions,
  useLibrarySubTree,
} from '../../../queries';
import {useLibraryTreeAssignedMetadata, useLibraryTreePlainList} from '../model/hooks.ts';
import {useUiHiddenObjects} from '../../../stores/preferences/named-preferences/ui-hidden-objects.ts';
import {isLibraryRoot} from '../../../utilities/guards.ts';
import './library-contents.css';
import classNames from 'classnames';
import {LibraryItem} from '../types.ts';
import {
  LibraryContentsItemPresentation,
  LibraryContentsItemPresentationProps,
} from './library-contents-item.tsx';
import PagedList from '../../shared/paged-list';
import {Alert, Skeleton} from 'antd';

const itemRender = (item: LibraryItem, options: LibraryContentsItemPresentationProps) => (
  <LibraryContentsItemPresentation item={item} {...options} />
);

export type LibraryFolderContentsCommonProps = CommonProps &
  LibraryContentsItemPresentationProps & {
    root?: number;
  };

type LibraryFolderContentsProps = LibraryFolderContentsCommonProps & {
  folder: LibraryRootFolder | Folder | undefined;
  pending: boolean;
  error?: string;
  loaded: boolean;
};

function LibraryFolderContents(props: LibraryFolderContentsProps) {
  const {
    className,
    style,
    root,
    folder,
    onItemClick,
    pending,
    error,
    loaded,
    ...presentationProps
  } = props;
  const {initialized: hiddenObjectsInitialized} = useUiHiddenObjects();
  const canNavigateBack = folder ? (isLibraryRoot(folder) ? false : root !== folder.id) : false;
  const plain = useLibraryTreePlainList(folder, {
    includeRootFolder: false,
    includeBackItem: canNavigateBack,
  });
  const folderId = isLibraryRoot(folder) ? undefined : folder?.id;
  const {data: metadata} = useQuery(metadataFolderQueryOptions(folderId, {enabled: !!folder}));
  const visible = useMemo(() => plain.filter((o) => o.level === 0), [plain]);
  const items = useLibraryTreeAssignedMetadata(visible, metadata);
  const onClick = useCallback(
    (item: LibraryItem) => {
      if (onItemClick) {
        onItemClick(item);
      }
    },
    [onItemClick],
  );
  if ((pending && !loaded) || !hiddenObjectsInitialized) {
    return (
      <div className={classNames(className)} style={style}>
        <Skeleton className="w-full h-full" active />
      </div>
    );
  }
  if (!loaded || error) {
    return (
      <div className={classNames(className)} style={style}>
        <Alert title={error ?? 'Error loading folder contents'} showIcon type="error" />
      </div>
    );
  }
  return (
    <div className={classNames(className)} style={style}>
      <PagedList
        className="w-full h-full overflow-auto"
        items={items}
        itemKey="id"
        render={itemRender}
        pageSize={50}
        onItemClick={onClick}
        {...presentationProps}
      />
    </div>
  );
}

function RootLibraryContents(props: LibraryFolderContentsCommonProps) {
  const tree = useLibrarySubTree({staleTime: 10_000});
  const {
    isFetching: pending,
    isSuccess: loaded,
    error: treeError,
  } = useQuery(libraryTreeQueryOptions({staleTime: 10_000}));
  const error = getQueryErrorMessage(treeError);
  return (
    <LibraryFolderContents
      folder={tree}
      pending={pending}
      loaded={loaded}
      error={error}
      {...props}
    />
  );
}

function FolderContents(
  props: LibraryFolderContentsCommonProps & {
    folder: number;
  },
) {
  const {folder, ...commonProps} = props;
  const {
    data,
    isFetching: pending,
    isSuccess: loaded,
    error: folderError,
  } = useQuery(folderQueryOptions(folder, {staleTime: 10_000}));
  return (
    <LibraryFolderContents
      folder={data}
      pending={pending}
      loaded={loaded}
      error={getQueryErrorMessage(folderError)}
      {...commonProps}
    />
  );
}

export {RootLibraryContents, FolderContents};
