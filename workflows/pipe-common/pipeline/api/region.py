from .json_parser import JsonParser


class CloudRegion:
    def __init__(self, id, name, owner, region_id, provider, default, storage_lifecycle_service_prop=None):
        self.id = int(id)
        self.name = str(name)
        self.owner = str(owner)
        self.region_id = str(region_id)
        self.default = default
        self.provider = str(provider)
        self.storage_lifecycle_service_properties = StorageLifecycleServiceProperties(storage_lifecycle_service_prop) \
            if storage_lifecycle_service_prop is not None \
            else None

    @classmethod
    def from_json(cls, data):
        id = JsonParser.get_required_field(data, 'id')
        name = JsonParser.get_required_field(data, 'name')
        owner = JsonParser.get_required_field(data, 'owner')
        region_id = JsonParser.get_required_field(data, 'regionId')
        provider = JsonParser.get_required_field(data, 'provider')
        default = JsonParser.get_required_field(data, 'default')
        storage_lifecycle_service_properties = JsonParser.get_optional_field(data, "storageLifecycleServiceProperties")
        return CloudRegion(id, name, owner, region_id, provider, default, storage_lifecycle_service_properties)


class StorageLifecycleServiceProperties:

    def __init__(self, properties):
        self.properties = properties
