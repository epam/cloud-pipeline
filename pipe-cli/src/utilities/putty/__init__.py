# PuTTY is copyright 1997-2020 Simon Tatham.
#
# Portions copyright Robert de Bath, Joris van Rantwijk, Delian
# Delchev, Andreas Schultz, Jeroen Massar, Wez Furlong, Nicolas Barry,
# Justin Bradford, Ben Harris, Malcolm Smith, Ahmad Khalifa, Markus
# Kuhn, Colin Watson, Christopher Staite, Lorenz Diener, Christian
# Brabandt, Jeff Smith, Pavel Kryukov, Maxim Kuznetsov, Svyatoslav
# Kuzmich, Nico Williams, Viktor Dukhovni, Josh Dersch, Lars Brinkhoff,
# and CORE SDI S.A.
#
# Permission is hereby granted, free of charge, to any person
# obtaining a copy of this software and associated documentation files
# (the "Software"), to deal in the Software without restriction,
# including without limitation the rights to use, copy, modify, merge,
# publish, distribute, sublicense, and/or sell copies of the Software,
# and to permit persons to whom the Software is furnished to do so,
# subject to the following conditions:
#
# The above copyright notice and this permission notice shall be
# included in all copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
# EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
# MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
# NONINFRINGEMENT.  IN NO EVENT SHALL THE COPYRIGHT HOLDERS BE LIABLE
# FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF
# CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
# WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.


def get_putty_fingerprint(ssh_public_key):
    from .kh2reg import handle_line

    class _PuttyFingerprintsDict(dict):
        def key(self, key, value):
            self[key] = value

    putty_fingerprints = _PuttyFingerprintsDict()
    known_host_line = '{} {}'.format('cloud-pipeline', ssh_public_key)
    handle_line(known_host_line, putty_fingerprints, [])
    return next(iter(putty_fingerprints.values())) if putty_fingerprints else None
