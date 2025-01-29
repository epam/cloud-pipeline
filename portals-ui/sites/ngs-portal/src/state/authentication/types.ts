import type { User, UserMetadata } from '@cloud-pipeline/core';
import {LoadableStoreActions, LoadableStoreState} from "../common/loadable-store/types.ts";

export type AuthenticatedUserInfo = { user: User; metadata: UserMetadata; };

export type AuthenticationState = LoadableStoreState<AuthenticatedUserInfo | undefined>;

export type AuthenticationActions = LoadableStoreActions<AuthenticatedUserInfo | undefined>

export type AuthenticationStore = AuthenticationState & AuthenticationActions;
