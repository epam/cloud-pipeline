import os

import pytest

from pyfs import rm, mv
from pyio import write_dirs, read_dirs


operation_config = [
    {
        'name': 'mv file',
        'from': 'file-old',
        'to': 'file-new',
        'before': {
            'file-old': ''
        },
        'after': {
            'file-new': ''
        }
    },
    {
        'name': 'mv file from folder to root',
        'from': 'folder/file-old',
        'to': 'file-new',
        'before': {
            'folder': {
                'file-old': ''
            }
        },
        'after': {
            'folder': {},
            'file-new': ''
        }
    },
    {
        'name': 'mv file from root to folder',
        'from': 'file-old',
        'to': 'folder/file-new',
        'before': {
            'file-old': '',
            'folder': {}
        },
        'after': {
            'folder': {
                'file-new': ''
            }
        }
    },
    {
        'name': 'mv file from folder to folder',
        'from': 'folder-old/file-old',
        'to': 'folder-new/file-new',
        'before': {
            'folder-old': {
                'file-old': ''
            },
            'folder-new': {}
        },
        'after': {
            'folder-old': {},
            'folder-new': {
                'file-new': ''
            }
        }
    },
    {
        'name': 'mv file from folder to subfolder',
        'from': 'folder/file-old',
        'to': 'folder/subfolder/file-new',
        'before': {
            'folder': {
                'subfolder': {},
                'file-old': ''
            }
        },
        'after': {
            'folder': {
                'subfolder': {
                    'file-new': ''
                }
            }
        }
    },
    {
        'name': 'mv file from subfolder to folder',
        'from': 'folder/subfolder/file-old',
        'to': 'folder/file-new',
        'before': {
            'folder': {
                'subfolder': {
                    'file-old': ''
                }
            }
        },
        'after': {
            'folder': {
                'subfolder': {},
                'file-new': ''
            }
        }
    }, {
        'name': 'mv folder with files from root to folder',
        'from': 'folder-old',
        'to': 'folder/subfolder-new',
        'before': {
            'folder-old': {
                'file': ''
            },
            'folder': {}
        },
        'after': {
            'folder': {
                'subfolder-new': {
                    'file': ''
                }
            },
        }
    },
    {
        'name': 'mv folder with files from folder to root',
        'from': 'folder/subfolder-old',
        'to': 'folder-new',
        'before': {
            'folder': {
                'subfolder-old': {
                    'file': ''
                }
            }
        },
        'after': {
            'folder': {},
            'folder-new': {
                'file': ''
            }
        }
    }, {
        'name': 'mv subfolder from folder to folder',
        'from': 'folder-1/subfolder-old',
        'to': 'folder-2/subfolder-new',
        'before': {
            'folder-1': {
                'subfolder-old': {}
            },
            'folder-2': {}
        },
        'after': {
            'folder-1': {},
            'folder-2': {
                'subfolder-new': {}
            }
        }
    }, {
        'name': 'mv subfolder with files from folder to folder',
        'from': 'folder-1/subfolder-old',
        'to': 'folder-2/subfolder-new',
        'before': {
            'folder-1': {
                'subfolder-old': {
                    'file': ''
                }
            },
            'folder-2': {}
        },
        'after': {
            'folder-1': {},
            'folder-2': {
                'subfolder-new': {
                    'file': ''
                }
            }
        }
    }
]


@pytest.fixture(scope='function', autouse=True)
def teardown_function(mount_path):
    yield
    rm(mount_path, under=True, recursive=True, force=True)


@pytest.mark.parametrize('config', operation_config, ids=lambda config: config['name'])
def test_mv(mount_path, config):
    write_dirs(mount_path, config['before'])
    mv(os.path.join(mount_path, config['from']), os.path.join(mount_path, config['to']))
    assert read_dirs(mount_path) == config['after']
