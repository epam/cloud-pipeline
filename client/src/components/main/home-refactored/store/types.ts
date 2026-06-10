import type {Layout} from 'react-grid-layout/legacy';

export type PanelLayoutItem = Layout[number];

export type PanelContentHandle = {
  usesActiveRuns?: boolean;
  usesCompletedRuns?: boolean;
  update?: () => void;
};

export type PanelHandle = {
  update?: () => void;
  contentPanel?: PanelContentHandle;
};

export type HomeLayoutManager = {
  restoreDefaultLayout: () => void;
  setPanelsLayout: (layout: PanelLayoutItem[], rebuildHeights?: boolean) => PanelLayoutItem[];
  getPanelsLayout: (rebuildIfNull?: boolean, staticPanels?: string[]) => PanelLayoutItem[];
  addPanels: (panels: string[]) => void;
  removePanel: (panel: string) => void;
};

export type HomeRunFilter = {
  loaded: boolean;
  pending: boolean;
  value: unknown[];
  networkError?: string;
  filter: (params?: unknown) => Promise<unknown>;
};

export type HomeDataSources = {
  activeRuns: HomeRunFilter;
  completedRuns: HomeRunFilter;
  services: HomeRunFilter;
  myIssues: {
    loaded: boolean;
    pending: boolean;
    networkError?: string;
    fetch: () => Promise<unknown>;
  };
};

export type HomeStoreState = {
  layoutLoaded: boolean;
  layoutError?: string;
  layoutManager?: HomeLayoutManager;
  panelsLayout: PanelLayoutItem[];
  layoutVersion: number;
  configureModalVisible: boolean;
  containerWidth: number | null;
  containerHeight: number | null;
  dataSources?: HomeDataSources;
  panelHandles: Record<string, PanelHandle | null>;
};

export type HomeStoreActions = {
  initializeLayout: () => Promise<void>;
  initializeDataSources: (userName: string) => void;
  setConfigureModalVisible: (visible: boolean) => void;
  setContainerDimensions: (width: number, height: number) => void;
  syncPanelsLayout: () => void;
  onLayoutChanged: (layout: PanelLayoutItem[], update?: boolean) => void;
  removePanel: (panelKey: string) => void;
  addPanels: (panelKeys: string[]) => void;
  restoreDefaultLayout: () => void;
  registerPanel: (panelKey: string, handle: PanelHandle | null) => void;
  refreshAll: () => void;
  refreshActiveRuns: () => Promise<void>;
  refreshCompletedRuns: () => Promise<void>;
  refreshServices: () => Promise<void>;
  refreshIssues: () => Promise<void>;
  updateAllPanels: () => void;
  reset: () => void;
};

export type HomeStore = HomeStoreState & HomeStoreActions;
