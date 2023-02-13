class StorageUsage(object):

    def __init__(self):
        self.usage = {}

    def add_item(self, tier, size):
        if tier not in self.usage:
            self.usage[tier] = StorageUsageItem(tier)
        self.usage.get(tier).add_item(size)

    def populate(self, tier, size, count, effective_size, effective_count,
                 old_versions_size, old_versions_effective_size):
        if tier not in self.usage:
            self.usage[tier] = StorageUsageItem(tier)
        self.usage.get(tier).populate(size, count, effective_size, effective_count,
                                      old_versions_size, old_versions_effective_size)

    def get_usage(self):
        return self.usage

    def get_tiers(self):
        return self.usage.keys()

    def get_total_count(self):
        return sum([item.count for item in self.usage.values()])

    def get_total_size(self):
        return sum([item.size for item in self.usage.values()])

    def get_total_old_versions_size(self):
        return sum([item.old_versions_size for item in self.usage.values()])


class StorageUsageItem(object):

    def __init__(self, tier="STANDARD"):
        self.tier = tier
        self.size = 0
        self.effective_size = 0
        self.count = 0
        self.effective_count = 0
        self.old_versions_size = 0
        self.old_versions_effective_size = 0

    def add_item(self, size):
        self.size += size
        self.count += 1
        self.effective_size += size
        self.effective_count += 1

    def populate(self, size, count, effective_size, effective_count, old_versions_size, old_versions_effective_size):
        self.size = size
        self.count = count
        self.effective_size = effective_size
        self.effective_count = effective_count
        self.old_versions_size = old_versions_size
        self.old_versions_effective_size = old_versions_effective_size

