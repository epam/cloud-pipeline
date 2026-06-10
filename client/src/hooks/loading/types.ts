import {LoadableObject} from '../../@types/common.ts';

export type LoadingHookState<T> = LoadableObject & {
  data?: T;
};

export type LoadableData<A extends unknown[], T> = (...args: A) => Promise<T>;
