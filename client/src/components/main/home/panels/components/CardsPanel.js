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
import {Card, Icon, Input, Popover, Row} from 'antd';
import renderSeparator from './renderSeparator';
import styles from './CardsPanel.css';
import {favouriteStorage} from '../../utils/favourites';

const ACTION = PropTypes.shape({
  title: PropTypes.string,
  overlay: PropTypes.object,
  icon: PropTypes.string,
  action: PropTypes.func
});

const ACTION_MIN_HEIGHT = 18;

@favouriteStorage
@observer
export default class CardsPanel extends React.Component {
  static propTypes = {
    search: PropTypes.shape({
      placeholder: PropTypes.string,
      searchFn: PropTypes.func
    }),
    panelKey: PropTypes.string,
    style: PropTypes.object,
    onClick: PropTypes.func,
    childRenderer: PropTypes.func,
    cardClassName: PropTypes.oneOfType([PropTypes.string, PropTypes.func]),
    cardStyle: PropTypes.oneOfType([PropTypes.object, PropTypes.func]),
    actions: PropTypes.oneOfType([PropTypes.func, PropTypes.arrayOf(ACTION)]),
    emptyMessage: PropTypes.oneOfType([PropTypes.func, PropTypes.string]),
    isFavourite: PropTypes.func,
    favouriteEnabled: PropTypes.oneOfType([PropTypes.bool, PropTypes.func]),
    onSetFavourite: PropTypes.func,
    displayOnlyFavourites: PropTypes.bool,
    itemId: PropTypes.oneOfType([PropTypes.string, PropTypes.func]),
    getFavourites: PropTypes.func,
    setFavourites: PropTypes.func
  };

  static defaultProps = {
    displayOnlyFavourites: false,
    itemId: item => item.id
  };

  state = {
    actionInProgress: false,
    inProgressActionsTitle: null,
    popovers: [],
    search: null
  };

  onActionClicked = (e, action, source) => {
    e.stopPropagation();
    if (this.state.actionInProgress) {
      return;
    }
    this.setState({
      actionInProgress: true,
      inProgressActionsTitle: action.title
    }, async () => {
      action.action && await action.action(source);
      this.setState({
        actionInProgress: false,
        inProgressActionsTitle: null
      });
    });
  };

  openPopover = (index) => {
    const popovers = this.state.popovers;
    if (popovers.indexOf(index) === -1) {
      popovers.push(index);
      this.setState({popovers});
    }
  };

  closePopover = (index) => {
    const popovers = this.state.popovers;
    const itemIndex = popovers.indexOf(index);
    if (itemIndex >= 0) {
      popovers.splice(itemIndex, 1);
      this.setState({popovers});
    }
  };

  getItemIdentifier = (child) => {
    if (this.props.itemId) {
      if (typeof this.props.itemId === 'function') {
        return this.props.itemId(child);
      } else {
        return child[this.props.itemId];
      }
    }
    return null;
  };

  childIsFavourite = (child) => {
    if (this.props.getFavourites) {
      const childIdentifier = this.getItemIdentifier(child);
      return this.props.getFavourites().indexOf(childIdentifier) >= 0;
    }
    return false;
  };

  renderFavouriteSelector = (child, childIsFavourite) => {
    const onFavouriteClick = (e) => {
      e && e.stopPropagation();
      const itemId = this.getItemIdentifier(child);
      if (this.props.setFavourites) {
        this.props.setFavourites(itemId, !childIsFavourite);
        this.forceUpdate();
      }
    };
    return (
      <Row
        onClick={onFavouriteClick}
        className={styles.cardFavouriteContainer}
        type="flex"
        align="middle"
        justify="center">
        <Icon className={styles.notFavouriteSelector} type="star-o" style={{fontSize: 'large'}} />
        <Icon className={styles.favouriteSelector} type="star" style={{fontSize: 'large'}} />
      </Row>
    );
  };

  renderChildActions = (child, index, actions) => {
    if (actions && actions.length > 0) {
      const onVisibleChange = (visible) => {
        if (visible) {
          this.openPopover(index);
        } else {
          this.closePopover(index);
        }
      };
      const getIconType = (action) => {
        const {actionInProgress, inProgressActionsTitle} = this.state;
        if (actionInProgress && inProgressActionsTitle === action.title) {
          return 'loading';
        }
        return action.icon;
      };
      return (
        <div
          className={
            `${styles.actionsContainer} ${this.state.popovers.indexOf(index) >= 0 ? styles.hovered : ''}`
          }>
          <div
            type="actions-container-background"
            className={styles.actionsContainerBackground} />
          {
            actions.map((action, index, array) => {
              return (
                <Row
                  type="flex"
                  justify="start"
                  align="middle"
                  key={index}
                  className={styles.actionButton}
                  onClick={e => this.onActionClicked(e, action, child)}
                  style={{
                    flex: 1.0 / array.length,
                    minHeight: ACTION_MIN_HEIGHT
                  }}>
                  <Row type="flex" align="middle">
                    {
                      action.icon
                        ? <Icon style={action.style} type={getIconType(action)} />
                        : undefined
                    }
                    {
                      action.overlay
                        ? (
                          <Popover
                            onVisibleChange={onVisibleChange}
                            content={action.overlay}>
                            <span style={action.style}>{action.title}</span>
                          </Popover>
                        )
                        : <span style={action.style}>{action.title}</span>
                    }
                  </Row>
                </Row>
              );
            })
          }
        </div>
      );
    }
    return null;
  };

