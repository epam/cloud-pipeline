from ..utils import execute


def mkdir(folder_path, recursive=False):
    execute('mkdir ' +
            ('-p ' if recursive else ' ') +
            ('"%s" ' % folder_path))


def rm(item_path, recursive=False, force=False, under=False):
    execute('rm ' +
            ('-r ' if recursive else ' ') +
            ('-f ' if force else ' ') +
            (('"%s" ' % item_path) if not under else ('"%s"/* ' % item_path)))


def touch(file_path):
    execute('touch "%s"' % file_path)


def mv(old_path, new_path):
    execute('mv "%s" "%s"' % (old_path, new_path))


def truncate(file_path, size):
    execute('truncate -s "%s" "%s"' % (size, file_path))


def fallocate(file_path, size):
    execute('fallocate -l "%s" "%s"' % (size, file_path))


def head(file_path, size=None, write_to=None, append_to=None):
    return execute('head ' +
                   (('-c %s ' % size) if size else ' ') +
                   ('"%s" ' % file_path) +
                   (('> "%s" ' % write_to) if write_to else ' ') +
                   (('>> "%s" ' % append_to) if append_to else ' '))


def tail(file_path, size=None, write_to=None, append_to=None):
    return execute('tail ' +
                   (('-c %s ' % size) if size else ' ') +
                   ('"%s" ' % file_path) +
                   (('> "%s" ' % write_to) if write_to else ' ') +
                   (('>> "%s" ' % append_to) if append_to else ' '))


def cp(source_file_path, destination_file_path):
    execute('cp "%s" "%s"' % (source_file_path, destination_file_path))
