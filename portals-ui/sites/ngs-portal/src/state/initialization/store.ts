import createLoadableStore from '../common/loadable-store/create-loadable-store.ts';
import { initialize } from './initialize.ts';
import type { InitializationMessage, InitializationStore } from './types.ts';

export const initializationStore = createLoadableStore<InitializationStore>(
  (_, __, getter) => initialize(getter().registerMessage),
  false,
  (setter, getter) => ({
    messages: [],
    registerMessage(message: string | InitializationMessage): void {
      const { messages = [] } = getter();
      const sliced = messages.slice();
      if (typeof message === 'string') {
        console.log(message);
        sliced.push({ message });
      } else {
        console.log([message.message, message.details].filter(Boolean).join(' : '));
        sliced.push(message);
      }
      setter({ messages: sliced });
    },
    initialize() {
      (async () => {
        try {
          await getter().load(false);
        } catch (error) {
          console.error('error initializing application:');
          console.error(error);
        }
      })();
    },
  }),
);

initializationStore.getState().initialize();
