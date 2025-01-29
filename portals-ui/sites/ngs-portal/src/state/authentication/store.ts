import createLoadableStore from '../common/loadable-store/create-loadable-store.ts';
import { authenticate } from './authenticate.ts';

export const authenticationStore = createLoadableStore(authenticate);
