from .json_parser import JsonParser


class CloudRegion:
    def __init__(self, id, name, owner, region_id, provider, default,
                 storage_lifecycle_service_prop=None,
                 cloud_specific_attributes=None):
        self.id = int(id)
        self.name = str(name)
        self.owner = str(owner)
        self.region_id = str(region_id)
        self.default = default
        self.provider = str(provider)
        self.storage_lifecycle_service_properties = storage_lifecycle_service_prop
        self.cloud_specific_attributes = cloud_specific_attributes

    @classmethod
    def from_json(cls, data):
        id = JsonParser.get_required_field(data, 'id')
        name = JsonParser.get_required_field(data, 'name')
        owner = JsonParser.get_required_field(data, 'owner')
        region_id = JsonParser.get_required_field(data, 'regionId')
        provider = JsonParser.get_required_field(data, 'provider')
        default = JsonParser.get_required_field(data, 'default')

        storage_lifecycle_service_prop = JsonParser.get_optional_field(data, "storageLifecycleServiceProperties")
        storage_lifecycle_service_properties = StorageLifecycleServiceProperties(storage_lifecycle_service_prop) \
            if storage_lifecycle_service_prop is not None \
            else None

        cloud_specific_attributes = cloud_specific_attributes_from_json(provider, data)
        return CloudRegion(id, name, owner, region_id, provider, default,
                           storage_lifecycle_service_prop=storage_lifecycle_service_properties,
                           cloud_specific_attributes=cloud_specific_attributes)


def cloud_specific_attributes_from_json(region_type, data):
    if region_type == "AWS":
        return AWSRegionAttributes.from_json(data)
    else:
        return None


class StorageLifecycleServiceProperties:

    def __init__(self, sls_properties_object):
        if "properties" in sls_properties_object:
            self.properties = sls_properties_object["properties"]
        else:
            self.properties = {}


class AWSRegionAttributes:

    def __init__(self, iam_role, temp_credentials_role, profile):
        self.iam_role = iam_role
        self.temp_credentials_role = temp_credentials_role
        self.profile = profile

    @classmethod
    def from_json(cls, data):
        iam_role = JsonParser.get_optional_field(data, 'iamRole')
        temp_credentials_role = JsonParser.get_optional_field(data, 'tempCredentialsRole')
        profile = JsonParser.get_optional_field(data, 'profile')
        return AWSRegionAttributes(iam_role, temp_credentials_role, profile)
