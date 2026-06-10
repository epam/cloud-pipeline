import type {ComponentType, CSSProperties} from 'react';
import type {NavigationPage} from '../../routing/paths.ts';

export type UiNavigationPageKey =
  | NavigationPage
  | 'logout'
  | 'stop-impersonation'
  | 'divider'
  | 'launch';

export type LaunchFormSettings = Record<string, Record<string, unknown>>;

export type EstimatedPriceVisibility = {
  logs: boolean;
  pipelines: boolean;
  tools: boolean;
};

export type OptionalParametersFilterVisibility = {
  pipelines: boolean;
  tools: boolean;
};

export type UiNavigationAttributes = {
  pages?: string[];
  dashboard?: unknown;
  homePage?: string;
  searchDocumentTypes?: string[];
  libraryExpanded?: boolean;
  launchForm?: LaunchFormSettings;
  runLogsMainTask?: boolean;
};

export type UiNavigationState = {
  loaded: boolean;
  pending: boolean;
  error?: string;
  userPages?: string[];
  dashboard?: unknown;
  homePage?: string;
  searchDocumentTypes?: string[];
  libraryExpanded?: boolean;
  launchForm?: LaunchFormSettings;
  runLogsMainTask?: boolean;
  supportTemplate?: string;
};

export type NavigationItemContext = {
  impersonation?: {
    isImpersonated?: boolean;
    impersonatedUserName?: string;
    stopImpersonation?: () => void;
  };
};

export type NavigationItem = {
  key: UiNavigationPageKey | string;
  title?: string | ((context: NavigationItemContext) => string | undefined);
  icon?: ComponentType<{style?: CSSProperties}>;
  iconStyle?: CSSProperties;
  path?: string;
  keys?: string[];
  isDefault?: boolean;
  isLink?: boolean;
  isDivider?: boolean;
  static?: boolean;
  hidden?: boolean;
  visible?: boolean | ((context: NavigationItemContext) => boolean);
  action?: (context: NavigationItemContext) => void;
};

export type UiNavigationStore = UiNavigationState & {
  load: (force?: boolean) => Promise<void>;
  setLibraryExpanded: (value: boolean) => void;
  reset: () => void;
};
