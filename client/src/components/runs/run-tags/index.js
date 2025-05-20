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
import RunTagPopover from './run-tag-popover';

const activeRunStatuses = ['RUNNING', 'PAUSED', 'PAUSING', 'RESUMING'];

const KNOWN_TAG_NAMES = {
  idle: 'idle',
  pressure: 'pressure',
  sge_in_use: 'sge_in_use',
  slurm_in_use: 'slurm_in_use',
  recovered: 'recovered',
  node_unavailable: 'node_unavailable',
  proc_out_of_memory: 'proc_out_of_memory',
  network_limit: 'network_limit',
  network_pressure: 'network_pressure',
  long_running: 'long_running',
  mlflow_experiment: 'CP_MLFLOW_EXPERIMENT_ID',
  mlflow_run: 'CP_MLFLOW_RUN_UUID'
};

const KNOWN_TAG_RENDER = {
  [KNOWN_TAG_NAMES.network_limit.toLowerCase()]: (name) => name,
  [KNOWN_TAG_NAMES.mlflow_experiment.toLowerCase()]: mlflowExperimentTagRenderer,
  [KNOWN_TAG_NAMES.mlflow_run.toLowerCase()]: tagSemiValueRenderer
};

export function networkLimitValueRender (value) {
  if (value === undefined || value === null) {
    return undefined;
  }
  let str = value;
  if (typeof str === 'string') {
    str = str.trim();
  }
  const bytes = Number(str);
  if (Number.isNaN(bytes)) {
    return `${str}`;
  }
  const minimalValue = 0.01;
  const mb = bytes / Math.pow(1024, 2);
  if (mb > 0 && mb < minimalValue) {
    return `< ${minimalValue} Mb/s`;
  }
  return `${mb.toFixed(2)} Mb/s`;
}

const KNOWN_TAG_VALUE_RENDER = {
  [KNOWN_TAG_NAMES.network_limit]: (name, value) => networkLimitValueRender(value),
  [KNOWN_TAG_NAMES.mlflow_experiment]: (_, value) => value,
  [KNOWN_TAG_NAMES.mlflow_run]: (_, value) => value
};

const KNOWN_TAG_PRETTY_NAME = {
  [KNOWN_TAG_NAMES.mlflow_experiment.toLowerCase()]: 'MLFLOW EXPERIMENT',
  [KNOWN_TAG_NAMES.mlflow_run.toLowerCase()]: 'MLFLOW RUN'
};

function getTagName (tag) {
  return KNOWN_TAG_PRETTY_NAME[tag.toLowerCase()] || tag;
}

function collapseText (text, size = 10) {
  return text.slice(0, size);
}

function tagSemiValueRenderer (tag, value) {
  if (value) {
    return `${getTagName(tag)}: ${collapseText(String(value), 8)}`;
  }
  return getTagName(tag);
}

function mlflowExperimentTagRenderer (tag, value) {
  if (value) {
    const experiments = value.split(',').map((p) => p.trim()).filter((p) => p.length > 0);
    if (experiments.length > 1) {
      return `${experiments.length} MLFLOW EXPERIMENTS`;
    }
  }
  return tagSemiValueRenderer(tag, value);
}

