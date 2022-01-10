# Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import os


class NotificationSender(object):

    def __init__(self, api_instance, logger, email_template_path, user, cc_users_list, subject):
        self.api = api_instance
        self.logger = logger
        self.email_template_path = email_template_path
        self.user = user
        self.cc_users_list = cc_users_list
        self.subject = subject

    def queue_notification(self, message):
        email_template = self._read_template()
        email_body = email_template.format(message)
        self.logger.info('Registering notification...')
        new_notification = self.api.create_notification(self.subject, email_body, self.user, self.cc_users_list)
        if new_notification and 'id' in new_notification:
            self.logger.info('Notification with id={} is registered successfully!'.format(new_notification['id']))
        else:
            self.logger.warn('Unable to retrieve id of the queued notification!')

    def _read_template(self):
        if os.path.exists(self.email_template_path):
            with open(self.email_template_path, 'r') as email_template_file:
                return email_template_file.read()
        else:
            raise RuntimeError('No template file found at ''{}'' '.format(self.email_template_path))
