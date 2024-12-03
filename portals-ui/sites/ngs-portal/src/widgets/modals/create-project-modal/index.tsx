import {
  fetchAvailableDataStorages,
  registerProject,
} from '@cloud-pipeline/api';
import type { DataStorage } from '@cloud-pipeline/core';
import { noop } from '@cloud-pipeline/core';
import {
  ModalHeader,
  ModalFooter,
  Button,
  ModalBlocker,
  ModalWindow,
  TextInput,
  LabeledInput,
  PickerInput,
  DataPickerRow,
  PickerItem,
  SuccessNotification,
  ErrorNotification,
} from '@epam/uui';
import type { DataRowProps, DataSourceState } from '@epam/uui-core';
import { useAsyncDataSource, useUuiContext, type IModal } from '@epam/uui-core';
import { useCallback, useEffect, useState } from 'react';
import { useAuthenticatedUser } from '../../../state/authentication/hooks';
import CircleLoaderIcon from '@epam/assets/icons/loaders/circle-loader.svg?react';
import { loadProjects } from '../../../state/projects/load-projects';
import './styles.css';

export const CreateProjectModal = (props: IModal<string>) => {
  const { uuiNotifications } = useUuiContext();
  const authenticatedUser = useAuthenticatedUser();
  const [name, setName] = useState<string | undefined>('');
  const [defaultDataStorageId, setDefaultDataStorageId] = useState<
    number | undefined
  >();
  const [pending, setPending] = useState(false);
  const [spin, setSpin] = useState(false);
  useEffect(() => {
    if (authenticatedUser) {
      setDefaultDataStorageId(Number(authenticatedUser.defaultStorageId));
    }
  }, [authenticatedUser]);
  const dataSource = useAsyncDataSource<
    DataStorage,
    number | undefined,
    undefined
  >(
    {
      api: async () => {
        setPending(true);
        const dataStorages = await fetchAvailableDataStorages();
        setPending(false);
        return dataStorages;
      },
    },
    [],
  );
  const renderDataStorageRow = useCallback(
    (
      props: DataRowProps<DataStorage, number | undefined>,
      dsState: DataSourceState,
    ) => {
      const { key, ...restProps } = props;
      return (
        <DataPickerRow
          {...restProps}
          key={key}
          alignActions="center"
          padding="12"
          cx="text-nowrap"
          renderItem={(item) => (
            <PickerItem
              {...restProps}
              title={item.name}
              subtitle={item.pathMask}
              dataSourceState={dsState}
              cx="text-nowrap picker-item"
            />
          )}
        />
      );
    },
    [],
  );
  const onOk = async () => {
    if (pending || !name?.length) {
      return;
    }
    setPending(true);
    setSpin(true);
    try {
      await registerProject(name);
      await loadProjects();
      uuiNotifications
        .show((props) => (
          <SuccessNotification {...props}>
            <b>Project {name} successfully created.</b>
          </SuccessNotification>
        ))
        .then(noop)
        .catch(noop);
      props.success('');
    } catch (error) {
      uuiNotifications
        .show((props) => (
          <ErrorNotification {...props}>
            <b>Failed to create project {name}.</b>
            <span>
              {error instanceof Error ? error.message : String(error)}
            </span>
          </ErrorNotification>
        ))
        .then(noop)
        .catch(noop);
    } finally {
      setPending(false);
      setSpin(false);
    }
  };
  return (
    <ModalBlocker {...props}>
      <ModalWindow width={'70vw'} style={{ maxWidth: 740 }}>
        <ModalHeader title="Create project" onClose={() => props.abort()} />
        <div className="flex flex-col gap-2 p-4">
          <LabeledInput
            htmlFor="name"
            label={<span className="item-label">Name:</span>}
            labelPosition="left">
            <TextInput
              id="name"
              value={name}
              onValueChange={setName}
              placeholder="Project name"
              size="30"
              isDisabled={pending}
            />
          </LabeledInput>
          <LabeledInput
            htmlFor="datastorage"
            label={<span className="item-label">Default datastorage:</span>}
            labelPosition="left">
            <PickerInput
              dataSource={dataSource}
              value={defaultDataStorageId}
              onValueChange={setDefaultDataStorageId}
              renderRow={renderDataStorageRow}
              selectionMode="single"
              valueType="id"
              sorting={{ field: 'name', direction: 'asc' }}
              getName={(item) => `${item.name} (${item.pathMask})`}
              id="datastorage"
              size="30"
              isDisabled={pending}
              placeholder="Default datastorage"
            />
          </LabeledInput>
        </div>
        <ModalFooter cx="justify-end">
          <Button
            color="secondary"
            fill="outline"
            caption="Cancel"
            onClick={() => props.abort()}
          />
          <Button
            color="primary"
            caption="Ok"
            isDisabled={pending || spin || !name?.length}
            onClick={() => {
              onOk().then(noop).catch(noop);
            }}
            iconPosition="right"
            icon={spin ? CircleLoaderIcon : undefined}
          />
        </ModalFooter>
      </ModalWindow>
    </ModalBlocker>
  );
};
