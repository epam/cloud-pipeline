# MIT License

# Copyright (c) 2016 Chris R

# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:

# The above copyright notice and this permission notice shall be included in all
# copies or substantial portions of the Software.

# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.

FROM ubuntu:20.04

ENV DEBIAN_FRONTEND noninteractive

ARG WINE_VERSION=winehq-stable
ARG PYTHON_VERSION=3.10.11
ARG PYINSTALLER_VERSION=5.6

# we need wine for this all to work, so we'll use the PPA
RUN set -x \
    && dpkg --add-architecture i386 \
    && apt-get update -qy \
    && apt-get install --no-install-recommends -qfy gettext gpg-agent rename apt-transport-https software-properties-common winbind cabextract wget curl zip unzip xvfb xdotool x11-utils xterm \
    && wget -nv https://dl.winehq.org/wine-builds/winehq.key \
    && apt-key add winehq.key \
    && add-apt-repository 'https://dl.winehq.org/wine-builds/ubuntu/' \
    && apt-get update -qy \
    && apt-get install --install-recommends -qfy $WINE_VERSION \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/* \
    && wget -nv https://raw.githubusercontent.com/Winetricks/winetricks/master/src/winetricks \
    && chmod +x winetricks \
    && mv winetricks /usr/local/bin

# wine settings
ENV WINEARCH win64
ENV WINEDEBUG fixme-all
ENV WINEPREFIX /wine

# PYPI repository location
ENV PYPI_URL=https://pypi.python.org/
# PYPI index location
ENV PYPI_INDEX_URL=https://pypi.python.org/simple

# install python in wine, using the msi packages to install, extracting
# the files directly, since installing isn't running correctly.
RUN set -x \
    && winetricks win10 \
    && for msifile in `echo core dev exe lib path pip tcltk tools`; do \
        wget -nv "https://www.python.org/ftp/python/$PYTHON_VERSION/amd64/${msifile}.msi"; \
        wine msiexec /i "${msifile}.msi" /qb TARGETDIR=C:/Python310; \
        rm ${msifile}.msi; \
    done \
    && cd /wine/drive_c/Python310 \
    && echo 'wine '\''C:\Python310\python.exe'\'' "$@"' > /usr/bin/python \
    && echo 'wine '\''C:\Python310\Scripts\easy_install.exe'\'' "$@"' > /usr/bin/easy_install \
    && echo 'wine '\''C:\Python310\Scripts\pip.exe'\'' "$@"' > /usr/bin/pip \
    && echo 'wine '\''C:\Python310\Scripts\pyinstaller.exe'\'' "$@"' > /usr/bin/pyinstaller \
    && echo 'wine '\''C:\Python310\Scripts\pyupdater.exe'\'' "$@"' > /usr/bin/pyupdater \
    && echo 'assoc .py=PythonScript' | wine cmd \
    && echo 'ftype PythonScript=c:\Python310\python.exe "%1" %*' | wine cmd \
    && while pgrep wineserver >/dev/null; do echo "Waiting for wineserver"; sleep 1; done \
    && chmod +x /usr/bin/python /usr/bin/easy_install /usr/bin/pip /usr/bin/pyinstaller /usr/bin/pyupdater \
    && (pip install -U pip || true) \
    && rm -rf /tmp/.wine-*

ENV W_DRIVE_C=/wine/drive_c
ENV W_WINDIR_UNIX="$W_DRIVE_C/windows"
ENV W_SYSTEM64_DLLS="$W_WINDIR_UNIX/system32"
ENV W_TMP="$W_DRIVE_C/windows/temp/_$0"

# install Microsoft Visual C++ Redistributable for Visual Studio 2017 dll files
RUN set -x \
    && rm -f "$W_TMP"/* \
    && wget -P "$W_TMP" https://download.visualstudio.microsoft.com/download/pr/11100230/15ccb3f02745c7b206ad10373cbca89b/VC_redist.x64.exe \
    && cabextract -q --directory="$W_TMP" "$W_TMP"/VC_redist.x64.exe \
    && cabextract -q --directory="$W_TMP" "$W_TMP/a10" \
    && cabextract -q --directory="$W_TMP" "$W_TMP/a11" \
    && cd "$W_TMP" \
    && rename 's/_/\-/g' *.dll \
    && cp "$W_TMP"/*.dll "$W_SYSTEM64_DLLS"/

# put the src folder inside wine
RUN mkdir /src/ && ln -s /src /wine/drive_c/src
VOLUME /src/
WORKDIR /wine/drive_c/src/
RUN mkdir -p /wine/drive_c/tmp

RUN pip install pyinstaller==$PYINSTALLER_VERSION
