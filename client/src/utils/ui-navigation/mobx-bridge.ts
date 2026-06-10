import {action, computed, makeObservable, observable, runInAction} from 'mobx';
import {navigationPages} from '../../routing/paths.ts';
import {
  getActiveNavigationKey,
  getHomePath,
  getNavigationItems,
  isPageUnavailable,
  isSearchEnabled,
  matchNavigationItemByPath,
  shouldRedirectFromUnavailablePage,
} from '../../stores/ui-navigation/selectors.ts';
import {
  getLaunchFormUtils,
  loadUiNavigation,
  uiNavigationStore,
} from '../../stores/ui-navigation/ui-navigation-store.ts';

/**
 * MobX-compatible facade for legacy components still injected via Root.jsx.
 * New code should use hooks from `stores/ui-navigation` directly.
 */
class UINavigationMobXBridge {
  userPages?: string[];
  dashboard?: unknown;
  homePage?: string;
  searchDocumentTypes?: string[];
  supportTemplate?: string;
  launchForm?: Record<string, Record<string, unknown>>;
  runLogsMainTask?: boolean;
  _libraryExpanded?: boolean;
  _loaded = false;
  _aiChatBotAvailable = false;

  private readonly unsubscribe: () => void;

  private syncFromStore(state: ReturnType<typeof uiNavigationStore.getState>): void {
    runInAction(() => {
      this.userPages = state.userPages;
      this.dashboard = state.dashboard;
      this.homePage = state.homePage;
      this.searchDocumentTypes = state.searchDocumentTypes;
      this.supportTemplate = state.supportTemplate;
      this.launchForm = state.launchForm;
      this.runLogsMainTask = state.runLogsMainTask;
      this._libraryExpanded = state.libraryExpanded;
      this._loaded = state.loaded;
      this._aiChatBotAvailable = state.aiChatBotAvailable;
    });
  }

  constructor(_authenticatedUserInfo?: unknown, _preferences?: unknown) {
    makeObservable(this, {
      userPages: observable,
      dashboard: observable,
      homePage: observable,
      searchDocumentTypes: observable,
      supportTemplate: observable,
      launchForm: observable,
      runLogsMainTask: observable,
      _libraryExpanded: observable,
      _loaded: observable,
      _aiChatBotAvailable: observable,
      aiChatBotAvailable: computed,
      availablePages: computed,
      navigationItems: computed,
      loaded: computed,
      home: computed,
      libraryExpanded: computed,
      utils: computed,
      fetch: action,
    });

    this.unsubscribe = uiNavigationStore.subscribe((state) => this.syncFromStore(state));
    this.syncFromStore(uiNavigationStore.getState());

    void loadUiNavigation();
  }

  get aiChatBotAvailable() {
    return this._aiChatBotAvailable;
  }

  get availablePages() {
    if (!this._loaded) {
      return new Set<string>();
    }
    const allPages = Object.values(navigationPages);
    let pages = [...new Set((this.userPages ?? allPages).map((page) => page.toLowerCase()))];
    if (!this._aiChatBotAvailable) {
      pages = pages.filter((page) => page !== navigationPages.chat);
    }
    return new Set(pages);
  }

  get navigationItems() {
    return getNavigationItems(
      {
        loaded: this._loaded,
        pending: false,
        userPages: this.userPages,
        dashboard: this.dashboard,
        homePage: this.homePage,
        searchDocumentTypes: this.searchDocumentTypes,
        libraryExpanded: this._libraryExpanded,
        launchForm: this.launchForm,
        runLogsMainTask: this.runLogsMainTask,
        supportTemplate: this.supportTemplate,
      },
      this._aiChatBotAvailable,
    );
  }

  get loaded() {
    return this._loaded;
  }

  get home() {
    return getHomePath(
      {
        loaded: this._loaded,
        pending: false,
        userPages: this.userPages,
        homePage: this.homePage,
      },
      this._aiChatBotAvailable,
    );
  }

  get libraryExpanded() {
    return this._libraryExpanded ?? true;
  }

  set libraryExpanded(value: boolean) {
    uiNavigationStore.getState().setLibraryExpanded(value);
  }

  get utils() {
    return getLaunchFormUtils();
  }

  static testPage(router: {location: {pathname: string}}) {
    return matchNavigationItemByPath(router.location.pathname);
  }

  fetch() {
    return loadUiNavigation(true);
  }

  fetchDashboard() {
    return loadUiNavigation().then(() => this.dashboard);
  }

  fetchSearchDocumentTypes() {
    return loadUiNavigation().then(() => this.searchDocumentTypes);
  }

  pageIsUnavailable(pageKey: string) {
    return isPageUnavailable(
      pageKey,
      {
        loaded: this._loaded,
        pending: false,
        userPages: this.userPages,
      },
      this._aiChatBotAvailable,
    );
  }

  searchEnabled() {
    return isSearchEnabled(
      {
        loaded: this._loaded,
        pending: false,
        userPages: this.userPages,
      },
      this._aiChatBotAvailable,
    );
  }

  getActivePage(router: {location: {pathname: string}}) {
    return getActiveNavigationKey(
      router.location.pathname,
      {
        loaded: this._loaded,
        pending: false,
        userPages: this.userPages,
      },
      this._aiChatBotAvailable,
    );
  }

  redirectIfPageIsUnavailable(router: {
    location: {pathname: string};
    push: (path: string) => void;
  }) {
    if (
      shouldRedirectFromUnavailablePage(
        router.location.pathname,
        this.home,
        {
          loaded: this._loaded,
          pending: false,
          userPages: this.userPages,
        },
        this._aiChatBotAvailable,
      )
    ) {
      router.push(this.home);
    }
  }
}

export default UINavigationMobXBridge;
