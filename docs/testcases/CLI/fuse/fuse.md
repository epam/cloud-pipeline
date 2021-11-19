# Pipe fuse testcases

All the cases below can be performed at the moment, only for the following objects:

- `AWS` NFS
- `AWS` Storage
- `GCP` Storage

## `touch` cases

**Preparations**:

1. Mount the storage into a folder via `pipe storage mount ...` command
2. Open the folder where the storage was mounted
3. All cases should be performed in that mounted folder

| Case ID | Description | Testcase steps | Expected results |
| --- | --- | --- | --- |
| **TC-PIPE-FUSE-1** | **`touch` non-existing file** | rm -rf file<br />touch file | The file `file` exists and has size 0 |
| **TC-PIPE-FUSE-2** | **`touch` non-existing file in folder** | rm -rf folder<br />mkdir folder<br />touch folder/file | The file `folder/file` exists and has size 0 |
| **TC-PIPE-FUSE-3** | **`touch` non-existing file in subfolder** | rm -rf folder<br />mkdir -p folder/subfolder<br />touch folder/subfolder/file | The file `folder/subfolder/file` exists and has size 0 |
| **TC-PIPE-FUSE-4** | **`touch` empty file** | truncate -s 0 file<br />touch file | The file `file` exists and has size 0 |
| **TC-PIPE-FUSE-5** | **`touch` non-empty file** | truncate -s 1 file<br />touch file | The file `file` exists and has size 1 |

## `mv` cases

**Preparations**:

1. Mount the storage into a folder via `pipe storage mount ...` command
2. Open the folder where the storage was mounted
3. All cases should be performed in that mounted folder

| Case ID | Description | Testcase steps | Expected results |
| --- | --- | --- | --- |
| **TC-PIPE-FUSE-6** | **`mv` file** | touch file-old<br />mv file-old file-new | The only 1 file (`file-old`) was moved in the directory |
| **TC-PIPE-FUSE-7** | **`mv` file from folder to root** | mkdir -p folder<br />touch folder/file-old<br />mv folder/file-old file-new | The only 1 file (`folder/file-old`) was moved in the directory |
| **TC-PIPE-FUSE-8** | **`mv` file from root to folder** | mkdir -p folder<br />touch file-old<br />mv file-old folder/file-new | The only 1 file (`file-old`) was moved in the directory |
| **TC-PIPE-FUSE-9** | **`mv` file from folder to folder** | mkdir -p folder-1<br />mkdir -p folder-2<br />touch folder-1/file-old<br />mv folder-1/file-old folder-2/file-new | The only 1 file (`folder-1/file-old`) was moved in the directory |
| **TC-PIPE-FUSE-10** | **`mv` file from folder to subfolder** | mkdir -p folder/subfolder<br />touch folder/file-old<br />mv folder/file-old folder/subfolder/file-new | The only 1 file (`folder/file-old`) was moved in the directory |
| **TC-PIPE-FUSE-11** | **`mv` file from subfolder to folder** | mkdir -p folder/subfolder<br />touch folder/subfolder/file-old<br />mv folder/subfolder/file-old folder/file-new | The only 1 file (`folder/subfolder/file-old`) was moved in the directory |
| **TC-PIPE-FUSE-12** | **`mv` folder with files from root to folder** | mkdir -p folder-old<br />touch folder-old/file<br />mv folder-old folder/subfolder-new | The only 1 folder (`folder-old`) was moved in the directory |
| **TC-PIPE-FUSE-13** | **`mv` folder with files from folder to root** | mkdir -p folder/subfolder-old<br />touch folder/subfolder-old/file<br />mv folder/subfolder-old folder-new | The only 1 folder (`folder/subfolder-old`) was moved in the directory |
| **TC-PIPE-FUSE-14** | **`mv` subfolder from folder to folder** | mkdir -p folder-1/subfolder-old<br />mkdir -p folder-2<br />mv folder-1/subfolder-old folder-2/subfolder-new | The only 1 folder (`folder-1/subfolder-old`) was moved in the directory |
| **TC-PIPE-FUSE-15** | **`mv` subfolder with files from folder to folder** | mkdir -p folder-1/subfolder-old<br />mkdir -p folder-2<br />touch folder-1/subfolder-old/file<br />mv folder-1/subfolder-old folder-2/subfolder-new | The only 1 folder (`folder-1/subfolder-old`) was moved in the directory |

