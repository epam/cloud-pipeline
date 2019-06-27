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

import __init__
import sys

# from ntlmaps import server, config, config_affairs
#  from ntlmaps import command_line
import lib
from lib import command_line, config, config_affairs, server


def override_config_with_command_line_options(conf, options):
    if options.has_key('port'):
        conf['GENERAL']['LISTEN_PORT'] = options['port']

    if options.has_key('username'):
        conf['NTLM_AUTH']['USER'] = options['username']
        # if you are setting a username, then you don't want
        # to use basic auth as NTLM username/password, so
        # force it off
        conf['NTLM_AUTH']['NTLM_TO_BASIC'] = 0


    if options.has_key('password'):
        conf['NTLM_AUTH']['PASSWORD'] = options['password']


    if options.has_key('domain'):
        conf['NTLM_AUTH']['NT_DOMAIN'] = options['domain']


def get_config_filename(options):
    config_file = __init__.ntlmaps_dir + '/'
    if options.has_key('config_file') and options['config_file'] != '':
        config_file = options['config_file']
    else:
        config_file += 'server.cfg'

    return config_file


def main(args):
    # --------------------------------------------------------------
    # config affairs
    # look for default config name in lib/config.py

    options = command_line.parse_command_line(args)

    conf = lib.config.read_config(get_config_filename(options))

    override_config_with_command_line_options(conf, options)

    conf['GENERAL']['VERSION'] = '0.9.9.0.2'

    print 'NTLM authorization Proxy Server v%s' % conf['GENERAL']['VERSION']
    print 'Copyright (C) 2001-2012 by Tony Heupel, Dmitry Rozmanov, and others.'

    config = config_affairs.arrange(conf)

    # --------------------------------------------------------------
    # let's run it
    serv = server.AuthProxyServer(config)
    serv.run()


if __name__ == '__main__':
    args = sys.argv
    args = args[1:]
    main(args)
