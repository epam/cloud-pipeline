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
import {inject, Provider as MobxProvider} from 'mobx-react';
import {action, observable} from 'mobx';
import classNames from 'classnames';
import GridLayout from 'react-grid-layout';
import {Icon} from 'antd';
import * as GeneralReportLayout from './general-report';
import * as InstanceReportLayout from './instance-report';
import * as StorageReportLayout from './storage-report';
import RestoreButton, {restoreLayoutConsumer, RestoreLayoutProvider} from './restore-button';
import styles from './layout.css';

const UPDATE_SIZE_TIMER_MS = 500;

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
      className={
        classNames(
          styles.panelContainer,
          containerClassName
        )
      }
      style={containerStyle}
    >
      <div
        className={
          classNames(
            styles.panel,
            'cp-panel',
            className
          )
        }
        style={style}
      >
        {children}
        <div className={classNames(styles.panelMove, 'cp-billing-layout-panel-move')}>
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
    if (!this.props.children) {
      return null;
    }
    const {layoutDimensions, layout, gridStyles, staticPanels = []} = this.props;
    const panelsLayout = layout.getPanelsLayout(true, staticPanels);
    return (
      <GridLayout
        className="cp-billing-layout"
        draggableHandle={`.${styles.panelMove}.cp-billing-layout-panel-move`}
        layout={panelsLayout}
        cols={gridStyles.gridCols}
        width={layoutDimensions.width - gridStyles.scrollBarSize}
        margin={[gridStyles.panelMargin, gridStyles.panelMargin]}
        containerPadding={[0, 0]}
        rowHeight={gridStyles.rowHeight(layoutDimensions.height)}
        onDragStop={(layout) => this.onLayoutChanged(layout, true)}
        onLayoutChange={(layout) => this.onLayoutChanged(layout, false)}
      >
        {this.props.children.filter(Boolean)}
      </GridLayout>
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
  current = {width: 0, height: 0};

  componentDidMount () {
    this.sizeChecker();
  }

  componentWillUnmount () {
    cancelAnimationFrame(this.sizeCheckerFrame);
    clearTimeout(this.sizeUpdateTimer);
  }

  sizeChecker = () => {
    this.updateContainerSize();
    this.sizeCheckerFrame = requestAnimationFrame(() => this.sizeChecker());
  };

  initializeContainer = (container) => {
    this.setState({container}, () => this.updateContainerSize(true));
  };

  updateContainerSize = (immediate = false) => {
    const {container} = this.state;
    if (container && container.clientWidth && container.clientHeight) {
      const containerWidth = container.clientWidth;
      const containerHeight = container.clientHeight;
      if (containerWidth !== this.current.width || containerHeight !== this.current.height) {
        clearTimeout(this.sizeUpdateTimer);
        this.current = {
          width: containerWidth,
          height: containerHeight
        };
        if (!immediate) {
          this.sizeUpdateTimer = setTimeout(() => {
            this.store.setSize(
              this.current.width,
              this.current.height
            );
          }, UPDATE_SIZE_TIMER_MS);
        } else {
          this.store.setSize(
            this.current.width,
            this.current.height
          );
        }
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
