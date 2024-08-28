#  Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

import logging
import os

import click
import errno
import jwt
import prettytable
import requests
from cheroot import wsgi
from wsgidav import http_authenticator
from wsgidav import util
from wsgidav.dc.base_dc import BaseDomainController
from wsgidav.error_printer import ErrorPrinter
from wsgidav.fs_dav_provider import FilesystemProvider
from wsgidav.http_authenticator import HTTPAuthenticator
from wsgidav.request_resolver import RequestResolver
from wsgidav.wsgidav_app import WsgiDAVApp

from src.utilities.background_process_manager import BackgroundProcessManager


def mkdir(path):
    try:
        os.makedirs(path)
    except OSError as e:
        if e.errno == errno.EEXIST and os.path.isdir(path):
            pass
        else:
            raise


class StorageServerArgs:

    def __init__(self, path=None, host=None, port=None):
        self.path = str(path) if path else None
        self.host = str(host) if host else None
        self.port = int(port) if port else None

    @staticmethod
    def from_args(parsed_args):
        return StorageServerArgs(
            path=parsed_args.get('path'),
            host=parsed_args.get('host'),
            port=parsed_args.get('port'),
        )

    @staticmethod
    def parser(parse_func):
        def _parse(args):
            return StorageServerArgs.from_args(parse_func(args))

        return _parse


class BearerDomainController(BaseDomainController):

    def __init__(self, wsgidav_app, config):
        super().__init__(wsgidav_app, config)
        dc_conf = util.get_dict_value(config, "bearer_dc", as_dict=True)
        self._pub_key = dc_conf.get("pub_key")
        if self._pub_key is None:
            raise RuntimeError("Missing option: bearer_dc.pub_key")

    def __str__(self):
        return "{}()".format(self.__class__.__name__)

    def get_domain_realm(self, *args, **kwargs):
        return '/'

    def require_authentication(self, *args, **kwargs):
        return True

    def is_share_anonymous(self, *args, **kwargs):
        return False

    def basic_auth_user(self, realm, user_name, password, environ):
        logging.warning('Basic auth is not supported')
        return False

    def digest_auth_user(self, realm, user_name, environ):
        logging.warning('Digest auth is not supported')
        return False

    def bearer_auth_user(self, realm, bearer, environ):
        try:
            self._decode_jwt(bearer, self._pub_key)
            return True
        except Exception:
            logging.warning('Bearer decoding has failed', exc_info=True)
            return False

    def _decode_jwt(self, token, public_key):
        try:
            return jwt.decode(
                token,
                self._normalize_pub_key(public_key),
                algorithms=["RS512"]
            )
        except jwt.ExpiredSignatureError:
            logging.warning("Expired token.", exc_info=True)
            raise RuntimeError("Expired token.")
        except jwt.InvalidTokenError:
            logging.warning("Invalid token.", exc_info=True)
            raise RuntimeError("Invalid token.")

    def supports_http_digest_auth(self):
        return False

    def _normalize_pub_key(self, pub_key):
        if "BEGIN PUBLIC KEY" not in pub_key:
            return "-----BEGIN PUBLIC KEY-----\n" \
                + pub_key \
                + "\n-----END PUBLIC KEY-----"
        else:
            return pub_key


