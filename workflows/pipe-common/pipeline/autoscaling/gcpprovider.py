from googleapiclient import discovery

INSTANCE_USER_NAME = "pipeline"

NO_BOOT_DEVICE_NAME = 'sdb1'


class GCPInstanceProvider(object):

    def __init__(self, project_id, cloud_region):
        self.cloud_region = cloud_region
        self.project_id = project_id
        self.client = discovery.build('compute', 'v1')

    def find_and_tag_instance(self, old_id, new_id):
        pass

    def verify_run_id(self, run_id):
        pass

    def check_instance(self, ins_id, run_id, num_rep, time_rep):
        pass

    def get_instance_names(self, ins_id):
        pass

    def find_instance(self, run_id):
        pass

    def terminate_instance(self, ins_id):
        pass

    def terminate_instance_by_ip(self, internal_ip):
        pass