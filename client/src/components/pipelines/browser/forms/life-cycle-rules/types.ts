/*
 * Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
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

export interface TransitionCriterion {
  type?: string;
  value?: string;
}

export interface TransitionItem {
  storageClass?: string;
  transitionAfterDays?: number;
  transitionDate?: string | object;
}

export interface Recipient {
  name: string;
  principal: boolean;
}

export interface RuleNotification {
  enabled?: boolean;
  recipients?: Recipient[];
  notifyUsers?: boolean;
  notifyBeforeDays?: number | string;
  prolongDays?: number | string;
  subject?: string;
  body?: string;
}

export interface FormNotification extends Omit<RuleNotification, 'enabled'> {
  disabled?: boolean;
}

export interface Rule {
  id?: string | number;
  pathGlob?: string;
  objectGlob?: string;
  transitionMethod?: string;
  transitionCriterion?: TransitionCriterion;
  transitions?: TransitionItem[];
  notification?: RuleNotification;
  prolongations?: Prolongation[];
  datastorageId?: string | number;
}

export interface LifeCycleEditModalProps {
  visible?: boolean;
  onOk?: (payload: Rule, id?: string | number) => void;
  onCancel?: () => void;
  rule?: Rule;
  createNewRule?: boolean;
  pending?: boolean;
}

export interface NotificationTemplate {
  name: string;
  body?: string;
  subject?: string;
}

export interface Storage {
  id?: string | number;
  storageType?: string;
  type?: string;
}

export interface RestoreEntry {
  path: string;
  status: string;
}

export interface ParentRestore {
  status?: string;
  restoredTill?: string;
  started?: string;
}

export interface RestoreInfo {
  parentRestore?: ParentRestore;
  currentRestores?: RestoreEntry[] | Record<string, unknown>;
}

export interface RestoreItem {
  path?: string;
  type?: string;
}

export interface RestorePayload {
  days: number | string;
  restoreVersions: boolean;
  restoreMode: string;
  force: boolean;
  notification: {
    enabled: boolean;
    recipients?: Recipient[];
    notifyUsers: boolean;
  };
  paths?: {path: string; type: string}[];
}

export interface Prolongation {
  prolongedDate: string;
  days: number;
  userId?: number;
  path: string;
}

export interface ExecutionEntry {
  updated: string;
  storageClass: string;
  path: string;
  status: string;
}

export interface UserInfo {
  id: number;
  name: string;
}
