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
import {Alert} from 'antd';
import {formatCloudCommitmentDate} from '../helpers';
import styles from '../../edit-hot-node-pool.css';

function formatOfferingWindow (startDate, endDate) {
  const start = formatCloudCommitmentDate(startDate, 'YYYY-MM-DD HH:mm');
  const end = formatCloudCommitmentDate(endDate, 'YYYY-MM-DD HH:mm');
  if (start && end) {
    return `${start} – ${end}`;
  }
  return start || end;
}

// AWS prices a capacity block in USD in every region, so USD (or no currency) gets a `$` prefix.
function formatUpfrontFee (amount, currency) {
  if (amount === undefined || amount === null || Number.isNaN(Number(amount))) {
    return undefined;
  }
  const formatted = Number(amount).toLocaleString('en', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  });
  if (!currency || currency.toUpperCase() === 'USD') {
    return `$${formatted}`;
  }
  return `${formatted} ${currency}`;
}

// The nodes count is deliberately absent here - the read-only Configuration section shows it.
function CapacityBlockOfferingCard ({offering, selected, disabled, onSelect}) {
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
  // `type` carries the selection rather than a separate highlight class; `showIcon` stays true
  // in both states so the icon's padding never shifts the text.
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
  // antd 2.13.14's `Alert` drops unknown props, so role/click/keyboard handling sits on a wrapper.
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
      aria-pressed={selected}
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
  onSelect: PropTypes.func
};

CapacityBlockOfferingCard.defaultProps = {
  selected: false,
  disabled: false,
  onSelect: () => {}
};

export default CapacityBlockOfferingCard;
