import type {ReactNode} from 'react';
import {Provider} from 'mobx-react';
import {legacyMobXStores} from '../../mobx-stores/legacy-stores.js';

type LegacyMobXStoresProviderProps = {
  children: ReactNode;
};

/**
 * Temporary MobX Provider for legacy @inject components rendered from src/pages.
 * Mirrors Root.jsx until pages no longer depend on MobX stores.
 */
function LegacyMobXStoresProvider({children}: LegacyMobXStoresProviderProps) {
  return <Provider {...legacyMobXStores}>{children}</Provider>;
}

export {LegacyMobXStoresProvider};
