/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import React from 'react';
import PropTypes from 'prop-types';
import {inject, observer, Provider} from 'mobx-react';
import {observable} from 'mobx';
import styles from './restore-button.css';

class RestoreContext {
  @observable context;

  constructor () {
    this.context = null;
  }

  registerContext = (context, onRestore) => {
    this.context = context;
    this.onRestore = onRestore;
  }

  restore = () => {
    if (this.context && this.context.restoreDefaultLayout) {
      this.context.restoreDefaultLayout();
      if (this.onRestore) {
        this.onRestore();
      }
    }
  }
}

const context = new RestoreContext();

const RestoreLayoutProvider = ({children}) => (
  <Provider layoutContext={context}>
    {children}
  </Provider>
);

const restoreLayoutConsumer = (component) => inject('layoutContext')(observer(component));

class RestoreButtonComponent extends React.Component {
  state = {
    modalVisible: false
  };

  onRestoreClick = () => {
    const {layoutContext} = this.props;
    if (layoutContext) {
      layoutContext.restore();
    }
  }

  render () {
    const {className, layoutContext} = this.props;
    const disabled = !layoutContext;
    return (
      <div
        className={[
          styles.button,
          className,
          disabled ? styles.disabled : false
        ].filter(Boolean).join(' ')}
        onClick={this.onRestoreClick}
      >
        Restore layout
      </div>
    );
  }
}

RestoreButtonComponent.propTypes = {
  className: PropTypes.string
};

const RestoreButton = restoreLayoutConsumer(RestoreButtonComponent);

export {RestoreLayoutProvider, restoreLayoutConsumer};
export default RestoreButton;
