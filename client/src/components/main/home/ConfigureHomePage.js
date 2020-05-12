/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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
import {observer} from 'mobx-react';
import PropTypes from 'prop-types';
import {
  Panels,
  PanelIcons,
  PanelTitles,
  PanelInfos,
  Layout
} from './layout';
import {Button, Checkbox, Col, Icon, Modal, Row, Tooltip} from 'antd';
import {getDisplayOnlyFavourites, setDisplayOnlyFavourites} from './utils/favourites';
import localization from '../../../utils/localization';

@localization.localizedComponent
@observer
export default class ConfigureHomePage extends localization.LocalizedReactComponent {

  static propTypes = {
    visible: PropTypes.bool,
    onCancel: PropTypes.func,
    onSave: PropTypes.func
  };

  state = {
    panels: [],
    displayOnlyFavourites: false
  };

  onSave = () => {
    const visibleKeys = this.state.panels.filter(panel => panel.visible).map(panel => panel.key);
    const panels = Layout.getPanelsLayout();
    const removedPanels = panels.filter(item => visibleKeys.indexOf(item.i) === -1).map(item => item.i);
    const addedPanels = visibleKeys.filter(key => panels.filter(item => item.i === key).length === 0);
    removedPanels.forEach(panel => Layout.removePanel(panel));
    Layout.addPanels(addedPanels);
    setDisplayOnlyFavourites(this.state.displayOnlyFavourites);
    this.props.onSave && this.props.onSave();
  };

  restoreDefaultLayoutClicked = () => {
    Layout.restoreDefaultLayout();
    this.props.onSave && this.props.onSave();
  };

  onChangeVisibility = (key) => (e) => {
    const panels = this.state.panels;
    const [item] = panels.filter(item => item.key === key);
    if (item) {
      item.visible = e.target.checked;
      this.setState({panels});
    }
  };

  onDisplayOnlyFavouritesChanged = (e) => {
    this.setState({
      displayOnlyFavourites: e.target.checked
    });
  };

  render () {
    return (
      <Modal
        width="33%"
        title="Configure dashboard"
        visible={this.props.visible}
        onCancel={this.props.onCancel}
        footer={
          <Row type="flex" justify="space-between">
            <Button onClick={this.restoreDefaultLayoutClicked}>Restore default layout</Button>
            <Col>
              <Button onClick={this.props.onCancel}>Cancel</Button>
              <Button type="primary" onClick={this.onSave}>OK</Button>
            </Col>
          </Row>
        }>
        <table style={{borderCollapse: 'collapse', width: '100%'}}>
          <tbody>
          {
            this.state.panels.map((panel, index) => {
              return (
                <tr
                  key={panel.key}
                  style={
                    index % 2 === 0
                      ? {height: 22}
                      : {height: 22, backgroundColor: '#f4f4f4'}
                  }>
                  <td style={{borderCollapse: 'separate'}}>
                    <Checkbox
                      disabled={
                        panel.visible &&
                        this.state.panels.filter(i => i.visible).length === 1
                      }
                      checked={panel.visible}
                      onChange={this.onChangeVisibility(panel.key)}>
                      {panel.icon}
                      {panel.title}
                    </Checkbox>
                  </td>
                  <td style={{textAlign: 'right'}}>
                  {
                    panel.info &&
                    <Tooltip title={panel.info} placement="left">
                      <Icon type="question-circle" />
                    </Tooltip>
                  }
                  </td>
                </tr>
              );
            })
          }
          </tbody>
        </table>
        <Row type="flex" style={{marginTop: 10}}>
          <Checkbox
            checked={this.state.displayOnlyFavourites}
            onChange={this.onDisplayOnlyFavouritesChanged}>
            Show only favourites
          </Checkbox>
        </Row>
      </Modal>
    );
  }

  componentWillReceiveProps (nextProps) {
    if (this.props.visible !== nextProps.visible) {
      this.updatePanelsState();
    }
  }

  updatePanelsState = () => {
    const panels = [];
    const layout = Layout.getPanelsLayout();
    for (let key in Panels) {
      if (Panels.hasOwnProperty(key)) {
        let title = PanelTitles[Panels[key]];
        if (typeof title === 'function') {
          title = title(this.localizedString);
        }
        let info = PanelInfos[Panels[key]];
        if (typeof info === 'function') {
          info = info(this.localizedString);
        }
        let icon;
        if (PanelIcons[Panels[key]]) {
          icon = (
            <Icon
              type={PanelIcons[Panels[key]]}
              style={{
                fontSize: 'larger',
                marginRight: 5
              }} />
          );
        }
        panels.push({
          key: Panels[key],
          title,
          info,
          icon,
          visible: layout.filter(item => item.i === Panels[key]).length > 0
        });
      }
    }
    this.setState({
      panels,
      displayOnlyFavourites: getDisplayOnlyFavourites()
    });
  };
}
