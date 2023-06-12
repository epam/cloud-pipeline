/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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
import {Button, Icon, Popover} from 'antd';
import styles from './ome-tiff-annotations-renderer.css';

function AnnotationsTooltip () {
  return (
    <Popover
      content={(
        <div>
          <div className={styles.infoRow} style={{margin: '5px 0'}}>
            To create annotation use the following controls:
          </div>
          <div className={styles.infoRow}>
            <Button
              size="small"
              className={styles.actionInfo}
            >
              <Icon type="plus-circle-o" />
            </Button>
            <span style={{marginLeft: 5}}>
              - add circle annotation
            </span>
          </div>
          <div className={styles.infoRow}>
            <Button
              size="small"
              className={styles.actionInfo}
            >
              <Icon type="plus-square-o" />
            </Button>
            <span style={{marginLeft: 5}}>
              - add rectangle annotation
            </span>
          </div>
          <div className={styles.infoRow}>
            <Button
              size="small"
              className={styles.actionInfo}
            >
              <Icon
                type="arrow-up"
                style={{
                  transform: 'rotate(-45deg)'
                }}
              />
            </Button>
            <span style={{marginLeft: 5}}>
              - add arrow annotation
            </span>
          </div>
          <div className={styles.infoRow}>
            <Button
              size="small"
              className={styles.actionInfo}
            >
              <Icon type="edit" />
            </Button>
            <span style={{marginLeft: 5}}>
              - add path annotation
            </span>
          </div>
          <div className={styles.infoRow}>
            <Button
              size="small"
              className={styles.actionInfo}
            >
              A
            </Button>
            <span style={{marginLeft: 5}}>
              - add text annotation
            </span>
          </div>
          <div className={styles.infoRow} style={{margin: '5px 0'}}>
            To modify annotation (resize / move / delete) select it first and then:
          </div>
          <div className={styles.infoRow}>
            <span style={{marginLeft: 5}}>
              - drag it to move
            </span>
          </div>
          <div className={styles.infoRow}>
            <span style={{marginLeft: 5}}>
              - SHIFT + drag it to resize
            </span>
          </div>
          <div className={styles.infoRow}>
            <span style={{marginLeft: 5, marginRight: 5}}>
              - click
            </span>
            <Button
              size="small"
              type="danger"
              className={styles.actionInfo}
            >
              Remove
            </Button>
            <span style={{marginLeft: 5}}>
              to delete it
            </span>
          </div>
        </div>
      )}
    >
      <Icon
        type="info-circle"
        className="tooltip-text"
        style={{
          cursor: 'pointer',
          marginRight: 10,
          fontSize: 'larger'
        }}
      />
    </Popover>
  );
}

export default AnnotationsTooltip;
