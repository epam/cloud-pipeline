from PyInstaller.utils.hooks import copy_metadata, collect_data_files, collect_all

datas = copy_metadata('certifi')
datas = collect_data_files('certifi')
datas, binaries, hiddenimports = collect_all('certifi')
