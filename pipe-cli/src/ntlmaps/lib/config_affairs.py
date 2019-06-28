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

from src.ntlmaps.lib import logger

try:
    import _thread as thread
except ImportError:
    import thread
import sys

#-------------------------------------------------------------------------
def arrange(conf):
    ""

    #-----------------------------------------------
    # GENERAL
    conf['GENERAL']['PARENT_PROXY']

    # if we do not use proxy then we do not need its port
    if conf['GENERAL']['PARENT_PROXY']:
        conf['GENERAL']['AVAILABLE_PROXY_LIST'] = str(conf['GENERAL']['PARENT_PROXY']).split()
        conf['GENERAL']['PARENT_PROXY'] = conf['GENERAL']['AVAILABLE_PROXY_LIST'].pop()
        conf['GENERAL']['PARENT_PROXY_PORT'] = makeInt(conf['GENERAL']['PARENT_PROXY_PORT'], 'PARENT_PROXY_PORT')
        conf['GENERAL']['PARENT_PROXY_TIMEOUT'] = makeInt(conf['GENERAL']['PARENT_PROXY_TIMEOUT'], 'PARENT_PROXY_TIMEOUT')
    try:
        conf['GENERAL']['MAX_CONNECTION_BACKLOG'] = int(conf['GENERAL']['MAX_CONNECTION_BACKLOG'])
    except ValueError:
        if conf['GENERAL']['MAX_CONNECTION_BACKLOG'] == 'SOMAXCONN':
            conf['GENERAL']['MAX_CONNECTION_BACKLOG'] = socket.SOMAXCONN
        else:
            print("ERROR: There is a problem with 'MAX_CONNECTION_BACKLOG' in the config (neither a number nor 'SOMAXCONN'?)")
            sys.exit(1)

    conf['GENERAL']['LISTEN_PORT'] = makeInt(conf['GENERAL']['LISTEN_PORT'], 'LISTEN_PORT')

    conf['GENERAL']['ALLOW_EXTERNAL_CLIENTS'] = makeInt(conf['GENERAL']['ALLOW_EXTERNAL_CLIENTS'], 'ALLOW_EXTERNAL_CLIENTS')
    hostname = socket.gethostname()
    conf['GENERAL']['HOST'] = hostname
    try:
        externalIP = socket.gethostbyname_ex(hostname)[2]
    except socket.error: # socket.gaierror in Python 2.x
        print("ERROR: Unable to get the IP address of this machine.  This is not a fatal problem, but may cause problems for you using this proxy in some scenarios.")
        externalIP = []
    conf['GENERAL']['HOST_IP_LIST'] = externalIP + ['127.0.0.1']

    conf['GENERAL']['FRIENDLY_IPS'] = conf['GENERAL']['HOST_IP_LIST'] + str(conf['GENERAL']['FRIENDLY_IPS']).split()

    conf['GENERAL']['URL_LOG'] = makeInt(conf['GENERAL']['URL_LOG'], 'URL_LOG')
    url_logger = logger.Logger('url.log', conf['GENERAL']['URL_LOG'])
    url_logger_lock = thread.allocate_lock()
    conf['GENERAL']['URL_LOGGER'] = url_logger
    conf['GENERAL']['URL_LOG_LOCK'] = url_logger_lock


    #-----------------------------------------------
    # NTLM_AUTH
    if 'NTLM_FLAGS' not in conf['NTLM_AUTH']:
        conf['NTLM_AUTH']['NTLM_FLAGS'] = ''
    #conf['NTLM']['FULL_NTLM'] = makeInt(conf['NTLM']['FULL_NTLM'], 'FULL_NTLM')
    conf['NTLM_AUTH']['LM_PART'] = makeInt(conf['NTLM_AUTH']['LM_PART'], 'LM_PART')
    conf['NTLM_AUTH']['NT_PART'] = makeInt(conf['NTLM_AUTH']['NT_PART'], 'NT_PART')
    conf['NTLM_AUTH']['NTLM_TO_BASIC'] = makeInt(conf['NTLM_AUTH']['NTLM_TO_BASIC'], 'NTLM_TO_BASIC')
    if not conf['NTLM_AUTH']['NT_DOMAIN']:
        print("ERROR: NT DOMAIN must be set.")
        sys.exit(1)
    if 'PASSWORD' not in conf['NTLM_AUTH']:
        conf['NTLM_AUTH']['PASSWORD'] = ''


    #-----------------------------------------------
    # DEBUG
    conf['DEBUG']['DEBUG'] = makeInt(conf['DEBUG']['DEBUG'], 'DEBUG')
    conf['DEBUG']['AUTH_DEBUG'] = makeInt(conf['DEBUG']['AUTH_DEBUG'], 'AUTH_DEBUG')
    conf['DEBUG']['BIN_DEBUG'] = makeInt(conf['DEBUG']['BIN_DEBUG'], 'BIN_DEBUG')

    # screen activity
    if 'SCR_DEBUG' in conf['DEBUG']:
           conf['DEBUG']['SCR_DEBUG'] = int(conf['DEBUG']['SCR_DEBUG'])
    else:
        conf['DEBUG']['SCR_DEBUG'] = 0

    return conf

def makeInt(string, errorDesc='an item'):
    try:
        ret = int(string)
    except ValueError:
        print("ERROR: There is a problem with "+errorDesc+" in the config (is it not a number?)")
        sys.exit(1)
    return ret
