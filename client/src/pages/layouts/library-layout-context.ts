import {useOutletContext} from 'react-router-dom';
import {
  LibraryActionsStore,
  LibraryMenuActions,
} from '../../components/library/library-actions/library-actions-store.ts';
import {useEffect} from 'react';

type LibraryLayoutOutletContext = {
  actionsStore: LibraryActionsStore;
};

function useLibraryLayoutOutletContext() {
  return useOutletContext<LibraryLayoutOutletContext>();
}

function useLibraryMenuActions(init: () => LibraryMenuActions, deps?: unknown[]) {
  const {actionsStore} = useOutletContext<LibraryLayoutOutletContext>();
  const {configureMenuActions} = actionsStore;
  return useEffect(() => configureMenuActions(init()), [configureMenuActions, ...(deps ?? [])]);
}

export {useLibraryLayoutOutletContext, useLibraryMenuActions};
export type {LibraryLayoutOutletContext};
