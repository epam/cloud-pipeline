import {createStore} from 'zustand';
import {createHomeDataSources} from './create-data-sources.ts';
import {createHomeLayoutManager} from './layout-manager.ts';
import type {HomeStore, PanelLayoutItem} from './types.ts';

const initialState = {
  layoutLoaded: false,
  layoutError: undefined,
  layoutManager: undefined,
  panelsLayout: [] as PanelLayoutItem[],
  layoutVersion: 0,
  configureModalVisible: false,
  containerWidth: null as number | null,
  containerHeight: null as number | null,
  dataSources: undefined,
  panelHandles: {} as HomeStore['panelHandles'],
};

const homeStore = createStore<HomeStore>((set, get) => ({
  ...initialState,
  reset() {
    set({...initialState, panelHandles: {}});
  },
  async initializeLayout() {
    const {layoutLoaded, layoutManager} = get();
    if (layoutLoaded && layoutManager) {
      return;
    }
    try {
      const manager = await createHomeLayoutManager();
      const panelsLayout = manager.getPanelsLayout();
      set({
        layoutLoaded: true,
        layoutError: undefined,
        layoutManager: manager,
        panelsLayout,
        layoutVersion: get().layoutVersion + 1,
      });
    } catch (error) {
      set({
        layoutLoaded: true,
        layoutError: error instanceof Error ? error.message : 'Failed to load dashboard layout',
      });
      throw error;
    }
  },
  initializeDataSources(userName) {
    if (get().dataSources) {
      return;
    }
    set({dataSources: createHomeDataSources(userName)});
  },
  setConfigureModalVisible(visible) {
    set({configureModalVisible: visible});
  },
  setContainerDimensions(width, height) {
    set({containerWidth: width, containerHeight: height});
  },
  syncPanelsLayout() {
    const {layoutManager} = get();
    if (!layoutManager) {
      return;
    }
    set({
      panelsLayout: layoutManager.getPanelsLayout(),
      layoutVersion: get().layoutVersion + 1,
    });
  },
  onLayoutChanged(layout, update = false) {
    const {layoutManager} = get();
    if (!layoutManager) {
      return;
    }
    layoutManager.setPanelsLayout([...layout], false);
    if (update) {
      get().syncPanelsLayout();
    }
  },
  removePanel(panelKey) {
    const {layoutManager, panelHandles} = get();
    if (!layoutManager) {
      return;
    }
    layoutManager.removePanel(panelKey);
    const nextHandles = {...panelHandles};
    delete nextHandles[panelKey];
    set({
      panelHandles: nextHandles,
      panelsLayout: layoutManager.getPanelsLayout(),
      layoutVersion: get().layoutVersion + 1,
    });
  },
  addPanels(panelKeys) {
    const {layoutManager} = get();
    if (!layoutManager) {
      return;
    }
    layoutManager.addPanels(panelKeys);
    get().syncPanelsLayout();
  },
  restoreDefaultLayout() {
    const {layoutManager} = get();
    if (!layoutManager) {
      return;
    }
    layoutManager.restoreDefaultLayout();
    get().syncPanelsLayout();
  },
  registerPanel(panelKey, handle) {
    set((state) => ({
      panelHandles: {
        ...state.panelHandles,
        [panelKey]: handle,
      },
    }));
  },
  updateAllPanels() {
    const {panelHandles} = get();
    Object.values(panelHandles).forEach((handle) => {
      handle?.update?.();
    });
  },
  refreshAll() {
    get().refreshActiveRuns();
    get().refreshCompletedRuns();
    get().refreshServices();
    get().refreshIssues();
  },
  async refreshActiveRuns() {
    const {dataSources, panelHandles} = get();
    if (!dataSources) {
      return;
    }
    let shouldUpdate = false;
    for (const handle of Object.values(panelHandles)) {
      if (handle?.contentPanel?.usesActiveRuns) {
        shouldUpdate = true;
        break;
      }
    }
    if (!shouldUpdate) {
      return;
    }
    await dataSources.activeRuns.filter();
    if (dataSources.activeRuns.networkError) {
      throw new Error(dataSources.activeRuns.networkError);
    }
    get().updateAllPanels();
  },
  async refreshCompletedRuns() {
    const {dataSources, panelHandles} = get();
    if (!dataSources) {
      return;
    }
    let shouldUpdate = false;
    for (const handle of Object.values(panelHandles)) {
      if (handle?.contentPanel?.usesCompletedRuns) {
        shouldUpdate = true;
        break;
      }
    }
    if (!shouldUpdate) {
      return;
    }
    await dataSources.completedRuns.filter();
    if (dataSources.completedRuns.networkError) {
      throw new Error(dataSources.completedRuns.networkError);
    }
    get().updateAllPanels();
  },
  async refreshServices() {
    const {dataSources} = get();
    if (!dataSources) {
      return;
    }
    await dataSources.services.filter();
    if (dataSources.services.networkError) {
      throw new Error(dataSources.services.networkError);
    }
    get().updateAllPanels();
  },
  async refreshIssues() {
    const {dataSources} = get();
    if (!dataSources) {
      return;
    }
    await dataSources.myIssues.fetch();
    if (dataSources.myIssues.networkError) {
      throw new Error(dataSources.myIssues.networkError);
    }
    get().updateAllPanels();
  },
}));

export {homeStore};
