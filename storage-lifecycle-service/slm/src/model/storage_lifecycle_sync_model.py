class StorageLifecycleRuleActionItems:

    def __init__(self, transition_destination):
        self.executions = []
        self.notification_queue = []
        self.destination_transitions_queues = {
            destination: [] for destination in transition_destination
        }

    def with_notification(self, path, destination, date_of_action):
        self.notification_queue.append({
          "path": path,
          "transition": destination,
          "date_of_action": date_of_action
        })

    def with_transition(self, destination, file):
        self.destination_transitions_queues[destination].append(file)

    def with_execution(self, execution):
        if execution:
            self.executions.append(execution)

    def merge(self, to_be_merged):
        if to_be_merged:
            self.executions.extend(to_be_merged.executions)
            self.notification_queue.extend(to_be_merged.notification_queue)
            for destination, queue in to_be_merged.destination_transitions_queues.items():
                self.destination_transitions_queues.get(destination, {}).update(queue)

    def copy(self):
        result = StorageLifecycleRuleActionItems([])
        result.merge(self)
        return result

