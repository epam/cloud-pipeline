import {useCallback, useRef, useState} from 'react';
import {useNavigate} from 'react-router-dom';
import {Button, Dropdown, message, Modal} from 'antd';
import {
  CloudUploadOutlined,
  DeleteOutlined,
  PlusOutlined,
  SettingOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import {useQueryClient} from '@tanstack/react-query';

import type {CommonProps} from '../../../../@types/common.ts';
import {useMetadataActions} from './hooks.ts';
import MetadataEntityUpload from '../../../../models/folderMetadata/MetadataEntityUpload';
import AddInstanceForm from '../../../pipelines/browser/forms/AddInstanceForm.jsx';
import UploadButton from '../../../special/UploadButton.jsx';
import UploadToDatastorageForm from '../../../pipelines/browser/forms/UploadToDatastorageForm.jsx';
import {useStringPreferenceValue} from '../../../../queries/preferences/hooks.ts';
import {storagesQueryOptions} from '../../../../queries/datastorage/datastorage.ts';
import {launchRun} from '../../../../api/runs/runs-api.ts';
import SessionStorageWrapper from '../../../special/SessionStorageWrapper.js';

type SettingsActionProps = CommonProps & {
  folderId?: number | string;
  metadataClass?: string;
  attributesVisible?: boolean;
  onToggleAttributes?: (visible: boolean) => void;
};

function SettingsAction(props: SettingsActionProps) {
  const {
    folderId,
    metadataClass,
    attributesVisible = false,
    onToggleAttributes,
  } = props;

  const numericFolderId = folderId !== undefined ? Number(folderId) : undefined;
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const [open, setOpen] = useState(false);
  const [addInstanceVisible, setAddInstanceVisible] = useState(false);
  const [addInstancePending, setAddInstancePending] = useState(false);
  const [transferVisible, setTransferVisible] = useState(false);

  const uploadButtonRef = useRef<{triggerClick: () => void} | null>(null);

  const {
    entityTypes,
    currentMetadataClassId,
    pathFields,
    nonPathFields,
    invalidateAfterMutation,
    addInstance,
    deleteClass,
  } = useMetadataActions(numericFolderId, metadataClass);

  const transferPipelineId = useStringPreferenceValue('storage.transfer.pipeline.id');
  const transferPipelineVersion = useStringPreferenceValue('storage.transfer.pipeline.version');
  const transferAvailable =
    !!transferPipelineId && !!transferPipelineVersion && pathFields.length > 0;

  const handleDelete = useCallback(() => {
    if (!metadataClass || numericFolderId === undefined) return;
    Modal.confirm({
      title: `Delete class '${metadataClass}'?`,
      onOk: async () => {
        const hide = message.loading(`Removing class '${metadataClass}'...`, 0);
        try {
          await deleteClass();
        } catch (error) {
          message.error(String(error), 5);
        } finally {
          hide();
        }
      },
    });
  }, [metadataClass, numericFolderId, deleteClass]);

  const handleAddInstance = useCallback(
    async (values: Record<string, unknown>) => {
      setAddInstancePending(true);
      try {
        await addInstance(values);
        setAddInstanceVisible(false);
      } catch (error) {
        message.error(String(error), 5);
      } finally {
        setAddInstancePending(false);
      }
    },
    [addInstance],
  );

  const handleTransfer = useCallback(
    async (values: {
      destination: string;
      pathFields?: string[];
      nameField?: string;
      createFolders?: boolean;
      updatePathValues?: boolean;
      threadsCount?: string;
    }) => {
      let destination = values.destination;
      if (!destination.endsWith('/')) destination += '/';
      destination = destination.toLowerCase();

      const storages = await queryClient.fetchQuery(storagesQueryOptions());
      const matchingStorage = storages.find((s) => {
        let mask = (s.pathMask ?? '').toLowerCase();
        if (!mask.endsWith('/')) mask += '/';
        return destination === mask || destination.startsWith(mask);
      });

      const getParam = (value: string | boolean | undefined, type = 'string', required = true) => ({
        value: String(value ?? ''),
        type,
        required,
      });

      const hide = message.loading('Starting transfer job...', 0);
      try {
        await launchRun({
          cloudRegionId: matchingStorage?.regionId,
          pipelineId: Number(transferPipelineId),
          version: transferPipelineVersion,
          force: true,
          params: {
            DESTINATION_DIRECTORY: getParam(values.destination),
            METADATA_ID: getParam(String(numericFolderId)),
            METADATA_CLASS: getParam(metadataClass),
            METADATA_COLUMNS: getParam((values.pathFields ?? []).join(',')),
            FILE_NAME_FORMAT_COLUMN: getParam(values.nameField, 'string', false),
            CREATE_FOLDERS_FOR_COLUMNS: getParam(values.createFolders, 'boolean', false),
            UPDATE_PATH_VALUES: getParam(values.updatePathValues, 'boolean', false),
            ...(values.threadsCount
              ? {MAX_THREADS_COUNT: getParam(values.threadsCount, 'string', false)}
              : {}),
          },
        });
        setTransferVisible(false);
        navigate(SessionStorageWrapper.getActiveRunsLink());
      } catch (error) {
        message.error(String(error), 5);
      } finally {
        hide();
      }
    },
    [
      queryClient,
      transferPipelineId,
      transferPipelineVersion,
      numericFolderId,
      metadataClass,
      navigate,
    ],
  );

  const onClick = useCallback(
    ({key}: {key: string}) => {
      setOpen(false);
      switch (key) {
        case 'add-metadata':
          setAddInstanceVisible(true);
          break;
        case 'upload':
          uploadButtonRef.current?.triggerClick();
          break;
        case 'transfer':
          setTransferVisible(true);
          break;
        case 'delete':
          handleDelete();
          break;
        case 'show-attributes':
          onToggleAttributes?.(!attributesVisible);
          break;
        default:
          break;
      }
    },
    [attributesVisible, onToggleAttributes, handleDelete],
  );

  const items = [
    {
      key: 'add-metadata',
      label: (
        <span>
          <PlusOutlined style={{marginRight: 5}} />
          Add instance
        </span>
      ),
    },
    {
      key: 'upload',
      label: (
        <span>
          <UploadOutlined style={{marginRight: 5}} />
          Upload metadata
        </span>
      ),
    },
    ...(transferAvailable
      ? [
          {
            key: 'transfer',
            label: (
              <span>
                <CloudUploadOutlined style={{marginRight: 5}} />
                Transfer to the cloud
              </span>
            ),
          },
          {type: 'divider' as const, key: 'divider-1'},
        ]
      : []),
    {
      key: 'delete',
      label: (
        <span className="cp-danger">
          <DeleteOutlined style={{marginRight: 5}} />
          Delete class
        </span>
      ),
    },
    {type: 'divider' as const, key: 'divider-2'},
    {
      key: 'show-attributes',
      label: attributesVisible ? 'Hide attributes' : 'Show attributes',
    },
  ];

  return (
    <>
      <Dropdown trigger={['click']} open={open} onOpenChange={setOpen} menu={{items, onClick}}>
        <Button id="metadata-actions-button" size="small">
          <SettingOutlined />
        </Button>
      </Dropdown>
      <AddInstanceForm
        folderId={numericFolderId}
        visible={addInstanceVisible}
        pending={addInstancePending}
        onCreate={handleAddInstance}
        onCancel={() => setAddInstanceVisible(false)}
        entityType={currentMetadataClassId}
        entityTypes={entityTypes}
      />
      {numericFolderId !== undefined && (
        <UploadButton
          multiple={false}
          synchronous
          style={{display: 'none'}}
          title="Upload metadata"
          action={MetadataEntityUpload.uploadUrl(numericFolderId)}
          onInitialized={(component: {triggerClick: () => void}) => {
            uploadButtonRef.current = component;
          }}
          onRefresh={invalidateAfterMutation}
        />
      )}
      <UploadToDatastorageForm
        visible={transferVisible}
        fields={nonPathFields}
        pathFields={pathFields}
        onTransfer={handleTransfer}
        onClose={() => setTransferVisible(false)}
      />
    </>
  );
}

export {SettingsAction};
