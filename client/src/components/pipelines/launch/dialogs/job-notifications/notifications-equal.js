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

import compareArrays from '../../../../../utils/compareArrays';

export function notificationsEqual (a, b) {
  if (!a && !b) {
    return true;
  }
  if (!a || !b) {
    return false;
  }
  const {
    type: aType,
    triggerStatuses: aTrigger,
    recipients: aRecipients,
    subject: aSubject,
    body: aBody
  } = a;
  const {
    type: bType,
    triggerStatuses: bTrigger,
    recipients: bRecipients,
    subject: bSubject,
    body: bBody
  } = b;
  return aType === bType &&
    compareArrays(aTrigger, bTrigger) &&
    compareArrays(aRecipients, bRecipients) &&
    aSubject === bSubject &&
    aBody === bBody;
}

export function notificationArraysAreEqual (a, b) {
  if (!a && !b) {
    return true;
  }
  const aArray = a || [];
  const bArray = b || [];
  if (aArray.length !== bArray.length) {
    return false;
  }
  for (let i = 0; i < aArray.length; i++) {
    const aNotification = aArray[i];
    const bNotification = bArray.find(n => notificationsEqual(n, aNotification));
    if (!bNotification) {
      return false;
    }
  }
  for (let i = 0; i < bArray.length; i++) {
    const bNotification = bArray[i];
    const aNotification = aArray.find(n => notificationsEqual(n, bNotification));
    if (!aNotification) {
      return false;
    }
  }
  return true;
}
