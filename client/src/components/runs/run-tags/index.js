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
import classNames from 'classnames';
import {Link} from 'react-router';
import styles from './run-tags.css';
import moment from 'moment-timezone';
import RunTagDatePopover from './run-tag-date-popover';

const activeRunStatuses = ['RUNNING', 'PAUSED', 'PAUSING', 'RESUMING'];
const PREDEFINED_TAGS = [{
  tag: 'idle',
  color: 'warning'
}, {
  tag: 'pressure',
  color: 'critical'
}, {
  tag: 'sge_in_use',
  color: 'primary'
}, {
  tag: 'slurm_in_use',
  color: 'primary'
}, {
  tag: 'recovered',
  color: 'critical, hovered'
}, {
  tag: 'node_unavailable'
}, {
  tag: 'proc_out_of_memory',
  color: 'critical'
}];

const KNOWN_COLORS = {
  default: '',
  warning: 'warning',
  critical: 'critical',
  accent: 'hovered',
  primary: 'primary'
};

const mergePredefinedAndUserTags = (predefinedTags = [], userTags = []) => {
  const tags = predefinedTags.map(tag => typeof tag === 'string' ? {tag} : tag);
  userTags.forEach(userTag => {
    const currentTag = typeof userTag === 'string' ? {tag: userTag} : userTag;
    const knownIdx = tags.findIndex((t) => t.tag.toLowerCase() === currentTag.tag.toLowerCase());
    if (knownIdx >= 0) {
      tags[knownIdx] = Object.assign(tags[knownIdx], currentTag);
      return;
    }
    tags.push(currentTag);
  });
  return tags;
};

const isKnownTag = (tagName, preferences = {}) => {
  const userTags = preferences.uiRunsTags || [];
  return mergePredefinedAndUserTags(PREDEFINED_TAGS, userTags)
    .some(t => t.tag.toLowerCase() === (tagName || '').toLowerCase());
};

const isKnownTagWithDateSuffix = (tagName, preferences = {}) => {
  const suffix = preferences.systemRunTagDateSuffix;
  const userTags = preferences.uiRunsTags || [];
  const knownTagsWithDateSuffix = mergePredefinedAndUserTags(
    PREDEFINED_TAGS,
    userTags
  ).map(({tag}) => `${tag}${suffix}`);
  return knownTagsWithDateSuffix.some((tag) => tag.toLowerCase() === (tagName || '').toLowerCase());
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

const getTagColors = (color = '') => {
  if (!color.length) {
    return [];
  }
  return color
    .split(',')
    .filter(Boolean)
    .map(color => KNOWN_COLORS[color.trim().toLowerCase()] || color);
};

function Tag (
  {
    className,
    tagName,
    value,
    instance,
    theme,
    onMouseEnter,
    onMouseLeave,
    onClick,
    onFocus,
    predefinedTags
  }
) {
  let display = value;
  if (`${value}` === 'true') {
    display = tagName;
  }
  const tagOptions = predefinedTags
    .find(({tag}) => tag.toLowerCase() === tagName.toLowerCase()) || {};
  const isInstanceLink = instance &&
    instance.nodeName &&
    `${tagOptions.instanceLink}` !== 'false';
  const element = (
    <span
      className={
        classNames(
          styles.runTag,
          className,
          'cp-tag',
          'accent',
          ...getTagColors(tagOptions.color),
          {
            filled: /^black$/i.test(theme),
            link: tagOptions.link || isInstanceLink
          }
        )
      }
      onMouseEnter={onMouseEnter}
      onMouseLeave={onMouseLeave}
      onClick={onClick}
      onFocus={onFocus}
    >
      {(display || '').toUpperCase()}
    </span>
  );
  if (tagOptions.link) {
    return (
      <a
        className={styles.link}
        onMouseEnter={onMouseEnter}
        onMouseLeave={onMouseLeave}
        onClick={onClick}
        onFocus={onFocus}
        href={tagOptions.link}
        target="_blank"
      >
        {element}
      </a>
    );
  }
  if (isInstanceLink) {
    const instanceLink = `/cluster/${instance.nodeName}/monitor`;
    return (
      <Link
        id={tagName}
        to={instanceLink}
        className={styles.link}
        onMouseEnter={onMouseEnter}
        onMouseLeave={onMouseLeave}
        onClick={onClick}
        onFocus={onFocus}
      >
        {element}
      </Link>
    );
  }
  return element;
}

function RunTagsComponent (
  {
    className,
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
  const predefinedTags = mergePredefinedAndUserTags(
    PREDEFINED_TAGS,
    preferences.uiRunsTags || []
  );
  for (let tagName in tags) {
    if (
      Object.prototype.hasOwnProperty.call(tags, tagName) &&
      !skipTag(tagName, tags, preferences) &&
      (!onlyKnown || isKnownTag(tagName, preferences))
    ) {
      const info = getDateInfo(tags, tagName, preferences);
      result.push({
        isKnown: isKnownTag(tagName, preferences),
        element: (
          <RunTagDatePopover
            date={info}
            key={tagName}
            tag={tagName}
          >
            <Tag
              className={tagClassName}
              tagName={tagName}
              value={tags[tagName]}
              instance={instance}
              theme={theme}
              predefinedTags={predefinedTags}
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
