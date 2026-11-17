import os
import pykube
import requests
from requests.packages.urllib3.exceptions import InsecureRequestWarning

try:
    # pykube 0.15 (Python 2) builds the session of a client in this module
    from pykube import session as _pykube_session
except ImportError:
    # pykube-ng (Python 3) builds it in HTTPClient, and its adapter sets the CA of the cluster for each request
    _pykube_session = None
    from pykube.http import KubernetesHTTPAdapter as _KubernetesHTTPAdapter

    class _NoVerify_Kube_HTTPAdapter(_KubernetesHTTPAdapter):
        """A wrapper for pykube-ng's adapter to keep 'verify' False if the session sets it so,
        instead of the CA of the cluster config.
        """
        def _setup_request_certificates(self, config, request, kwargs):
            verify = kwargs.get('verify')
            super(_NoVerify_Kube_HTTPAdapter, self)._setup_request_certificates(config, request, kwargs)
            if verify is False:
                kwargs['verify'] = False


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
        return _pykube_session.GCPSession(config, gcloud_file).create()
    else:
        return NoVerify_Requests_Session()


def _noverify_session(session, adapter):
    noverify_session = NoVerify_Requests_Session()
    noverify_session.headers.update(session.headers)
    noverify_session.mount('https://', adapter)
    noverify_session.mount('http://', adapter)
    noverify_session.verify = session.verify
    return noverify_session


class NoVerify_Kube_Client(object):
    """
    Wrapper for PyKube interface which prioritizes session.verify before REQUESTS_CA_BUNDLE
    Default version will ignoe session.verify=False if REQUESTS_CA_BUNDLE is set
    """
    @staticmethod
    def get_client(config):
        requests.packages.urllib3.disable_warnings(InsecureRequestWarning)
        if _pykube_session is not None:
            _pykube_session._session_object = _noverify_session_object
            return pykube.HTTPClient(config)
        adapter = _NoVerify_Kube_HTTPAdapter(config)
        client = pykube.HTTPClient(config, http_adapter=adapter)
        client.session = _noverify_session(client.session, adapter)
        return client
