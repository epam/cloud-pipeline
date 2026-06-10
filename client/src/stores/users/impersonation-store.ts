import {createStore} from 'zustand';
import type {UserInfo} from '../../@types/users.ts';
import {loadImpersonation, stopImpersonationRequest} from '../../api/users/impersonation-api.ts';
import {getErrorDescription} from '../../utilities/errors.ts';
import invalidateEdgeTokens from '../../utils/invalidate-edge-tokens';

type ImpersonationStore = {
  impersonatedUser?: UserInfo;
  originalUser?: UserInfo;
  pending: boolean;
  loaded: boolean;
  error?: string;
  load: (force?: boolean) => Promise<void>;
  stopImpersonation: () => Promise<void>;
};

const impersonationStore = createStore<ImpersonationStore>((set, get) => ({
  impersonatedUser: undefined,
  originalUser: undefined,
  pending: false,
  loaded: false,
  error: undefined,
  async load(force = false) {
    const {loaded, pending} = get();
    if ((loaded && !force) || pending) {
      return;
    }
    set({pending: true, error: undefined});
    try {
      const info = await loadImpersonation();
      set({
        impersonatedUser: info.impersonated,
        originalUser: info.original,
        loaded: true,
        pending: false,
        error: undefined,
      });
    } catch (error) {
      set({
        pending: false,
        error: getErrorDescription(error),
      });
    }
  },
  async stopImpersonation() {
    try {
      await invalidateEdgeTokens();
      await stopImpersonationRequest();
      window.location.href = SERVER;
    } catch {
      window.location.href = SERVER;
    }
  },
}));

export {impersonationStore};