## `rm` cases

**Preparations**:

1. Mount the storage into a folder via `pipe storage mount ...` command
2. Open the folder where the storage was mounted
3. All cases should be performed in that mounted folder

| Case ID | Description | Testcase steps | Expected results |
| --- | --- | --- | --- |
| **TC-PIPE-FUSE-16** | **`rm` file** | touch file<br />rm file | The only 1 file (`file`) was removed in the directory |
| **TC-PIPE-FUSE-17** | **`rm` file in folder** | rm -rf folder<br />mkdir folder<br />touch folder/file<br />rm folder/file | The only 1 file (`folder/file`) was removed in the directory |
| **TC-PIPE-FUSE-18** | **`rm` file in subfolder** | rm -rf folder<br />mkdir -p folder/subfolder<br />touch folder/subfolder/file<br />rm folder/subfolder/file | The only 1 file (`folder/subfolder/file`) was removed in the directory |
| **TC-PIPE-FUSE-19** | **`rm` file with space in name** | touch "file name"<br />rm "file name" | The only 1 file (`file name`) was removed in the directory |
| **TC-PIPE-FUSE-20** | **`rm` file with uppercase letters in name** | touch FILE<br />rm FILE | The only 1 file (`FILE`) was removed in the directory |
| **TC-PIPE-FUSE-21** | **`rm` folder** | mkdir -p folder<br />rm -r folder | The only 1 folder (`folder`) was removed in the directory |
| **TC-PIPE-FUSE-22** | **`rm` subfolder** | mkdir -p folder/subfolder<br />rm -r folder/subfolder | The only 1 folder (`folder/subfolder`) was removed in the directory |
| **TC-PIPE-FUSE-23** | **`rm` folder with subfolder** | mkdir -p folder/subfolder<br />rm -r folder | The only 1 folder (`folder`) was removed in the directory |
| **TC-PIPE-FUSE-24** | **`rm` folder with file** | mkdir -p folder<br />touch folder/file<br />rm -r folder | The only 1 folder (`folder`) was removed in the directory |
| **TC-PIPE-FUSE-25** | **`rm` subfolder with file** | mkdir -p folder/subfolder<br />touch folder/subfolder/file<br />rm -r folder/subfolder | The only 1 folder (`folder/subfolder`) was removed in the directory |
| **TC-PIPE-FUSE-26** | **`rm` folder with subfolder with file** | mkdir -p folder/subfolder<br />touch folder/subfolder/file<br />rm -r folder | The only 1 folder (`folder`) was removed in the directory |

## `mkdir` cases

**Preparations**:

1. Mount the storage into a folder via `pipe storage mount ...` command
2. Open the folder where the storage was mounted
3. All cases should be performed in that mounted folder

| Case ID | Description | Testcase steps | Expected results |
| --- | --- | --- | --- |
| **TC-PIPE-FUSE-27** | **`mkdir` folder** | rm -rf folder<br />mkdir folder | The only 1 folder (`folder`) was added in the directory |
| **TC-PIPE-FUSE-28** | **`mkdir` subfolder** | rm -rf folder<br />mkdir folder<br />cd folder<br />mkdir subfolder | There are two folders (`folder` and `folder/subfolder`) added in the directory |
| **TC-PIPE-FUSE-29** | **`mkdir` subfolder with parent** | rm -rf folder<br />mkdir -p folder/subfolder | There are two folders (`folder` and `folder/subfolder`) added in the directory |
| **TC-PIPE-FUSE-30** | **`mkdir` folder with space in name** | rm -rf "folder name"<br />mkdir "folder name" | The only 1 folder (`folder name`) was added in the directory |
| **TC-PIPE-FUSE-31** | **`mkdir` folder with uppercase letters in name** | rm -rf FOLDER<br />mkdir FOLDER | The only 1 folder (`FOLDER`) was added in the directory |

## `fallocate` cases

**Prerequisites**:  
    These cases should be performed several times - each time for different file size (0, 1, 1000 KB, 1 MB, 1050 KB, 30 MB, 600 MB). For that in cases, the variable `SIZE` is being used - e.g. `SIZE=$((600 * 1024 * 1024))`.

**Preparations**:

1. Mount the storage into a folder via `pipe storage mount ...` command
2. Open the folder where the storage was mounted
3. All cases should be performed twice:
    - in the mounted folder
    - then in some local folder

