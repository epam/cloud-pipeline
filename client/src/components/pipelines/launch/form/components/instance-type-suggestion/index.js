/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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
import PropTypes from 'prop-types';
import classNames from 'classnames';
import moment from 'moment-timezone';
import {Alert, Dropdown, Icon, Menu} from 'antd';
import AWSRegionTag from '../../../../../special/AWSRegionTag';
import {
  COMMITMENT_DATE_TIME_FORMAT
} from '../../../../../cluster/hot-node-pool/edit-hot-node-pool/constants';
import {
  getReservationDisplayInstanceCount
} from '../../../../../cluster/hot-node-pool/edit-hot-node-pool/helpers';
import styles from './instance-type-suggestion.css';

function parseDate (value) {
  if (!value) {
    return undefined;
  }
  const parsed = moment.utc(value, COMMITMENT_DATE_TIME_FORMAT);
  return parsed.isValid() ? parsed : undefined;
}

function displayDate (value) {
  const parsed = parseDate(value);
  return parsed ? moment(parsed.toDate()).format('D MMM YYYY') : undefined;
}

// Every pool handed to this component is already an active capacity block -
// `capacityBlocks`'s own filter (`isCapacityReservation: true, active: true`)
// decided that server-side, so there is no window or status check left to do
// here. This only pairs a pool with its matching `/cluster/capacity-reservation`
// record, when there is one, which is where the count and end date fall back to
// when the pool itself does not carry them (a non-admin on somebody else's pool).
function buildSuggestions (pools, reservations) {
  return (pools || [])
    .filter((pool) => pool && pool.instanceType)
    .map((pool) => {
      const reservation = (reservations || []).find((r) => r.nodePoolId === pool.id);
      const count = pool.count !== undefined && pool.count !== null
        ? pool.count
        : getReservationDisplayInstanceCount(reservation);
      return {
        pool,
        reservation,
        instanceType: pool.instanceType,
        regionId: pool.regionId,
        count,
        endDate: pool.endDate || (reservation || {}).endDate
      };
    });
}

// How many suggestions the alert itself lists. Past this the rest go behind the
// "show more" link, so a user with a dozen reservations does not get a dozen
// rows pushed in above the node type selector.
export const MAX_VISIBLE_SUGGESTIONS = 3;

class InstanceTypeSuggestion extends React.Component {
  state = {
    poolsListVisible: false
  };

  get suggestions () {
    const {
      pools,
      reservations,
      regionId,
      instanceTypes
    } = this.props;
    let suggestions = buildSuggestions(pools, reservations);
    if (regionId !== undefined && regionId !== null) {
      suggestions = suggestions
        .filter((suggestion) => `${suggestion.regionId}` === `${regionId}`);
    }
    // A pool whose instance type the node type selector does not offer - it is
    // outside `cluster.allowed.instance.types` for this tool - is a dead end:
    // selecting it sets a value the select cannot resolve. An empty list means
    // the allowed types have not loaded yet, so nothing is filtered on that
    // account.
    if (!instanceTypes || !instanceTypes.length) {
      return suggestions;
    }
    const available = new Set(
      instanceTypes
        .map((instanceType) => (instanceType.name || '').toLowerCase())
        .filter(Boolean)
    );
    return suggestions
      .filter((suggestion) => available.has(suggestion.instanceType.toLowerCase()));
  }

  selectSuggestion = (suggestion) => {
    const {disabled, onSelect, currentInstanceType} = this.props;
    if (!suggestion || disabled) {
      return;
    }
    const {instanceType, pool} = suggestion;
    this.setState({poolsListVisible: false});
    if (onSelect && instanceType !== currentInstanceType) {
      onSelect(instanceType, pool);
    }
  };

  onPoolsListItemClick = ({key}) => {
    this.selectSuggestion(this.suggestions.find((o) => `${o.pool.id}` === `${key}`));
  };

  onPoolsListVisibleChange = (visible) => this.setState({poolsListVisible: visible});

