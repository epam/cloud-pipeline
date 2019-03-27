
class AbstractInstanceProvider(object):

    def run_instance(self, is_spot, bid_price, ins_type, ins_hdd, ins_img, ins_key, run_id, kms_encyr_key_id,
                     num_rep, time_rep, kube_ip, kubeadm_token):
        pass

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

    def terminate_instance_by_ip(self, node_internal_ip):
        pass
