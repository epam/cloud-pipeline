import {useCallback, useState} from 'react';
import {useQuery, useQueryClient} from '@tanstack/react-query';
import {CommonProps} from '../../../@types/common.ts';
import {LibraryItem, LibraryItemType} from '../types.ts';
import './library-header.css';
import {Configuration, DataStorage, Folder, Pipeline} from '../../../@types/library.ts';
import {message as antdMessage} from 'antd';
import {Revision} from '../../../@types/pipeline.ts';
import {EditableBreadcrumb} from './editable-breadcrumb.tsx';
import {isConfiguration, isDataStorage, isFolder, isPipeline} from '../../../utilities/guards.ts';
import {saveConfiguration, updateDataStorage, updateFolder, updatePipeline} from '../../../api';
import {
  configurationKeys,
  configurationQueryOptions,
  dataStorageKeys,
  dataStorageQueryOptions,
  folderKeys,
  folderQueryOptions,
  libraryTreeKeys,
  pipelineKeys,
  pipelineQueryOptions,
} from '../../../queries';
import {getErrorDescription} from '../../../utilities/errors.ts';

function LibraryEditablePipeline(
  props: CommonProps & {disabled?: boolean; pipeline: Pipeline; revision?: Revision},
) {
  const {className, style, disabled, pipeline, revision} = props;
  const queryClient = useQueryClient();
  const [pending, setPending] = useState(false);
  const {data: pipelineObj, isFetching: pipelineObjPending} = useQuery(
    pipelineQueryOptions(pipeline.id),
  );
  const onChangeName = useCallback(
    (newName: string) => {
      if (pipelineObj) {
        (async () => {
          let succeeded = false;
          const hide = antdMessage.loading(
            <span>
              Renaming <b>{pipelineObj.name}</b>...
            </span>,
          );
          try {
            setPending(true);
            await updatePipeline({
              id: pipelineObj.id,
              name: newName,
              description: pipelineObj.description,
              parentFolderId: pipelineObj.parentFolderId,
              branch: pipelineObj.branch,
              visibility: pipelineObj.visibility,
              configurationPath: pipelineObj.configurationPath,
              codePath: pipelineObj.codePath,
              docsPath: pipelineObj.docsPath,
            });
            await queryClient.invalidateQueries({queryKey: pipelineKeys.detail(pipelineObj.id)});
            succeeded = true;
          } catch (error) {
            antdMessage.error(
              <span>
                Error renaming <b>{pipelineObj.name}</b>: {getErrorDescription(error)}
              </span>,
              5,
            );
          } finally {
            hide();
            setPending(false);
          }
          if (succeeded) {
            try {
              await queryClient.invalidateQueries({queryKey: libraryTreeKeys.all});
            } catch {
              // noop
            }
          }
        })();
      }
    },
    [setPending, pipelineObj, queryClient],
  );
  return (
    <EditableBreadcrumb
      value={(pipelineObj ?? pipeline).name}
      pending={pending || pipelineObjPending}
      onChange={onChangeName}
      display={revision ? (o) => `${o} (${revision.name})` : (pipelineObj ?? pipeline).name}
      className={className}
      style={style}
      disabled={pending || pipelineObjPending || disabled}
    />
  );
}

function LibraryEditableStorage(props: CommonProps & {disabled?: boolean; storage: DataStorage}) {
  const {className, style, disabled, storage} = props;
  const queryClient = useQueryClient();
  const [pending, setPending] = useState(false);
  const {data: storageObj, isFetching: storageObjPending} = useQuery(
    dataStorageQueryOptions(storage.id),
  );
  const onChangeName = useCallback(
    (newName: string) => {
      if (storageObj) {
        (async () => {
          let succeeded = false;
          const hide = antdMessage.loading(
            <span>
              Renaming <b>{storageObj.name}</b>...
            </span>,
          );
          try {
            setPending(true);
            await updateDataStorage({
              id: storageObj.id,
              parentFolderId: storageObj.parentFolderId,
              name: newName,
              description: storageObj.description,
              path: storageObj.path,
            });
            await queryClient.invalidateQueries({queryKey: dataStorageKeys.detail(storageObj.id)});
            succeeded = true;
          } catch (error) {
            antdMessage.error(
              <span>
                Error renaming <b>{storageObj.name}</b>: {getErrorDescription(error)}
              </span>,
              5,
            );
          } finally {
            hide();
            setPending(false);
          }
          if (succeeded) {
            try {
              await queryClient.invalidateQueries({queryKey: libraryTreeKeys.all});
            } catch {
              // noop
            }
          }
        })();
      }
    },
    [setPending, storageObj, queryClient],
  );
  return (
    <EditableBreadcrumb
      value={(storageObj ?? storage).name}
      pending={pending || storageObjPending}
      onChange={onChangeName}
      className={className}
      style={style}
      disabled={pending || storageObjPending || disabled}
    />
  );
}

