import os
import pykube
import requests
from requests.packages.urllib3.exceptions import InsecureRequestWarning

class NoVerify_Requests_Session(requests.Session):
    """A wrapper for requests.Session to override 'verify' property, ignoring REQUESTS_CA_BUNDLE environment variable.
    This is a workaround for https://github.com/kennethreitz/requests/issues/3829
    """
    def merge_environment_settings(self, url, proxies, stream, verify, *args, **kwargs):
        if self.verify is False:
            verify = False
        return super(NoVerify_Requests_Session, self).merge_environment_settings(url, proxies, stream, verify, *args, **kwargs)


def _noverify_session_object(strategy=None, config=None, gcloud_file=None):
    if strategy in ["token", "client-certificate", "basic-auth"]:
        return NoVerify_Requests_Session()
    elif strategy in ["gcp"]:
        return GCPSession(config, gcloud_file).create()
    else:
        return NoVerify_Requests_Session()

class NoVerify_Kube_Client(object):
    """
    Wrapper for PyKube interface which prioritizes session.verify before REQUESTS_CA_BUNDLE
    Default version will ignoe session.verify=False if REQUESTS_CA_BUNDLE is set
    """
    @staticmethod
    def get_client(config):
        pykube.session._session_object = _noverify_session_object
        requests.packages.urllib3.disable_warnings(InsecureRequestWarning)
        return pykube.HTTPClient(config)