| Case ID | Description | Testcase steps | Expected results |
| --- | --- | --- | --- |
| **TC-PIPE-FUSE-32** | **`fallocate` non-existing file to size** | rm -rf file<br />fallocate -l ${SIZE} file | The received files (`file`) in the mounted and local folders are identical |
| **TC-PIPE-FUSE-33** | **`fallocate` empty file to size** | truncate -s 0 file<br />fallocate -l ${SIZE} file | The received files (`file`) in the mounted and local folders are identical |
| **TC-PIPE-FUSE-34** | **`fallocate` file to half its size** | truncate -s ${SIZE} file<br />fallocate -l $((SIZE / 2)) file | The received files (`file`) in the mounted and local folders are identical |
| **TC-PIPE-FUSE-35** | **`fallocate` file to its size** | truncate -s ${SIZE} file<br />fallocate -l ${SIZE} file | The received files (`file`) in the mounted and local folders are identical |
| **TC-PIPE-FUSE-36** | **`fallocate` file to double its size** | truncate -s ${SIZE} file<br />fallocate -l $((SIZE * 2)) file | The received files (`file`) in the mounted and local folders are identical |

## `truncate` cases

**Prerequisites**:  
    These cases should be performed several times - each time for different file size (0, 1, 1000 KB, 1 MB, 1050 KB, 30 MB, 600 MB). For that in cases, the variable `SIZE` is being used - e.g. `SIZE=$((600 * 1024 * 1024))`.

**Preparations**:

1. Mount the storage into a folder via `pipe storage mount ...` command
2. Open the folder where the storage was mounted
3. All cases should be performed twice:
    - in the mounted folder
    - then in some local folder

| Case ID | Description | Testcase steps | Expected results |
| --- | --- | --- | --- |
| **TC-PIPE-FUSE-37** | **`truncate` non-existing file to size** | rm -rf file<br />truncate -s ${SIZE} file | The received files (`file`) in the mounted and local folders are identical |
| **TC-PIPE-FUSE-38** | **`truncate` empty file to size** | truncate -s 0 file<br />truncate -s ${SIZE} file | The received files (`file`) in the mounted and local folders are identical |
| **TC-PIPE-FUSE-39** | **`truncate` file to half its size** | truncate -s ${SIZE} file<br />truncate -s $((SIZE / 2)) file | The received files (`file`) in the mounted and local folders are identical |
| **TC-PIPE-FUSE-40** | **`truncate` file to its size** | truncate -s ${SIZE} file<br />truncate -s ${SIZE} file | The received files (`file`) in the mounted and local folders are identical |
| **TC-PIPE-FUSE-41** | **`truncate` file to double its size** | truncate -s ${SIZE} file<br />truncate -s $((SIZE * 2)) file | The received files (`file`) in the mounted and local folders are identical |

## Read cases

**Prerequisites**:

- These cases should be performed several times - each time for different file size (0, 1, 1000 KB, 1 MB, 1050 KB, 30 MB, 600 MB). For that in cases, the variable `SIZE` is being used - e.g. `SIZE=$((600 * 1024 * 1024))`.
- These cases use the file with random content (`../urandom`). It should be received in the following way: `head -c ${BIGGEST_SIZE} /dev/urandom > ../urandom` where `${BIGGEST_SIZE}` is the biggest value from the possible `$SIZE` values used for the tests.

**Preparations**:

1. Mount the storage into a folder via `pipe storage mount ...` command
2. Open the folder where the storage was mounted
3. All cases should be performed twice:
    - in the mounted folder
    - then in some local folder

