from json_parser import JsonParser


class CloudRegion:
    def __init__(self, id, name, owner, region_id, provider, default):
        self.id = int(id)
        self.name = str(name)
        self.owner = str(owner)
        self.region_id = str(region_id)
        self.default = default
        self.provider = str(provider)

    @classmethod
    def from_json(cls, data):
        id = JsonParser.get_required_field(data, 'id')
        name = JsonParser.get_required_field(data, 'name')
        owner = JsonParser.get_required_field(data, 'owner')
        region_id = JsonParser.get_required_field(data, 'regionId')
        provider = JsonParser.get_required_field(data, 'provider')
        default = JsonParser.get_required_field(data, 'default')
        return CloudRegion(id, name, owner, region_id, provider, default)