const PREDEFINED_TAGS = [{
  tag: KNOWN_TAG_NAMES.idle,
  color: 'warning'
}, {
  tag: KNOWN_TAG_NAMES.pressure,
  color: 'critical'
}, {
  tag: KNOWN_TAG_NAMES.sge_in_use,
  color: 'primary'
}, {
  tag: KNOWN_TAG_NAMES.slurm_in_use,
  color: 'primary'
}, {
  tag: KNOWN_TAG_NAMES.recovered,
  color: 'critical, hovered'
}, {
  tag: KNOWN_TAG_NAMES.node_unavailable
}, {
  tag: KNOWN_TAG_NAMES.proc_out_of_memory,
  color: 'critical'
}, {
  tag: KNOWN_TAG_NAMES.network_limit,
  color: 'critical, accent'
}, {
  tag: KNOWN_TAG_NAMES.network_pressure,
  color: 'critical'
}, {
  tag: KNOWN_TAG_NAMES.long_running,
  color: 'warning'
}, {
  tag: KNOWN_TAG_NAMES.mlflow_experiment,
  color: 'primary',
  instanceLink: false
}, {
  tag: KNOWN_TAG_NAMES.mlflow_run,
  color: 'primary',
  instanceLink: false
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

const getValue = (tags, tag) => {
  const valueRenderer = KNOWN_TAG_VALUE_RENDER[tag];
  if (valueRenderer && typeof valueRenderer === 'function') {
    return valueRenderer(tag, (tags || {})[tag]);
  }
  return undefined;
};

const skipTag = (tag, tags, preferences) => {
  return `${tags[tag]}` === 'false' ||
    /^alias$/i.test(tag) ||
    isKnownTagWithDateSuffix(tag, preferences);
};

const isUserTag = (tag, preferences) => {
  const userTags = preferences.uiRunsTags || [];
  return userTags.some((t) => t.tag === tag);
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
    onFocus,
    predefinedTags,
    interactive = true,
    small = true
  }
) {
  let display = value;
  if (`${value}` === 'true') {
    display = getTagName(tagName);
  }
  const tagRenderFn = KNOWN_TAG_RENDER[tagName.toLowerCase()];
  if (tagRenderFn && typeof tagRenderFn === 'function') {
    display = tagRenderFn(tagName, value);
  }
  const tagOptions = predefinedTags
    .find(({tag}) => tag.toLowerCase() === tagName.toLowerCase()) || {};
  const isInstanceLink = instance &&
    instance.nodeName &&
    `${tagOptions.instanceLink}` !== 'false' &&
    !tagOptions.userTag;
  const handleClick = event => {
    if (tagOptions.link || isInstanceLink) {
      event && event.stopPropagation();
    }
  };
  let valueToDisplay = tagOptions.display || (display || '').toUpperCase();
  if (tagOptions.userTag && `${value}`.toLowerCase() !== 'true' && `${value}`.trim().length > 0) {
    const v = `${value}`.trim();
    valueToDisplay = `${tagOptions.display || tagOptions.tag}: ${v}`;
  }
  const element = (
    <span
      className={
        classNames(
          styles.runTag,
          {[styles.small]: small},
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
      onMouseEnter={interactive ? onMouseEnter : undefined}
      onMouseLeave={interactive ? onMouseLeave : undefined}
      onClick={interactive ? handleClick : undefined}
      onFocus={interactive ? onFocus : undefined}
    >
      {valueToDisplay}
    </span>
  );
  if (tagOptions.link) {
    return (
      <a
        className={styles.link}
        onMouseEnter={interactive ? onMouseEnter : undefined}
        onMouseLeave={interactive ? onMouseLeave : undefined}
        onClick={interactive ? handleClick : undefined}
        onFocus={interactive ? onFocus : undefined}
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
        onMouseEnter={interactive ? onMouseEnter : undefined}
        onMouseLeave={interactive ? onMouseLeave : undefined}
        onClick={interactive ? handleClick : undefined}
        onFocus={interactive ? onFocus : undefined}
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
    style = {display: 'inline'},
    onlyKnown,
    overflow,
    tagClassName,
    run,
    theme,
    preferences,
    excludeTags = [],
    excludeCustomUserTags = false,
    showOnlyCustomUserTags: showOnlyCustomUserTagsProps = false,
    interactive = true,
    small = true
  }
) {
  if (!run) {
    return null;
  }
  const {status, tags, instance} = run;
  const showOnlyCustomUserTags = showOnlyCustomUserTagsProps || !activeRunStatuses.includes(status);
  if (!tags) {
    return null;
  }
  const result = [];
  const predefinedTags = mergePredefinedAndUserTags(
    PREDEFINED_TAGS,
    preferences.uiRunsTags || []
  );
  const timestampTagHasCounterpart = (tagName) => {
    const suffix = preferences.systemRunTagDateSuffix;
    return suffix &&
      suffix.length > 0 &&
      tagName.toLowerCase().endsWith(suffix.toLowerCase()) &&
      Object.prototype.hasOwnProperty.call(tags, tagName.slice(0, tagName.length - suffix.length));
  };

  const customUserTags = preferences.uiRunsUserTags.map(({tag}) => tag);

  for (let tagName in tags) {
    if (
      Object.prototype.hasOwnProperty.call(tags, tagName) &&
      !skipTag(tagName, tags, preferences) &&
      (!onlyKnown || isKnownTag(tagName, preferences))
    ) {
      if (
        timestampTagHasCounterpart(tagName) ||
        excludeTags.includes(tagName.toLowerCase()) ||
        (excludeCustomUserTags && customUserTags.includes(tagName)) ||
        (showOnlyCustomUserTags && !customUserTags.includes(tagName))
      ) {
        continue;
      }
      const info = getDateInfo(tags, tagName, preferences);
      const value = getValue(tags, tagName);
      result.push({
        isKnown: isKnownTag(tagName, preferences),
        element: (
          <RunTagPopover
            date={info}
            value={value}
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
              interactive={interactive}
              small={small}
            />
          </RunTagPopover>
        )
      });
    }
  }
  result.sort((rA, rB) => rB.isKnown - rA.isKnown);
  if (result.length === 0) {
    return null;
  }
  if (!overflow && overflow !== 0) {
    return (
      <div
        className={className}
        style={style}
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
        style={style}
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
      style={style}
    >
      {result.slice(0, tagsToDisplayCount).map(r => r.element)}
      {popover}
    </div>
  );
}

const RunTags = inject('preferences')(observer(RunTagsComponent));

RunTags.shouldDisplayTags = function (
  run,
  preferences,
  onlyKnown = false,
  excludeUserTags = false
) {
  if (!run) {
    return false;
  }
  const {status, tags} = run;
  const onlyUserTags = !activeRunStatuses.includes(status);
  if (!tags) {
    return false;
  }
  let tagsCount = 0;
  for (let tag in tags) {
    const userTag = isUserTag(tag, preferences);
    if (excludeUserTags && userTag) {
      continue;
    }
    if (
      Object.prototype.hasOwnProperty.call(tags, tag) &&
      !skipTag(tag, tags, preferences) &&
      (!onlyKnown || isKnownTag(tag, preferences)) &&
      (!onlyUserTags || isUserTag(tag, preferences))
    ) {
      tagsCount += 1;
    }
  }
  return tagsCount > 0;
};

export {KNOWN_TAG_NAMES};
export default RunTags;
