/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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
import classNames from 'classnames';
import moment from 'moment-timezone';
import {Alert} from 'antd';
import {COMMITMENT_DATE_TIME_FORMAT} from '../constants';
import styles from '../../edit-hot-node-pool.css';

function formatOfferingDate (value) {
  const parsed = moment(value, COMMITMENT_DATE_TIME_FORMAT);
  return parsed.isValid() ? parsed.format('YYYY-MM-DD HH:mm') : undefined;
}

function formatOfferingWindow (startDate, endDate) {
  const start = formatOfferingDate(startDate);
  const end = formatOfferingDate(endDate);
  if (start && end) {
    return `${start} – ${end}`;
  }
  return start || end;
}

// Thousands-separated, two decimals. AWS prices a capacity block in USD in every
// region, so a USD (or absent) currency gets a `$` prefix rather than a suffix.
function formatUpfrontFee (amount, currency) {
  if (amount === undefined || amount === null || Number.isNaN(Number(amount))) {
    return undefined;
  }
  const formatted = Number(amount).toLocaleString('en', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  });
  if (!currency || currency === 'USD') {
    return `$${formatted}`;
  }
  return `${formatted} ${currency}`;
}

// The offering's own facts, as a single line: zone, instance type, window, price. The
// nodes count is not here - the Configuration section's read-only `Nodes count` is
// filled from it instead. The card is an antd `Alert`, whose type carries the
// selection - `success` once selected, `info` otherwise - rather than a highlight of
// its own; `showIcon` is passed in both states so the icon's padding never moves the
// text, and the `info` state only hides the icon itself, in CSS. `interactive`
// defaults to false: the candidates control's row is usually the selectable unit, so
// the card has no role of its own unless a row with several offerings makes each card
// its own click target.
function CapacityBlockOfferingCard ({offering, selected, disabled, interactive, onSelect}) {
  const {
    offeringId,
    instanceType,
    availabilityZone,
    startDate,
    endDate,
    upfrontFeeAmount,
    upfrontFeeCurrency
  } = offering || {};
  const offeringWindow = formatOfferingWindow(startDate, endDate);
  const price = formatUpfrontFee(upfrontFeeAmount, upfrontFeeCurrency);
  // One cell per fact, so a missing fact simply leaves its own cell empty.
  const content = [
    <div key="zone" className={styles.capacityBlockOfferingCardCell}>{availabilityZone}</div>,
    <div key="type" className={styles.capacityBlockOfferingCardCell}>
      {instanceType && <b>{instanceType}</b>}
    </div>,
    <div key="window" className={styles.capacityBlockOfferingCardCell}>{offeringWindow}</div>,
    <div key="price" className={styles.capacityBlockOfferingCardCell}>{price}</div>
  ];
  const type = selected ? 'success' : 'info';
  const alert = (
    <Alert
      type={type}
      showIcon
      className={classNames(
        styles.capacityBlockOfferingCard,
        {[styles.capacityBlockOfferingCardIconHidden]: type === 'info'}
      )}
      message={
        <span className={styles.capacityBlockOfferingCardMessage}>
          {content}
        </span>
      }
    />
  );
  if (!interactive) {
    return alert;
  }
  // antd 2.13.14's `Alert` does not forward unknown props to its root element, so the
  // role/click/keyboard handling lives on a wrapping element instead.
  const select = () => {
    if (!disabled) {
      onSelect(offeringId);
    }
  };
  const onKeyDown = (event) => {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      select();
    }
  };
  return (
    <div
      role="button"
      tabIndex={disabled ? -1 : 0}
      aria-disabled={disabled}
      className={classNames(
        styles.capacityBlockOfferingCardClickable,
        {[styles.disabled]: disabled}
      )}
      onClick={select}
      onKeyDown={onKeyDown}
    >
      {alert}
    </div>
  );
}

CapacityBlockOfferingCard.propTypes = {
  offering: PropTypes.shape({
    offeringId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
    instanceType: PropTypes.string,
    availabilityZone: PropTypes.string,
    startDate: PropTypes.string,
    endDate: PropTypes.string,
    upfrontFeeAmount: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
    upfrontFeeCurrency: PropTypes.string
  }),
  selected: PropTypes.bool,
  disabled: PropTypes.bool,
  interactive: PropTypes.bool,
  onSelect: PropTypes.func
};

CapacityBlockOfferingCard.defaultProps = {
  selected: false,
  disabled: false,
  interactive: false,
  onSelect: () => {}
};

export default CapacityBlockOfferingCard;
