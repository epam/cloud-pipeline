class StorageLifecycleRuleActionItems:

    def __init__(self, rule_id=None, folder=None, mode="FOLDER"):
        self.rule_id = rule_id
        self.folder = folder
        self.mode = mode
        self.executions = []
        self.notification_queue = []
        self.destination_transitions_queues = {}

    def with_rule_id(self, rule_id):
        self.rule_id = rule_id
        return self

    def with_mode(self, mode):
        self.mode = mode
        return self

    def with_folder(self, folder):
        self.folder = folder
        return self

    def with_notification(self, path, storage_class, date_of_action, prolong_days):
        self.notification_queue.append({
          "path": path,
          "storage_class": storage_class,
          "date_of_action": date_of_action,
          "prolong_days": prolong_days
        })
        return self

    def with_transition(self, destination, file):
        if destination not in self.destination_transitions_queues:
            self.destination_transitions_queues[destination] = []
        self.destination_transitions_queues[destination].append(file)
        return self

    def with_execution(self, execution):
        if execution:
            self.executions.append(execution)
        return self
