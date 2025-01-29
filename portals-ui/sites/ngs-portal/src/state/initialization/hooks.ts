import { useLoadableStore } from '../common/loadable-store/hooks.ts';
import { initializationStore } from './store.ts';
import type { InitializationStore } from './types.ts';

export function useInitializationStore(): InitializationStore {
  return useLoadableStore(initializationStore);
}

export function useApplicationInitialized(): boolean {
  return useInitializationStore().data;
}
