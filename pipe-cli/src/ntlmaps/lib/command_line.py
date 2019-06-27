# This file is part of 'NTLM Authorization Proxy Server'
# This file Copyright 2012 Tony C. Heupel
# NTLM Authorization Proxy Server is
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
import getopt

def parse_command_line(cmdline):
    """ Parse command line into a tuple (except for the configuration file)
        NOTE: Must only contain the command-line options
    """
    opts, values = getopt.getopt(cmdline,
                                 '',
                                 ['config=', 'domain=', 'username=', 'password=',
                                 'port='])

    options = {}
    for opt in opts:
        option, value = opt
        if option == '--domain':
            options['domain'] = value
        elif option == '--username':
            options['username'] = value
        elif option == '--password':
            options['password'] = value
        elif option == '--port':
            options['port'] = int(value)
        elif option == '--config':
            options['config_file'] = value

    return options