class BearerHTTPAuthenticator(HTTPAuthenticator):

    def __init__(self, wsgidav_app, next_app, config):
        super().__init__(wsgidav_app, next_app, config)
        self._logger = util.get_module_logger(http_authenticator.__name__)

    def __call__(self, environ, start_response):
        realm = self.domain_controller.get_domain_realm(environ["PATH_INFO"], environ)

        environ["wsgidav.auth.realm"] = realm
        environ["wsgidav.auth.user_name"] = "user"

        force_logout = False
        if "logout" in environ.get("QUERY_STRING", ""):
            force_logout = True
            self._logger.warning("Force logout")

        if environ["REQUEST_METHOD"] == "OPTIONS":
            self._logger.warning("No authorization required for OPTIONS method")
            return self.next_app(environ, start_response)

        if "HTTP_AUTHORIZATION" in environ and not force_logout:
            auth_header = environ["HTTP_AUTHORIZATION"]
            auth_match = self._header_method.search(auth_header)
            auth_method = "None"
            if auth_match:
                auth_method = auth_match.group(1).lower()

            if auth_method == "digest":
                return self.handle_digest_auth_response(environ, start_response)
            elif auth_method == "basic":
                return self.handle_basic_auth_response(environ, start_response)
            elif auth_method == "bearer":
                return self.handle_bearer_auth_request(environ, start_response)

            self._logger.warning(
                "HTTPAuthenticator: respond with 400 Bad request; Auth-Method: {}".format(
                    auth_method
                )
            )

            start_response(
                "400 Bad Request",
                [("Content-Length", "0"), ("Date", util.get_rfc1123_time())],
            )
            return [""]

        return self.send_basic_auth_response(environ, start_response)

    def handle_bearer_auth_request(self, environ, start_response):
        realm = self.domain_controller.get_domain_realm(environ["PATH_INFO"], environ)
        auth_header = environ["HTTP_AUTHORIZATION"]
        try:
            auth_value = auth_header[len("Bearer "):].strip()
        except Exception:
            auth_value = ""

        if self.domain_controller.bearer_auth_user(realm, auth_value, environ):
            environ["wsgidav.auth.realm"] = realm
            return self.next_app(environ, start_response)

        self._logger.warning("Authentication (bearer) failed for bearer, realm {!r}.".format(realm))
        return self.send_basic_auth_response(environ, start_response)


