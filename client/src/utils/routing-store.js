/*
 * MobX store synced with React Router 7 (createHashRouter).
 * RouterStore receives the router via setRouter(); it subscribes to router.state
 * and exposes location, action, params and navigation methods (push, replace, go, etc.).
 *
 * Injected from Root as `routing` (use @inject('routing') or inject(({routing}) => ...)).
 * Do not confuse with the `router` prop from the withRouter HOC — that is a separate,
 * legacy compatibility object for class components still on the migration path.
 */

import {action, makeObservable, observable} from 'mobx';

export class RouterStore {
  location = null;
  action = null;
  params = {};

  constructor() {
    makeObservable(this, {
      location: observable,
      action: observable,
      params: observable,
      _updateLocation: action,
      updateParams: action,
      setRouter: action,
    });
    this._router = null;
    this.push = this.push.bind(this);
    this.replace = this.replace.bind(this);
    this.go = this.go.bind(this);
    this.goBack = this.goBack.bind(this);
    this.goForward = this.goForward.bind(this);
  }

  _updateLocation({action: historyAction, location}) {
    this.location = location;
    this.action = historyAction;
  }

  updateParams(params) {
    this.params = params || {};
  }

  setRouter(router) {
    this._router = router;
    const state = router.state;
    this._updateLocation({action: state.historyAction, location: state.location});
    const params = state.matches.reduce((acc, m) => ({...acc, ...m.params}), {});
    this.updateParams(params);
    router.subscribe((state) => {
      this._updateLocation({action: state.historyAction, location: state.location});
      const nextParams = state.matches.reduce((acc, m) => ({...acc, ...m.params}), {});
      this.updateParams(nextParams);
    });
  }

  push(to) {
    if (this._router) {
      this._router.navigate(to);
    }
  }

  replace(to) {
    if (this._router) {
      this._router.navigate(to, {replace: true});
    }
  }

  go(n) {
    if (this._router) {
      this._router.navigate(n);
    }
  }

  goBack() {
    if (this._router) {
      this._router.navigate(-1);
    }
  }

  goForward() {
    if (this._router) {
      this._router.navigate(1);
    }
  }
}
