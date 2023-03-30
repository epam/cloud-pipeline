/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

import {action, observable} from 'mobx';
import {fadeoutHex} from '../../../../../themes/utilities/color-utilities';

const PALETTE = [
  '#1E07F5',
  '#08C7E4',
  '#A4F703',
  '#F900B0',
  '#ffffff',
  '#30910f',
  '#ef1a41',
  '#bf1dec',
  '#da6d29',
  '#0ab07e',
  '#333333'
];

function pickColor (existing = []) {
  const existingCorrected = existing.map(o => o.toUpperCase());
  return PALETTE.map(color => ({
    color,
    weight: existingCorrected.filter(o => o === color.toUpperCase()).length
  }))
    .sort((a, b) => a.weight - b.weight)
    .map(o => o.color)[0] || PALETTE[0] || '#FFFFFF';
}

class OutlineObjectConfiguration {
  static importConfiguration (options) {
    return new OutlineObjectConfiguration(options);
  }
  @observable object;
  @observable visible;
  @observable color;
  @observable url;
  @observable backgroundUrl;
  fetchUrl;
  fetchBackgroundUrl;
  constructor (options = {}) {
    const {
      object,
      visible,
      color,
      url,
      backgroundUrl,
      fetchUrl,
      fetchBackgroundUrl
    } = options;
    this.object = object;
    this.visible = visible;
    this.color = color;
    this.url = url;
    this.fetchUrl = fetchUrl;
    this.backgroundUrl = backgroundUrl;
    this.fetchBackgroundUrl = fetchBackgroundUrl;
  }

  fetchUrls = async () => {
    try {
      const [url, backgroundUrl] = await Promise.all(
        [
          this.fetchUrl,
          this.fetchBackgroundUrl
        ]
          .filter(Boolean)
          .map(fn => fn())
      );
      this.url = url || this.url;
      this.backgroundUrl = backgroundUrl || this.backgroundUrl;
    } catch (e) {
      console.warn(e.message);
    }
  };

  exportConfiguration = () => {
    return {
      object: this.object,
      color: this.color,
      visible: this.visible
    };
  };
}

export default class GraphicsOutputConfiguration {
  static importConfigurations (configurations) {
    const result = new GraphicsOutputConfiguration();
    result.configurations = JSON.parse(configurations)
      .map(config => OutlineObjectConfiguration.importConfiguration(config));
    return result;
  }
  /**
   * @type {OutlineObjectConfiguration[]}
   */
  @observable configurations = [];
  @observable hidden = false;
  /**
   * @type {AnalysisOutputResult}
   * @private
   */
  @observable _overlayImage = undefined;
  constructor (analysisOutputs = []) {
    this.update(analysisOutputs);
  }

  /**
   * @param {AnalysisOutputResult[]} analysisOutputs
   * @param {AnalysisOutputResult} overlayImage
   * @param {*} [viewer]
   */
  update (analysisOutputs = [], overlayImage, viewer) {
    const objectNames = [...new Set(analysisOutputs.filter(o => o.object).map(o => o.object))];
    /**
     * @type {OutlineObjectConfiguration[]}
     */
    const objects = objectNames.map(name => {
      const outline = analysisOutputs.find(o => o.object === name && !o.background);
      const background = analysisOutputs.find(o => o.object === name && o.background);
      if (outline || background) {
        return new OutlineObjectConfiguration({
          object: name,
          url: outline ? outline.url : undefined,
          fetchUrl: outline ? outline.fetchUrlAndReportAccess : undefined,
          backgroundUrl: background ? background.url : undefined,
          fetchBackgroundUrl: background ? background.fetchUrlAndReportAccess : undefined,
          visible: true
        });
      }
      return undefined;
    }).filter(Boolean);
    const newObjects = objects
      .filter(object => !this.configurations.find(c => c.object === object.object));
    const updated = this.configurations
      .filter(config => !!objects.find(o => o.object === config.object))
      .concat(newObjects);
    updated.forEach(config => {
      const existed = objects.find(o => o.object === config.object);
      config.url = existed ? existed.url : config.url;
      config.fetchUrl = existed ? existed.fetchUrl : config.fetchUrl;
      config.backgroundUrl = existed ? existed.backgroundUrl : config.backgroundUrl;
      config.fetchBackgroundUrl = existed ? existed.fetchBackgroundUrl : config.fetchBackgroundUrl;
      if (!config.color) {
        const currentColors = updated.map(o => o.color).filter(Boolean);
        config.color = pickColor(currentColors);
      }
    });
    this.configurations = updated;
    this._overlayImage = overlayImage;
    (this.renderOutlines)(viewer);
  }

  detachResults = (hcsImageViewer) => {
    this._overlayImage = undefined;
    this.configurations.forEach(aConfiguration => {
      aConfiguration.url = undefined;
      aConfiguration.backgroundUrl = undefined;
      aConfiguration.fetchUrl = undefined;
      aConfiguration.fetchBackgroundUrl = undefined;
    });
    (this.renderOutlines)(hcsImageViewer);
  };

  @action
  moveDown (idx, viewer) {
    const current = this.configurations[idx];
    const next = this.configurations[idx + 1];
    if (current && next) {
      const result = [...this.configurations];
      result.splice(idx, 2, next, current);
      this.configurations = result;
      (this.renderOutlines)(viewer);
    }
  }

  @action
  moveUp (idx, viewer) {
    if (idx === 0) {
      return;
    }
    const current = this.configurations[idx];
    const previous = this.configurations[idx - 1];
    if (current && previous) {
      const result = [...this.configurations];
      result.splice(idx - 1, 2, current, previous);
      this.configurations = result;
      (this.renderOutlines)(viewer);
    }
  }

  setOverlayImage = (overlayImage, viewer) => {
    this._overlayImage = overlayImage;
    return this.renderOutlines(viewer);
  };

  outputIsSelectedAsOverlayImage = (overlayImage) => {
    return this._overlayImage &&
      overlayImage &&
      this._overlayImage.id === overlayImage.id;
  };

  renderOutlines = async (hcsImageViewer) => {
    if (!hcsImageViewer) {
      return;
    }
    const setOverlayImages = images => hcsImageViewer.setOverlayImages(images);
    const overlays = [];
    if (this._overlayImage) {
      const url = await this._overlayImage.fetchUrl();
      overlays.push({
        url,
        color: '#FFFFFF',
        ignoreColor: '#000000',
        ignoreColorAccuracy: 0.08
      });
    }
    if (this.hidden) {
      setOverlayImages(overlays);
    } else {
      await Promise.all(
        this.configurations
          .filter(configuration => configuration.visible)
          .map(configuration => configuration.fetchUrls())
      );
      const objectImages = this.configurations
        .filter(outline => outline.visible)
        .map(outline => [
          outline.backgroundUrl ? {
            url: outline.backgroundUrl,
            color: fadeoutHex(outline.color, 0.6)
          } : undefined,
          outline.url ? {url: outline.url, color: outline.color} : undefined
        ])
        .reduce((r, c) => ([...r, ...c]), [])
        .filter(Boolean);
      setOverlayImages(
        [
          ...overlays,
          ...objectImages
        ]
      );
    }
  };

  exportConfigurations () {
    return JSON.stringify(
      this.configurations.map(config => config.exportConfiguration())
    );
  }
}
