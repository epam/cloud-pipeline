import buildLayout from '../../../special/grid-layout/layout';
import defaultState from '../../home/layout/default-panels-state';
import defaultSizes from '../../home/layout/default-panels-sizes';
import neighbors from '../../home/layout/panel-neighbors';
import gridStyle from '../../home/layout/grid-style';
import {mapPanelIdentifier} from '../../home/layout/panels';
import {loadUiNavigation, uiNavigationStore} from '../../../../stores/ui-navigation';
import {PANELS_LAYOUT_STORAGE_KEY} from './constants';
import type {HomeLayoutManager} from './types';

function migrateStoredPanelIdentifiers(): void {
  try {
    const layout = JSON.parse(localStorage.getItem(PANELS_LAYOUT_STORAGE_KEY) ?? 'null');
    if (!Array.isArray(layout)) {
      return;
    }
    let modified = false;
    layout.forEach((panel: {i: string}) => {
      const identifier = mapPanelIdentifier(panel.i);
      modified = modified || identifier !== panel.i;
      panel.i = identifier;
    });
    if (modified) {
      localStorage.setItem(PANELS_LAYOUT_STORAGE_KEY, JSON.stringify(layout));
    }
  } catch {
    /* empty */
  }
}

migrateStoredPanelIdentifiers();

export async function createHomeLayoutManager(): Promise<HomeLayoutManager> {
  await loadUiNavigation();
  const dashboard = uiNavigationStore.getState().dashboard;

  return buildLayout({
    // @ts-ignore
    defaultState: (dashboard as typeof defaultState) || defaultState,
    storage: PANELS_LAYOUT_STORAGE_KEY,
    defaultSizes,
    // @ts-ignore
    panelNeighbors: neighbors,
    gridStyle,
  });
}
