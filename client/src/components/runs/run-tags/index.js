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
import {inject, observer} from 'mobx-react';
import AdaptedLink from '../../special/AdaptedLink';
import styles from './run-tags.css';
import moment from 'moment-timezone';
import RunTagDatePopover from './run-tag-date-popover';

const activeRunStatuses = ['RUNNING', 'PAUSED', 'PAUSING', 'RESUMING'];
const KNOWN_TAGS = [
  'idle',
  'pressure'
];

const isInstanceLink = (tag) => {
  return KNOWN_TAGS.some(t => t.toLowerCase() === (tag || '').toLowerCase());
};

const isKnownTag = (tag, preferenes) => {
  return KNOWN_TAGS.some(t => t.toLowerCase() === (tag || '').toLowerCase());
};

const isKnownTagWithDateSuffix = (tag, preferences) => {
  const suffix = preferences.systemRunTagDateSuffix;
  const knownTagsWithDateSuffix = KNOWN_TAGS.map((knownTag) => `${knownTag}${suffix}`);
  return knownTagsWithDateSuffix.some(t => t.toLowerCase() === (tag || '').toLowerCase());
};

const getDateInfo = (tags, tag, preferences) => {
  const suffix = preferences.systemRunTagDateSuffix;
  const tagName = `${tag}${suffix}`;
  if (Object.prototype.hasOwnProperty.call(tags, tagName)) {
    const since = moment.utc(tags[tagName]);
    if (since.isValid()) {
      return since;
    }
  }
  return undefined;
};

const skipTag = (tag, tags, preferences) => {
  return `${tags[tag]}` === 'false' ||
    /^alias$/i.test(tag) ||
    isKnownTagWithDateSuffix(tag, preferences);
};

function Tag (
  {
    className,
    tag,
    value,
    instance,
    location,
    theme,
    onMouseEnter,
    onMouseLeave,
    onClick,
    onFocus
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
      onMouseEnter={onMouseEnter}
      onMouseLeave={onMouseLeave}
      onClick={onClick}
      onFocus={onFocus}
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
        onMouseEnter={onMouseEnter}
        onMouseLeave={onMouseLeave}
        onClick={onClick}
        onFocus={onFocus}
      >
        {element}
      </AdaptedLink>
    );
  }
  return element;
}

function RunTagsComponent (
  {
    className,
    location,
    onlyKnown,
    overflow,
    tagClassName,
    run,
    theme,
    preferences
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
      !skipTag(tag, tags, preferences) &&
      (!onlyKnown || isKnownTag(tag, preferences))
    ) {
      const info = getDateInfo(tags, tag, preferences);
      result.push({
        isKnown: isKnownTag(tag, preferences),
        element: (
          <RunTagDatePopover
            date={info}
            key={tag}
            tag={tag}
          >
            <Tag
              className={tagClassName}
              tag={tag}
              value={tags[tag]}
              instance={instance}
              location={location}
              theme={theme}
            />
          </RunTagDatePopover>
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

const RunTags = inject('preferences')(observer(RunTagsComponent));

RunTags.shouldDisplayTags = function (run, preferences, onlyKnown = false) {
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
      !skipTag(tag, tags, preferences) &&
      (!onlyKnown || isKnownTag(tag, preferences))
    ) {
      return true;
    }
  }
  return false;
};

export default RunTags;
