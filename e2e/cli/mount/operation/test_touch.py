import os

import pytest

from pyfs import touch, rm
from pyio import write_dirs, read_dirs

operation_config = [
    {
        'case': 'TC-PIPE-FUSE-1',
        'name': 'test touch non existing file',
        'target': 'file',
        'before': {},
        'after': {
            'file': ''
        }
    },
    {
        'case': 'TC-PIPE-FUSE-2',
        'name': 'test touch non existing file in folder',
        'target': 'folder/file',
        'before': {
            'folder': {}
        },
        'after': {
            'folder': {
                'file': ''
            }
        }
    },
    {
        'case': 'TC-PIPE-FUSE-3',
        'name': 'test touch non existing file in subfolder',
        'target': 'folder/subfolder/file',
        'before': {
            'folder': {
                'subfolder': {}
            }
        },
        'after': {
            'folder': {
                'subfolder': {
                    'file': ''
                }
            }
        }
    },
    {
        'case': 'TC-PIPE-FUSE-4',
        'name': 'test touch empty file',
        'target': 'file',
        'before': {
            'file': ''
        },
        'after': {
            'file': ''
        }
    },
    {
        'case': 'TC-PIPE-FUSE-5',
        'name': 'test touch non empty file',
        'target': 'file',
        'before': {
            'file': 'content'
        },
        'after': {
            'file': 'content'
        }
    }
]


@pytest.fixture(scope='function', autouse=True)
def teardown_function(mount_path):
    yield
    rm(mount_path, under=True, recursive=True, force=True)


@pytest.mark.parametrize('config,test_case', zip(operation_config, [config['case'] for config in operation_config]),
                         ids=[config['name'] for config in operation_config])
def test_touch(mount_path, config, test_case):
    write_dirs(mount_path, config['before'])
    touch(os.path.join(mount_path, config['target']))
    assert read_dirs(mount_path) == config['after']
