import {createLocalStorageStore} from '../base/local-storage-store.ts';
import {useStore} from 'zustand';
import {useMemo} from 'react';

export const libraryExpandedStore = createLocalStorageStore<
  {libraryExpanded: boolean},
  {setLibraryExpanded: (o: boolean) => void}
>(
  (set) => ({
    libraryExpanded: true,
    setLibraryExpanded: (libraryExpanded: boolean) => {
      set({libraryExpanded});
    },
  }),
  {
    localStorageKey: 'library_expanded',
    defaultValue: {libraryExpanded: true},
    mapper(o: unknown) {
      if (o === undefined || o === null) {
        return undefined;
      }
      try {
        const parsed =
          typeof o === 'string' ? JSON.parse(o) : typeof o === 'boolean' ? o : undefined;
        if (typeof parsed === 'boolean') {
          return {
            libraryExpanded: parsed,
          };
        }
      } catch {
        // noop
      }
      return undefined;
    },
  },
);

export function useLibraryExpanded(): [boolean, (o: boolean) => void] {
  const {libraryExpanded, setLibraryExpanded} = useStore(libraryExpandedStore);
  return useMemo(
    () => [libraryExpanded, setLibraryExpanded],
    [libraryExpanded, setLibraryExpanded],
  );
}
