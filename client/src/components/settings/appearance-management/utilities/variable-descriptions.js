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
  regionIcon: 'region-icon'
};

const Variables = {
  applicationBackgroundColor: {
    key: '@application-background-color',
    name: 'Application background color',
    type: VariableTypes.color
  },
  applicationColor: {
    key: '@application-color',
    name: 'Text color',
    type: VariableTypes.color
  },
  applicationColorFaded: {
    key: '@application-color-faded',
    name: 'Text color faded',
    type: VariableTypes.color
  },
  applicationColorDisabled: {
    key: '@application-color-disabled',
    name: 'Disabled text color',
    type: VariableTypes.color
  },
  applicationColorAccent: {
    key: '@application-color-accent',
    name: 'Accented text color',
    type: VariableTypes.color
  },
  primaryColor: {
    key: '@primary-color',
    name: 'Primary action color',
    type: VariableTypes.color
  },
  primaryHoverColor: {
    key: '@primary-hover-color',
    name: 'Primary action hovered color',
    type: VariableTypes.color
  },
  primaryActiveColor: {
    key: '@primary-active-color',
    name: 'Primary action active color',
    type: VariableTypes.color
  },
  primaryTextColor: {
    key: '@primary-text-color',
    name: 'Primary action text color',
    type: VariableTypes.color
  },
  primaryColorSemiTransparent: {
    key: '@primary-color-semi-transparent',
    name: 'Metadata table selection background',
    type: VariableTypes.color
  },
  colorSuccess: {
    key: '@color-success',
    name: 'Success status color',
    type: VariableTypes.color
  },
  colorError: {
    key: '@color-error',
    name: 'Error status color',
    type: VariableTypes.color
  },
  colorWarning: {
    key: '@color-warning',
    name: 'Warning status color',
    type: VariableTypes.color
  },
  colorInfo: {
    key: '@color-info',
    name: 'Info color',
    type: VariableTypes.color
  },
  colorGreen: {
    key: '@color-green',
    name: 'Green color',
    type: VariableTypes.color
  },
  colorRed: {
    key: '@color-red',
    name: 'Red color',
    type: VariableTypes.color
  },
  colorYellow: {
    key: '@color-yellow',
    name: 'Yellow color',
    type: VariableTypes.color
  },
  colorBlue: {
    key: '@color-blue',
    name: 'Blue color',
    type: VariableTypes.color
  },
  colorViolet: {
    key: '@color-violet',
    name: 'Violet color',
    type: VariableTypes.color
  },
  colorSensitive: {
    key: '@color-sensitive',
    name: 'Sensitive object color',
    type: VariableTypes.color
  },
  colorAqua: {
    key: '@color-aqua',
    name: 'Aqua color',
    type: VariableTypes.color
  },
  colorAquaLight: {
    key: '@color-aqua-light',
    name: 'Aqua light color',
    type: VariableTypes.color
  },
  colorPink: {
    key: '@color-pink',
    name: 'Pink color',
    type: VariableTypes.color
  },
  colorPinkDusty: {
    key: '@color-pink-dusty',
    name: 'Dusty pink color',
    type: VariableTypes.color
  },
  colorPinkLight: {
    key: '@color-pink-light',
    name: 'Light pink color',
    type: VariableTypes.color
  },
  colorBlueDimmed: {
    key: '@color-blue-dimmed',
    name: 'Blue dimmed color',
    type: VariableTypes.color
  },
  colorGrey: {
    key: '@color-grey',
    name: 'Grey color',
    type: VariableTypes.color
  },
  spinner: {
    key: '@spinner',
    name: 'Loading indicator color',
    type: VariableTypes.color
  },
  elementHoverColor: {
    key: '@element-hover-color',
    name: 'Lists: hovered element text color',
    type: VariableTypes.color
  },
  elementHoverBackgroundColor: {
    key: '@element-hover-background-color',
    name: 'Lists: hovered element background',
    type: VariableTypes.color
  },
  elementSelectedColor: {
    key: '@element-selected-color',
    name: 'Lists: selected element text color',
    type: VariableTypes.color
  },
  elementSelectedBackgroundColor: {
    key: '@element-selected-background-color',
    name: 'Lists: selected element background',
    type: VariableTypes.color
  },
  inputBackground: {
    key: '@input-background',
    name: 'Input control background',
    type: VariableTypes.color
  },
  inputBackgroundDisabled: {
    key: '@input-background-disabled',
    name: 'Disabled input control background',
    type: VariableTypes.color
  },
  inputAddon: {
    key: '@input-addon',
    name: 'Input control addon background',
    type: VariableTypes.color
  },
  inputBorder: {
    key: '@input-border',
    name: 'Input control border',
    type: VariableTypes.color
  },
  inputColor: {
    key: '@input-color',
    name: 'Input control text color',
    type: VariableTypes.color
  },
  inputPlaceholderColor: {
    key: '@input-placeholder-color',
    name: 'Input control placeholder color',
    type: VariableTypes.color
  },
  inputBorderHoverColor: {
    key: '@input-border-hover-color',
    name: 'Hovered input control border',
    type: VariableTypes.color
  },
  inputShadowColor: {
    key: '@input-shadow-color',
    name: 'Hovered input control shadow',
    type: VariableTypes.color
  },
  inputSearchIconColor: {
    key: '@input-search-icon-color',
    name: 'Input control search icon',
    type: VariableTypes.color
  },
  inputSearchIconHoveredColor: {
    key: '@input-search-icon-hovered-color',
    name: 'Input control search icon hovered',
    type: VariableTypes.color
  },
  panelBackgroundColor: {
    key: '@panel-background-color',
    name: 'Panels background color',
    type: VariableTypes.color
  },
  panelBorderColor: {
    key: '@panel-border-color',
    name: 'Panels border color',
    type: VariableTypes.color
  },
  cardBackgroundColor: {
    key: '@card-background-color',
    name: 'Cards background color',
    type: VariableTypes.color
  },
  cardBorderColor: {
    key: '@card-border-color',
    name: 'Cards border color',
    type: VariableTypes.color
  },
  cardHoveredShadowColor: {
    key: '@card-hovered-shadow-color',
    name: 'Hovered card shadow',
    type: VariableTypes.color
  },
  cardActionsActiveBackground: {
    key: '@card-actions-active-background',
    name: 'Card actions background color',
    type: VariableTypes.color
  },
  cardHeaderBackground: {
    key: '@card-header-background',
    name: 'Card header background color',
    type: VariableTypes.color
  },
  cardServiceBackgroundColor: {
    key: '@card-service-background-color',
    name: 'Service cards background color',
    type: VariableTypes.color
  },
  cardServiceBorderColor: {
    key: '@card-service-border-color',
    name: 'Service cards border color',
    type: VariableTypes.color
  },
  cardServiceHoveredShadowColor: {
    key: '@card-service-hovered-shadow-color',
    name: 'Service card shadow',
    type: VariableTypes.color
  },
  cardServiceActionsActiveBackground: {
    key: '@card-service-actions-active-background',
    name: 'Service card actions background color',
    type: VariableTypes.color
  },
  cardServiceHeaderBackground: {
    key: '@card-service-header-background',
    name: 'Service card header background color',
    type: VariableTypes.color
  },
  navigationPanelColor: {
    key: '@navigation-panel-color',
    name: 'Navigation panel color',
    type: VariableTypes.color
  },
  navigationPanelColorImpersonated: {
    key: '@navigation-panel-color-impersonated',
    name: 'Impersonated navigation panel color',
    type: VariableTypes.color
  },
  navigationPanelHighlightedColor: {
    key: '@navigation-panel-highlighted-color',
    name: 'Navigation panel active item background',
    type: VariableTypes.color
  },
  navigationPanelHighlightedColorImpersonated: {
    key: '@navigation-panel-highlighted-color-impersonated',
    name: 'Impersonated active item background',
    type: VariableTypes.color
  },
  navigationItemColor: {
    key: '@navigation-item-color',
    name: 'Navigation panel icon color',
    type: VariableTypes.color
  },
  navigationItemRunsColor: {
    key: '@navigation-item-runs-color',
    name: 'Navigation panel jobs icon color',
    type: VariableTypes.color
  },
  tagKeyBackgroundColor: {
    key: '@tag-key-background-color',
    name: 'Key-value attribute: key background',
    type: VariableTypes.color
  },
  tagKeyValueDividerColor: {
    key: '@tag-key-value-divider-color',
    name: 'Key-value attribute: divider',
    type: VariableTypes.color
  },
  tagValueBackgroundColor: {
    key: '@tag-value-background-color',
    name: 'Key-value attribute: value background',
    type: VariableTypes.color
  },
  nfsIconColor: {
    key: '@nfs-icon-color',
    name: 'NFS Storage icon',
    type: VariableTypes.color
  },
  awsIcon: {
    key: '@aws-icon',
    name: 'AWS icon',
    type: VariableTypes.providerIcon,
    provider: 'AWS'
  },
  awsIconContrast: {
    key: '@aws-icon-contrast',
    name: 'AWS icon (contrasted)',
    type: VariableTypes.providerIcon,
    provider: 'AWS'
  },
  gcpIcon: {
    key: '@gcp-icon',
    name: 'GCP icon',
    type: VariableTypes.providerIcon,
    provider: 'GCP'
  },
  gcpIconContrast: {
    key: '@gcp-icon-contrast',
    name: 'GCP icon (contrasted)',
    type: VariableTypes.providerIcon,
    provider: 'GCP'
  },
  azureIcon: {
    key: '@azure-icon',
    name: 'AZURE icon',
    type: VariableTypes.providerIcon,
    provider: 'AZURE'
  },
  azureIconContrast: {
    key: '@azure-icon-contrast',
    name: 'AZURE icon (contrasted)',
    type: VariableTypes.providerIcon,
    provider: 'AZURE'
  },
  euRegionIcon: {
    key: '@eu-region-icon',
    name: 'EU region icon',
    type: VariableTypes.regionIcon
  },
  usRegionIcon: {
    key: '@us-region-icon',
    name: 'US region icon',
    type: VariableTypes.regionIcon
  },
  saRegionIcon: {
    key: '@sa-region-icon',
    name: 'SA region icon',
    type: VariableTypes.regionIcon
  },
  cnRegionIcon: {
    key: '@cn-region-icon',
    name: 'CN region icon',
    type: VariableTypes.regionIcon
  },
  caRegionIcon: {
    key: '@ca-region-icon',
    name: 'CA region icon',
    type: VariableTypes.regionIcon
  },
  apNortheast1RegionIcon: {
    key: '@ap-northeast-1-region-icon',
    name: 'AP North-East 1 region icon',
    type: VariableTypes.regionIcon
  },
  apNortheast2RegionIcon: {
    key: '@ap-northeast-2-region-icon',
    name: 'AP North-East 2 region icon',
    type: VariableTypes.regionIcon
  },
  apNortheast3RegionIcon: {
    key: '@ap-northeast-3-region-icon',
    name: 'AP North-East 3 region icon',
    type: VariableTypes.regionIcon
  },
  apSouth1RegionIcon: {
    key: '@ap-south-1-region-icon',
    name: 'AP South 1 region icon',
    type: VariableTypes.regionIcon
  },
  apSoutheast1RegionIcon: {
    key: '@ap-southeast-1-region-icon',
    name: 'AP South-East 1 region icon',
    type: VariableTypes.regionIcon
  },
  apSoutheast2RegionIcon: {
    key: '@ap-southeast-2-region-icon',
    name: 'AP South-East 2 region icon',
    type: VariableTypes.regionIcon
  },
  taiwanRegionIcon: {
    key: '@taiwan-region-icon',
    name: 'Taiwan region icon',
    type: VariableTypes.regionIcon
  },
  modalMaskBackground: {
    key: '@modal-mask-background',
    name: 'Dialogs overlay background',
    type: VariableTypes.color
  },
  evenElementBackground: {
    key: '@even-element-background',
    name: 'Even elements background',
    type: VariableTypes.color
  },
  alertSuccessBackground: {
    key: '@alert-success-background',
    name: 'Success alert background',
    type: VariableTypes.color
  },
  alertSuccessBorder: {
    key: '@alert-success-border',
    name: 'Success alert border',
    type: VariableTypes.color
  },
  alertSuccessIcon: {
    key: '@alert-success-icon',
    name: 'Success alert icon',
    type: VariableTypes.color
  },
  alertWarningBackground: {
    key: '@alert-warning-background',
    name: 'Warning alert background',
    type: VariableTypes.color
  },
  alertWarningBorder: {
    key: '@alert-warning-border',
    name: 'Warning alert border',
    type: VariableTypes.color
  },
  alertWarningIcon: {
    key: '@alert-warning-icon',
    name: 'Warning alert icon',
    type: VariableTypes.color
  },
  alertErrorBackground: {
    key: '@alert-error-background',
    name: 'Error alert background',
    type: VariableTypes.color
  },
  alertErrorBorder: {
    key: '@alert-error-border',
    name: 'Error alert border',
    type: VariableTypes.color
  },
  alertErrorIcon: {
    key: '@alert-error-icon',
    name: 'Error alert icon',
    type: VariableTypes.color
  },
  alertInfoBackground: {
    key: '@alert-info-background',
    name: 'Info alert background',
    type: VariableTypes.color
  },
  alertInfoBorder: {
    key: '@alert-info-border',
    name: 'Info alert border',
    type: VariableTypes.color
  },
  alertInfoIcon: {
    key: '@alert-info-icon',
    name: 'Info alert icon',
    type: VariableTypes.color
  },
  tableElementSelectedBackgroundColor: {
    key: '@table-element-selected-background-color',
    name: 'Tables: selected element background',
    type: VariableTypes.color
  },
  tableElementSelectedColor: {
    key: '@table-element-selected-color',
    name: 'Tables: selected element text color',
    type: VariableTypes.color
  },
  tableElementHoverBackgroundColor: {
    key: '@table-element-hover-background-color',
    name: 'Tables: hovered element background',
    type: VariableTypes.color
  },
  tableElementHoverColor: {
    key: '@table-element-hover-color',
    name: 'Tables: hovered element text color',
    type: VariableTypes.color
  },
  tableBorderColor: {
    key: '@table-border-color',
    name: 'Tables: border',
    type: VariableTypes.color
  },
  tableHeadColor: {
    key: '@table-head-color',
    name: 'Tables: header text color',
    type: VariableTypes.color
  },
  menuActiveColor: {
    key: '@menu-active-color',
    name: 'Active/hovered menu item color',
    type: VariableTypes.color
  },
  btnDangerColor: {
    key: '@btn-danger-color',
    name: 'Danger button: text color',
    type: VariableTypes.color
  },
  btnDangerBackgroundColor: {
    key: '@btn-danger-background-color',
    name: 'Danger button: background color',
    type: VariableTypes.color
  },
  btnDangerActiveColor: {
    key: '@btn-danger-active-color',
    name: 'Danger button: active text color',
    type: VariableTypes.color
  },
  btnDangerActiveBackground: {
    key: '@btn-danger-active-background',
    name: 'Danger button: active background color',
    type: VariableTypes.color
  },
  btnDisabledColor: {
    key: '@btn-disabled-color',
    name: 'Disabled button: text color',
    type: VariableTypes.color
  },
  btnDisabledBackgroundColor: {
    key: '@btn-disabled-background-color',
    name: 'Disabled button: background color',
    type: VariableTypes.color
  },
  codeBackgroundColor: {
    key: '@code-background-color',
    name: 'Code editor background color',
    type: VariableTypes.color
  },
  searchHighlightTextColor: {
    key: '@search-highlight-text-color',
    name: 'Search results: highlighted text color',
    type: VariableTypes.color
  },
  searchHighlightTextBackgroundColor: {
    key: '@search-highlight-text-background-color',
    name: 'Search results: highlighted background',
    type: VariableTypes.color
  },
  backgroundImage: {
    key: '@background-image',
    name: 'Application background image',
    type: VariableTypes.image
  },
  logoImage: {
    key: '@logo-image',
    name: 'Application logo',
    type: VariableTypes.image
  },
  navigationBackgroundImage: {
    key: '@navigation-background-image',
    name: 'Navigation panel background image',
    type: VariableTypes.image
  }
};

export {
  Variables,
  VariableTypes
};
