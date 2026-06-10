import {createStore} from 'zustand';
import {getAuthenticatedUser} from '../users/hooks.ts';
import {LIBRARY_EXPANDED_STORAGE_KEY} from './constants.ts';
import {
  fetchNavigationAttributes,
  fetchSupportTemplate,
  isAiChatBotAvailable,
} from './fetch-navigation-config.ts';
import {estimatedPriceVisible, showOptionalParametersFilter} from './launch-form-utils.ts';
import {UiNavigationStore} from './types.ts';

function readLibraryExpandedFromStorage(): boolean | undefined {
  try {
    const storageValue = JSON.parse(localStorage.getItem(LIBRARY_EXPANDED_STORAGE_KEY) ?? 'null');
    if (typeof storageValue === 'boolean') {
      return storageValue;
    }
  } catch {
    /* empty */
  }
  return undefined;
}

const initialState = {
  loaded: false,
  pending: false,
  error: undefined,
  userPages: undefined,
  dashboard: undefined,
  homePage: undefined,
  searchDocumentTypes: undefined,
  libraryExpanded: readLibraryExpandedFromStorage(),
  launchForm: undefined,
  runLogsMainTask: undefined,
  supportTemplate: undefined,
  aiChatBotAvailable: false,
};

const uiNavigationStore = createStore<UiNavigationStore & {aiChatBotAvailable: boolean}>(
  (set, get) => ({
    ...initialState,
    reset() {
      set({...initialState, libraryExpanded: readLibraryExpandedFromStorage()});
    },
    setLibraryExpanded(value: boolean) {
      set({libraryExpanded: value});
      localStorage.setItem(LIBRARY_EXPANDED_STORAGE_KEY, JSON.stringify(value));
    },
    async load(force = false) {
      const {loaded, pending} = get();
      if ((loaded && !force) || pending) {
        return;
      }
      set({pending: true, error: undefined});
      try {
        const [attributes, supportTemplate, aiChatBotAvailable] = await Promise.all([
          fetchNavigationAttributes(),
          fetchSupportTemplate(),
          isAiChatBotAvailable(),
        ]);
        const user = getAuthenticatedUser();
        const {
          pages,
          dashboard,
          homePage,
          searchDocumentTypes,
          libraryExpanded,
          launchForm,
          runLogsMainTask,
        } = attributes;

        set({
          loaded: true,
          pending: false,
          userPages: !user.admin && pages ? [...pages] : undefined,
          dashboard,
          homePage,
          searchDocumentTypes,
          launchForm,
          runLogsMainTask,
          supportTemplate,
          aiChatBotAvailable,
          libraryExpanded: libraryExpanded !== undefined ? libraryExpanded : get().libraryExpanded,
        });
      } catch (error) {
        set({
          pending: false,
          error: error instanceof Error ? error.message : 'Failed to load UI navigation',
        });
        throw error;
      }
    },
  }),
);

let loadPromise: Promise<void> | undefined;

export async function loadUiNavigation(force = false): Promise<void> {
  if (force) {
    loadPromise = undefined;
  }
  if (!loadPromise) {
    loadPromise = uiNavigationStore
      .getState()
      .load(force)
      .catch((error) => {
        loadPromise = undefined;
        throw error;
      });
  }
  return loadPromise;
}

export function getLaunchFormUtils() {
  const {launchForm} = uiNavigationStore.getState();
  return {
    estimatedPriceVisible: () => estimatedPriceVisible(launchForm),
    showOptionalParametersFilter: () => showOptionalParametersFilter(launchForm),
  };
}

export {uiNavigationStore};
