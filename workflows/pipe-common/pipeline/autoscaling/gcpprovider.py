from googleapiclient import discovery

INSTANCE_USER_NAME = "pipeline"

NO_BOOT_DEVICE_NAME = 'sdb1'


class GCPInstanceProvider(object):

    def __init__(self, project_id, cloud_region):
        self.cloud_region = cloud_region
        self.project_id = project_id
        self.client = discovery.build('compute', 'v1')