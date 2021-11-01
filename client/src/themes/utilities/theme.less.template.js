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

export default `
@THEME .ant-layout-sider {
  background-color: @navigation-panel-color;
}
@THEME .ant-layout {
  background-color: @application-background-color;
  color: @application-color;
}
@THEME h1,
@THEME h2,
@THEME h3,
@THEME h4,
@THEME h5 {
  color: @application-color;
}
@THEME .ant-input {
  background-color: @input-background;
  border-color: @input-border;
  color: @input-color;
}
@THEME .ant-input::placeholder {
  color: @input-placeholder-color;
}
@THEME .ant-input-affix-wrapper .ant-input-suffix {
  color: @input-search-icon-color;
}
@THEME .ant-input-affix-wrapper .ant-input-suffix .ant-input-search-icon:hover {
  color: @input-search-icon-hovered-color;
}
@THEME .ant-input:hover:not(.ant-input-disabled),
@THEME .ant-input-affix-wrapper:hover .ant-input:not(.ant-input-disabled) {
  border-color: @input-border-hover-color;
  box-shadow: 0 0 0 2px @input-shadow-color;
}
@THEME .ant-input-affix-wrapper:focus .ant-input:not(.ant-input-disabled),
@THEME .ant-input:focus:not(.ant-input-disabled),
@THEME .ant-input:active:not(.ant-input-disabled) {
  border-color: @input-border-hover-color;
  box-shadow: 0 0 0 2px @input-shadow-color;
}
@THEME .cp-text-not-important {
  color: @application-color-faded;
}
@THEME .ant-modal-mask {
  background-color: @modal-mask-background;
}
@THEME .ant-modal-content {
  background-color: @card-background-color;
  color: @application-color;
}
@THEME .ant-modal-header,
@THEME .ant-modal-footer {
  background-color: @card-background-color;
  color: @application-color;
  border-color: @card-border-color;
}
@THEME .ant-modal-title {
  color: @application-color;
}
@THEME .ant-modal-close {
@THEME color: fadeout(@application-color, 20%);
}
@THEME .ant-modal-close:focus,
@THEME .ant-modal-close:hover {
  color: @application-color;
}
@THEME .cp-even-odd-element:nth-child(even) {
  background-color: @even-element-background;
}
@THEME .ant-tree li .ant-tree-node-content-wrapper {
  color: @application-color;
}
@THEME .ant-tree li .ant-tree-node-content-wrapper:hover {
  background-color: @table-element-hover-background-color;
  color: @table-element-hover-color;
}
@THEME .ant-tree li .ant-tree-node-content-wrapper.ant-tree-node-selected {
  background-color: @table-element-selected-background-color;
  color: @table-element-selected-color;
}
@THEME .ant-menu,
@THEME .ant-menu-submenu > .ant-menu {
  background-color: transparent;
  color: @menu-color;
  border-color: @menu-border-color;
}
@THEME .ant-menu > .ant-menu-item > a,
@THEME .ant-menu > .ant-menu-submenu > a {
  color: currentColor;
}
@THEME .ant-menu > .ant-menu-item > a:hover,
@THEME .ant-menu > .ant-menu-submenu > a:hover {
  color: @menu-active-color;
}
@THEME .ant-menu:not(.ant-menu-horizontal) > .ant-menu-item-selected {
  background-color: transparent;
}
@THEME .ant-menu > .ant-menu-item:hover,
@THEME .ant-menu > .ant-menu-submenu:hover,
@THEME .ant-menu > .ant-menu-item-active,
@THEME .ant-menu > .ant-menu-submenu-active,
@THEME .ant-menu > .ant-menu-item-open,
@THEME .ant-menu > .ant-menu-submenu-open,
@THEME .ant-menu > .ant-menu-item-selected,
@THEME .ant-menu > .ant-menu-submenu-selected,
@THEME .ant-menu-inline .ant-menu-item::after,
@THEME .ant-menu-vertical .ant-menu-item::after,
@THEME .ant-menu-submenu-title:hover,
@THEME .ant-menu-item:active,
@THEME .ant-menu-submenu-title:active {
  border-color: @menu-active-color;
  color: @menu-active-color;
  background-color: transparent;
}
@THEME .ant-tabs {
  color: @menu-color;
}
@THEME .ant-tabs .ant-tabs-bar {
  border-color: @menu-border-color;
}
@THEME .ant-tabs .ant-tabs-bar .ant-tabs-ink-bar {
  background-color: @menu-active-color;
}
@THEME .ant-tabs .ant-tabs-bar .ant-tabs-tab-active,
@THEME .ant-tabs .ant-tabs-bar .ant-tabs-tab:hover {
  color: @menu-active-color;
}
@THEME .ant-tabs.ant-tabs-card > .ant-tabs-bar .ant-tabs-tab {
  border-color: @card-border-color;
  background-color: @card-background-color;
}
@THEME .ant-tabs.ant-tabs-card > .ant-tabs-bar .ant-tabs-tab-active {
  color: @menu-active-color;
}
@THEME .ant-tabs.ant-tabs-card .ant-tabs-bar {
  margin-bottom: 0;
}
@THEME .ant-tabs.ant-tabs-card .ant-tabs-bar + .ant-tabs-content {
  padding: 16px 5px 5px;
  background-color: @card-background-color;
  border-left: 1px solid @card-border-color;
  border-right: 1px solid @card-border-color;
  border-bottom: 1px solid @card-border-color;
}
@THEME .ant-checkbox-wrapper + span {
  color: @application-color;
}
@THEME .ant-checkbox-disabled + span {
  color: @application-color-disabled;
}
@THEME .cp-panel {
  border: 1px solid @panel-border-color;
  background-color: @panel-background-color;
  color: @application-color;
}
@THEME .cp-panel.cp-panel-transparent {
  background-color: @panel-transparent-color;
  border-color: transparent;
}
@THEME .cp-panel.cp-panel-transparent:hover {
  box-shadow: none;
  border-color: transparent;
}
@THEME .cp-panel .cp-panel-card {
  border: 1px solid @card-border-color;
  background-color: @card-background-color;
  margin: 2px;
}
@THEME .cp-panel .cp-panel-card.cp-card-service {
  border: 1px solid @card-service-border-color;
  background-color: @card-service-background-color;
}
@THEME .cp-panel .cp-panel-card:hover {
  box-shadow: 0 1px 6px @card-hovered-shadow-color;
  border-color: @card-hovered-shadow-color;
}
@THEME .cp-panel .cp-panel-card.cp-card-service:hover {
  box-shadow: 0 1px 6px @card-service-hovered-shadow-color;
  border-color: @card-service-hovered-shadow-color;
}
@THEME .cp-panel .cp-panel-card .cp-panel-card-title,
@THEME .cp-panel .cp-panel-card .cp-panel-card-text {
  color: @application-color;
}
@THEME .cp-panel .cp-panel-card .cp-panel-card-sub-text {
  color: @application-color-faded;
}
@THEME .cp-panel .cp-panel-card .cp-panel-card-title {
  font-weight: bold;
}
@THEME .cp-panel .cp-panel-card .cp-card-action-button {
  color: @primary-color;
}
@THEME .cp-panel .cp-panel-card .cp-panel-card-actions .cp-panel-card-actions-background {
  background-color: @card-background-color;
}
@THEME .cp-panel .cp-panel-card.cp-card-service .cp-panel-card-actions .cp-panel-card-actions-background {
  background-color: @card-service-background-color;
}
@THEME .cp-panel .cp-panel-card.cp-card-service .cp-panel-card-actions:hover .cp-panel-card-actions-background,
@THEME .cp-panel .cp-panel-card.cp-card-service .cp-panel-card-actions.hovered .cp-panel-card-actions-background {
  background-color: @card-service-actions-active-background;
}
@THEME .cp-panel .cp-panel-card .cp-panel-card-actions:hover .cp-panel-card-actions-background,
@THEME .cp-panel .cp-panel-card .cp-panel-card-actions.hovered .cp-panel-card-actions-background {
  background-color: @card-actions-active-background;
}
@THEME .cp-panel .cp-panel-card.cp-card-service .cp-panel-card-actions:hover .cp-panel-card-actions-background,
@THEME .cp-panel .cp-panel-card.cp-card-service .cp-panel-card-actions.hovered .cp-panel-card-actions-background {
  background-color: @card-service-actions-active-background;
}
@THEME .cp-navigation-panel {
  background-color: @navigation-panel-color;
}
@THEME .cp-navigation-panel.impersonated {
  background-color: @navigation-panel-color-impersonated;
}
@THEME .cp-navigation-panel .cp-navigation-menu-item {
  color: @navigation-item-color;
  background-color: transparent;
  text-transform: uppercase;
  border: 0 solid transparent;
  width: 100%;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: larger;
  text-decoration: none;
}
@THEME .cp-navigation-panel .cp-navigation-menu-item.selected {
  background-color: @navigation-panel-highlighted-color;
}
@THEME .cp-navigation-panel.impersonated .cp-navigation-menu-item.selected {
  background-color: @navigation-panel-highlighted-color-impersonated;
}
@THEME .cp-navigation-panel .cp-navigation-menu-item i {
  font-weight: bold;
  font-size: large;
}
@THEME .cp-navigation-panel .cp-navigation-menu-item.cp-runs-menu-item.active {
  color: @navigation-item-runs-color;
}
@THEME .cp-dashboard-sticky-panel {
  background-color: @application-background-color;
}
@THEME .cp-project-tag-key {
  background-color: @tag-key-background-color;
  border-bottom: 1px solid @tag-key-value-divider-color;
}
@THEME .cp-project-tag-value {
  background-color: @tag-value-background-color;
}
@THEME .cp-dashboard-panel-card-header {
  background-color: @card-header-background;
}
@THEME .cp-nfs-storage-type {
  color: @nfs-icon-color;
}
@THEME .cp-notification-status-info {
  color: @status-good-color;
}
@THEME .cp-notification-status-warning {
  color: @status-warning-color;
}
@THEME .cp-notification-status-critical {
  color: @status-bad-color;
}
@THEME .cp-new-notification {
  border-color: @color-blue;
  box-shadow: 0 0 1em @color-blue;
}
@THEME .provider.aws {
  background-image: @aws-icon;
}
@THEME .provider.gcp {
  background-image: @gcp-icon;
  background-color: transparent;
  box-shadow: 0 0 0 1px transparent;
}
@THEME .provider.azure {
  background-image: @azure-icon;
}
@THEME .flag.eu {
  background-image: @eu-region-icon;
}
@THEME .flag.us {
  background-image: @us-region-icon;
}
@THEME .flag.sa {
  background-image: @sa-region-icon;
}
@THEME .flag.cn {
  background-image: @cn-region-icon;
}
@THEME .flag.ca {
  background-image: @ca-region-icon;
}
@THEME .flag.ap.ap-northeast-1 {
  background-image: @ap-northeast-1-region-icon;
}
@THEME .flag.ap.ap-northeast-2 {
  background-image: @ap-northeast-2-region-icon;
}
@THEME .flag.ap.ap-northeast-3 {
  background-image: @ap-northeast-3-region-icon;
}
@THEME .flag.ap.ap-south-1 {
  background-image: @ap-south-1-region-icon;
}
@THEME .flag.ap.ap-southeast-1 {
  background-image: @ap-southeast-1-region-icon;
}
@THEME .flag.ap.ap-southeast-2 {
  background-image: @ap-southeast-2-region-icon;
}
@THEME .flag.taiwan {
  background-image: @taiwan-region-icon;
}
@THEME .ant-alert {
  color: @application-color;
}
@THEME .ant-alert.ant-alert-success {
  background-color: @alert-success-background;
  border-color: @alert-success-border;
}
@THEME .ant-alert.ant-alert-info {
  background-color: @alert-info-background;
  border-color: @alert-info-border;
}
@THEME .ant-alert.ant-alert-error {
  background-color: @alert-error-background;
  border-color: @alert-error-border;
}
@THEME .ant-alert.ant-alert-warning {
  background-color: @alert-warning-background;
  border-color: @alert-warning-border;
}
@THEME .ant-alert.ant-alert-success .ant-alert-icon,
@THEME .ant-message .ant-message-success .anticon {
  color: @alert-success-icon;
}
@THEME .ant-alert.ant-alert-info .ant-alert-icon,
@THEME .ant-message .ant-message-info .anticon {
  color: @alert-info-icon;
}
@THEME .ant-alert.ant-alert-error .ant-alert-icon,
@THEME .ant-message .ant-message-error .anticon {
  color: @alert-error-icon;
}
@THEME .ant-alert.ant-alert-warning .ant-alert-icon,
@THEME .ant-message .ant-message-warning .anticon {
  color: @alert-warning-icon;
}
@THEME .ant-message .ant-message-loading .anticon {
  color: @alert-info-icon;
}
@THEME .ant-message-notice .ant-message-notice-content {
  background-color: @card-background-color;
  color: @application-color;
  box-shadow: 0 2px 8px @card-hovered-shadow-color;
}
@THEME .cp-tool-header {
  border-color: @menu-border-color;
}
@THEME .cp-tool-panel + .cp-tool-panel {
  margin-left: 10px;
}
@THEME .cp-tool-panel .cp-tool-panel-header {
  padding: 10px;
  background-color: @card-header-background;
  border: 1px solid @card-border-color;
  border-radius: 5px 5px 0 0;
}
@THEME .cp-tool-panel .cp-tool-panel-body {
  margin-top: -1px;
  padding: 10px;
  border: 1px solid @card-border-color;
  background-color: @card-background-color;
  border-radius: 0 0 5px 5px;
}
@THEME .cp-tool-no-description {
  color: @application-color-faded;
}




@THEME .cp-billing-menu {
  width: fit-content;
  margin-left: 0;
  min-height: 100%;
}
@THEME .cp-billing-menu .ant-menu-submenu-title,
@THEME .cp-billing-menu .ant-menu-item,
@THEME .cp-billing-menu .cp-billing-sub-menu .ant-menu-item {
  height: 32px;
  line-height: 32px;
}
@THEME .cp-billing-menu .cp-billing-sub-menu .ant-menu-submenu-title {
  position: relative;
  left: 1px;
  margin-left: -1px;
  z-index: 1;
  height: 32px;
  line-height: 32px;
}
@THEME .cp-billing-menu .cp-billing-sub-menu.ant-menu-submenu-open .ant-menu-submenu-title {
  background-color: transparent;
  color: @menu-color;
}
@THEME .cp-billing-menu .cp-billing-sub-menu.cp-billing-sub-menu-selected .ant-menu-submenu-title {
  background-color: transparent;
  color: @menu-active-color;
}
@THEME .cp-billing-menu .cp-billing-sub-menu .ant-menu-submenu-title::after {
@THEME content: "";
  position: absolute;
  right: 0;
  top: 0;
  bottom: 0;
  border-right: 3px solid @menu-active-color;
  transform: scaleY(0.0001);
  opacity: 0;
  transition: transform 0.15s cubic-bezier(0.215, @THEME 0.61, @THEME 0.355, @THEME 1), @THEME opacity 0.15s cubic-bezier(0.215, @THEME 0.61, @THEME 0.355, 1);
}
@THEME .cp-billing-menu .cp-billing-sub-menu.cp-billing-sub-menu-selected .ant-menu-submenu-title::after {
@THEME transition: transform 0.15s cubic-bezier(0.645, @THEME 0.045, @THEME 0.355, @THEME 1), @THEME opacity 0.15s cubic-bezier(0.645, @THEME 0.045, @THEME 0.355, 1);
  opacity: 1;
  transform: scaleY(1);
}
@THEME .cp-billing-table td {
  border: 1px solid @panel-border-color;
}
@THEME .cp-billing-table td.cp-billing-table-pending {
  background-color: @card-background-color;
}
@THEME .cp-search-clear-filters-button {
  background: @primary-color;
  color: @primary-text-color;
}
@THEME .cp-search-actions > * {
  border-right: 1px solid @panel-border-color;
}
@THEME .cp-search-clear-button {
  color: @application-color-faded;
}
@THEME .cp-search-clear-button:hover {
  color: @application-color;
}
@THEME .cp-search-clear-filters-button.cp-search-clear-filters-button-disabled {

}
@THEME .cp-search-filter .cp-search-filter-header {
  border-bottom: 1px dashed transparent;
}
@THEME .cp-search-filter .cp-search-filter-header:hover {
  background-color: @card-header-background;
}
@THEME .cp-search-filter .cp-search-filter-header.cp-search-filter-header-expanded {
  border-bottom: 1px dashed @panel-border-color;
}
@THEME .cp-search-filter .cp-search-filter-header .cp-search-filter-header-caret {
  color: @application-color;
}
@THEME .cp-search-filter .cp-search-filter-header.cp-search-filter-header-expanded .cp-search-filter-header-caret {
  transform: rotate(90deg);
}
`;
