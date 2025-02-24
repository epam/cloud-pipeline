import { useCallback, useEffect, useState } from 'react';
import type { DataStorage, Project } from '@cloud-pipeline/core';
import type { CommonProps } from '@cloud-pipeline/components';
import { Alert, Spin } from 'antd';
import classNames from 'classnames';
import { StorageBrowser } from '../../../../widgets/storage-browser';
import { useProjectDataStoragesConfiguration } from '../../../../shared/hooks/use-project-data-storages.ts';
import { useDataStoragesStore } from '../../../../state/storages/hooks.ts';

type Props = CommonProps & {
  project: Project;
};

export function ProjectStorages(props: Props) {
  const { className, style, project } = props;

  const { pending, loaded } = useDataStoragesStore();
  const { defaultDataStorage, dataStorages: projectDataStorages } = useProjectDataStoragesConfiguration(project);

  const [storage, setStorage] = useState(defaultDataStorage);
  const [path, setPath] = useState('');

  const onChangePath = useCallback(
    (newPath?: string) => {
      setPath(newPath ?? '');
    },
    [setPath],
  );

  const onStorageChange = useCallback(
    (newStorage: DataStorage, newPath?: string) => {
      setStorage(newStorage);
      onChangePath(newPath);
    },
    [setStorage, onChangePath],
  );

  useEffect(() => {
    if (defaultDataStorage) {
      onStorageChange(defaultDataStorage);
    }
  }, [defaultDataStorage, onStorageChange]);

  return (
    <div className={classNames(className, 'h-full flex flex-col overflow-hidden')} style={style}>
      {!storage && pending && !loaded && <Spin />}
      {!storage && !pending && <Alert type="warning" message="Project data storage is not specified" />}
      {storage && (
        <StorageBrowser
          storage={storage}
          path={path}
          onPathChange={onChangePath}
          onStorageChange={onStorageChange}
          storages={projectDataStorages}
          showBreadcrumbs
          showHeaderControls
          showItemActions
          className="flex-1 overflow-auto"
        />
      )}
    </div>
  );
}
