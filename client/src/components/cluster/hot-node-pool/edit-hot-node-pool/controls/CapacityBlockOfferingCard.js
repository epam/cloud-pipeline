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

// Thousands-separated, two decimals.
function formatUpfrontFee (amount, currency) {
  if (amount === undefined || amount === null || Number.isNaN(Number(amount))) {
    return undefined;
  }
  const formatted = Number(amount).toLocaleString('en', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  });
  return currency ? `${formatted} ${currency}` : formatted;
}

// One offering's own facts, laid out as a 2x2 table: instance type and window on the
// first line, availability zone and price on the second. The nodes count is not here -
// the Configuration section's read-only `Nodes count` is filled from it instead.
// `interactive` defaults to false: the candidates control's row is the selectable unit
// and owns the highlight, so the card here is a plain element with no role, no
// selected styling and no click handling of its own. A caller that still wants a
// self-contained clickable card sets `interactive`.
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
  // Two rows of two cells, always rendered, so a missing fact leaves its own cell
  // empty rather than shifting the others out of their column.
  const content = [
    <div key="row1" className={styles.capacityBlockOfferingCardRow}>
      <div className={styles.capacityBlockOfferingCardCell}>
        {instanceType && <b>{instanceType}</b>}
      </div>
      <div className={styles.capacityBlockOfferingCardCell}>{offeringWindow}</div>
    </div>,
    <div key="row2" className={styles.capacityBlockOfferingCardRow}>
      <div className={styles.capacityBlockOfferingCardCell}>{availabilityZone}</div>
      <div className={styles.capacityBlockOfferingCardCell}>{price}</div>
    </div>
  ];
  if (!interactive) {
    return (
      <div className={styles.capacityBlockOfferingCard}>
        {content}
      </div>
    );
  }
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
        styles.capacityBlockOfferingCard,
        styles.capacityBlockOfferingCardClickable,
        'cp-table-element',
        {
          'cp-table-element-selected': selected,
          [styles.disabled]: disabled
        }
      )}
      onClick={select}
      onKeyDown={onKeyDown}
    >
      {content}
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
