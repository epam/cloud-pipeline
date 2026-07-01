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

import {useCallback, useEffect, useState} from 'react';
import {message} from 'antd';
import DataStorageLifeCycleRulesLoad from '../../../../../models/dataStorage/lifeCycleRules/DataStorageLifeCycleRulesLoad';
import NotificationTemplates from '../../../../../models/settings/NotificationTemplates';
import type {NotificationTemplate, Rule} from './types';

export function useLifeCycleRules(storageId: string | number | undefined) {
  const [rules, setRules] = useState<Rule[]>([]);
  const [pending, setPending] = useState(false);

  const fetch = useCallback(async () => {
    if (!storageId) return;
    const hide = message.loading('Loading transition rules...', 0);
    setPending(true);
    const request = new DataStorageLifeCycleRulesLoad(storageId);
    await request.fetch();
    hide();
    if (request.error) {
      message.error(request.error, 5);
    } else {
      const sorted = [...(request.value || [])].sort((a: Rule, b: Rule) =>
        (a.pathGlob ?? '').localeCompare(b.pathGlob ?? ''),
      );
      setRules(sorted);
    }
    setPending(false);
  }, [storageId]);

  useEffect(() => {
    fetch();
  }, [fetch]);

  return {rules, pending, fetch};
}

export function useNotificationTemplate(ruleId?: string | number) {
  const [template, setTemplate] = useState<NotificationTemplate | undefined>(undefined);
  const [pending, setPending] = useState(false);

  const fetch = useCallback(async () => {
    setPending(true);
    const request = new NotificationTemplates();
    await request.fetch();
    setPending(false);
    if (!request.error) {
      const found = (request.value || []).find(
        (t: NotificationTemplate) => t.name === 'DATASTORAGE_LIFECYCLE_ACTION',
      );
      setTemplate(found);
    }
  }, []);

  useEffect(() => {
    fetch();
    // Refetch when rule changes so the template is up-to-date for each opened rule.
  }, [fetch, ruleId]);

  return {template, pending};
}

export function useLifeCycleRulesCount(storageId?: string | number, path?: string) {
  const [rulesAmount, setRulesAmount] = useState<number | undefined>(undefined);

  const fetch = useCallback(async () => {
    if (!storageId) return;
    const request = new DataStorageLifeCycleRulesLoad(storageId, path);
    await request.fetch();
    if (request.error) {
      message.error(request.error, 5);
    } else {
      setRulesAmount((request.value || []).length);
    }
  }, [storageId, path]);

  return {rulesAmount, fetch};
}
