#!/usr/bin/python

# This file is Copyright 2004 Darryl A. Dixon <esrever_otua@pythonhacker.is-a-geek.net>
# and is part of 'NTLM Authorization Proxy Server',
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

import os, sys

ntlmaps_dir = os.path.dirname(os.path.abspath(__file__))
ntlmaps_libdir = ntlmaps_dir + '/lib'
sys.path.append(ntlmaps_libdir)

del os, sys
