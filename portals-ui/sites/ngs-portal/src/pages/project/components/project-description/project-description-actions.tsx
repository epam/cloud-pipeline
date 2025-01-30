import { PencilIcon, EyeIcon, CheckIcon } from '@heroicons/react/24/outline';
import { Button, Tooltip, Spin } from 'antd';

type Props = {
  isEditMode: boolean;
  isPreviewMode: boolean;
  isLoading: boolean;
  isDescriptionEmpty: boolean;
  onTogglePreview: () => void;
  onSave: () => void;
  onCancel: () => void;
  onEdit: () => void;
};

export const DescriptionActions = ({
  isEditMode,
  isPreviewMode,
  isLoading,
  isDescriptionEmpty,
  onTogglePreview,
  onSave,
  onCancel,
  onEdit,
}: Props) => {
  if (isEditMode) {
    return (
      <div className="flex items-center gap-2">
        <Button
          onClick={onTogglePreview}
          className="px-2 py-1"
          disabled={isLoading}>
          <div className="flex items-center">
            {isPreviewMode ? (
              <PencilIcon className="w-4 h-4" />
            ) : (
              <EyeIcon className="w-4 h-4" />
            )}
            <span className="ml-1.5">{isPreviewMode ? 'Edit' : 'Preview'}</span>
          </div>
        </Button>

        <Tooltip
          title={isDescriptionEmpty ? 'Please provide a description' : ''}>
          <Button
            type="primary"
            onClick={onSave}
            className="px-2 py-1"
            disabled={isDescriptionEmpty || isLoading}>
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
          onClick={onCancel}
          type="text"
          disabled={isLoading}
          className="px-2 py-1">
          Cancel
        </Button>
      </div>
    );
  }

  return (
    <Button onClick={onEdit} className="px-2 py-1">
      <div className="flex items-center">
        <PencilIcon className="w-4 h-4" />
        <span className="ml-1.5">Edit</span>
      </div>
    </Button>
  );
};
