/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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
import PropTypes from 'prop-types';
import classNames from 'classnames';
import {computed} from 'mobx';
import {
  processLinks,
  prepareCloudPipelineLinks,
  getCloudPipelineAbsoluteURL,
  injectCloudPipelineLinksHelpers,
  fetchCloudPipelineLinks,
  getCloudPipelineLinks
} from './utilities';
import getMarkdownRenderer, {renderHtml} from './renderer';

export {
  injectCloudPipelineLinksHelpers,
  fetchCloudPipelineLinks,
  getCloudPipelineLinks,
  getCloudPipelineAbsoluteURL,
  renderHtml,
  getMarkdownRenderer,
  processLinks
};

@injectCloudPipelineLinksHelpers
class Markdown extends React.Component {
  constructor (props) {
    super(props);
    this.renderer = getMarkdownRenderer({
      links: () => this.links,
      renderPipelineLinkIcon: () => this.renderPipelineLinkIcon,
      renderPipelineLinks: () => this.renderPipelineLinks,
      getLink: (url) => this.getLink(url)
    });
  }

  @computed
  get links () {
    const {
      dockerRegistries,
      pipelinesLibrary,
      hiddenObjectsTreeFilter,
      hiddenToolsTreeFilter
    } = this.props;
    return getCloudPipelineLinks({
      dockerRegistries,
      pipelinesLibrary,
      hiddenObjectsTreeFilter,
      hiddenToolsTreeFilter
    });
  };

  get renderPipelineLinks () {
    const {cloudPipelineLinks} = this.props;
    return !!cloudPipelineLinks;
  }

  get renderPipelineLinkIcon () {
    const {cloudPipelineLinks} = this.props;
    return cloudPipelineLinks &&
      (
        typeof cloudPipelineLinks !== 'object' ||
        (
          cloudPipelineLinks.icon === undefined ||
          cloudPipelineLinks.icon
        )
      );
  }

  getLink (url) {
    return getCloudPipelineAbsoluteURL(url, this.props);
  }

  render () {
    const {
      className,
      id,
      md,
      style,
      target,
      onClick
    } = this.props;
    if (!md) {
      return null;
    }
    let html = this.renderer.render(
      this.renderPipelineLinks
        ? prepareCloudPipelineLinks(md)
        : md
    );
    if (target) {
      html = processLinks(html, target);
    }
    return (
      <div
        id={id}
        className={classNames(className, 'markdown')}
        dangerouslySetInnerHTML={{__html: html}}
        style={style}
        onClick={onClick}
      />
    );
  }
}

Markdown.propTypes = {
  className: PropTypes.string,
  id: PropTypes.string,
  md: PropTypes.string,
  style: PropTypes.object,
  target: PropTypes.string,
  cloudPipelineLinks: PropTypes.oneOfType([
    PropTypes.bool,
    PropTypes.shape({
      absoluteUri: PropTypes.bool,
      icon: PropTypes.bool
    })
  ]),
  onClick: PropTypes.func
};

export default Markdown;
