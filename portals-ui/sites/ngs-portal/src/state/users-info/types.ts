import type { UserInfo } from '@cloud-pipeline/core';
import type { LoadableStore } from '../common/loadable-store/types.ts';

export type UsersInfoStore = LoadableStore<UserInfo[]>;
