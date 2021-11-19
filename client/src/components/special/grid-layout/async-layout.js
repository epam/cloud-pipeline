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
import {observable} from 'mobx';
import {inject, observer, Provider} from 'mobx-react';
import {Alert} from 'antd';
import buildLayout from './layout';
import LoadingView from '../LoadingView';

export default class AsyncLayout {
  static inject (loader) {
    return Component => observer(
      class extends React.Component {
        @observable asyncLayout;
        constructor (props) {
          super(props);
          this.asyncLayout = new AsyncLayout(loader(props));
        }

        render () {
          if (this.asyncLayout.loaded) {
            if (this.asyncLayout.error) {
              return (
                <Alert type="error" message={this.asyncLayout.error} />
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
    return inject('layout')(observer(...opts));
  }

  @observable loaded = false;
  @observable error = undefined;
  @observable layout;
  constructor (layoutOptionsLoader) {
    if (layoutOptionsLoader && layoutOptionsLoader.then) {
      layoutOptionsLoader
        .then(options => {
          this.layout = buildLayout(options);
          this.loaded = true;
        })
        .catch(e => {
          this.error = e.message;
        })
        .then(() => {
          this.error = undefined;
        });
    } else {
      this.layout = buildLayout(layoutOptionsLoader);
      this.loaded = true;
      this.error = undefined;
    }
  }
}
