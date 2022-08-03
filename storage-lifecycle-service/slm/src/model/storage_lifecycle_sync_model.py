class StorageLifecycleRuleActionItems:

    def __init__(self, transition_destination):
        self.notification_queue = []
        self.destination_transitions_queues = {
            destination: [] for destination in transition_destination
        }

    def with_notification(self, path, destination, days_till_action):
        self.notification_queue.append({
          "path": path,
          "transition": destination,
          "days_till_action": days_till_action
        })

    def with_transition(self, destination, file):
        self.destination_transitions_queues[destination].append(file)

    def merge(self, to_be_merged):
        self.notification_queue.extend(to_be_merged.notification_queue)
        for destination, queue in to_be_merged.destination_transitions_queues.items():
            self.destination_transitions_queues.get(destination, {}).update(queue)
