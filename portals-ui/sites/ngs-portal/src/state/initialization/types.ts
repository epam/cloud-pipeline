import type {
  LoadableStoreActions,
  LoadableStoreState,
} from '../common/loadable-store/types.ts';

export type InitializationMessage = {
  message: string;
  details?: string;
  source?: string;
};

export type InitializationState = LoadableStoreState<boolean> & {
  messages: InitializationMessage[];
};

export type InitializationActions = LoadableStoreActions<boolean> & {
  initialize(): void;
  registerMessage(message: string | InitializationMessage): void;
};

export type InitializationStore = InitializationState & InitializationActions;
