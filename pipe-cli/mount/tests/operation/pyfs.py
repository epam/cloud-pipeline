from utils import MB, execute, assert_content


def mkdir(folder_path, recursive=False):
    if recursive:
        execute('mkdir -p "%s"' % folder_path)
    else:
        execute('mkdir "%s"' % folder_path)


def rm(folder_path, recursive=False):
    if recursive:
        execute('rm -r "%s"' % folder_path)
    else:
        execute('rm "%s"' % folder_path)


def touch(file_path):
    execute('touch "%s"' % file_path)
