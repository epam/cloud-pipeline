import datetime

ISO_DATE_FORMAT = "%Y-%m-%dT%H:%M:%S.%fZ"


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

    def __init__(self, notify_before_days, prolong_days, user_to_notify_ids,
                 keep_informed_admins, keep_informed_owner, enabled, subject, body):
        self.notify_before_days = notify_before_days
        self.prolong_days = prolong_days
        self.user_to_notify_ids = user_to_notify_ids
        self.keep_informed_admins = keep_informed_admins
        self.keep_informed_owner = keep_informed_owner
        self.enabled = enabled
        self.subject = subject
        self.body = body


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

    def __init__(self, execution_id, status, path, storage_class, updated):
        self.execution_id = execution_id
        self.path = path
        self.status = status
        self.storage_class = storage_class
        self.updated = updated


class LifecycleRuleParser:

    def __init__(self, default_lifecycle_notification_json):
        self.default_lifecycle_notification = self._parse_notification(default_lifecycle_notification_json)

    def parse_rule(self, rule_json_dict):
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
            else StorageLifecycleTransitionCriterion("Default", None)
        notification = self._parse_notification(rule_json_dict["notification"]) \
            if "notification" in rule_json_dict \
            else self.default_lifecycle_notification

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
            path=execution_json["path"],
            status=execution_json["status"],
            storage_class=execution_json["storageClass"],
            updated=LifecycleRuleParser._parse_timestamp(
                execution_json["updated"]
            ) if "updated" in execution_json else None
        )

    @staticmethod
    def _parse_transition_criterion(transition_criterion_json):
        if not transition_criterion_json or "type" not in transition_criterion_json:
            return StorageLifecycleTransitionCriterion("DEFAULT", None)

        return StorageLifecycleTransitionCriterion(
            type=transition_criterion_json["type"], value=transition_criterion_json["value"]
        )

    @staticmethod
    def _parse_transitions(transitions_json):
        def _parse_transition(transition_json):
            return StorageLifecycleRuleTransition(
                storage_class=transition_json["storageClass"] if "storageClass" in transitions_json else None,
                transition_date=LifecycleRuleParser._parse_timestamp(
                    transition_json["transitionDate"]
                ) if "transitionDate" in transitions_json else None,
                transition_after_days=transition_json["transitionAfterDays"] if "transitionAfterDays" in transitions_json else None
            )

        if not transitions_json:
            return []

        return [_parse_transition(transition) for transition in transitions_json]

    @staticmethod
    def _parse_prolongations(prolongations_json):
        def _parse_prolongation(prolongation_json):
            return StorageLifecycleRuleProlongation(
                prolongation_id=prolongation_json["id"] if "id" in prolongations_json else None,
                path=prolongation_json["path"] if "path" in prolongations_json else None,
                days=prolongation_json["days"] if "days" in prolongations_json else 0,
                prolonged_date=LifecycleRuleParser._parse_timestamp(
                    prolongation_json["prolongedDate"]
                ) if "prolongedDate" in prolongations_json else None
            )

        if not prolongations_json:
            return []

        return [_parse_prolongation(transition) for transition in prolongations_json]

    @staticmethod
    def _parse_notification(notification_json):
        return StorageLifecycleNotification(
            notify_before_days=notification_json["notifyBeforeDays"]
            if "notifyBeforeDays" in notification_json["notifyBeforeDays"]
            else None,
            prolong_days=notification_json["prolongDays"]
            if "prolongDays" in notification_json["prolongDays"]
            else None,
            user_to_notify_ids=notification_json["informedUserIds"]
            if "informedUserIds" in notification_json["informedUserIds"]
            else [],
            keep_informed_admins=notification_json["keepInformedAdmins"]
            if "keepInformedAdmins" in notification_json["keepInformedAdmins"]
            else False,
            keep_informed_owner=notification_json["keepInformedOwner"]
            if "keepInformedOwner" in notification_json["keepInformedOwner"]
            else False,
            enabled=notification_json["enabled"]
            if "enabled" in notification_json["enabled"]
            else True,
            subject=notification_json["subject"]
            if "subject" in notification_json["subject"]
            else None,
            body=notification_json["body"]
            if "body" in notification_json["body"]
            else None
        )

    @staticmethod
    def _parse_timestamp(timestamp_string):
        return datetime.datetime.strptime(timestamp_string, ISO_DATE_FORMAT)
