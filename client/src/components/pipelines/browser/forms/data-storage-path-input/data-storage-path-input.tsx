import {useDataStoragePathInput} from './use-data-storage-path-input.ts';
import {FsPathInput} from './fs-path-input.tsx';
import {ObjectStoragePathInput} from './object-storage-path-input.tsx';
import type {DataStoragePathInputProps} from './types.ts';

function DataStoragePathInput({
  isFS = false,
  disabled,
  isNew,
  addExistingStorageFlag,
  onPressEnter,
  ...rest
}: DataStoragePathInputProps) {
  const ctrl = useDataStoragePathInput({isFS, ...rest});

  if (!isFS) {
    return (
      <ObjectStoragePathInput
        ctrl={ctrl}
        disabled={disabled}
        isNew={isNew}
        addExistingStorageFlag={addExistingStorageFlag}
        onPressEnter={onPressEnter}
      />
    );
  }

  return <FsPathInput ctrl={ctrl} disabled={disabled} onPressEnter={onPressEnter} />;
}

export {DataStoragePathInput};
