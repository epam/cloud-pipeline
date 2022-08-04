from slm.src.model.storage_lifecycle_rule_model import LifecycleRuleParser


class CloudPipelineDataSource:

    def __init__(self):
        pass

    def load_lifecycle_rules_for_storage(self, datastorage_id):
        pass

    def load_lifecycle_rule(self, datastorage_id, rule_id):
        pass

    def create_lifecycle_rule_execution(self, datastorage_id, rule_id, execution):
        pass

    def load_lifecycle_rule_executions(self, datastorage_id, rule_id):
        pass

    def load_lifecycle_rule_executions_by_path(self, datastorage_id, rule_id, path):
        pass

    def update_status_lifecycle_rule_execution(self, datastorage_id, execution_id, status):
        pass

    def load_default_lifecycle_rule_notification(self):
        pass


class RESTApiCloudPipelineDataSource:

    DATASTORAGE_LIFECYCLE_ACTION_NOTIFICATION_TYPE = "DATASTORAGE_LIFECYCLE_ACTION"

    def __init__(self, api):
        self.api = api
        self.parser = LifecycleRuleParser(self._load_default_lifecycle_rule_notification())

    def load_lifecycle_rules_for_storage(self, datastorage_id):
        rules_json = self.api.load_lifecycle_rules_for_storage(datastorage_id)
        return [self.parser.parse_rule(rule) for rule in (rules_json if rules_json else [])]

    def load_lifecycle_rule(self, datastorage_id, rule_id):
        rule_json = self.api.load_lifecycle_rule(datastorage_id, rule_id)
        return self.parser.parse_rule(rule_json)

    def create_lifecycle_rule_execution(self, datastorage_id, rule_id, execution):
        return self.parser.parse_execution(
            self.api.create_lifecycle_rule_execution(datastorage_id, rule_id, execution)
        )

    def load_lifecycle_rule_executions(self, datastorage_id, rule_id):
        executions_json = self.api.load_lifecycle_rule_executions(datastorage_id, rule_id)
        return [self.parser.parse_execution(execution) for execution in (executions_json if executions_json else [])]

    def load_lifecycle_rule_executions_by_path(self, datastorage_id, rule_id, path):
        executions_json = self.api.load_lifecycle_rule_executions_by_path(datastorage_id, rule_id, path)
        return [self.parser.parse_execution(execution) for execution in (executions_json if executions_json else [])]

    def update_status_lifecycle_rule_execution(self, datastorage_id, execution_id, status):
        return self.parser.parse_execution(
            self.api.update_status_lifecycle_rule_execution(datastorage_id, execution_id, status)
        )

    def _load_default_lifecycle_rule_notification(self):
        default_lifecycle_notification_templates = next(
            filter(
                lambda t: t["name"] == self.DATASTORAGE_LIFECYCLE_ACTION_NOTIFICATION_TYPE,
                self.api.load_notification_templates()
            ), None
        )

        default_lifecycle_notification_settings = next(
            filter(
                lambda t: t["type"] == self.DATASTORAGE_LIFECYCLE_ACTION_NOTIFICATION_TYPE,
                self.api.load_notification_settings()
            ), None
        )
        default_lifecycle_rule_prolong_days = self.api.get_preference("storage.lifecycle.prolong.days")
        default_lifecycle_rule_notify_before_days = self.api.get_preference("storage.lifecycle.notify.before.days")
        if not default_lifecycle_notification_settings or not default_lifecycle_notification_templates \
                or not default_lifecycle_rule_prolong_days or not default_lifecycle_rule_notify_before_days:
            return None

        return {
            "id": default_lifecycle_notification_templates["id"],
            "keepInformedAdmins": default_lifecycle_notification_settings["keepInformedAdmins"],
            "keepInformedOwners": default_lifecycle_notification_settings["keepInformedOwners"],
            "enabled": default_lifecycle_notification_settings["enabled"],
            "subject": default_lifecycle_notification_templates["subject"],
            "body": default_lifecycle_notification_templates["body"],
            "prolongDays": default_lifecycle_rule_prolong_days,
            "notifyBeforeDays": default_lifecycle_rule_notify_before_days
        }
