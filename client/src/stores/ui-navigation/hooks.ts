import {useMemo} from 'react';
import {useStore} from 'zustand';
import {navigationPages} from '../../routing/paths.ts';
import {
  getActiveNavigationKey,
  getAvailablePageKeys,
  getHomePath,
  getNavigationItems,
  isPageUnavailable,
  isSearchEnabled,
  matchNavigationItemByPath,
  shouldRedirectFromUnavailablePage,
} from './selectors.ts';
import {NavigationItemContext} from './types.ts';
import {getLaunchFormUtils, loadUiNavigation, uiNavigationStore} from './ui-navigation-store.ts';

export function useUiNavigationStore() {
  return useStore(uiNavigationStore);
}

export function useUiNavigationLoaded(): boolean {
  return useStore(uiNavigationStore, (state) => state.loaded);
}

export function useUiNavigationPending(): boolean {
  return useStore(uiNavigationStore, (state) => state.pending);
}

export function useNavigationItems(
  context: NavigationItemContext = {},
): ReturnType<typeof getNavigationItems> {
  const state = useUiNavigationStore();
  return useMemo(
    () => getNavigationItems(state, state.aiChatBotAvailable).filter((item) => !item.hidden),
    [state, state.loaded, state.userPages, state.aiChatBotAvailable],
  );
}

export function useHomePath(): string {
  const state = useUiNavigationStore();
  return useMemo(
    () => getHomePath(state, state.aiChatBotAvailable),
    [state, state.loaded, state.homePage, state.userPages, state.aiChatBotAvailable],
  );
}

export function useLibraryExpanded(): [boolean, (value: boolean) => void] {
  const libraryExpanded = useStore(uiNavigationStore, (state) => state.libraryExpanded ?? true);
  const setLibraryExpanded = useStore(uiNavigationStore, (state) => state.setLibraryExpanded);
  return [libraryExpanded, setLibraryExpanded];
}

export function useActiveNavigationKey(pathname: string): string | undefined {
  const state = useUiNavigationStore();
  return useMemo(
    () => getActiveNavigationKey(pathname, state, state.aiChatBotAvailable),
    [pathname, state, state.loaded, state.userPages, state.aiChatBotAvailable],
  );
}

export function useSearchEnabled(): boolean {
  const state = useUiNavigationStore();
  return useMemo(
    () => isSearchEnabled(state, state.aiChatBotAvailable),
    [state, state.loaded, state.userPages, state.aiChatBotAvailable],
  );
}

export function usePageIsUnavailable(pageKey: string): boolean {
  const state = useUiNavigationStore();
  return useMemo(
    () => isPageUnavailable(pageKey, state, state.aiChatBotAvailable),
    [pageKey, state, state.loaded, state.userPages, state.aiChatBotAvailable],
  );
}

export function useShouldRedirectFromUnavailablePage(pathname: string, homePath: string): boolean {
  const state = useUiNavigationStore();
  return useMemo(
    () => shouldRedirectFromUnavailablePage(pathname, homePath, state, state.aiChatBotAvailable),
    [pathname, homePath, state, state.loaded, state.userPages, state.aiChatBotAvailable],
  );
}

export function useDashboardConfiguration(): unknown {
  return useStore(uiNavigationStore, (state) => state.dashboard);
}

export function useSearchDocumentTypes(): string[] | undefined {
  return useStore(uiNavigationStore, (state) => state.searchDocumentTypes);
}

export function useRunLogsMainTask(): boolean | undefined {
  return useStore(uiNavigationStore, (state) => state.runLogsMainTask);
}

export function useSupportTemplate(): string | undefined {
  return useStore(uiNavigationStore, (state) => state.supportTemplate);
}

export function useLaunchFormUtils() {
  const launchForm = useStore(uiNavigationStore, (state) => state.launchForm);
  return useMemo(() => getLaunchFormUtils(), [launchForm]);
}

export {loadUiNavigation, matchNavigationItemByPath, getAvailablePageKeys};
