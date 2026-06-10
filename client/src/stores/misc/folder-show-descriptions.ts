import {createLocalStorageStore} from '../base/local-storage-store.ts';
import {useStore} from 'zustand';
import {useMemo} from 'react';

type FolderShowDescriptionsState = {
  showDescriptions: boolean;
};

type FolderShowDescriptionsActions = {
  setShowDescriptions: (showDescriptions: boolean) => void;
};

export const folderShowDescriptions = createLocalStorageStore<
  FolderShowDescriptionsState,
  FolderShowDescriptionsActions
>(
  (set) => ({
    showDescriptions: true,
    setShowDescriptions: (showDescriptions: boolean) => {
      set({showDescriptions});
    },
  }),
  {
    localStorageKey: 'show_description',
    defaultValue: {showDescriptions: true},
    mapper(o: unknown): Partial<FolderShowDescriptionsState> | undefined {
      if (o === undefined || o === null) {
        return undefined;
      }
      try {
        const parsed =
          typeof o === 'string' ? JSON.parse(o) : typeof o === 'object' ? o : undefined;
        if (parsed) {
          const {value, showDescriptions = value} = parsed as Record<string, unknown>;
          if (typeof showDescriptions === 'boolean') {
            return {
              showDescriptions,
            };
          }
        }
      } catch {
        // noop
      }
      return undefined;
    },
  },
);

export function useFolderShowDescriptions(): [boolean, (o: boolean) => void] {
  const {showDescriptions, setShowDescriptions} = useStore(folderShowDescriptions);
  return useMemo(
    () => [showDescriptions, setShowDescriptions],
    [showDescriptions, setShowDescriptions],
  );
}
