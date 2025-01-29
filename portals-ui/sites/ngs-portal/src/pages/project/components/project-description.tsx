import { Markdown } from '@cloud-pipeline/components';
import { Button, Input, Spin, Tooltip } from 'antd';
import { PencilIcon, CheckIcon } from '@heroicons/react/24/outline';
import { useState } from 'react';

const { TextArea } = Input;

type Props = {
  onSave: (description?: string) => Promise<void>;
  description?: string;
};

const DEFAULT_DESCRIPTION = `## This project does not have a description. 
#### Start by adding one.`;

export const ProjectDescription = ({ description, onSave }: Props) => {
  const [descriptionValue, setDescriptionValue] = useState(description);
  const [isEditMode, setIsEditMode] = useState(false);
  const [isLoading, setIsLoading] = useState(false);

  const handleCancel = () => {
    setIsEditMode(false);
  };

  const enterEditMode = () => {
    setDescriptionValue(description);
    setIsEditMode(true);
  };

  const handleSave = async () => {
    if (descriptionValue === description) {
      setIsEditMode(false);
      return;
    }

    setIsLoading(true);

    try {
      await onSave(descriptionValue);
    } catch {
      console.error(`Error saving project description`);
    } finally {
      setIsLoading(false);
      setIsEditMode(false);
    }
  };

  const handleDescriptionChange = (
    e: React.ChangeEvent<HTMLTextAreaElement>,
  ) => {
    setDescriptionValue(e.target.value);
  };

  const renderActions = () => {
    if (isEditMode) {
      const isDescriptionValueEmpty = !descriptionValue?.trim();

      return (
        <div className="flex items-center">
          <Tooltip
            title={isDescriptionValueEmpty ? 'Please provide description' : ''}>
            <Button
              onClick={() => void handleSave()}
              className="px-2 py-1"
              disabled={isDescriptionValueEmpty || isLoading}>
              <div className="flex items-center">
                {isLoading ? (
                  <Spin size="small" />
                ) : (
                  <CheckIcon className="w-4 h-4" />
                )}
                <span className="ml-1.5">Save</span>
              </div>
            </Button>
          </Tooltip>

          <Button
            onClick={handleCancel}
            type="text"
            disabled={isLoading}
            className="ml-2 px-2 py-1">
            Cancel
          </Button>
        </div>
      );
    }

    return (
      <Button onClick={enterEditMode} className="px-2 py-1">
        <div className="flex items-center">
          <PencilIcon className="w-4 h-4" />
          <span className="ml-1.5">Edit</span>
        </div>
      </Button>
    );
  };

  return (
    <div className="flex flex-col h-full">
      <div className="flex items-center justify-between">
        <b className="text-base">Description</b>

        {renderActions()}
      </div>

      <div className="flex-grow flex flex-col mt-4">
        {isEditMode ? (
          <TextArea
            className="w-full flex-grow resize-none"
            value={descriptionValue}
            onChange={handleDescriptionChange}
            autoFocus
          />
        ) : (
          <Markdown>{description ?? DEFAULT_DESCRIPTION}</Markdown>
        )}
      </div>
    </div>
  );
};
