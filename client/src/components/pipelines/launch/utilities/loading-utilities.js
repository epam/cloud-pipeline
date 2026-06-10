class Loader {
  constructor(key, callbacks) {
    this.key = key;
    this.promise = undefined;
    this.token = undefined;
    const {onStart = () => {}, onError = () => {}, onLoaded = () => {}} = callbacks || {};
    this.onStart = onStart;
    this.onError = onError;
    this.onLoaded = onLoaded;
  }

  load = async (token, fn) => {
    if (this.token !== token || !this.promise) {
      this.token = token;
      const commit = (c) => {
        if (token === this.token) {
          c();
        }
      };
      this.promise = new Promise((resolve, reject) => {
        (async () => {
          try {
            commit(() => this.onStart());
            const v = await fn();
            commit(() => this.onLoaded(v));
            resolve(v);
          } catch (error) {
            commit(() => this.onError(error.message));
            reject(error);
          }
        })();
      });
    }
    return this.promise;
  };
}

export class LoadingUtilities {
  constructor() {
    this.loaders = [];
  }

  ensureLoader = (key, callbacks) => {
    let loader = this.loaders.find((l) => l.key === key);
    if (!loader) {
      loader = new Loader(key, callbacks);
      this.loaders.push(loader);
    }
    return loader;
  };

  generateSetStateCallbacks = (key, setState) => ({
    onStart: () => {
      setState({[key]: {pending: true, error: undefined, value: undefined}});
    },
    onError: (e) => {
      setState({[key]: {pending: false, error: e, value: undefined}});
    },
    onLoaded: (v) => {
      setState({[key]: {pending: false, error: undefined, value: v}});
    },
  });

  load = async (key, token, fn, callbacks = undefined) => {
    const loader = this.ensureLoader(key, callbacks);
    return loader.load(token, fn);
  };

  loadWithSetStateCallbacks = async (key, token, fn, setState) => {
    return this.load(key, token, fn, this.generateSetStateCallbacks(key, setState));
  };

  getLoadingState = (key, state) => {
    const {[key]: keyedState = {}} = state;
    const {pending = false, error = undefined, value = undefined} = keyedState;
    return {
      pending,
      error,
      value,
    };
  };
}
