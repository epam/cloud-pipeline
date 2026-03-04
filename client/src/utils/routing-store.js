/*
 * MobX 6–compatible replacement for mobx-react-router.
 * mobx-react-router uses observe(store, 'location', ...), which in MobX 6
 * throws "Cannot obtain atom from undefined" when the store uses the old
 * decorator-based API. This module provides the same RouterStore + syncHistoryWithStore
 * API using makeObservable and reaction() instead of observe().
 */

import {action, makeObservable, observable, reaction} from 'mobx';

export class RouterStore {
  location = null;
  history = null;

  constructor () {
    makeObservable(this, {
      location: observable,
      _updateLocation: action
    });
    this.push = this.push.bind(this);
    this.replace = this.replace.bind(this);
    this.go = this.go.bind(this);
    this.goBack = this.goBack.bind(this);
    this.goForward = this.goForward.bind(this);
  }

  _updateLocation (newState) {
    this.location = newState;
  }

  push (location) {
    this.history.push(location);
  }

  replace (location) {
    this.history.replace(location);
  }

  go (n) {
    this.history.go(n);
  }

  goBack () {
    this.history.goBack();
  }

  goForward () {
    this.history.goForward();
  }
}

/**
 * Syncs history with RouterStore and returns an enhanced history object.
 * Uses reaction() instead of observe() for MobX 6 compatibility.
 */
export function syncHistoryWithStore (history, store) {
  store.history = history;

  const handleLocationChange = (location) => {
    store._updateLocation(location);
  };

  const unsubscribeFromHistory = history.listen(handleLocationChange);
  handleLocationChange(history.getCurrentLocation());

  return {
    ...history,
    listen (listener) {
      const dispose = reaction(
        () => store.location,
        (location) => listener(location),
        {fireImmediately: true}
      );
      return () => dispose();
    },
    unsubscribe () {
      unsubscribeFromHistory();
    }
  };
}