class StorageServerManager:

    def start(self, path, host, port, read_only,
              auth_anonymous,
              auth_basic, auth_basic_username, auth_basic_password,
              auth_bearer, auth_bearer_pub_key,
              timeout_start, foreground, log_file, parse_storage_server_start_args):
        background_proc_manager = BackgroundProcessManager(
            required_proc_args=['storage', 'server', 'start'],
            parse_proc_args=StorageServerArgs.parser(parse_storage_server_start_args))
        mkdir(path)
        if foreground:
            return self._serve_foreground(host, port, path, read_only,
                                          auth_anonymous,
                                          auth_basic, auth_basic_username, auth_basic_password,
                                          auth_bearer, auth_bearer_pub_key)
        else:
            return self._serve_background(host, port, path, timeout_start, log_file, background_proc_manager)

    def _serve_foreground(self, host, port, path, read_only,
                          auth_anonymous,
                          auth_basic, auth_basic_username, auth_basic_password,
                          auth_bearer, auth_bearer_pub_key):
        logging.info('Initializing storage server dav://%s:%s -> %s...', host, port, path)
        wsgi_app_config = {
            "host": host,
            "port": port,
            "provider_mapping": {
                "/": FilesystemProvider(path, readonly=read_only, fs_opts={})
            },
            "verbose": 4,
            "logging": {
                "enable": True,
                "enable_loggers": [],
            },
            "property_manager": True,
            "lock_storage": True,
            "hotfixes": {
                "win_accept_anonymous_options": True
            },
        }
        if auth_anonymous:
            wsgi_app_config.update({
                "middleware_stack": [
                    ErrorPrinter,
                    HTTPAuthenticator,
                    RequestResolver,
                ],
                "http_authenticator": {
                    "domain_controller": None,
                    "accept_basic": True,
                    "accept_digest": False,
                    "default_to_digest": False,
                    "ssl_certificate": False
                },
                "simple_dc": {
                    "user_mapping": {
                        "*": True
                    }
                }
            })
        elif auth_basic:
            wsgi_app_config.update({
                "middleware_stack": [
                    ErrorPrinter,
                    HTTPAuthenticator,
                    RequestResolver,
                ],
                "http_authenticator": {
                    "domain_controller": None,
                    "accept_basic": True,
                    "accept_digest": False,
                    "default_to_digest": False,
                    "ssl_certificate": False
                },
                "simple_dc": {
                    "user_mapping": {
                        "*": {
                            auth_basic_username: {
                                "password": auth_basic_password
                            }
                        }
                    }
                }
            })
        elif auth_bearer:
            wsgi_app_config.update({
                "middleware_stack": [
                    ErrorPrinter,
                    BearerHTTPAuthenticator,
                    RequestResolver,
                ],
                "http_authenticator": {
                    "domain_controller": "src.utilities.storage_server_manager.BearerDomainController",
                    "accept_basic": True,
                    "accept_digest": False,
                    "default_to_digest": False,
                    "ssl_certificate": False
                },
                "bearer_dc": {
                    "pub_key": auth_bearer_pub_key
                }
            })
        else:
            raise RuntimeError('No auth method has been enabled')
        wsgi_app = WsgiDAVApp(wsgi_app_config)
        wsgi_server = wsgi.Server(bind_addr=(host, port), wsgi_app=wsgi_app, server_name='PIPE_STORAGE_SERVER')

        logging.info('Serving storage server...')
        try:
            wsgi_server.start()
        except KeyboardInterrupt:
            logging.warning('Interrupted.')
        finally:
            wsgi_server.stop()

    def _serve_background(self, host, port, path, timeout_start, log_file, background_proc_manager):
        logging.info('Launching background storage server dav://%s:%s -> %s...', host, port, path)
        background_proc_manager.launch(
            is_ready_func=lambda proc: self._is_dav_ready('127.0.0.1', port, timeout=10),
            log_file=log_file,
            polling_timeout=timeout_start,
            polling_delay=1)

    def _is_dav_ready(self, host, port, timeout):
        try:
            response = requests.request('OPTIONS', 'http://{}:{}/'.format(host, port), timeout=timeout)
            return response.status_code == 200
        except Exception:
            logging.warning('WebDAV server is not ready yet', exc_info=True)
            return False

    def stop(self, path, host, port, timeout_stop, force, ignore_owner, parse_storage_server_start_args):
        background_proc_manager = BackgroundProcessManager(
            required_proc_args=['storage', 'server', 'start'],
            parse_proc_args=StorageServerArgs.parser(parse_storage_server_start_args))
        owner = self._get_current_user() if not ignore_owner else None
        for proc_info in background_proc_manager.find():
            if owner and proc_info.owner != owner:
                logging.debug('Skipping process #%s '
                              'because it has %s owner but %s owner is required...',
                              proc_info.pid, proc_info.owner, owner)
                continue
            if path and proc_info.args.path != path:
                logging.debug('Skipping process #%s '
                              'because it has %s path but %s path is required.',
                              proc_info.pid, proc_info.args.path, path)
                continue
            if host and proc_info.args.host != host:
                logging.debug('Skipping process #%s '
                              'because it has %s host but %s host is required.',
                              proc_info.pid, proc_info.args.host, host)
                continue
            if port and proc_info.args.port != port:
                logging.debug('Skipping process #%s '
                              'because it has %s port but %s port is required.',
                              proc_info.pid, proc_info.args.port, port)
                continue
            background_proc_manager.kill(proc_info.proc, timeout_stop, force)

    def _get_current_user(self):
        import psutil
        return psutil.Process().username()

    def list(self, parse_storage_server_start_args):
        background_proc_manager = BackgroundProcessManager(
            required_proc_args=['storage', 'server', 'start'],
            parse_proc_args=StorageServerArgs.parser(parse_storage_server_start_args))
        procs_table = prettytable.PrettyTable()
        procs_table.field_names = ['PID', 'PPID', 'Owner', 'Path', 'Host', 'Port']
        procs_table.sortby = 'PID'
        procs_table.align = 'l'
        for proc_info in background_proc_manager.find():
            procs_table.add_row([proc_info.pid,
                                 proc_info.ppid,
                                 proc_info.owner,
                                 proc_info.args.path,
                                 proc_info.args.host,
                                 proc_info.args.port, ])
        click.echo(procs_table)
