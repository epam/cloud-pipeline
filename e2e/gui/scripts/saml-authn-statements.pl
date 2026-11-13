#!/usr/bin/perl
#
# Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

# Reads a Java serialization stream (the SPRING_SECURITY_CONTEXT attribute of a Spring session) on stdin and prints,
# for each AuthnStatement of each SAML assertion in it, a line "<AuthnInstant>\t<SessionNotOnOrAfter>" ("-" for a
# missing attribute). Nothing else of the assertions is printed.
#
# Spring SAML 1.0.x writes an assertion in SAMLObject.writeObject with ObjectOutputStream.writeUTF: in block-data mode,
# so the 2-byte UTF length and the XML are cut into blocks of at most 1024 bytes, each preceded by a header:
# TC_BLOCKDATA (0x77, 1-byte length) for up to 255 bytes, TC_BLOCKDATALONG (0x7A, 4-byte length) above. The XML is
# rebuilt from these blocks with the exact lengths (a header in the middle of an attribute would corrupt a plain
# search of the bytes). An XML that does not rebuild exactly is skipped.

use strict;
use warnings;

binmode STDIN;
local $/;
my $bytes = <STDIN>;
exit 0 unless defined $bytes;
my $size = length $bytes;

# Rebuilds the writeUTF string whose XML starts at offset $x; undef if the bytes before it are not its block header
sub rebuild {
    my ($x) = @_;
    return undef if $x < 4;
    my $need = unpack('n', substr($bytes, $x - 2, 2)) + 2;   # the UTF length, then the data
    my $first = $need < 1024 ? $need : 1024;
    my $left;
    if ($first > 255) {
        return undef if $x < 7 || substr($bytes, $x - 7, 1) ne "\x7a" || unpack('N', substr($bytes, $x - 6, 4)) != $first;
    } else {
        return undef if substr($bytes, $x - 4, 1) ne "\x77" || ord(substr($bytes, $x - 3, 1)) != $first;
    }
    $left = $first;
    my $pos = $x - 2;
    my $data = '';
    while (1) {
        my $take = $need - length $data;
        $take = $left if $left < $take;
        return undef if $pos + $take > $size;
        $data .= substr($bytes, $pos, $take);
        $pos += $take;
        $left -= $take;
        last if length $data >= $need;
        my $header = substr($bytes, $pos, 1);
        my $rest = $need - length $data;
        my $expected = $rest < 1024 ? $rest : 1024;
        if ($expected > 255 && $header eq "\x7a" && unpack('N', substr($bytes, $pos + 1, 4)) == $expected) {
            $pos += 5;
        } elsif ($expected <= 255 && $header eq "\x77" && ord(substr($bytes, $pos + 1, 1)) == $expected) {
            $pos += 2;
        } else {
            return undef;
        }
        $left = $expected;
    }
    return substr($data, 2);
}

while ($bytes =~ /<\?xml|<(?:[A-Za-z_][\w.-]*:)?Assertion[\s>]/g) {
    my $xml = rebuild($-[0]);
    next unless defined $xml;
    while ($xml =~ /<(?:[A-Za-z_][\w.-]*:)?AuthnStatement\b([^>]*)>/g) {
        my $attributes = $1;
        my ($instant) = $attributes =~ /\sAuthnInstant="([^"]*)"/;
        my ($not_after) = $attributes =~ /\sSessionNotOnOrAfter="([^"]*)"/;
        print(($instant // '-') . "\t" . ($not_after // '-') . "\n");
    }
}
