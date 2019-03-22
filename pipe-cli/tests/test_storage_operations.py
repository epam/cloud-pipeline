from src.utilities.storage.common import StorageOperations


def test_get_item_name():
    assert 'file' == StorageOperations.get_item_name('file', 'file')
    assert 'file' == StorageOperations.get_item_name('file', '')
    assert 'folder/file' == StorageOperations.get_item_name('folder/file', 'a/f')
    assert 'file' == StorageOperations.get_item_name('folder/file', 'folder/file')
    assert 'file' == StorageOperations.get_item_name('folder/file', 'folder/')
    assert 'folder/file' == StorageOperations.get_item_name('folder/file', 'folder')
    assert 'subfolder/' == StorageOperations.get_item_name('folder/subfolder/', 'folder/')
    assert 'subfolder/' == StorageOperations.get_item_name('folder/subfolder/', 'folder/subfolder')


def test_is_relative_path():
    assert StorageOperations.is_relative_path('folder/subfolder/file', 'folder/subfolder')
    assert not StorageOperations.is_relative_path('folder/subfolder1/', 'folder/subfolder')
    assert not StorageOperations.is_relative_path('folder/subfolder1/file', 'folder/subfolder')