  renderCard = (child, index) => {
    let actions = this.props.actions;
    if (actions && typeof actions === 'function') {
      actions = actions(child);
    }
    let cardClassName = this.props.cardClassName;
    if (typeof cardClassName === 'function') {
      cardClassName = cardClassName(child);
    }
    let cardStyle = this.props.cardStyle;
    if (typeof cardStyle === 'function') {
      cardStyle = cardStyle(child);
    }
    cardStyle = cardStyle || {};
    let favouriteEnabled = this.props.favouriteEnabled;
    if (typeof favouriteEnabled === 'function') {
      favouriteEnabled = favouriteEnabled(child);
    }
    const childIsFavourite = this.childIsFavourite(child);
    let cardClass = favouriteEnabled ? `${styles.card} ${styles.favouriteEnabled}` : styles.card;
    if (cardClassName) {
      cardClass = `${cardClassName} ${cardClass}`;
    }
    cardClass = childIsFavourite ? `${cardClass} ${styles.favouriteItem}` : `${cardClass} ${styles.notFavouriteItem}`;
    return (
      <Card
        key={child.id || index}
        className={cardClass}
        bodyStyle={{padding: 10, height: '100%'}}
        style={Object.assign({
          width: 'initial',
          margin: 2,
          minHeight: actions && actions.length > 0
            ? ACTION_MIN_HEIGHT * actions.length + 10
            : undefined
        }, cardStyle)}
        onClick={() => this.props.onClick && this.props.onClick(child)}>
        {
          favouriteEnabled &&
          this.renderFavouriteSelector(child, childIsFavourite)
        }
        <div
          type="card-content"
          style={favouriteEnabled ? {paddingRight: 30} : {}}
          className={styles.cardContent}>
          {this.props.childRenderer(child, this.state.search)}
        </div>
        {this.renderChildActions(child, index, actions)}
      </Card>
    );
  };

  onSearchChange = (e) => {
    this.setState({
      search: e.target.value
    });
  };

  render () {
    const items = this.props.search && this.props.search.searchFn
      ? (this.props.children || []).filter(item => this.props.search.searchFn(item, this.state.search))
      : (this.props.children || []);
    const personalItemsFiltered = items.filter(item => !item.isGlobal);
    const globalItemsFiltered = items.filter(item => item.isGlobal);
    let personalItems = [...personalItemsFiltered, ...globalItemsFiltered.filter(this.childIsFavourite)];
    let globalItems = this.state.search ? globalItemsFiltered.filter(i => !this.childIsFavourite(i)) : [];
    if (!this.state.search && this.props.displayOnlyFavourites) {
      personalItems = personalItems.filter(this.childIsFavourite);
      globalItems = [];
    }
    const favourites = personalItems.filter(this.childIsFavourite);
    const other = personalItems.filter(i => !this.childIsFavourite(i));
    let emptyMessage = this.props.emptyMessage;
    if (typeof emptyMessage === 'function') {
      emptyMessage = emptyMessage(this.state.search);
    }
    return (
      <Row className={styles.cardsPanelContainer} style={this.props.style}>
        {
          this.props.search &&
          <Row type="flex" align="middle">
            <Input.Search
              value={this.state.search}
              size="small"
              onChange={this.onSearchChange}
              placeholder={this.props.search.placeholder}
              style={{margin: 2}} />
          </Row>
        }
        <div style={{overflow: 'auto', flex: 1}}>
          {
            personalItems.length === 0 && emptyMessage &&
            <Row type="flex" align="middle" style={{flex: 1, margin: 5}}>
              <span>{emptyMessage}</span>
            </Row>
          }
          <Row type="flex" justify="center">
            {
              favourites.map(this.renderCard)
            }
          </Row>
          <Row type="flex" justify="center">
            {
              other.map((child, index) => this.renderCard(child, index + (favourites || []).length))
            }
          </Row>
          {
            globalItems.length > 0 && renderSeparator('Global search', 0)
          }
          <Row type="flex" justify="center">
            {
              globalItems.map((child, index) =>
                this.renderCard(child, index + (favourites || []).length + (other || []).length))
            }
          </Row>
        </div>
      </Row>
    );
  }
}
