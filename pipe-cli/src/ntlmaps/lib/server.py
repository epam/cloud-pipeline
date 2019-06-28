# This file is part of 'NTLM Authorization Proxy Server'
# Copyright 2001 Dmitry A. Rozmanov <dima@xenon.spb.ru>
#
# NTLM APS is free software; you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation; either version 2 of the License, or
# (at your option) any later version.
#
# NTLM APS is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with the sofware; see the file COPYING. If not, write to the
# Free Software Foundation, Inc.,
# 59 Temple Place, Suite 330, Boston, MA 02111-1307, USA.
#

import socket

from src.ntlmaps.lib import proxy_client, www_client, ntlm_procs, monitor_upstream

try:
    import _thread as thread
except ImportError:
    import thread
import sys
import signal
import getpass


class AuthProxyServer(object):

    def __init__(self, config):
        self.config = config
        self.MyHost = ''
        self.ListenPort = self.config['GENERAL']['LISTEN_PORT']
        self.sigLock = thread.allocate_lock() # For locking in the sigHandler
        self.monLock = thread.allocate_lock() # For keeping the monitor thread sane
        self.watchUpstream = 0
        if not self.config['NTLM_AUTH']['NTLM_TO_BASIC']:
            if not self.config['NTLM_AUTH']['PASSWORD']:
                tries = 3
                print('------------------------')
                while tries and (not self.config['NTLM_AUTH']['PASSWORD']):
                    tries = tries - 1
                    self.config['NTLM_AUTH']['PASSWORD'] = getpass.getpass('Your NT password to be used:')
            if not self.config['NTLM_AUTH']['PASSWORD']:
                print('Sorry. PASSWORD is required, bye.')
                sys.exit(1)
        else:
            # TODO: migrate this properly so placeholders aren't required
            self.config['NTLM_AUTH']['USER'] = 'placeholder_username'
            self.config['NTLM_AUTH']['PASSWORD'] = 'placeholder_password'
        # hashed passwords calculation
        self.config['NTLM_AUTH']['LM_HASHED_PW'] = ntlm_procs.create_LM_hashed_password(self.config['NTLM_AUTH']['PASSWORD'])
        self.config['NTLM_AUTH']['NT_HASHED_PW'] = ntlm_procs.create_NT_hashed_password(self.config['NTLM_AUTH']['PASSWORD'])

    #--------------------------------------------------------------
    def run(self):
        signal.signal(signal.SIGINT, self.sigHandler)
        if self.config['GENERAL']['PARENT_PROXY'] and self.config['GENERAL']['AVAILABLE_PROXY_LIST']:
            self.watchUpstream = 1
            self.monitor = monitor_upstream.monitorThread(self.config, signal.SIGINT)
            thread.start_new_thread(self.monitor.run, ())
        try:
            s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            s.bind((self.MyHost, self.ListenPort))
        except socket.error:
            print("ERROR: Could not create socket. Possibly port %s is still being used by another process." % self.config['GENERAL']['LISTEN_PORT'])
            sys.exit(1)
        print('Now listening at %s on port %s' % (self.config['GENERAL']['HOST'], self.config['GENERAL']['LISTEN_PORT']))

        while(1):
            s.listen(self.config['GENERAL']['MAX_CONNECTION_BACKLOG'])
            try:
                conn, addr = s.accept()
                if self.config['GENERAL']['ALLOW_EXTERNAL_CLIENTS']:
                    self.client_run(conn, addr)
                else:
                    if addr[0] in self.config['GENERAL']['FRIENDLY_IPS']:
                        self.client_run(conn, addr)
                    else:
                        conn.close()
            except socket.error:
                pass
        s.close()

    #--------------------------------------------------------------
    def client_run(self, conn, addr):
        if self.config['GENERAL']['PARENT_PROXY']:
            # working with MS Proxy
            if self.watchUpstream:
                # Locking here is really more of a 'nice to have';
                # if performance suffers on heavy load we can trade
                # drops here for drops on bad proxy later.
                self.monLock.acquire()
                c = proxy_client.proxy_HTTP_Client(conn, addr, self.config)
                self.monitor.threadsToKill.append(c)
                self.monLock.release()
            else:
                c = proxy_client.proxy_HTTP_Client(conn, addr, self.config)
        else:
            # working with MS IIS and any other
            c = www_client.www_HTTP_Client(conn, addr, self.config)
        thread.start_new_thread(c.run, ())

    #--------------------------------------------------------------
    def sigHandler(self, signum=None, frame=None):
        if signum == signal.SIGINT:
            if self.watchUpstream:
                if self.sigLock.acquire(0):
                    old_monitor = self.monitor
                    self.config['GENERAL']['AVAILABLE_PROXY_LIST'].insert(0, self.config['GENERAL']['PARENT_PROXY'])
                    self.monLock.acquire() # Keep locked section as small as possible
                    self.config['GENERAL']['PARENT_PROXY'] = self.config['GENERAL']['AVAILABLE_PROXY_LIST'].pop()
                    self.monitor = monitor_upstream.monitorThread(self.config, signal.SIGINT)
                    self.monLock.release()
                    print("Moving to proxy server: "+self.config['GENERAL']['PARENT_PROXY'])
                    old_monitor.alive = 0
                    thread.start_new_thread(self.monitor.run, ())
                    map(lambda x: x.exit(), old_monitor.threadsToKill)
                    old_monitor.die() # Protected from recursion by lock
                    self.sigLock.release()
            else:
                # SIGINT is only special if we are in upstream mode:
                print('Got SIGINT, exiting now...')
                sys.exit(1)
        else:
            print('Got SIGNAL '+str(signum)+', exiting now...')
            sys.exit(1)
        return
