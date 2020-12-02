/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
import {inject, observer} from 'mobx-react';
import {Icon} from 'antd';
import classNames from 'classnames';
import ToolImage from '../../../models/tools/ToolImage';
import styles from './docker-image-details.css';

function DockerImageDetails ({className, docker, dockerRegistries, onlyImage = false}) {
  if (!docker) {
    return null;
  }
  const [r, g, iv] = docker.split('/');
  const [i, v] = iv.split(':');
  let registry = r;
  let id, iconId;
  if (dockerRegistries.loaded) {
    const rObj = (dockerRegistries.value.registries || [])
      .find(reg => (reg.path || '').toLowerCase() === r.toLowerCase());
    if (rObj) {
      registry = rObj.description || rObj.path;
      const group = (rObj.groups || [])
        .find(gr => (gr.name || '').toLowerCase() === g.toLowerCase());
      if (group) {
        const tool = (group.tools || [])
          .find(t => (t.image || '').toLowerCase() === `${g}/${i}`.toLowerCase());
        if (tool && tool.hasIcon && tool.iconId) {
          iconId = tool.iconId;
          id = tool.id;
        }
      }
    }
  }
  return (
    <div key={docker} className={classNames(styles.container, className)}>
      <span
        className={classNames(styles.sub, {[styles.hidden]: onlyImage})}
      >
        {registry}
      </span>
      <Icon
        className={classNames(styles.sub, {[styles.hidden]: onlyImage})}
        type="right"
      />
      <span
        className={classNames(styles.sub, {[styles.hidden]: onlyImage})}
      >
        {g}
      </span>
      <Icon
        className={classNames(styles.sub, {[styles.hidden]: onlyImage})}
        type="right"
      />
      {
        id && iconId && (
          <img
            src={ToolImage.url(id, iconId)}
          />
        )
      }
      <span
        className={styles.main}
      >
        {i}
      </span>
      {
        v && v !== 'latest' && (
          <span
            className={styles.version}
          >
            {v}
          </span>
        )
      }
    </div>
  );
}

export default inject('dockerRegistries')(observer(DockerImageDetails));
