import argparse
import errno
import logging
import os

from pipefuse.wedavfs import WebDavFS
from fuse import FUSE


def start(mountpoint, webdav, default_mode, mount_options=None):
    if mount_options is None:
        mount_options = {}
    try:
        os.makedirs(mountpoint)
    except OSError as e:
        if e.errno != errno.EEXIST:
            raise
    FUSE(WebDavFS(webdav, int(default_mode, 8)), mountpoint, nothreads=False, foreground=True, **mount_options)


def parse_mount_options(options_string):
    options = {}
    if not options_string:
        return options
    for option in options_string.split(","):
        option_string = option.strip()
        chunks = option_string.split("=")
        key = chunks[0]
        value = True if len(chunks) == 1 else chunks[1]
        options[key] = value
    return options


if __name__ == '__main__':
    logger = logging.getLogger("fuse")
    streamHandler = logging.StreamHandler()
    streamHandler.setLevel(logging.DEBUG)
    formatter = logging.Formatter('%(asctime)s - %(name)s - %(levelname)s - %(message)s')
    streamHandler.setFormatter(formatter)
    logger.addHandler(streamHandler)

    parser = argparse.ArgumentParser()
    parser.add_argument("-p", "--mountpoint", type=str, required=True, help="Mount folder")
    parser.add_argument("-w", "--webdav", type=str, required=True, help="Webdav link")
    parser.add_argument("-m", "--mode", type=str, required=False, default="775",
                        help="Default mode for webdav files")
    parser.add_argument("-o", "--options", type=str, required=False,
                        help="String with mount options supported by FUSE")
    args = parser.parse_args()

    start(args.mountpoint, args.webdav, default_mode=args.mode, mount_options=parse_mount_options(args.options))
