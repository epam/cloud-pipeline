# Copyright 2022 EPAM Systems, Inc. (https://www.epam.com/)
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

from sls.util.date_utils import parse_timestamp, parse_date


class StorageLifecycleTransitionCriterion:

    def __init__(self, type, value):
        self.type = type
        self.value = value


class StorageLifecycleRuleTransition:

    def __init__(self, storage_class, transition_date=None, transition_after_days=None):
        self.storage_class = storage_class
        self.transition_date = transition_date
        self.transition_after_days = transition_after_days


class StorageLifecycleRuleProlongation:

    def __init__(self, prolongation_id, path, prolonged_date, days):
        self.prolongation_id = prolongation_id
        self.path = path
        self.prolonged_date = prolonged_date
        self.days = days


class StorageLifecycleNotification:

    def __init__(self, notify_before_days, prolong_days, recipients, enabled, subject, body, notify_users):
        self.notify_before_days = notify_before_days
        self.prolong_days = prolong_days
        self.recipients = recipients
        self.enabled = enabled
        self.subject = subject
        self.body = body
        self.notify_users = notify_users


class StorageLifecycleRule:

    def __init__(self, rule_id, datastorage_id, path_glob, transition_method, object_glob=None,
                 transition_criterion=StorageLifecycleTransitionCriterion("DEFAULT", None),
                 prolongations=None, transitions=None, notification=None):

        if transitions is None:
            transitions = []
        if prolongations is None:
            prolongations = []

        self.rule_id = rule_id
        self.datastorage_id = datastorage_id
        self.path_glob = path_glob
        self.transition_method = transition_method
        self.object_glob = object_glob
        self.transition_criterion = transition_criterion
        self.prolongations = prolongations
        self.transitions = transitions
        self.notification = notification


class StorageLifecycleRuleExecution:

    def __init__(self, execution_id, rule_id, status, path, storage_class, updated):
        self.execution_id = execution_id
        self.rule_id = rule_id
        self.path = path
        self.status = status
        self.storage_class = storage_class
        self.updated = updated


class LifecycleRuleParser:

    def parse_rule(self, rule_json_dict, default_lifecycle_notification):
        if not rule_json_dict:
            return None

        transitions = self._parse_transitions(rule_json_dict["transitions"])\
            if "transitions" in rule_json_dict \
            else []
        prolongations = self._parse_prolongations(rule_json_dict["prolongations"])\
            if "prolongations" in rule_json_dict \
            else []
        transition_criterion = self._parse_transition_criterion(rule_json_dict["transitionCriterion"]) \
            if "transitionCriterion" in rule_json_dict \
            else StorageLifecycleTransitionCriterion("DEFAULT", None)
        notification = self._parse_notification(rule_json_dict["notification"], default_lifecycle_notification) \
            if "notification" in rule_json_dict \
            else default_lifecycle_notification

        return StorageLifecycleRule(
            rule_id=rule_json_dict["id"],
            datastorage_id=rule_json_dict["datastorageId"],
            path_glob=rule_json_dict["pathGlob"],
            object_glob=rule_json_dict["objectGlob"] if "objectGlob" in rule_json_dict else None,
            transitions=transitions,
            transition_method=rule_json_dict["transitionMethod"],
            prolongations=prolongations,
            transition_criterion=transition_criterion,
            notification=notification
        )

    @staticmethod
    def parse_execution(execution_json):
        if not execution_json:
            return None
        return StorageLifecycleRuleExecution(
            execution_id=execution_json["id"],
            rule_id=execution_json["ruleId"],
            path=execution_json["path"],
            status=execution_json["status"],
            storage_class=execution_json["storageClass"],
            updated=parse_timestamp(
                execution_json["updated"]
            ) if "updated" in execution_json else None
        )

    @staticmethod
    def _parse_transition_criterion(transition_criterion_json):
        if not transition_criterion_json or "type" not in transition_criterion_json:
            return StorageLifecycleTransitionCriterion("DEFAULT", None)

        return StorageLifecycleTransitionCriterion(
            type=transition_criterion_json["type"],
            value=transition_criterion_json["value"] if "value" in transition_criterion_json else None
        )

    @staticmethod
    def _parse_transitions(transitions_json):
        def _parse_transition(transition_json):
            return StorageLifecycleRuleTransition(
                storage_class=transition_json["storageClass"] if "storageClass" in transition_json else None,
                transition_date=parse_date(
                    transition_json["transitionDate"]
                ) if "transitionDate" in transition_json else None,
                transition_after_days=transition_json["transitionAfterDays"] if "transitionAfterDays" in transition_json else None
            )

        if not transitions_json:
            return []

        return [_parse_transition(transition) for transition in transitions_json]

    @staticmethod
    def _parse_prolongations(prolongations_json):
        def _parse_prolongation(prolongation_json):
            return StorageLifecycleRuleProlongation(
                prolongation_id=prolongation_json["id"] if "id" in prolongation_json else None,
                path=prolongation_json["path"] if "path" in prolongation_json else None,
                days=prolongation_json["days"] if "days" in prolongation_json else 0,
                prolonged_date=parse_timestamp(
                    prolongation_json["prolongedDate"]
                ) if "prolongedDate" in prolongation_json else None
            )

        if not prolongations_json:
            return []

        return [_parse_prolongation(prolongation) for prolongation in prolongations_json]

    @staticmethod
    def _parse_notification(notification_json, default_notification):
        return StorageLifecycleNotification(
            notify_before_days=notification_json["notifyBeforeDays"]
            if "notifyBeforeDays" in notification_json else default_notification.notify_before_days,
            prolong_days=notification_json["prolongDays"]
            if "prolongDays" in notification_json else default_notification.prolong_days,
            recipients=notification_json["recipients"]
            if "recipients" in notification_json else default_notification.recipients,
            enabled=notification_json["enabled"] if "enabled" in notification_json else default_notification.enabled,
            subject=notification_json["subject"] if "subject" in notification_json else default_notification.subject,
            body=notification_json["body"] if "body" in notification_json else default_notification.body,
            notify_users=notification_json["notifyUsers"]
            if "notifyUsers" in notification_json else default_notification.notify_users
        )
