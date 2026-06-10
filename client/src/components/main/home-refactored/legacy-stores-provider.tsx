import {ReactNode} from 'react';
import {Provider} from 'mobx-react';
import {legacyMobXStores} from '../../../mobx-stores/legacy-stores.js';

type HomeLegacyStoresProviderProps = {
  children: ReactNode;
};

function HomeLegacyStoresProvider({children}: HomeLegacyStoresProviderProps) {
  return <Provider {...legacyMobXStores}>{children}</Provider>;
}

export {HomeLegacyStoresProvider};