function LibraryEditableConfiguration(
  props: CommonProps & {disabled?: boolean; configuration: Configuration},
) {
  const {className, style, disabled, configuration} = props;
  const queryClient = useQueryClient();
  const [pending, setPending] = useState(false);
  const {data: configurationObj, isFetching: configurationObjPending} = useQuery(
    configurationQueryOptions(configuration.id),
  );
  const onChangeName = useCallback(
    (newName: string) => {
      if (configurationObj) {
        (async () => {
          let succeeded = false;
          const hide = antdMessage.loading(
            <span>
              Renaming <b>{configurationObj.name}</b>...
            </span>,
          );
          try {
            setPending(true);
            await saveConfiguration({
              id: configurationObj.id,
              name: newName,
              description: configurationObj.description,
              parentId: configurationObj.parent?.id,
              entries: configurationObj.entries?.map((entry) => entry),
            });
            await queryClient.invalidateQueries({
              queryKey: configurationKeys.detail(configurationObj.id),
            });
            succeeded = true;
          } catch (error) {
            antdMessage.error(
              <span>
                Error renaming <b>{configurationObj.name}</b>: {getErrorDescription(error)}
              </span>,
              5,
            );
          } finally {
            hide();
            setPending(false);
          }
          if (succeeded) {
            try {
              await queryClient.invalidateQueries({queryKey: libraryTreeKeys.all});
            } catch {
              // noop
            }
          }
        })();
      }
    },
    [setPending, configurationObj, queryClient],
  );
  return (
    <EditableBreadcrumb
      value={(configurationObj ?? configuration).name}
      pending={pending || configurationObjPending}
      onChange={onChangeName}
      className={className}
      style={style}
      disabled={pending || configurationObjPending || disabled}
    />
  );
}

function LibraryEditableFolder(props: CommonProps & {disabled?: boolean; folder: Folder}) {
  const {className, style, disabled, folder} = props;
  const queryClient = useQueryClient();
  const [pending, setPending] = useState(false);
  const {data: folderObj, isFetching: folderObjPending} = useQuery(folderQueryOptions(folder.id));
  const onChangeName = useCallback(
    (newName: string) => {
      if (folderObj) {
        (async () => {
          let succeeded = false;
          const hide = antdMessage.loading(
            <span>
              Renaming <b>{folderObj.name}</b>...
            </span>,
          );
          try {
            setPending(true);
            await updateFolder({id: folderObj.id, parentId: folderObj.parentId, name: newName});
            await queryClient.invalidateQueries({queryKey: folderKeys.detail(folderObj.id)});
            succeeded = true;
          } catch (error) {
            antdMessage.error(
              <span>
                Error renaming <b>{folderObj.name}</b>: {getErrorDescription(error)}
              </span>,
              5,
            );
          } finally {
            hide();
            setPending(false);
          }
          if (succeeded) {
            try {
              await queryClient.invalidateQueries({queryKey: libraryTreeKeys.all});
            } catch {
              // noop
            }
          }
        })();
      }
    },
    [setPending, folderObj, queryClient],
  );
  return (
    <EditableBreadcrumb
      value={(folderObj ?? folder).name}
      pending={pending || folderObjPending}
      onChange={onChangeName}
      className={className}
      style={style}
      disabled={pending || folderObjPending || disabled}
    />
  );
}

function LibraryNotEditableItem(props: CommonProps & {disabled?: boolean; item: LibraryItem}) {
  const {className, style, disabled, item} = props;
  return (
    <EditableBreadcrumb
      value=""
      editable={false}
      className={className}
      style={style}
      disabled={disabled}
      display={item.name}
    />
  );
}

function LibraryEditableItem(
  props: CommonProps & {
    disabled?: boolean;
    item: LibraryItem;
  },
) {
  const {className, style, item, disabled = false} = props;
  if (
    (item.type === LibraryItemType.pipeline || item.type === LibraryItemType.pipelineVersion) &&
    isPipeline(item.object)
  ) {
    return (
      <LibraryEditablePipeline
        className={className}
        style={style}
        disabled={disabled}
        pipeline={item.object}
        revision={item.revision}
      />
    );
  }
  if (item.type === LibraryItemType.storage && isDataStorage(item.object)) {
    return (
      <LibraryEditableStorage
        className={className}
        style={style}
        disabled={disabled}
        storage={item.object}
      />
    );
  }
  if (
    (item.type === LibraryItemType.folder || item.type === LibraryItemType.project) &&
    isFolder(item.object)
  ) {
    return (
      <LibraryEditableFolder
        className={className}
        style={style}
        disabled={disabled}
        folder={item.object}
      />
    );
  }
  if (item.type === LibraryItemType.configuration && isConfiguration(item.object)) {
    return (
      <LibraryEditableConfiguration
        className={className}
        style={style}
        disabled={disabled}
        configuration={item.object}
      />
    );
  }
  return (
    <LibraryNotEditableItem className={className} style={style} disabled={disabled} item={item} />
  );
}

export {LibraryEditableItem};
