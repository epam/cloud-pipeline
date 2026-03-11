/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import React from 'react';
import {observable, makeObservable} from 'mobx';
import {inject, observer, Provider} from 'mobx-react';
import {Alert} from 'antd';
import buildLayout from './layout';
import LoadingView from '../LoadingView';

export default class AsyncLayout {
  static inject (loader) {
    return Component => observer(
      class extends React.Component {
        constructor (props) {
          super(props);
          this._mounted = false;
          this.asyncLayout = new AsyncLayout(loader(props), () => this._mounted);
        }

        componentDidMount () {
          this._mounted = true;
        }

        componentWillUnmount () {
          this._mounted = false;
        }

        render () {
          if (this.asyncLayout.loaded) {
            if (this.asyncLayout.error) {
              return (
                <Alert type="error" title={this.asyncLayout.error} />
              );
            }
            return (
              <Provider layout={this.asyncLayout.layout}>
                <Component
                  {...this.props}
                />
              </Provider>
            );
          }
          return (<LoadingView />);
        }
      }
    );
  }

  static use (...opts) {
    return inject('layout')(...opts);
  }

  loaded = false;
  error = undefined;
  layout;
  constructor (layoutOptionsLoader, isMounted = () => true) {
    makeObservable(this, {
      loaded: observable,
      error: observable,
      layout: observable
    });
    if (layoutOptionsLoader && layoutOptionsLoader.then) {
      layoutOptionsLoader
        .then(options => {
          if (isMounted()) {
            this.layout = buildLayout(options);
            this.loaded = true;
          }
        })
        .catch(e => {
          if (isMounted()) {
            this.error = e.message;
          }
        })
        .then(() => {
          if (isMounted()) {
            this.error = undefined;
          }
        });
    } else {
      this.layout = buildLayout(layoutOptionsLoader);
      this.loaded = true;
      this.error = undefined;
    }
  }
}
