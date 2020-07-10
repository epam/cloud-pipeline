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
import {inject, observer, Provider as MobxProvider} from 'mobx-react';
import {action, observable} from 'mobx';
import GridLayout from 'react-grid-layout';
import {Icon} from 'antd';
import * as GeneralReportLayout from './general-report';
import * as InstanceReportLayout from './instance-report';
import * as StorageReportLayout from './storage-report';
import RestoreButton, {restoreLayoutConsumer, RestoreLayoutProvider} from './restore-button';
import styles from './layout.css';

const UPDATE_SIZE_TIMER_MS = 250;

class GridLayoutContainerStore {
  @observable width;
  @observable height;

  constructor (width, height) {
    this.width = width;
    this.height = height;
  }

  @action
  setSize (width, height) {
    this.width = width;
    this.height = height;
  }
}

function LayoutPanel ({children, containerClassName, containerStyle, className, style}) {
  return (
    <div
      className={[styles.panelContainer, containerClassName].filter(Boolean).join(' ')}
      style={containerStyle}
    >
      <div
        className={[styles.panel, className].filter(Boolean).join(' ')}
        style={style}
      >
        {children}
        <div className={styles.panelMove}>
          <Icon type="arrows-alt" />
        </div>
      </div>
    </div>
  );
}

LayoutPanel.propTypes = {
  children: PropTypes.node,
  className: PropTypes.string,
  style: PropTypes.object
};

class LayoutComponent extends React.Component {
  static Panel = LayoutPanel;

  componentDidMount () {
    const {layoutContext, layout} = this.props;
    layoutContext.registerContext(layout, () => this.onRestore());
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    const {layoutContext, layout} = this.props;
    layoutContext.registerContext(layout, () => this.onRestore());
  }

  onRestore = () => {
    this.forceUpdate();
  };

  onLayoutChanged = (layout, update = false) => {
    const {layout: panelsLayout, onLayoutChange} = this.props;
    panelsLayout.setPanelsLayout(layout, false);
    if (update && onLayoutChange) {
      onLayoutChange();
    }
    this.forceUpdate();
  };

  render () {
    const {layoutDimensions, layout, gridStyles, staticPanels = []} = this.props;
    const panelsLayout = layout.getPanelsLayout(true, staticPanels);
    return (
      <div
        style={{
          position: 'relative',
          width: '100%',
          height: '100%',
          overflow: 'auto'
        }}
      >
        <GridLayout
          className="billing-layout"
          draggableHandle={`.${styles.panelMove}`}
          layout={panelsLayout}
          cols={gridStyles.gridCols}
          width={layoutDimensions.width - gridStyles.scrollBarSize}
          margin={[gridStyles.panelMargin, gridStyles.panelMargin]}
          containerPadding={[5, 0]}
          rowHeight={gridStyles.rowHeight(layoutDimensions.height)}
          onDragStop={(layout) => this.onLayoutChanged(layout, true)}
          onLayoutChange={(layout) => this.onLayoutChanged(layout, false)}
        >
          {this.props.children}
        </GridLayout>
      </div>
    );
  }
}

LayoutComponent.propTypes = {
  gridStyles: PropTypes.object,
  layout: PropTypes.object,
  children: PropTypes.node,
  onLayoutChange: PropTypes.func,
  staticPanels: PropTypes.arrayOf(PropTypes.string)
};

const Layout = inject('layoutDimensions')(
  restoreLayoutConsumer(LayoutComponent)
);

class Container extends React.Component {
  state = {
    container: undefined
  };

  store = new GridLayoutContainerStore(0, 0);

  componentDidMount () {
    this.updateTimer = setInterval(() => this.updateContainerSize(), UPDATE_SIZE_TIMER_MS);
  }

  componentWillUnmount () {
    clearInterval(this.updateTimer);
  }

  initializeContainer = (container) => {
    this.setState({container}, this.updateContainerSize);
  };

  updateContainerSize = () => {
    const {container} = this.state;
    if (container) {
      const containerWidth = container.clientWidth || window.innerWidth;
      const containerHeight = container.clientHeight || window.innerHeight;
      if (containerWidth !== this.store.width || containerHeight !== this.store.height) {
        this.store.setSize(
          containerWidth,
          containerHeight
        );
      }
    }
  };

  render () {
    return (
      <MobxProvider layoutDimensions={this.store}>
        <div ref={this.initializeContainer} className={this.props.className}>
          {this.props.children}
        </div>
      </MobxProvider>
    );
  }
}

Container.propTypes = {
  className: PropTypes.string,
  children: PropTypes.node
};

export {
  Container,
  GeneralReportLayout,
  InstanceReportLayout,
  StorageReportLayout,
  Layout,
  RestoreButton,
  RestoreLayoutProvider,
  restoreLayoutConsumer
};
