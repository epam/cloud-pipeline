from utils import MB, execute, assert_content


def mkdir(folder_path, recursive=False):
    if recursive:
        execute('mkdir -p "%s"' % folder_path)
    else:
        execute('mkdir "%s"' % folder_path)


def rm(item_path, recursive=False):
    if recursive:
        execute('rm -r "%s"' % item_path)
    else:
        execute('rm "%s"' % item_path)


def touch(file_path):
    execute('touch "%s"' % file_path)


def mv(old_path, new_path):
    execute('mv "%s" "%s"' % (old_path, new_path))


def truncate(file_path, size):
    execute('truncate -s "%s" "%s"' % (size, file_path))


def fallocate(local_file, size):
    execute('fallocate -l "%s" "%s"' % (size, local_file))
