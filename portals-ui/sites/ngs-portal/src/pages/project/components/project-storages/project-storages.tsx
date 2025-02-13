import { StorageBrowser } from '../../../../widgets/storage-browser';

const STORAGE_ID_MOCK = 7673;

export function ProjectStorages() {
  return (
    <div className="h-full flex flec-col overflow-hidden">
      <StorageBrowser storageId={STORAGE_ID_MOCK} showHeaderControls className="flex-1 overflow-auto" />
    </div>
  );
}