| Case ID | Description | Testcase steps | Expected results |
| --- | --- | --- | --- |
| **TC-PIPE-FUSE-42** | **`cp` file from mount folder to local folder** | head -c ${SIZE} ../urandom > file<br />cp file ../file | The received files (`../file`) in the mounted and local folders are identical |
| **TC-PIPE-FUSE-43** | **`head` file** | head -c ${SIZE} ../urandom > file<br />head -c 10 file > ../file | The received files (`../file`) in the mounted and local folders are identical |
| **TC-PIPE-FUSE-44** | **`tail` file** | head -c ${SIZE} ../urandom > file<br />tail -c 10 file > ../file | The received files (`../file`) in the mounted and local folders are identical |
| **TC-PIPE-FUSE-45** | **Read from a position that is bigger than file length** | head -c ${SIZE} ../urandom > file<br />python <(cat &lt;&lt;EOF<br />with open('file') as f:<br />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;f.seek(${SIZE} * 2)<br />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;print(f.read(10))<br />EOF<br />) > ../file | The received files (`../file`) in the mounted and local folders are identical |
| **TC-PIPE-FUSE-46** | **Read a region exceeds file length** | head -c ${SIZE} ../urandom > file<br />python <(cat &lt;&lt;EOF<br />with open('file') as f:<br />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;f.seek(${SIZE} - 5)<br />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;print(f.read(10))<br />EOF<br />) > ../file | The received files (`../file`) in the mounted and local folders are identical |
| **TC-PIPE-FUSE-47** | **Read two non-sequential regions** | head -c ${SIZE} ../urandom > file<br />python <(cat &lt;&lt;EOF<br />with open('file') as f:<br />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;f.seek(5)<br />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;print(f.read(10))<br />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;f.seek(20)<br />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;print(f.read(10))<br />EOF<br />) > ../file | The received files (`../file`) in the mounted and local folders are identical |
| **TC-PIPE-FUSE-48** | **Read head and tail** | head -c ${SIZE} ../urandom > file<br />python <(cat &lt;&lt;EOF<br />with open('file') as f:<br />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;f.seek(0)<br />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;print(f.read(10))<br />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;f.seek(${SIZE} - 10)<br />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;print(f.read(10))<br />EOF<br />) > ../file | The received files (`../file`) in the mounted and local folders are identical |
| **TC-PIPE-FUSE-49** | **Read tail and head** | head -c ${SIZE} ../urandom > file<br />python <(cat &lt;&lt;EOF<br />with open('file') as f:<br />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;f.seek(${SIZE} - 10)<br />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;print(f.read(10))<br />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;f.seek(0)<br />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;print(f.read(10))<br />EOF<br />) > ../file | The received files (`../file`) in the mounted and local folders are identical |

## Write cases

**Prerequisites**:  

- These cases should be performed several times - each time for different file size (0, 1, 1000 KB, 1 MB, 1050 KB, 30 MB, 600 MB). For that in cases, the variable `SIZE` is being used - e.g. `SIZE=$((600 * 1024 * 1024))`.
- These cases use the file with random content (`../urandom`). It should be received in the following way: `head -c ${BIGGEST_SIZE} /dev/urandom > ../urandom` where `${BIGGEST_SIZE}` is the biggest value from the possible `$SIZE` values used for the tests.
- Some of these cases depend on mount parameters. For that in cases, the variable `CHUNKSIZE` is being used - `CHUNKSIZE=$(( 10 * 1024 * 1024 ))`.

**Preparations**:

1. Mount the storage into a folder via `pipe storage mount ...` command
2. Open the folder where the storage was mounted
3. All cases should be performed twice:
    - in the mounted folder
    - then in some local folder