  renderSuggestionInfo = (suggestion) => {
    const {
      pool,
      instanceType,
      regionId,
      count,
      endDate
    } = suggestion;
    const until = displayDate(endDate);
    return (
      <div className={styles.info}>
        <div className={styles.infoMain}>
          <span className={styles.instanceType}>
            {instanceType}
          </span>
          <AWSRegionTag
            className={classNames(styles.region, 'cp-text-not-important')}
            regionId={regionId}
            displayName
            showProvider={false}
            displayFlag={false}
          />,
          {
            count !== undefined && (
              <span className={classNames(styles.count, 'cp-text-not-important')}>
                {`${count} node${count === 1 ? '' : 's'} reserved`}
              </span>
            )
          }
        </div>
        <div className={classNames(styles.infoDetails, 'cp-text-not-important')}>
          {until ? `available until ${until} ` : undefined}
          {`(${pool.name})`}
        </div>
      </div>
    );
  };

  renderSuggestion = (suggestion, index) => {
    const {currentInstanceType, disabled} = this.props;
    const {pool, instanceType} = suggestion;
    const selected = currentInstanceType === instanceType;
    return (
      <div
        key={pool.id}
        className={
          classNames(
            styles.suggestion,
            {
              'cp-divider': index > 0,
              top: index > 0,
              [styles.disabled]: disabled,
              [styles.selected]: selected,
              'cp-primary': selected
            }
          )
        }
        onClick={() => this.selectSuggestion(suggestion)}
      >
        {this.renderSuggestionInfo(suggestion)}
        {
          selected
            ? (
              <span className={styles.action}>
                <Icon type="check" /> selected
              </span>
            )
            : (
              <a className={styles.action}>
                use this type
              </a>
            )
        }
      </div>
    );
  };

  renderPoolsList = (suggestions) => {
    const {currentInstanceType, disabled} = this.props;
    const selectedKeys = suggestions
      .filter((suggestion) => suggestion.instanceType === currentInstanceType)
      .map((suggestion) => `${suggestion.pool.id}`);
    return (
      <Menu
        className={styles.poolsList}
        selectedKeys={selectedKeys}
        onClick={this.onPoolsListItemClick}
      >
        {
          suggestions.map((suggestion) => (
            <Menu.Item
              key={`${suggestion.pool.id}`}
              className={styles.poolsListItem}
              disabled={disabled}
            >
              {this.renderSuggestionInfo(suggestion)}
            </Menu.Item>
          ))
        }
      </Menu>
    );
  };

  // `hidden` is what the alert did not list - the popup is the rest of the pools,
  // not another copy of the ones already on screen.
  renderShowMore = (hidden) => {
    const {poolsListVisible} = this.state;
    return (
      <Dropdown
        overlay={this.renderPoolsList(hidden)}
        trigger={['click']}
        placement="bottomLeft"
        visible={poolsListVisible}
        onVisibleChange={this.onPoolsListVisibleChange}
      >
        <a className={classNames(styles.showMore, 'underline')}>
          {`Show ${hidden.length} more`}
          <Icon
            className={styles.showMoreIcon}
            type={poolsListVisible ? 'up' : 'down'}
          />
        </a>
      </Dropdown>
    );
  };

  render () {
    const {className, style} = this.props;
    const {suggestions} = this;
    if (!suggestions.length) {
      return null;
    }
    const visible = suggestions.slice(0, MAX_VISIBLE_SUGGESTIONS);
    const hidden = suggestions.slice(MAX_VISIBLE_SUGGESTIONS);
    return (
      <Alert
        className={classNames(styles.container, className)}
        style={style}
        type="info"
        showIcon
        message={
          <div>
            <div className={styles.header}>
              {`You have reserved capacity available right now `}
              {`(${suggestions.length} ${suggestions.length === 1 ? 'pool' : 'pools'}). `}
              Launching a run on one of these node types will use the reservation.
            </div>
            <div className={styles.suggestions}>
              {visible.map(this.renderSuggestion)}
            </div>
            {hidden.length > 0 ? this.renderShowMore(hidden) : undefined}
          </div>
        }
      />
    );
  }
}

InstanceTypeSuggestion.propTypes = {
  className: PropTypes.string,
  currentInstanceType: PropTypes.string,
  disabled: PropTypes.bool,
  instanceTypes: PropTypes.array,
  onSelect: PropTypes.func,
  pools: PropTypes.array.isRequired,
  reservations: PropTypes.array.isRequired,
  regionId: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
  style: PropTypes.object
};

export default InstanceTypeSuggestion;
