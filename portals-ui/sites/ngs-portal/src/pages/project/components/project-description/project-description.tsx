import { Markdown } from '@cloud-pipeline/components';
import { Input } from 'antd';
import { useState } from 'react';
import { PlaceholderText } from '../../../../shared/ui';
import { DescriptionActions } from './project-description-actions';

const { TextArea } = Input;

type Props = {
  onSave: (description?: string) => Promise<void>;
  description?: string;
};

export const ProjectDescription = ({ description, onSave }: Props) => {
  const [descriptionValue, setDescriptionValue] = useState(description);
  const [isEditMode, setIsEditMode] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [isPreviewMode, setIsPreviewMode] = useState(false);

  const leaveEditMode = () => {
    setIsEditMode(false);
    setIsPreviewMode(false);
  };

  const togglePreviewMode = () => {
    setIsPreviewMode((prev) => !prev);
  };

  const handleCancel = () => {
    leaveEditMode();
  };

  const enterEditMode = () => {
    setDescriptionValue(description);
    setIsEditMode(true);
  };

  const handleSave = async () => {
    if (descriptionValue === description) {
      leaveEditMode();
      return;
    }

    setIsLoading(true);

    try {
      await onSave(descriptionValue);
    } catch {
      console.error(`Error saving project description`);
    } finally {
      setIsLoading(false);
      leaveEditMode();
    }
  };

  const handleDescriptionChange = (
    e: React.ChangeEvent<HTMLTextAreaElement>,
  ) => {
    setDescriptionValue(e.target.value);
  };

  const renderDescription = () => {
    if (isEditMode) {
      if (isPreviewMode) {
        return <Markdown>{descriptionValue}</Markdown>;
      }

      return (
        <TextArea
          className="w-full flex-grow resize-none"
          value={descriptionValue}
          onChange={handleDescriptionChange}
          autoFocus
        />
      );
    }

    if (description) {
      return <Markdown>{description}</Markdown>;
    }

    return <PlaceholderText>No description provided</PlaceholderText>;
  };

  return (
    <div className="flex flex-col h-full">
      <div className="flex items-center justify-between">
        <b className="text-base">Description</b>

        <DescriptionActions
          isDescriptionEmpty={!descriptionValue?.trim()}
          isEditMode={isEditMode}
          isPreviewMode={isPreviewMode}
          isLoading={isLoading}
          onTogglePreview={togglePreviewMode}
          onSave={() => void handleSave()}
          onCancel={handleCancel}
          onEdit={enterEditMode}
        />
      </div>

      <div className="flex-grow flex flex-col mt-4">{renderDescription()}</div>
    </div>
  );
};
