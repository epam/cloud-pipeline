import type { ChangeEvent } from 'react';
import { useMemo } from 'react';
import { useCallback, useEffect, useState } from 'react';
import { Button, Input, message, Modal } from 'antd';
import classNames from 'classnames';
import { ArrowUturnLeftIcon, TrashIcon } from '@heroicons/react/24/outline';
import type { CommonProps } from '@cloud-pipeline/components';
import { noop, type NgsData, type Project } from '@cloud-pipeline/core';
import { PlusIcon } from '@heroicons/react/24/solid';
import { updateProjectMetadata } from '@cloud-pipeline/api';
import { useReloadProjectsFn } from '../../../../state/projects/hooks.ts';
import { useProjectTags } from '../../../../shared/tags/use-project-tags.ts';
import { useMappedTags } from '../../../../shared/tags/use-mapped-tags.ts';
import { useNgsProjectSettings } from '../../../../state/settings/hooks.ts';
import { flattenStringIdentifiers } from '../../../../shared/helpers';
import type { EditedTag, ValidatedTag } from './types.ts';
import { generateTagValidation, generateUniqueTagKey } from './utilities.ts';

type EditProjectTagsModalProps = CommonProps & {
  project: Project;
  visible: boolean;
  onCancel: () => void;
};

export const EditProjectTagsModal = (props: EditProjectTagsModalProps) => {
  const { project, visible, onCancel } = props;
  const reloadProjects = useReloadProjectsFn();
  const [messageApi, contextHolder] = message.useMessage();
  const [tags, setTags] = useState<EditedTag[]>([]);
  const [pending, setPending] = useState(false);
  const projectFilteredTags = useProjectTags(project.data);
  const allTags = useMappedTags(project.data);
  const { tagsToDisplay: _tagsToDisplay, tagsToHide: _tagsToHide } =
    useNgsProjectSettings();
  // if we have `tagsToDisplay` settings set, we shall restrict user from creating anything but these tags
  const tagsToDisplay = useMemo(
    () => flattenStringIdentifiers(_tagsToDisplay),
    [_tagsToDisplay],
  );
  // We should prevent creating new tags with key in `tagsToHide`, as well as renaming tags to smth in `tagsToHide`
  const tagsToHide = useMemo(
    () => flattenStringIdentifiers(_tagsToHide),
    [_tagsToHide],
  );
  const addingAllowed = tagsToDisplay.length === 0;
  useEffect(() => {
    const current = allTags.map((tag) => ({
      ...tag,
      initialKey: tag.key,
      initialValue: tag.value,
      hidden: !projectFilteredTags.some((t) => t.key === tag.key),
      removed: false,
      isNewTag: false,
      // if we have `tagsToDisplay` settings set, we shall restrict user from creating anything but these tags
      readonlyKey: tagsToDisplay.length > 0,
      removable: tagsToDisplay.length === 0,
    }));
    for (const tagToDisplay of tagsToDisplay) {
      // if we have `tagsToDisplay` settings set, we shall restrict user from creating anything but these tags.
      // Here we're filling in missing tags from `tagsToDisplay` list
      if (
        !current.find((t) => t.key.toLowerCase() === tagToDisplay.toLowerCase())
      ) {
        current.push({
          key: tagToDisplay,
          initialKey: tagToDisplay,
          initialValue: '',
          value: '',
          type: 'string',
          hidden: false,
          isNewTag: false,
          readonlyKey: true,
          removable: false,
          removed: false,
        });
      }
    }
    setTags(current);
  }, [projectFilteredTags, allTags, tagsToDisplay]);
  const projectId = project.id;
  const onOk = useCallback(() => {
    const payload = {} as NgsData;
    tags.forEach((tag) => {
      if (!tag.removed) {
        payload[tag.key] = {
          value: tag.value,
          type: tag.type ?? 'string',
        };
      }
    });
    messageApi.open({
      key: 'updateTags',
      type: 'loading',
      content: 'Updating tags...',
    });
    setPending(true);
    updateProjectMetadata(payload, projectId)
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
        reloadProjects()
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
  }, [messageApi, onCancel, projectId, reloadProjects, tags, allTags]);
  const resetState = noop;
  const onChangeTag = useCallback(
    (tag: EditedTag, field: 'key' | 'value') =>
      (event: ChangeEvent<HTMLInputElement>) => {
        setTags((current) => {
          const idx = current.findIndex((t) => t.initialKey === tag.initialKey);
          if (idx >= 0) {
            const updated = current.slice();
            updated.splice(idx, 1, {
              ...tag,
              [field]: event.target.value,
            });
            return updated;
          }
          return current;
        });
      },
    [setTags],
  );
  const toggleDeleted = useCallback(
    (tag: EditedTag) => {
      setTags((current) => {
        const idx = current.findIndex((t) => t.initialKey === tag.initialKey);
        if (idx >= 0) {
          const updated = current.slice();
          updated.splice(
            idx,
            1,
            ...(tag.isNewTag
              ? []
              : [
                  {
                    ...tag,
                    removed: !updated[idx].removed,
                  },
                ]),
          );
          return updated;
        }
        return current;
      });
    },
    [setTags],
  );
  const addTag = useCallback(() => {
    setTags((current) =>
      current.concat([
        {
          key: '',
          initialKey: generateUniqueTagKey(),
          value: '',
          type: 'string',
          initialValue: '',
          removed: false,
          hidden: false,
          isNewTag: true,
          removable: true,
          readonlyKey: false,
        },
      ]),
    );
  }, [setTags]);
  const validation = useMemo(
    () => generateTagValidation(tagsToHide),
    [tagsToHide],
  );
  const validatedTags = useMemo<ValidatedTag[]>(
    () => tags.map(validation),
    [tags, validation],
  );
  const formChanged = tags.some(
    (tag) =>
      tag.removed ||
      tag.initialValue !== tag.value ||
      tag.initialKey !== tag.key,
  );
  const formValid = !validatedTags.some((t) => !t.hidden && t.validationError);
  return (
    <Modal
      title={`Edit ${project.name} tags`}
      open={visible}
      onOk={() => void onOk()}
      onCancel={onCancel}
      okButtonProps={{ disabled: !formChanged || !formValid || pending }}
      confirmLoading={pending}
      okText="Save"
      width={'70vw'}
      style={{ maxWidth: 740 }}
      afterClose={resetState}
      centered>
      {contextHolder}
      <div className="flex flex-col gap-2 py-4">
        {validatedTags.map((tag, index) => (
          <div key={index} className={classNames({ hidden: tag.hidden })}>
            <div className={classNames('flex flex-nowrap gap-1 items-center')}>
              <div className="flex flex-nowrap grow gap-1 items-center">
                Key:{' '}
                <Input
                  onChange={onChangeTag(tag, 'key')}
                  value={tag.key}
                  size="small"
                  disabled={tag.removed || tag.readonlyKey}
                />
              </div>
              <div className="ml-1 flex flex-nowrap grow gap-1 items-center">
                Text:{' '}
                <Input
                  onChange={onChangeTag(tag, 'value')}
                  value={tag.value}
                  size="small"
                  disabled={tag.removed}
                />
              </div>
              {tag.removable && (
                <Button
                  onClick={() => toggleDeleted(tag)}
                  size="small"
                  color={tag.removed ? 'primary' : 'danger'}
                  variant="solid">
                  {tag.removed ? (
                    <ArrowUturnLeftIcon className="w-4 h-4" />
                  ) : (
                    <TrashIcon className="w-4 h-4" />
                  )}
                </Button>
              )}
            </div>
            {tag.validationError && (
              <div className="text-xs text-red-500">{tag.validationError}</div>
            )}
          </div>
        ))}
      </div>
      {addingAllowed && (
        <Button size="small" onClick={addTag}>
          <PlusIcon className="h-4 w-4" /> Add tag
        </Button>
      )}
    </Modal>
  );
};