| Case ID | Description | Testcase steps | Expected results |
| --- | --- | --- | --- |
| **TC-PIPE-FUSE-50** | **`cp` file from local folder to mount folder** | head -c ${SIZE} ../urandom > ../file<br />cp ../file > file | The received files (`file`) in the mounted and local folders are identical |
| **TC-PIPE-FUSE-51** | **Append to file end** | head -c ${SIZE} ../urandom > file<br />head -c 10 ../urandom >> file | The received files (`file`) in the mounted and local folders are identical |
| **TC-PIPE-FUSE-52** | **Override file tail** | head -c ${SIZE} ../urandom > file<br />python <(cat &lt;&lt;EOF<br />import random<br />random.seed(42)<br />with open('file', 'r+') as f:<br />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;f.seek(${SIZE} - 10)<br />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;f.write(bytearray(map(random.getrandbits, (8,) * 10)))<br />EOF<br />) | The received files (`file`) in the mounted and local folders are identical |
| **TC-PIPE-FUSE-53** | **Override file head** | head -c ${SIZE} ../urandom > file<br />python <(cat &lt;&lt;EOF<br />import random<br />random.seed(42)<br />with open('file', 'r+') as f:<br />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;f.seek(0)<br />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;f.write(bytearray(map(random.getrandbits, (8,) * 10)))<br />EOF<br />) | The received files (`file`) in the mounted and local folders are identical |
| **TC-PIPE-FUSE-54** | **Write to a position that is bigger than file length** | head -c ${SIZE} ../urandom > file<br />python <(cat &lt;&lt;EOF<br />import random<br />random.seed(42)<br />with open('file', 'r+') as f:<br />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;f.seek(${SIZE} + 5)<br />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;f.write(bytearray(map(random.getrandbits, (8,) * 10)))<br />EOF<br />) | The received files (`file`) in the mounted and local folders are identical |
| **TC-PIPE-FUSE-55** | **Write a region that exceeds file length** | head -c ${SIZE} ../urandom > file<br />python <(cat &lt;&lt;EOF<br />import random<br />random.seed(42)<br />with open('file', 'r+') as f:<br />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;f.seek(${SIZE} - 5)<br />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;f.write(bytearray(map(random.getrandbits, (8,) * 10)))<br />EOF<br />) | The received files (`file`) in the mounted and local folders are identical |
| **TC-PIPE-FUSE-56** | **Write a region in first chunk** | head -c ${SIZE} ../urandom > file<br />python <(cat &lt;&lt;EOF<br />import random<br />random.seed(42)<br />with open('file', 'r+') as f:<br />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;f.seek(10)<br />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;f.write(bytearray(map(random.getrandbits, (8,) * 10)))<br />EOF<br />) | The received files (`file`) in the mounted and local folders are identical |
| **TC-PIPE-FUSE-57** | **Write a region in a single chunk** | head -c ${SIZE} ../urandom > file<br />python <(cat &lt;&lt;EOF<br />import random<br />random.seed(42)<br />with open('file', 'r+') as f:<br />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;f.seek(${CHUNKSIZE} + 10)<br />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;f.write(bytearray(map(random.getrandbits, (8,) * 10)))<br />EOF<br />) | The received files (`file`) in the mounted and local folders are identical |
| **TC-PIPE-FUSE-58** | **Write a region matching a single chunk** | head -c ${SIZE} ../urandom > file<br />python <(cat &lt;&lt;EOF<br />import random<br />random.seed(42)<br />with open('file', 'r+') as f:<br />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;f.seek(0)<br />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;f.write(bytearray(map(random.getrandbits, (8,) * ${CHUNKSIZE})))<br />EOF<br />) | The received files (`file`) in the mounted and local folders are identical |
| **TC-PIPE-FUSE-59** | **Write a region between two chunks** | head -c ${SIZE} ../urandom > file<br />python <(cat &lt;&lt;EOF<br />import random<br />random.seed(42)<br />with open('file', 'r+') as f:<br />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;f.seek(${CHUNKSIZE} - 5)<br />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;f.write(bytearray(map(random.getrandbits, (8,) * 10)))<br />EOF<br />) | The received files (`file`) in the mounted and local folders are identical |
| **TC-PIPE-FUSE-60** | **Write two regions in a single chunk** | head -c ${SIZE} ../urandom > file<br />python <(cat &lt;&lt;EOF<br />import random<br />random.seed(42)<br />with open('file', 'r+') as f:<br />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;f.seek(${CHUNKSIZE} + 10)<br />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;f.write(bytearray(map(random.getrandbits, (8,) * 10)))<br />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;f.seek(${CHUNKSIZE} + 100)<br />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;f.write(bytearray(map(random.getrandbits, (8,) * 10)))<br />EOF<br />) | The received files (`file`) in the mounted and local folders are identical |
| **TC-PIPE-FUSE-61** | **Write two regions in two adjacent chunks** | head -c ${SIZE} ../urandom > file<br />python <(cat &lt;&lt;EOF<br />import random<br />random.seed(42)<br />with open('file', 'r+') as f:<br />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;f.seek(10)<br />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;f.write(bytearray(map(random.getrandbits, (8,) * 10)))<br />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;f.seek(${CHUNKSIZE} + 10)<br />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;f.write(bytearray(map(random.getrandbits, (8,) * 10)))<br />EOF<br />) | The received files (`file`) in the mounted and local folders are identical |
| **TC-PIPE-FUSE-62** | **Write two regions in two non-adjacent chunks** | head -c ${SIZE} ../urandom > file<br />python <(cat &lt;&lt;EOF<br />import random<br />random.seed(42)<br />with open('file', 'r+') as f:<br />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;f.seek(10)<br />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;f.write(bytearray(map(random.getrandbits, (8,) * 10)))<br />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;f.seek(${CHUNKSIZE} * 2 + 10)<br />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;f.write(bytearray(map(random.getrandbits, (8,) * 10)))<br />EOF<br />) | The received files (`file`) in the mounted and local folders are identical |
| **TC-PIPE-FUSE-63** | **Write two regions between three chunks** | head -c ${SIZE} ../urandom > file<br />python <(cat &lt;&lt;EOF<br />import random<br />random.seed(42)<br />with open('file', 'r+') as f:<br />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;f.seek(${CHUNKSIZE} - 5)<br />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;f.write(bytearray(map(random.getrandbits, (8,) * 10)))<br />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;f.seek(${CHUNKSIZE} * 2 - 5)<br />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;f.write(bytearray(map(random.getrandbits, (8,) * 10)))<br />EOF<br />) | The received files (`file`) in the mounted and local folders are identical |
| **TC-PIPE-FUSE-64** | **Write two regions between four chunks** | head -c ${SIZE} ../urandom > file<br />python <(cat &lt;&lt;EOF<br />import random<br />random.seed(42)<br />with open('file', 'r+') as f:<br />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;f.seek(${CHUNKSIZE} - 5)<br />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;f.write(bytearray(map(random.getrandbits, (8,) * 10)))<br />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;f.seek(${CHUNKSIZE} * 3 - 5)<br />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;f.write(bytearray(map(random.getrandbits, (8,) * 10)))<br />EOF<br />) | The received files (`file`) in the mounted and local folders are identical |
| **TC-PIPE-FUSE-65** | **Write two regions with one of them exceeding file length** | head -c ${SIZE} ../urandom > file<br />python <(cat &lt;&lt;EOF<br />import random<br />random.seed(42)<br />with open('file', 'r+') as f:<br />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;f.seek(10)<br />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;f.write(bytearray(map(random.getrandbits, (8,) * 10)))<br />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;f.seek(${SIZE} - 5)<br />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;f.write(bytearray(map(random.getrandbits, (8,) * 10)))<br />EOF<br />) | The received files (`file`) in the mounted and local folders are identical |
| **TC-PIPE-FUSE-66** | **Write two regions with one of them starting from a position that is bigger than file length** | head -c ${SIZE} ../urandom > file<br />python <(cat &lt;&lt;EOF<br />import random<br />random.seed(42)<br />with open('file', 'r+') as f:<br />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;f.seek(10)<br />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;f.write(bytearray(map(random.getrandbits, (8,) * 10)))<br />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;f.seek(${SIZE} + 5)<br />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;f.write(bytearray(map(random.getrandbits, (8,) * 10)))<br />EOF<br />) | The received files (`file`) in the mounted and local folders are identical |
| **TC-PIPE-FUSE-67** | **Write two regions starting from a position that is bigger than file length** | head -c ${SIZE} ../urandom > file<br />python <(cat &lt;&lt;EOF<br />import random<br />random.seed(42)<br />with open('file', 'r+') as f:<br />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;f.seek(${SIZE} + 5)<br />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;f.write(bytearray(map(random.getrandbits, (8,) * 10)))<br />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;f.seek(${SIZE} + 20)<br />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;f.write(bytearray(map(random.getrandbits, (8,) * 10)))<br />EOF<br />) | The received files (`file`) in the mounted and local folders are identical |
| **TC-PIPE-FUSE-68** | **Write two overlapping regions** | head -c ${SIZE} ../urandom > file<br />python <(cat &lt;&lt;EOF<br />import random<br />random.seed(42)<br />with open('file', 'r+') as f:<br />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;f.seek(10)<br />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;f.write(bytearray(map(random.getrandbits, (8,) * 10)))<br />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;f.seek(15)<br />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;f.write(bytearray(map(random.getrandbits, (8,) * 10)))<br />EOF<br />) | The received files (`file`) in the mounted and local folders are identical |
| **TC-PIPE-FUSE-69** | **Write a region to an already written chunk** | head -c ${SIZE} ../urandom > file<br />python <(cat &lt;&lt;EOF<br />import random<br />random.seed(42)<br />with open('file', 'r+') as f:<br />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;f.seek(0)<br />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;f.write(bytearray(map(random.getrandbits, (8,) * ${CHUNKSIZE})))<br />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;f.seek(10)<br />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;f.write(bytearray(map(random.getrandbits, (8,) * ${CHUNKSIZE})))<br />EOF<br />) | The received files (`file`) in the mounted and local folders are identical |
