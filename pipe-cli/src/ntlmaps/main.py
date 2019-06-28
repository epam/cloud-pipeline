#! /usr/bin/python

# This file is part of 'NTLM Authorization Proxy Server'
# Copyright 2001 Dmitry A. Rozmanov <dima@xenon.spb.ru>
# Copyright 2012 Tony Heupel <tony@heupel.net>
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
# Modified 27.06.2019 - ntlm proxy changes for pipeline cli embedding
import os
import sys

from src.ntlmaps.lib import command_line, config_affairs, server
from src.ntlmaps.lib.config import read_config


def override_config_with_command_line_options(conf, options):
    if 'port' in options:
        conf['GENERAL']['LISTEN_PORT'] = options['port']

    if 'proxy-port' in options:
        conf['GENERAL']['PARENT_PROXY_PORT'] = options['proxy-port']

    if 'proxy-host' in options:
        conf['GENERAL']['PARENT_PROXY'] = options['proxy-host']

    if 'username' in options:
        conf['NTLM_AUTH']['USER'] = options['username']
        # if you are setting a username, then you don't want
        # to use basic auth as NTLM username/password, so
        # force it off
        conf['NTLM_AUTH']['NTLM_TO_BASIC'] = 0

    if 'password' in options:
        conf['NTLM_AUTH']['PASSWORD'] = options['password']

    if 'domain' in options:
        conf['NTLM_AUTH']['NT_DOMAIN'] = options['domain']


def get_config_filename(options):
    ntlmaps_dir = os.path.dirname(os.path.abspath(__file__))
    config_file = ntlmaps_dir + '/'
    if 'config_file' in options and options['config_file'] != '':
        config_file = options['config_file']
    else:
        config_file += 'server.cfg'

    return config_file


def build_default_config():
    conf = {}

    general_config = {}
    general_config.update({'LISTEN_PORT': ""})
    general_config.update({'PARENT_PROXY': ""})
    general_config.update({'PARENT_PROXY_PORT': ""})
    general_config.update({'PARENT_PROXY_TIMEOUT': 15})
    general_config.update({'ALLOW_EXTERNAL_CLIENTS': 0})
    general_config.update({'FRIENDLY_IPS': ""})
    general_config.update({'URL_LOG': 0})
    general_config.update({'MAX_CONNECTION_BACKLOG': 5})
    conf.update({'GENERAL': general_config})

    ntlm_auth_config = {}
    ntlm_auth_config.update({'NT_HOSTNAME': ""})
    ntlm_auth_config.update({'NT_DOMAIN': ""})
    ntlm_auth_config.update({'USER': ""})
    ntlm_auth_config.update({'PASSWORD': ""})
    ntlm_auth_config.update({'LM_PART': 1})
    ntlm_auth_config.update({'NT_PART': 0})
    ntlm_auth_config.update({'NTLM_FLAGS': " 06820000"})
    ntlm_auth_config.update({'NTLM_TO_BASIC': 0})
    conf.update({'NTLM_AUTH': ntlm_auth_config})

    debug_config = {}
    debug_config.update({'DEBUG': 0})
    debug_config.update({'BIN_DEBUG': 0})
    debug_config.update({'SCR_DEBUG': 0})
    debug_config.update({'AUTH_DEBUG': 0})
    conf.update({'DEBUG': debug_config})

    return conf


def main(args):
    # --------------------------------------------------------------
    # config affairs
    # look for default config name in lib/config.py

    options = command_line.parse_command_line(args)

    if 'cmd' in options and options['cmd']:
        conf = build_default_config()
    else:
        conf = read_config(get_config_filename(options))

    override_config_with_command_line_options(conf, options)

    conf['GENERAL']['VERSION'] = '0.9.9.0.2'

    print('NTLM authorization Proxy Server v%s' % conf['GENERAL']['VERSION'])
    print('Copyright (C) 2001-2012 by Tony Heupel, Dmitry Rozmanov, and others.')

    config = config_affairs.arrange(conf)

    # --------------------------------------------------------------
    # let's run it
    serv = server.AuthProxyServer(config)
    serv.run()


if __name__ == '__main__':
    args = sys.argv
    args = args[1:]
    main(args)
