import {navigationPages} from '../../routing/paths.ts';
import {navigationItems} from './navigation-items.ts';
import {NavigationItem, UiNavigationState} from './types.ts';

export function getFirstPathSegment(pathname: string): string {
  return (pathname || '').split('/').filter(Boolean).shift() ?? '';
}

export function matchNavigationItemByPath(pathname: string): NavigationItem | undefined {
  const segment = getFirstPathSegment(pathname);
  const pathRegExp = Number.isNaN(Number(segment)) ? new RegExp(`^${segment}$`, 'i') : /^library$/i;
  return navigationItems.find((item) =>
    (item.keys ?? [item.key]).find((key) => pathRegExp.test(key)),
  );
}

export function getAvailablePageKeys(
  state: UiNavigationState,
  aiChatBotAvailable: boolean,
): Set<string> {
  if (!state.loaded) {
    return new Set();
  }
  const allPages = Object.values(navigationPages);
  let pages = [...new Set((state.userPages ?? allPages).map((page) => page.toLowerCase()))];
  if (!aiChatBotAvailable) {
    pages = pages.filter((page) => page !== navigationPages.chat);
  }
  return new Set(pages);
}

export function getNavigationItems(
  state: UiNavigationState,
  aiChatBotAvailable: boolean,
): NavigationItem[] {
  if (!state.loaded) {
    return [];
  }
  const availablePages = getAvailablePageKeys(state, aiChatBotAvailable);
  return navigationItems.filter((item) => item.static || availablePages.has(item.key));
}

export function getHomePath(state: UiNavigationState, aiChatBotAvailable: boolean): string {
  const homePageKey = state.homePage || navigationPages.dashboard;
  const items = getNavigationItems(state, aiChatBotAvailable);
  const available = items.find((item) => item.key === homePageKey);
  if (available?.path) {
    return available.path;
  }
  return homePageKey;
}

export function getActiveNavigationKey(
  pathname: string,
  state: UiNavigationState,
  aiChatBotAvailable: boolean,
): string | undefined {
  const matched = matchNavigationItemByPath(pathname);
  if (!matched) {
    return undefined;
  }
  const items = getNavigationItems(state, aiChatBotAvailable);
  return items.find((item) => item.key === matched.key)?.key;
}

export function isPageUnavailable(
  pageKey: string,
  state: UiNavigationState,
  aiChatBotAvailable: boolean,
): boolean {
  return !getNavigationItems(state, aiChatBotAvailable).some((item) => item.key === pageKey);
}

export function isSearchEnabled(state: UiNavigationState, aiChatBotAvailable: boolean): boolean {
  return !isPageUnavailable(navigationPages.search, state, aiChatBotAvailable);
}

export function shouldRedirectFromUnavailablePage(
  pathname: string,
  homePath: string,
  state: UiNavigationState,
  aiChatBotAvailable: boolean,
): boolean {
  if (!state.loaded) {
    return false;
  }
  const matched = matchNavigationItemByPath(pathname);
  const activeKey = getActiveNavigationKey(pathname, state, aiChatBotAvailable);
  return Boolean(matched && !activeKey && pathname !== homePath);
}
