/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

const SECTIONS = {
  logs: 'logs',
  tools: 'tools',
  pipelines: 'pipelines'
};

export function estimatedPriceVisible (launchFormSettings) {
  const getSectionVisibility = (section) => {
    const config = launchFormSettings?.[section] || {};
    const {
      'estimates-visible': estimatesVisible = true,
      'estimated-price-visible': estimatedPriceSectionVisible = estimatesVisible
    } = config;
    return `${estimatedPriceSectionVisible}`.toLowerCase() === 'true';
  };
  return {
    logs: getSectionVisibility(SECTIONS.logs),
    pipelines: getSectionVisibility(SECTIONS.pipelines),
    tools: getSectionVisibility(SECTIONS.tools)
  };
}

export function showOptionalParametersFilter (launchFormSettings) {
  const getSectionVisibility = (section) => {
    const config = launchFormSettings?.[section] || {};
    const {
      'optional-parameters-filter': showOptional
    } = config;
    return `${showOptional}`.toLowerCase() === 'true';
  };
  return {
    pipelines: getSectionVisibility(SECTIONS.pipelines),
    tools: getSectionVisibility(SECTIONS.tools)
  };
}
