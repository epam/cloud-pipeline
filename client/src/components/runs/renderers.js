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
import {Popover, Row} from 'antd';
import classNames from 'classnames';

import AdaptedLink from '../special/AdaptedLink';

import styles from './renederers.css';

const IDLED_TAG = 'IDLED';
const PRESSURED_TAG = 'PRESSURED';

const knownTags = {
  [IDLED_TAG]: {
    title: 'Idle',
    className: styles.idleTag
  },
  [PRESSURED_TAG]: {
    title: 'Pressure',
    className: styles.pressureTag
  }
};

const maxTagItems = 2;

function tagDisplay (label, value, className = null) {
  return (
    <span
      className={classNames(styles.runTag, className)}
      key={label}
    >
      {value.toUpperCase()}
    </span>
  );
}

export function renderRunTags (
  tags,
  {
    location = {},
    instance = {},
    renderAll = false,
    onlyKnown = false
  }) {
  if (!tags) {
    return null;
  }
  const known = [];
  const rest = [];
  for (let key in tags) {
    if (Object.prototype.hasOwnProperty.call(tags, key)) {
      if (key in knownTags && `${tags[key]}` === 'true') {
        let linkTo;
        if (knownTags[key].link) {
          linkTo = knownTags[key].link;
        } else if (instance.nodeName) {
          linkTo = `/cluster/${instance.nodeName}/monitor`;
        } else if (instance.nodeIP) {
          const parts = instance.nodeIP.split('.');
          if (parts.length === 4) {
            linkTo = `/cluster/ip-${parts.join('-')}/monitor`;
          }
        }
        known.push(
          <AdaptedLink
            id={key}
            key={key}
            to={linkTo}
            location={location}
          >
            {tagDisplay(key, knownTags[key].title, knownTags[key].className)}
          </AdaptedLink>
        );
      } else if (`${tags[key]}` === 'true') {
        rest.push(tagDisplay(key, key));
      } else if (`${tags[key]}` !== 'false') {
        rest.push(tagDisplay(key, tags[key]));
      }
    }
  }
  if (renderAll) {
    return [...known, ...rest];
  }
  if (onlyKnown) {
    return known;
  }

  const popover = (
    <Popover
      key="more-tags-popover"
      content={(
        <Row type="flex" align="start" style={{flexDirection: 'column'}}>
          {[...known, ...rest]}
        </Row>
      )}
    >
      <a className={styles.moreLabel}>
        +{((known.length - maxTagItems) > 0 ? (known.length - maxTagItems) : 0) + rest.length} more
      </a>
    </Popover>
  );

  if (known.length > maxTagItems) {
    return [
      ...known.slice(0, 1),
      popover
    ];
  }

  const result = [...known];
  if (rest.length > 0) {
    result.push(popover);
  }
  return result;
}
