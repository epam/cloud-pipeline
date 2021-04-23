# Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

import logging
import os
import socket
import select
import time


DEFAULT_LOGGING_FORMAT = '%(asctime)s:%(levelname)s: %(message)s'


def create_foreground_tunnel(local_host, local_port, remote_host, remote_port,
                             chunk_size=4096, server_delay=0.0001):
    target_endpoint = (remote_host, remote_port)
    logging.info('Initializing tunnel %s:%s:%s...', local_port, remote_host, remote_port)
    server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server_socket.bind((local_host, local_port))
    server_socket.listen(5)
    inputs = []
    channel = {}
    configure_graceful_exiting()
    logging.info('Serving tunnel...')
    try:
        inputs.append(server_socket)
        while True:
            time.sleep(server_delay)
            logging.info('Waiting for connections...')
            inputs_ready, _, _ = select.select(inputs, [], [])
            for input in inputs_ready:
                if input == server_socket:
                    try:
                        logging.info('Initializing client connection...')
                        client_socket, address = server_socket.accept()
                    except KeyboardInterrupt:
                        raise
                    except:
                        logging.exception('Cannot establish client connection')
                        break
                    try:
                        logging.info('Initializing tunnel connection...')
                        tunnel_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                        tunnel_socket.connect(target_endpoint)
                    except KeyboardInterrupt:
                        raise
                    except:
                        logging.exception('Cannot establish tunnel connection')
                        client_socket.close()
                        break
                    inputs.append(client_socket)
                    inputs.append(tunnel_socket)
                    channel[client_socket] = tunnel_socket
                    channel[tunnel_socket] = client_socket
                    break

                logging.debug('Reading data...')
                read_data = None
                sent_data = None
                try:
                    read_data = input.recv(chunk_size)
                except KeyboardInterrupt:
                    raise
                except:
                    logging.exception('Cannot read data from socket')
                if read_data:
                    logging.debug('Writing data...')
                    try:
                        channel[input].send(read_data)
                        sent_data = read_data
                    except KeyboardInterrupt:
                        raise
                    except:
                        logging.exception('Cannot write data to socket')
                if not read_data or not sent_data:
                    logging.info('Closing client and tunnel connections...')
                    out = channel[input]
                    inputs.remove(input)
                    inputs.remove(out)
                    channel[out].close()
                    channel[input].close()
                    del channel[out]
                    del channel[input]
                    break
    except KeyboardInterrupt:
        logging.info('Interrupted...')
    except:
        logging.exception('Errored...')
    finally:
        logging.info('Closing all sockets...')
        for input in inputs:
            input.close()
        logging.info('Exiting...')


def configure_graceful_exiting():
    def throw_keyboard_interrupt(signum, frame):
        logging.info('Killed...')
        raise KeyboardInterrupt()

    import signal
    signal.signal(signal.SIGTERM, throw_keyboard_interrupt)


if __name__ == '__main__':
    remote_host = os.getenv('CP_CAP_TUNNEL_REMOTE_HOST', os.environ['NODE_IP'])
    remote_port = int(os.getenv('CP_CAP_TUNNEL_REMOTE_PORT', 4000))

    local_host = os.getenv('CP_CAP_TUNNEL_LOCAL_HOST', '0.0.0.0')
    local_port = int(os.getenv('CP_CAP_TUNNEL_LOCAL_PORT', 4000))

    log_level = os.getenv('CP_CAP_TUNNEL_LOG_LEVEL', logging.ERROR)

    logging.basicConfig(level=log_level or logging.ERROR, format=DEFAULT_LOGGING_FORMAT)

    create_foreground_tunnel(local_host, local_port, remote_host, remote_port)
