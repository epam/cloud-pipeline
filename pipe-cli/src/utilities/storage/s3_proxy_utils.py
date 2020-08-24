# Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

from botocore.vendored.requests.adapters import HTTPAdapter
from botocore.vendored.requests.packages.urllib3 import ProxyManager
from botocore.vendored.requests.packages.urllib3.connection import VerifiedHTTPSConnection
from botocore.vendored.requests.packages.urllib3.poolmanager import pool_classes_by_scheme, SSL_KEYWORDS

try:
    import http.client as http_client  # Python 3
    from http import HTTPStatus  # Python 3
    OK = HTTPStatus.OK
except ImportError:
    import httplib as http_client  # Python 2
    from httplib import OK  # Python 2


class VerifiedHTTPSConnectionWithHeaders(VerifiedHTTPSConnection):

    def _tunnel(self):
        """This is just a simple rework of the CONNECT method to combine
        the headers with the CONNECT request as it causes problems for
        some proxies
        """
        connect_str = "CONNECT %s:%d HTTP/1.0\r\n" % (self._tunnel_host, self._tunnel_port)
        header_bytes = connect_str.encode("ascii")

        for header, value in self._tunnel_headers.items():
            header_str = "%s: %s\r\n" % (header, value)
            header_bytes += header_str.encode("latin-1")

        self.send(header_bytes + b'\r\n')

        response = self.response_class(self.sock, method=self._method)
        (version, code, message) = response._read_status()

        if code != OK:
            self.close()
            raise OSError("Tunnel connection failed: %d %s" % (code,
                                                               message.strip()))
        while True:
            line = response.fp.readline(http_client._MAXLINE + 1)
            if len(line) > http_client._MAXLINE:
                raise RuntimeError("header line")
            if not line:
                # for sites which EOF without sending a trailer
                break
            if line in (b'\r\n', b'\n', b''):
                break


class AwsProxyManager(ProxyManager):

    def __init__(self, proxy_url, num_pools=10, headers=None,
                 proxy_headers=None, **connection_pool_kw):
        ProxyManager.__init__(self, proxy_url, num_pools, headers, proxy_headers, **connection_pool_kw)
        # Locally set the pool classes and keys so other PoolManagers can
        # override them.
        self.pool_classes_by_scheme = pool_classes_by_scheme

    def _new_pool(self, scheme, host, port):
        """
        Create a new :class:`ConnectionPool` based on host, port and scheme.

        This method is used to actually create the connection pools handed out
        by :meth:`connection_from_url` and companion methods. It is intended
        to be overridden for customization.
        """
        pool_cls = self.pool_classes_by_scheme[scheme]
        kwargs = self.connection_pool_kw
        if scheme == 'http':
            kwargs = self.connection_pool_kw.copy()
            for kw in SSL_KEYWORDS:
                kwargs.pop(kw, None)

        return pool_cls(host, port, **kwargs)


def proxy_from_url(url, **kw):
    return AwsProxyManager(url, **kw)


class AwsProxyConnectWithHeadersHTTPSAdapter(HTTPAdapter):
    """Overriding HTTP Adapter so that we can use our own Connection, since
        we need to get at _tunnel()
    """
    def proxy_manager_for(self, proxy, **proxy_kwargs):
        """Return urllib3 ProxyManager for the given proxy.

        This method should not be called from user code, and is only
        exposed for use when subclassing the
        :class:`HTTPAdapter <requests.adapters.HTTPAdapter>`.

        :param proxy: The proxy to return a urllib3 ProxyManager for.
        :param proxy_kwargs: Extra keyword arguments used to configure the Proxy Manager.
        :returns: ProxyManager
        """
        if not proxy in self.proxy_manager:
            proxy_headers = self.proxy_headers(proxy)
            self.proxy_manager[proxy] = proxy_from_url(
                proxy,
                proxy_headers=proxy_headers,
                num_pools=self._pool_connections,
                maxsize=self._pool_maxsize,
                block=self._pool_block,
                **proxy_kwargs)

        manager = self.proxy_manager[proxy]
        # Need to override the ConnectionCls with our Subclassed one to get at _tunnel()
        manager.pool_classes_by_scheme['https'].ConnectionCls = VerifiedHTTPSConnectionWithHeaders
        return manager
