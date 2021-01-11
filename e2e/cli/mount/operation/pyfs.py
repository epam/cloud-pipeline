from ..utils import execute


def mkdir(folder_path, recursive=False):
    execute('mkdir ' +
            ('-p ' if recursive else ' ') +
            ('"%s"' % folder_path))


def rm(item_path, recursive=False, force=False, under=False):
    execute('rm ' +
            ('-r ' if recursive else ' ') +
            ('-f ' if force else ' ') +
            (('"%s"' % item_path) if not under else ('"%s"/*' % item_path)))


def touch(file_path):
    execute('touch "%s"' % file_path)


def mv(old_path, new_path):
    execute('mv "%s" "%s"' % (old_path, new_path))


def truncate(file_path, size):
    execute('truncate -s "%s" "%s"' % (size, file_path))


def fallocate(local_file, size):
    execute('fallocate -l "%s" "%s"' % (size, local_file))
