#  Copyright 2022 EPAM Systems, Inc. (https://www.epam.com/)
#  #
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#  #
#     http://www.apache.org/licenses/LICENSE-2.0
#  #
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
#

class CloudPipelineNotification:

    def __init__(self, notification_template, notification_settings):
        self.template = notification_template
        self.settings = notification_settings

    @classmethod
    def build_from_dicts(cls, template_dict, settings_dict):
        template = CloudPipelineNotificationTemplate(
            template_id=template_dict["id"],
            name=template_dict["name"],
            subject=template_dict["subject"]
            if "subject" in template_dict
            else None,
            body=template_dict["body"]
            if "body" in template_dict
            else None
        )

        settings = CloudPipelineNotificationSettings(
            settings_id=int(settings_dict["id"]),
            template_id=int(settings_dict["templateId"]),
            notification_type=settings_dict["type"],
            informed_user_ids=settings_dict["informedUserIds"]
            if "informedUserIds" in settings_dict
            else [],
            keep_informed_admins=settings_dict["keepInformedAdmins"]
            if "keepInformedAdmins" in settings_dict
            else False,
            keep_informed_owner=settings_dict["keepInformedOwner"]
            if "keepInformedOwner" in settings_dict
            else False,
            enabled=settings_dict["enabled"]
            if "enabled" in settings_dict
            else True,
            threshold=int(settings_dict["threshold"])
            if "threshold" in settings_dict
            else None,
            resend_delay=int(settings_dict["resendDelay"])
            if "resendDelay" in settings_dict
            else None,
            statuses_to_inform=settings_dict["statuses_to_inform"]
            if "statuses_to_inform" in settings_dict
            else None,
        )

        return CloudPipelineNotification(template, settings)


class CloudPipelineNotificationTemplate:

    def __init__(self, template_id, name, subject, body):
        self.template_id = template_id
        self.name = name
        self.subject = subject
        self.body = body


class CloudPipelineNotificationSettings:

    def __init__(self, settings_id, template_id, notification_type, informed_user_ids,
                 keep_informed_admins, keep_informed_owner, enabled,
                 threshold=None, resend_delay=None, statuses_to_inform=None):
        self.settings_id = settings_id
        self.template_id = template_id
        self.notification_type = notification_type
        self.enabled = enabled
        self.informed_user_ids = informed_user_ids
        self.keep_informed_admins = keep_informed_admins
        self.keep_informed_owner = keep_informed_owner
        self.threshold = threshold
        self.resend_delay = resend_delay
        if statuses_to_inform is None:
            statuses_to_inform = []
        self.statuses_to_inform = statuses_to_inform

