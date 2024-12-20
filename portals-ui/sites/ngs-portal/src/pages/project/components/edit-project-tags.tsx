import type { ChangeEvent } from 'react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Button, Input, message, Modal } from 'antd';
import classNames from 'classnames';
import { PencilIcon, TrashIcon } from '@heroicons/react/24/outline';
import type { CommonProps } from '@cloud-pipeline/components';
import { noop, type NgsData, type Project } from '@cloud-pipeline/core';
import type { MappedTag } from '../../../shared/tags';
import { extractTags } from '../../../shared/tags';
import { PlusIcon } from '@heroicons/react/24/solid';
import { updateProjectMetadata } from '@cloud-pipeline/api';
import { loadProjects } from '../../../state/projects/load-projects';

type ButtonProps = CommonProps & {
  project: Project;
};

type ModalProps = CommonProps & {
  project: Project;
  visible: boolean;
  onCancel: () => void;
};

enum TagStates {
  new = 'new',
  touched = 'touched',
  markAsDeleted = 'markAsDeleted',
}

type EditedTag = MappedTag & {
  tagState?: TagStates | undefined;
  new?: boolean;
};

const EditProjectTagsModal = (props: ModalProps) => {
  const { project, visible, onCancel } = props;
  const [messageApi, contextHolder] = message.useMessage();
  const [tags, setTags] = useState<EditedTag[]>([]);
  const [pending, setPending] = useState(false);
  const initialTags = useMemo(() => extractTags(project.data), [project.data]);
  useEffect(() => {
    setTags(initialTags);
  }, [initialTags]);
  const onOk = () => {
    const payload = {} as NgsData;
    tags.forEach(({ key, value, type, tagState }: EditedTag) => {
      if (tagState !== TagStates.markAsDeleted) {
        payload[key] = {
          value,
          type: type ?? 'string',
        };
      }
    });
    messageApi.open({
      key: 'updateTags',
      type: 'loading',
      content: 'Updating tags...',
    });
    setPending(true);
    updateProjectMetadata(payload, project.id)
      .then(noop)
      .catch((error) => {
        messageApi.open({
          key: 'updateTags',
          type: 'error',
          content: (
            <div className="flex flex-col items-start">
              <b>Failed to update tags</b>
              <span>
                {error instanceof Error ? error.message : String(error)}
              </span>
            </div>
          ),
          duration: 2,
        });
      })
      .finally(() => {
        loadProjects()
          .then(noop)
          .catch(noop)
          .finally(() => {
            messageApi.open({
              key: 'updateTags',
              type: 'success',
              content: 'Tags successfully updated!',
              duration: 2,
            });
            setPending(false);
            onCancel();
          });
      });
  };
  const resetState = () => {};
  const onChangeTag = useCallback(
    (tag: EditedTag, field: 'key' | 'value') =>
      (event: ChangeEvent<HTMLInputElement>) => {
        const idx = tags.findIndex((t) => t === tag);
        if (idx >= 0) {
          const isTouched = initialTags[idx]?.[field] !== event.target.value;
          const updated = tags.slice();
          updated.splice(idx, 1, {
            ...tag,
            [field]: event.target.value,
            tagState: isTouched ? TagStates.touched : tag.tagState,
          });
          setTags(updated);
        }
      },
    [initialTags, tags],
  );
  const markAsDeleted = (tag: EditedTag) => {
    let updated;
    if (tag.new) {
      updated = tags.filter((t) => t !== tag);
    } else {
      const idx = tags.findIndex((t) => t === tag);
      updated = tags.slice();
      updated.splice(idx, 1, {
        ...tag,
        tagState: TagStates.markAsDeleted,
      });
    }
    setTags(updated);
  };
  const addTag = () => {
    setTags([
      ...tags,
      {
        key: '',
        value: '',
        new: true,
      } as EditedTag,
    ]);
  };
  const formChanged = tags.some(
    (tag) => !!Object.values(TagStates).some((state) => tag.tagState === state),
  );
  return (
    <Modal
      title={`Edit ${project.name} tags`}
      open={visible}
      onOk={() => void onOk()}
      onCancel={onCancel}
      okButtonProps={{ disabled: !formChanged || pending }}
      confirmLoading={pending}
      okText="Save"
      width={'70vw'}
      style={{ maxWidth: 740 }}
      afterClose={resetState}
      centered>
      {contextHolder}
      <div className="flex flex-col gap-2 py-4">
        {tags.map((tag, index) => (
          <div
            key={index}
            className={classNames(
              { hidden: tag.tagState === TagStates.markAsDeleted },
              'flex flex-nowrap gap-1 items-center',
            )}>
            <div className="flex flex-nowrap grow gap-1 items-center">
              Key:{' '}
              <Input
                onChange={onChangeTag(tag, 'key')}
                value={tag.key}
                size="small"
                disabled={tag.tagState === TagStates.markAsDeleted}
              />
            </div>
            <div className="ml-1 flex flex-nowrap grow gap-1 items-center">
              Text:{' '}
              <Input
                onChange={onChangeTag(tag, 'value')}
                value={tag.value}
                size="small"
                disabled={tag.tagState === TagStates.markAsDeleted}
              />
            </div>
            <Button
              onClick={() => markAsDeleted(tag)}
              size="small"
              color="danger"
              variant="solid">
              <TrashIcon className="w-4 h-4" />
            </Button>
          </div>
        ))}
      </div>
      <Button size="small" onClick={addTag}>
        <PlusIcon className="h-4 w-4" /> Add tag
      </Button>
    </Modal>
  );
};

const EditProjectTagsButton = (props: ButtonProps) => {
  const { className, project } = props;
  const [visible, setVisible] = useState(false);
  const showModal = useCallback(() => {
    setVisible(true);
  }, []);
  const hideModal = useCallback(() => {
    setVisible(false);
  }, []);
  return (
    <>
      <span
        className={classNames(
          className,
          'cursor-pointer flex flex-nowrap items-center gap-1 text-xs text-link',
        )}
        onClick={showModal}>
        <PencilIcon className="w-4 h-4" />
        <span>Edit Tags</span>
      </span>
      <EditProjectTagsModal
        project={project}
        onCancel={hideModal}
        visible={visible}
      />
    </>
  );
};

export { EditProjectTagsButton, EditProjectTagsModal };
