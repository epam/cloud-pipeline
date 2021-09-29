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
import AdaptedLink from '../../special/AdaptedLink';
import styles from './run-tags.css';

const activeRunStatuses = ['RUNNING', 'PAUSED', 'PAUSING', 'RESUMING'];
const KNOWN_TAGS_REGEXP = [
  /^idle$/i,
  /^pressure$/i
];

const isInstanceLink = (tag) => {
  return KNOWN_TAGS_REGEXP.some(t => t.test(tag));
};

const isKnownTag = (tag) => {
  return KNOWN_TAGS_REGEXP.some(t => t.test(tag));
};

const skipTag = (tag, tags) => {
  return `${tags[tag]}` === 'false';
};

function Tag (
  {
    className,
    tag,
    value,
    instance,
    location,
    theme
  }
) {
  let display = value;
  if (`${value}` === 'true') {
    display = tag;
  }
  const element = (
    <span
      className={[
        styles.runTag,
        styles[(tag || '').toLowerCase()],
        theme ? styles[`${theme}Theme`] : undefined,
        className
      ].filter(Boolean).join(' ')}
    >
      {(display || '').toUpperCase()}
    </span>
  );
  if (instance && instance.nodeName && isInstanceLink(tag) && location) {
    const instanceLink = `/cluster/${instance.nodeName}/monitor`;
    return (
      <AdaptedLink
        id={tag}
        to={instanceLink}
        location={location}
        className={styles.link}
      >
        {element}
      </AdaptedLink>
    );
  }
  return element;
}

export default function RunTags (
  {
    className,
    location,
    onlyKnown,
    overflow,
    tagClassName,
    run,
    theme
  }
) {
  if (!run) {
    return null;
  }
  const {status, tags, instance} = run;
  if (!tags || !activeRunStatuses.includes(status)) {
    return null;
  }
  const result = [];
  for (let tag in tags) {
    if (
      Object.prototype.hasOwnProperty.call(tags, tag) &&
      !skipTag(tag, tags) &&
      (!onlyKnown || isKnownTag(tag))
    ) {
      result.push({
        isKnown: isKnownTag(tag),
        element: (
          <Tag
            className={tagClassName}
            key={tag}
            tag={tag}
            value={tags[tag]}
            instance={instance}
            location={location}
            theme={theme}
          />
        )
      });
    }
  }
  result.sort((rA, rB) => rB.isKnown - rA.isKnown);
  if (!overflow && overflow !== 0) {
    return (
      <div
        className={className}
        style={{display: 'inline'}}
      >
        {result.map(r => r.element)}
      </div>
    );
  }
  let tagsToDisplayCount = result.filter(r => r.isKnown).length;
  if (typeof overflow !== 'boolean' && !isNaN(overflow)) {
    tagsToDisplayCount = Math.max(0, +overflow);
  }
  if (tagsToDisplayCount >= result.length) {
    return (
      <div
        className={className}
        style={{display: 'inline'}}
      >
        {result.map(r => r.element)}
      </div>
    );
  }
  const popover = (
    <Popover
      key="more-tags-popover"
      content={(
        <Row type="flex" align="start" style={{flexDirection: 'column'}}>
          {result.map(r => r.element)}
        </Row>
      )}
    >
      <a className={styles.moreLabel}>
        +{result.length - tagsToDisplayCount} more
      </a>
    </Popover>
  );
  return (
    <div
      className={className}
      style={{display: 'inline'}}
    >
      {result.slice(0, tagsToDisplayCount).map(r => r.element)}
      {popover}
    </div>
  );
}

RunTags.shouldDisplayTags = function (run, onlyKnown = false) {
  if (!run) {
    return false;
  }
  const {status, tags} = run;
  if (!tags || !activeRunStatuses.includes(status)) {
    return false;
  }
  for (let tag in tags) {
    if (
      Object.prototype.hasOwnProperty.call(tags, tag) &&
      !skipTag(tag, tags) &&
      (!onlyKnown || isKnownTag(tag))
    ) {
      return true;
    }
  }
  return false;
};
