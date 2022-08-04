class StorageLifecycleRuleActionItems:

    def __init__(self, rule_id, transition_destination):
        self.rule_id = rule_id
        self.executions = []
        self.notification_queue = []
        self.destination_transitions_queues = {
            destination: [] for destination in transition_destination
        }

    def with_rule_id(self, rule_id):
        self.rule_id = rule_id
        return self

    def with_notification(self, path, destination, date_of_action, prolong_days):
        self.notification_queue.append({
          "path": path,
          "transition": destination,
          "date_of_action": date_of_action,
          "prolong_days": prolong_days
        })
        return self

    def with_transition(self, destination, file):
        self.destination_transitions_queues[destination].append(file)
        return self

    def with_execution(self, execution):
        if execution:
            self.executions.append(execution)
        return self

    def merge(self, to_be_merged):
        if to_be_merged:
            self.rule_id = to_be_merged.rule_id
            self.executions.extend(to_be_merged.executions)
            self.notification_queue.extend(to_be_merged.notification_queue)
            for destination, queue in to_be_merged.destination_transitions_queues.items():
                self.destination_transitions_queues.get(destination, {}).update(queue)

    def copy(self):
        result = StorageLifecycleRuleActionItems(None, [])
        result.merge(self)
        return result

