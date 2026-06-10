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

const VariableTypes = {
  color: 'color',
  image: 'image',
  icon: 'icon',
  divider: 'divider',
  providerIcon: 'provider-icon',
  regionIcon: 'region-icon',
};

const Variables = {
  applicationBackgroundColor: {
    key: '--cp-color-bg-layout',
    name: 'Application background color',
    type: VariableTypes.color,
  },
  applicationColor: {
    key: '--cp-color-text',
    name: 'Text color',
    type: VariableTypes.color,
  },
  applicationColorFaded: {
    key: '--cp-color-text-secondary',
    name: 'Text color faded',
    type: VariableTypes.color,
  },
  applicationColorDisabled: {
    key: '--cp-color-text-disabled',
    name: 'Disabled text color',
    type: VariableTypes.color,
  },
  applicationColorAccent: {
    key: '--cp-color-text-accent',
    name: 'Accented text color',
    type: VariableTypes.color,
  },
  primaryColor: {
    key: '--cp-color-primary',
    name: 'Primary action color',
    type: VariableTypes.color,
  },
  primaryHoverColor: {
    key: '--cp-color-primary-hover',
    name: 'Primary action hovered color',
    type: VariableTypes.color,
  },
  primaryActiveColor: {
    key: '--cp-color-primary-active',
    name: 'Primary action active color',
    type: VariableTypes.color,
  },
  primaryTextColor: {
    key: '--cp-color-primary-on',
    name: 'Primary action text color',
    type: VariableTypes.color,
  },
  primaryColorSemiTransparent: {
    key: '--cp-color-primary-muted',
    name: 'Metadata table selection background',
    type: VariableTypes.color,
  },
  colorSuccess: {
    key: '--cp-color-success',
    name: 'Success status color',
    type: VariableTypes.color,
  },
  colorError: {
    key: '--cp-color-error',
    name: 'Error status color',
    type: VariableTypes.color,
  },
  colorWarning: {
    key: '--cp-color-warning',
    name: 'Warning status color',
    type: VariableTypes.color,
  },
  colorInfo: {
    key: '--cp-color-info',
    name: 'Info color',
    type: VariableTypes.color,
  },
  colorGreen: {
    key: '--cp-color-green',
    name: 'Green color',
    type: VariableTypes.color,
  },
  colorRed: {
    key: '--cp-color-red',
    name: 'Red color',
    type: VariableTypes.color,
  },
  colorYellow: {
    key: '--cp-color-yellow',
    name: 'Yellow color',
    type: VariableTypes.color,
  },
  colorBlue: {
    key: '--cp-color-blue',
    name: 'Blue color',
    type: VariableTypes.color,
  },
  colorViolet: {
    key: '--cp-color-violet',
    name: 'Violet color',
    type: VariableTypes.color,
  },
  colorSensitive: {
    key: '--cp-color-sensitive',
    name: 'Sensitive object color',
    type: VariableTypes.color,
  },
  colorAqua: {
    key: '--cp-color-aqua',
    name: 'Aqua color',
    type: VariableTypes.color,
  },
  colorAquaLight: {
    key: '--cp-color-aqua-light',
    name: 'Aqua light color',
    type: VariableTypes.color,
  },
  colorPink: {
    key: '--cp-color-pink',
    name: 'Pink color',
    type: VariableTypes.color,
  },
  colorPinkDusty: {
    key: '--cp-color-pink-dusty',
    name: 'Dusty pink color',
    type: VariableTypes.color,
  },
  colorPinkLight: {
    key: '--cp-color-pink-light',
    name: 'Light pink color',
    type: VariableTypes.color,
  },
  colorBlueDimmed: {
    key: '--cp-color-blue-dimmed',
    name: 'Blue dimmed color',
    type: VariableTypes.color,
  },
  colorGrey: {
    key: '--cp-color-grey',
    name: 'Grey color',
    type: VariableTypes.color,
  },
  spinner: {
    key: '--cp-color-spinner',
    name: 'Loading indicator color',
    type: VariableTypes.color,
  },
  elementHoverColor: {
    key: '--cp-color-interactive-hover-text',
    name: 'Lists: hovered element text color',
    type: VariableTypes.color,
  },
  elementHoverBackgroundColor: {
    key: '--cp-color-interactive-hover-bg',
    name: 'Lists: hovered element background',
    type: VariableTypes.color,
  },
  elementSelectedColor: {
    key: '--cp-color-interactive-selected-text',
    name: 'Lists: selected element text color',
    type: VariableTypes.color,
  },
  elementSelectedBackgroundColor: {
    key: '--cp-color-interactive-selected-bg',
    name: 'Lists: selected element background',
    type: VariableTypes.color,
  },
  inputBackground: {
    key: '--cp-color-bg-input',
    name: 'Input control background',
    type: VariableTypes.color,
  },
  inputBackgroundDisabled: {
    key: '--cp-color-bg-input-disabled',
    name: 'Disabled input control background',
    type: VariableTypes.color,
  },
  inputAddon: {
    key: '--cp-color-input-addon-bg',
    name: 'Input control addon background',
    type: VariableTypes.color,
  },
  inputBorder: {
    key: '--cp-color-border-input',
    name: 'Input control border',
    type: VariableTypes.color,
  },
  inputColor: {
    key: '--cp-color-input-text',
    name: 'Input control text color',
    type: VariableTypes.color,
  },
  inputPlaceholderColor: {
    key: '--cp-color-input-placeholder',
    name: 'Input control placeholder color',
    type: VariableTypes.color,
  },
  inputBorderHoverColor: {
    key: '--cp-color-input-border-hover',
    name: 'Hovered input control border',
    type: VariableTypes.color,
  },
  inputShadowColor: {
    key: '--cp-color-input-focus-ring',
    name: 'Hovered input control shadow',
    type: VariableTypes.color,
  },
  inputSearchIconColor: {
    key: '--cp-color-input-search-icon',
    name: 'Input control search icon',
    type: VariableTypes.color,
  },
  inputSearchIconHoveredColor: {
    key: '--cp-color-input-search-icon-hover',
    name: 'Input control search icon hovered',
    type: VariableTypes.color,
  },
  panelBackgroundColor: {
    key: '--cp-color-bg-container',
    name: 'Panels background color',
    type: VariableTypes.color,
  },
  panelBorderColor: {
    key: '--cp-color-border',
    name: 'Panels border color',
    type: VariableTypes.color,
  },
  cardBackgroundColor: {
    key: '--cp-color-bg-elevated',
    name: 'Cards background color',
    type: VariableTypes.color,
  },
  cardBorderColor: {
    key: '--cp-color-border-card',
    name: 'Cards border color',
    type: VariableTypes.color,
  },
  cardHoveredShadowColor: {
    key: '--cp-color-card-shadow',
    name: 'Hovered card shadow',
    type: VariableTypes.color,
  },
  cardActionsActiveBackground: {
    key: '--cp-color-card-actions-active-bg',
    name: 'Card actions background color',
    type: VariableTypes.color,
  },
  cardHeaderBackground: {
    key: '--cp-color-bg-elevated-header',
    name: 'Card header background color',
    type: VariableTypes.color,
  },
  cardServiceBackgroundColor: {
    key: '--cp-color-bg-service-card',
    name: 'Service cards background color',
    type: VariableTypes.color,
  },
  cardServiceBorderColor: {
    key: '--cp-color-border-service-card',
    name: 'Service cards border color',
    type: VariableTypes.color,
  },
  cardServiceHoveredShadowColor: {
    key: '--cp-color-service-card-shadow',
    name: 'Service card shadow',
    type: VariableTypes.color,
  },
  cardServiceActionsActiveBackground: {
    key: '--cp-color-service-card-actions-active-bg',
    name: 'Service card actions background color',
    type: VariableTypes.color,
  },
  cardServiceHeaderBackground: {
    key: '--cp-color-service-card-header-bg',
    name: 'Service card header background color',
    type: VariableTypes.color,
  },
  navigationPanelColor: {
    key: '--cp-color-nav-bg',
    name: 'Navigation panel color',
    type: VariableTypes.color,
  },
  navigationPanelColorImpersonated: {
    key: '--cp-color-nav-bg-impersonated',
    name: 'Impersonated navigation panel color',
    type: VariableTypes.color,
  },
  navigationPanelHighlightedColor: {
    key: '--cp-color-nav-bg-active',
    name: 'Navigation panel active item background',
    type: VariableTypes.color,
  },
  navigationPanelHighlightedColorImpersonated: {
    key: '--cp-color-nav-bg-impersonated-active',
    name: 'Impersonated active item background',
    type: VariableTypes.color,
  },
  navigationItemColor: {
    key: '--cp-color-nav-text',
    name: 'Navigation panel icon color',
    type: VariableTypes.color,
  },
  navigationItemRunsColor: {
    key: '--cp-color-nav-runs',
    name: 'Navigation panel jobs icon color',
    type: VariableTypes.color,
  },
  tagKeyBackgroundColor: {
    key: '--cp-color-tag-key-bg',
    name: 'Key-value attribute: key background',
    type: VariableTypes.color,
  },
  tagKeyValueDividerColor: {
    key: '--cp-color-tag-divider',
    name: 'Key-value attribute: divider',
    type: VariableTypes.color,
  },
  tagValueBackgroundColor: {
    key: '--cp-color-tag-value-bg',
    name: 'Key-value attribute: value background',
    type: VariableTypes.color,
  },
  nfsIconColor: {
    key: '--cp-color-nfs-icon',
    name: 'NFS Storage icon',
    type: VariableTypes.color,
  },
  awsIcon: {
    key: '--cp-asset-aws-icon',
    name: 'AWS icon',
    type: VariableTypes.providerIcon,
    provider: 'AWS',
  },
  awsIconContrast: {
    key: '--cp-asset-aws-icon-contrast',
    name: 'AWS icon (contrasted)',
    type: VariableTypes.providerIcon,
    provider: 'AWS',
  },
  gcpIcon: {
    key: '--cp-asset-gcp-icon',
    name: 'GCP icon',
    type: VariableTypes.providerIcon,
    provider: 'GCP',
  },
  gcpIconContrast: {
    key: '--cp-asset-gcp-icon-contrast',
    name: 'GCP icon (contrasted)',
    type: VariableTypes.providerIcon,
    provider: 'GCP',
  },
  azureIcon: {
    key: '--cp-asset-azure-icon',
    name: 'AZURE icon',
    type: VariableTypes.providerIcon,
    provider: 'AZURE',
  },
  azureIconContrast: {
    key: '--cp-asset-azure-icon-contrast',
    name: 'AZURE icon (contrasted)',
    type: VariableTypes.providerIcon,
    provider: 'AZURE',
  },
  euRegionIcon: {
    key: '--cp-asset-eu-region-icon',
    name: 'EU region icon',
    type: VariableTypes.regionIcon,
  },
  usRegionIcon: {
    key: '--cp-asset-us-region-icon',
    name: 'US region icon',
    type: VariableTypes.regionIcon,
  },
  saRegionIcon: {
    key: '--cp-asset-sa-region-icon',
    name: 'SA region icon',
    type: VariableTypes.regionIcon,
  },
  cnRegionIcon: {
    key: '--cp-asset-cn-region-icon',
    name: 'CN region icon',
    type: VariableTypes.regionIcon,
  },
  caRegionIcon: {
    key: '--cp-asset-ca-region-icon',
    name: 'CA region icon',
    type: VariableTypes.regionIcon,
  },
  apNortheast1RegionIcon: {
    key: '--cp-asset-ap-northeast-1-region-icon',
    name: 'AP North-East 1 region icon',
    type: VariableTypes.regionIcon,
  },
  apNortheast2RegionIcon: {
    key: '--cp-asset-ap-northeast-2-region-icon',
    name: 'AP North-East 2 region icon',
    type: VariableTypes.regionIcon,
  },
  apNortheast3RegionIcon: {
    key: '--cp-asset-ap-northeast-3-region-icon',
    name: 'AP North-East 3 region icon',
    type: VariableTypes.regionIcon,
  },
  apSouth1RegionIcon: {
    key: '--cp-asset-ap-south-1-region-icon',
    name: 'AP South 1 region icon',
    type: VariableTypes.regionIcon,
  },
  apSoutheast1RegionIcon: {
    key: '--cp-asset-ap-southeast-1-region-icon',
    name: 'AP South-East 1 region icon',
    type: VariableTypes.regionIcon,
  },
  apSoutheast2RegionIcon: {
    key: '--cp-asset-ap-southeast-2-region-icon',
    name: 'AP South-East 2 region icon',
    type: VariableTypes.regionIcon,
  },
  taiwanRegionIcon: {
    key: '--cp-asset-taiwan-region-icon',
    name: 'Taiwan region icon',
    type: VariableTypes.regionIcon,
  },
  modalMaskBackground: {
    key: '--cp-color-bg-overlay',
    name: 'Dialogs overlay background',
    type: VariableTypes.color,
  },
  evenElementBackground: {
    key: '--cp-color-bg-striped',
    name: 'Even elements background',
    type: VariableTypes.color,
  },
  alertSuccessBackground: {
    key: '--cp-color-alert-success-bg',
    name: 'Success alert background',
    type: VariableTypes.color,
  },
  alertSuccessBorder: {
    key: '--cp-color-alert-success-border',
    name: 'Success alert border',
    type: VariableTypes.color,
  },
  alertSuccessIcon: {
    key: '--cp-color-alert-success-icon',
    name: 'Success alert icon',
    type: VariableTypes.color,
  },
  alertWarningBackground: {
    key: '--cp-color-alert-warning-bg',
    name: 'Warning alert background',
    type: VariableTypes.color,
  },
  alertWarningBorder: {
    key: '--cp-color-alert-warning-border',
    name: 'Warning alert border',
    type: VariableTypes.color,
  },
  alertWarningIcon: {
    key: '--cp-color-alert-warning-icon',
    name: 'Warning alert icon',
    type: VariableTypes.color,
  },
  alertErrorBackground: {
    key: '--cp-color-alert-error-bg',
    name: 'Error alert background',
    type: VariableTypes.color,
  },
  alertErrorBorder: {
    key: '--cp-color-alert-error-border',
    name: 'Error alert border',
    type: VariableTypes.color,
  },
  alertErrorIcon: {
    key: '--cp-color-alert-error-icon',
    name: 'Error alert icon',
    type: VariableTypes.color,
  },
  alertInfoBackground: {
    key: '--cp-color-alert-info-bg',
    name: 'Info alert background',
    type: VariableTypes.color,
  },
  alertInfoBorder: {
    key: '--cp-color-alert-info-border',
    name: 'Info alert border',
    type: VariableTypes.color,
  },
  alertInfoIcon: {
    key: '--cp-color-alert-info-icon',
    name: 'Info alert icon',
    type: VariableTypes.color,
  },
  tableElementSelectedBackgroundColor: {
    key: '--cp-color-table-selected-bg',
    name: 'Tables: selected element background',
    type: VariableTypes.color,
  },
  tableElementSelectedColor: {
    key: '--cp-color-table-selected-text',
    name: 'Tables: selected element text color',
    type: VariableTypes.color,
  },
  tableElementHoverBackgroundColor: {
    key: '--cp-color-table-hover-bg',
    name: 'Tables: hovered element background',
    type: VariableTypes.color,
  },
  tableElementHoverColor: {
    key: '--cp-color-table-hover-text',
    name: 'Tables: hovered element text color',
    type: VariableTypes.color,
  },
  tableBorderColor: {
    key: '--cp-color-border-table',
    name: 'Tables: border',
    type: VariableTypes.color,
  },
  tableHeadColor: {
    key: '--cp-color-table-head-text',
    name: 'Tables: header text color',
    type: VariableTypes.color,
  },
  menuActiveColor: {
    key: '--cp-color-menu-active',
    name: 'Active/hovered menu item color',
    type: VariableTypes.color,
  },
  btnDangerColor: {
    key: '--cp-color-btn-danger-text',
    name: 'Danger button: text color',
    type: VariableTypes.color,
  },
  btnDangerBackgroundColor: {
    key: '--cp-color-btn-danger-bg',
    name: 'Danger button: background color',
    type: VariableTypes.color,
  },
  btnDangerActiveColor: {
    key: '--cp-color-btn-danger-active-text',
    name: 'Danger button: active text color',
    type: VariableTypes.color,
  },
  btnDangerActiveBackground: {
    key: '--cp-color-btn-danger-active-bg',
    name: 'Danger button: active background color',
    type: VariableTypes.color,
  },
  btnDisabledColor: {
    key: '--cp-color-btn-disabled-text',
    name: 'Disabled button: text color',
    type: VariableTypes.color,
  },
  btnDisabledBackgroundColor: {
    key: '--cp-color-btn-disabled-bg',
    name: 'Disabled button: background color',
    type: VariableTypes.color,
  },
  codeBackgroundColor: {
    key: '--cp-color-bg-code',
    name: 'Code editor background color',
    type: VariableTypes.color,
  },
  searchHighlightTextColor: {
    key: '--cp-color-search-highlight-text',
    name: 'Search results: highlighted text color',
    type: VariableTypes.color,
  },
  searchHighlightTextBackgroundColor: {
    key: '--cp-color-search-highlight-bg',
    name: 'Search results: highlighted background',
    type: VariableTypes.color,
  },
  backgroundImage: {
    key: '--cp-asset-bg-image',
    name: 'Application background image',
    type: VariableTypes.image,
  },
  logoImage: {
    key: '--cp-asset-logo',
    name: 'Application logo',
    type: VariableTypes.image,
  },
  navigationBackgroundImage: {
    key: '--cp-asset-nav-bg-image',
    name: 'Navigation panel background image',
    type: VariableTypes.image,
  },
};

export {Variables, VariableTypes};
