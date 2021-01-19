# Storage operations testcases

This folder contains testcases to check different operations with data storage objects (commands `pipe storage ...`).

| Case ID | Description/name |
|---|---|
| [**TC-PIPE-STORAGE-1**](TC-PIPE-STORAGE-1.md) | CP operation with files: absolute paths |
| [**TC-PIPE-STORAGE-2**](TC-PIPE-STORAGE-2.md) | MV operation with files: absolute paths |
| [**TC-PIPE-STORAGE-3**](TC-PIPE-STORAGE-3.md) | CP operation with files: absolute paths without filename specified |
| [**TC-PIPE-STORAGE-4**](TC-PIPE-STORAGE-4.md) | MV operation with files: absolute paths without filename |
| [**TC-PIPE-STORAGE-5**](TC-PIPE-STORAGE-5.md) | CP operation with files: path with home directory (~/) |
| [**TC-PIPE-STORAGE-6**](TC-PIPE-STORAGE-6.md) | MV operation with files: path with home directory (~/) |
| [**TC-PIPE-STORAGE-7**](TC-PIPE-STORAGE-7.md) | CP operation with files: --force option |
| [**TC-PIPE-STORAGE-8**](TC-PIPE-STORAGE-8.md) | MV operation with files: --force option |
| [**TC-PIPE-STORAGE-9**](TC-PIPE-STORAGE-9.md) | CP operation with files: relative paths |
| [**TC-PIPE-STORAGE-10**](TC-PIPE-STORAGE-10.md) | MV operation with files: relative paths |
| [**TC-PIPE-STORAGE-11**](TC-PIPE-STORAGE-11.md) | CP operation with folders: absolute paths |
| [**TC-PIPE-STORAGE-12**](TC-PIPE-STORAGE-12.md) | MV operation with folders: absolute paths |
| [**TC-PIPE-STORAGE-13**](TC-PIPE-STORAGE-13.md) | CP operation with folders: without --recursive |
| [**TC-PIPE-STORAGE-14**](TC-PIPE-STORAGE-14.md) | MV operation with folders: without --recursive |
| [**TC-PIPE-STORAGE-15**](TC-PIPE-STORAGE-15.md) | CP operation with folders: path with home directory (~/) |
| [**TC-PIPE-STORAGE-16**](TC-PIPE-STORAGE-16.md) | MV operation with folders: path with home directory (~/) |
| [**TC-PIPE-STORAGE-17**](TC-PIPE-STORAGE-17.md) | CP operation with folders: relative paths |
| [**TC-PIPE-STORAGE-18**](TC-PIPE-STORAGE-18.md) | MV operation with folders: relative paths |
| [**TC-PIPE-STORAGE-19**](TC-PIPE-STORAGE-19.md) | CP operation with folders: --force option |
| [**TC-PIPE-STORAGE-20**](TC-PIPE-STORAGE-20.md) | MV operation with folders: --force option |
| [**TC-PIPE-STORAGE-21**](TC-PIPE-STORAGE-21.md) | CP operation: wrong schema |
| [**TC-PIPE-STORAGE-22**](TC-PIPE-STORAGE-22.md) | MV operation: wrong schema |
| [**TC-PIPE-STORAGE-23**](TC-PIPE-STORAGE-23.md) | CP operation with role model |
| [**TC-PIPE-STORAGE-24**](TC-PIPE-STORAGE-24.md) | MV operation with role model |
| [**TC-PIPE-STORAGE-25**](TC-PIPE-STORAGE-25.md) | CP operation with folders: --exclude files |
| [**TC-PIPE-STORAGE-26**](TC-PIPE-STORAGE-26.md) | MV operation with folders: --exclude files |
| [**TC-PIPE-STORAGE-27**](TC-PIPE-STORAGE-27.md) | CP operation with folders: --include files |
| [**TC-PIPE-STORAGE-28**](TC-PIPE-STORAGE-28.md) | MV operation with folders: --include files |
| [**TC-PIPE-STORAGE-29**](TC-PIPE-STORAGE-29.md) | CP operation copy file to non existing storage |
| [**TC-PIPE-STORAGE-30**](TC-PIPE-STORAGE-30.md) | MV operation copy file to non existing storage |
| [**TC-PIPE-STORAGE-31**](TC-PIPE-STORAGE-31.md) | CP operation with folders: --include and --exclude options combination |
| [**TC-PIPE-STORAGE-32**](TC-PIPE-STORAGE-32.md) | MV operation with folders: --include and --exclude options combination |
| [**TC-PIPE-STORAGE-33**](TC-PIPE-STORAGE-33.md) | CP operations: non existing file/folder |
| [**TC-PIPE-STORAGE-34**](TC-PIPE-STORAGE-34.md) | MV operations: non existing file/folder |
| [**TC-PIPE-STORAGE-35**](TC-PIPE-STORAGE-35.md) | CP operations: copy between local paths |
| [**TC-PIPE-STORAGE-36**](TC-PIPE-STORAGE-36.md) | MV operations: copy between local paths |
| [**TC-PIPE-STORAGE-37**](TC-PIPE-STORAGE-37.md) | LS operation with role model |
| [**TC-PIPE-STORAGE-38**](TC-PIPE-STORAGE-38.md) | LS operation [List single file] |
| [**TC-PIPE-STORAGE-39**](TC-PIPE-STORAGE-39.md) | LS operation [List folder without trailing delimiter] |
| [**TC-PIPE-STORAGE-40**](TC-PIPE-STORAGE-40.md) | LS operation [List folder with trailing delimiter] |
| [**TC-PIPE-STORAGE-41**](TC-PIPE-STORAGE-41.md) | LS operation for non existing storage |
| [**TC-PIPE-STORAGE-42**](TC-PIPE-STORAGE-42.md) | LS operation for non existing file/folder |
| [**TC-PIPE-STORAGE-43**](TC-PIPE-STORAGE-43.md) | LS operation for wrong scheme |
| [**TC-PIPE-STORAGE-44**](TC-PIPE-STORAGE-44.md) | LS operation from root with role model |
| [**TC-PIPE-STORAGE-45**](TC-PIPE-STORAGE-45.md) | RM operation with role model |
| [**TC-PIPE-STORAGE-46**](TC-PIPE-STORAGE-46.md) | RM operation rm for storage's root |
| [**TC-PIPE-STORAGE-47**](TC-PIPE-STORAGE-47.md) | RM operation with files |
| [**TC-PIPE-STORAGE-48**](TC-PIPE-STORAGE-48.md) | RM operation with folders: --exclude and --include options |
| [**TC-PIPE-STORAGE-49**](TC-PIPE-STORAGE-49.md) | RM operation delete non existing file/folder |
| [**TC-PIPE-STORAGE-50**](TC-PIPE-STORAGE-50.md) | RM operation with wrong scheme |
| [**TC-PIPE-STORAGE-51**](TC-PIPE-STORAGE-51.md) | RM operation with folders |
| [**TC-PIPE-STORAGE-52**](TC-PIPE-STORAGE-52.md) | RM operation delete file from non existing storage |
| [**TC-PIPE-STORAGE-53**](TC-PIPE-STORAGE-53.md) | \[Negative\] Negative tests for mvtodir command |
| [**TC-PIPE-STORAGE-54**](TC-PIPE-STORAGE-54.md) | Create directory by CLI validation |
| [**TC-PIPE-STORAGE-55**](TC-PIPE-STORAGE-55.md) | \[Negative\] Try to create folder that have empty name |
| [**TC-PIPE-STORAGE-56**](TC-PIPE-STORAGE-56.md) | Validation of move storage to folder |
| [**TC-PIPE-STORAGE-57**](TC-PIPE-STORAGE-57.md) | Check "-y" flag for "remove" |
| [**TC-PIPE-STORAGE-58**](TC-PIPE-STORAGE-58.md) | LS operation paging |
| [**TC-PIPE-STORAGE-59**](TC-PIPE-STORAGE-59.md) | CP operation with similar file keys |
| [**TC-PIPE-STORAGE-60**](TC-PIPE-STORAGE-60.md) | CP operation with similar keys (versioning) |
| [**TC-PIPE-STORAGE-61**](TC-PIPE-STORAGE-61.md) | CP operation with tags |
| [**TC-PIPE-STORAGE-62**](TC-PIPE-STORAGE-62.md) | Copy file from storage to local relative path |
| [**TC-PIPE-STORAGE-63**](TC-PIPE-STORAGE-63.md) | Download files with similar keys |
| [**TC-PIPE-STORAGE-64**](TC-PIPE-STORAGE-64.md) | \[Negative\] CP operation with tags |
| [**TC-PIPE-STORAGE-65**](TC-PIPE-STORAGE-65.md) | Full names display ls test |
| [**TC-PIPE-STORAGE-66**](TC-PIPE-STORAGE-66.md) | CP in the storage root |
| [**TC-PIPE-STORAGE-67**](TC-PIPE-STORAGE-67.md) | CP file in storage where folder with same name |
| [**TC-PIPE-STORAGE-68**](TC-PIPE-STORAGE-68.md) | CP folder structure |
| [**TC-PIPE-STORAGE-69**](TC-PIPE-STORAGE-69.md) | CP all from storage |
| [**TC-PIPE-STORAGE-70**](TC-PIPE-STORAGE-70.md) | Upload files and folder that have names with spaces |
| [**TC-PIPE-STORAGE-71**](TC-PIPE-STORAGE-71.md) | RM file that have the same name as folder |
| [**TC-PIPE-STORAGE-72**](TC-PIPE-STORAGE-72.md) | RM folder that have the same name as file |
| [**TC-PIPE-STORAGE-73**](TC-PIPE-STORAGE-73.md) | Upload new file in not empty folder |
| [**TC-PIPE-STORAGE-74**](TC-PIPE-STORAGE-74.md) | MV file from storage folder that contain files with the same |
| [**TC-PIPE-STORAGE-75**](TC-PIPE-STORAGE-75.md) | MV file from local folder that contain files with the same prefix |
| [**TC-PIPE-STORAGE-76**](TC-PIPE-STORAGE-76.md) | MV file from storage folder that contain subfolder with the same name |
| [**TC-PIPE-STORAGE-77**](TC-PIPE-STORAGE-77.md) | MV: upload folder structure |
| [**TC-PIPE-STORAGE-78**](TC-PIPE-STORAGE-78.md) | CP: upload files with similar keys |
| [**TC-PIPE-STORAGE-79**](TC-PIPE-STORAGE-79.md) | MV: download folder structure |
| [**TC-PIPE-STORAGE-80**](TC-PIPE-STORAGE-80.md) | CP: upload folders with the same keys |
| [**TC-PIPE-STORAGE-81**](TC-PIPE-STORAGE-81.md) | CP: download folders with the same keys |
| [**TC-PIPE-STORAGE-82**](TC-PIPE-STORAGE-82.md) | MV: upload folders with the same keys |
| [**TC-PIPE-STORAGE-83**](TC-PIPE-STORAGE-83.md) | MV: download folders with the same keys |
| [**TC-PIPE-STORAGE-84**](TC-PIPE-STORAGE-84.md) | CP: upload folder with slash - should upload content only |
| [**TC-PIPE-STORAGE-85**](TC-PIPE-STORAGE-85.md) | MV: upload folder with slash - should upload content only |
| [**TC-PIPE-STORAGE-86**](TC-PIPE-STORAGE-86.md) | CP: upload folder with skip existing option |
| [**TC-PIPE-STORAGE-87**](TC-PIPE-STORAGE-87.md) | MV: upload folder with skip existing option |
| [**TC-PIPE-STORAGE-88**](TC-PIPE-STORAGE-88.md) | CP: upload folder with skip existing option (negative) |
| [**TC-PIPE-STORAGE-89**](TC-PIPE-STORAGE-89.md) | CP: upload file with skip existing option |
| [**TC-PIPE-STORAGE-90**](TC-PIPE-STORAGE-90.md) | MV: upload folder with skip existing option (negative) |
| [**TC-PIPE-STORAGE-91**](TC-PIPE-STORAGE-91.md) | CP: upload file with skip existing option (negative) |
| [**TC-PIPE-STORAGE-92**](TC-PIPE-STORAGE-92.md) | CP: download file with skip existing option |
| [**TC-PIPE-STORAGE-93**](TC-PIPE-STORAGE-93.md) | MV: upload file with skip existing option |
| [**TC-PIPE-STORAGE-94**](TC-PIPE-STORAGE-94.md) | CP: download file with skip existing option (negative) |
| [**TC-PIPE-STORAGE-95**](TC-PIPE-STORAGE-95.md) | MV: upload file with skip existing option (negative) |
| [**TC-PIPE-STORAGE-96**](TC-PIPE-STORAGE-96.md) | CP: download folder with skip existing option |
| [**TC-PIPE-STORAGE-97**](TC-PIPE-STORAGE-97.md) | CP: download folder with skip existing option (negative) |
| [**TC-PIPE-STORAGE-98**](TC-PIPE-STORAGE-98.md) | MV: download file with skip existing option |
| [**TC-PIPE-STORAGE-99**](TC-PIPE-STORAGE-99.md) | MV: download file with skip existing option (negative) |
| [**TC-PIPE-STORAGE-100**](TC-PIPE-STORAGE-100.md) | MV: download folder with skip existing option |
| [**TC-PIPE-STORAGE-101**](TC-PIPE-STORAGE-101.md) | MV: download folder with skip existing option (negative) |
| [**TC-PIPE-STORAGE-102**](TC-PIPE-STORAGE-102.md) | CP: download folder with slash - should download content only |
| [**TC-PIPE-STORAGE-103**](TC-PIPE-STORAGE-103.md) | CP: copy between storage folder with slash - should copy content |
| [**TC-PIPE-STORAGE-104**](TC-PIPE-STORAGE-104.md) | MV: download folder with slash - should download content only |
| [**TC-PIPE-STORAGE-105**](TC-PIPE-STORAGE-105.md) | MV: copy between storage folder with slash - should copy content only |
| [**TC-PIPE-STORAGE-106**](TC-PIPE-STORAGE-106.md) | CP: copy file between storages with skip existing option |
| [**TC-PIPE-STORAGE-107**](TC-PIPE-STORAGE-107.md) | CP: copy file between storages with skip existing option (negative) |
| [**TC-PIPE-STORAGE-108**](TC-PIPE-STORAGE-108.md) | CP: copy folder between storages with skip existing option |
| [**TC-PIPE-STORAGE-109**](TC-PIPE-STORAGE-109.md) | CP: copy folder between storages with skip existing option (negative) |
| [**TC-PIPE-STORAGE-110**](TC-PIPE-STORAGE-110.md) | MV: copy file between storages with skip existing option |
| [**TC-PIPE-STORAGE-111**](TC-PIPE-STORAGE-111.md) | MV: copy file between storages with skip existing option (negative) |
| [**TC-PIPE-STORAGE-112**](TC-PIPE-STORAGE-112.md) | MV: copy folder between storages with skip existing option |
| [**TC-PIPE-STORAGE-113**](TC-PIPE-STORAGE-113.md) | MV: copy folder between storages with skip existing option (negative) |
| [**TC-PIPE-STORAGE-114**](TC-PIPE-STORAGE-114.md) | Validation of mark for deletion file |
| [**TC-PIPE-STORAGE-115**](TC-PIPE-STORAGE-115.md) | Validation of restore marked for deletion file |
| [**TC-PIPE-STORAGE-116**](TC-PIPE-STORAGE-116.md) | Validation of file versions |
| [**TC-PIPE-STORAGE-117**](TC-PIPE-STORAGE-117.md) | Validation of restore specified version |
| [**TC-PIPE-STORAGE-118**](TC-PIPE-STORAGE-118.md) | Validation of file hard deletion |
| [**TC-PIPE-STORAGE-119**](TC-PIPE-STORAGE-119.md) | Validation of mark for deletion non-empty folder |
| [**TC-PIPE-STORAGE-120**](TC-PIPE-STORAGE-120.md) | Validation of restore marked for deletion non-empty folder |
| [**TC-PIPE-STORAGE-121**](TC-PIPE-STORAGE-121.md) | Validation of mark for deletion file for non-admin user |
| [**TC-PIPE-STORAGE-122**](TC-PIPE-STORAGE-122.md) | Validation of non-empty folder hard deletion |
| [**TC-PIPE-STORAGE-123**](TC-PIPE-STORAGE-123.md) | Validation of mark for deletion non-empty folder for non-admin user |
| [**TC-PIPE-STORAGE-124**](TC-PIPE-STORAGE-124.md) | Validation of hard deletion of marked for delete file |
| [**TC-PIPE-STORAGE-125**](TC-PIPE-STORAGE-125.md) | Validation of hard deletion of marked for deletion non-empty folder |
| [**TC-PIPE-STORAGE-126**](TC-PIPE-STORAGE-126.md) | Validation of hard deletion files with common keys |
| [**TC-PIPE-STORAGE-127**](TC-PIPE-STORAGE-127.md) | \[Negative\] Validation of file versions for non-admin and non-owner user |
| [**TC-PIPE-STORAGE-128**](TC-PIPE-STORAGE-128.md) | \[Negative\] Validation of restore marked for deletion file for non-admin and non-owner user |
| [**TC-PIPE-STORAGE-129**](TC-PIPE-STORAGE-129.md) | \[Negative\] Validation of file hard deletion for non-admin and non-owner user |
| [**TC-PIPE-STORAGE-130**](TC-PIPE-STORAGE-130.md) | \[Negative\] Validation of restore marked for deletion non-empty folder for non-admin and non-owner user |
| [**TC-PIPE-STORAGE-131**](TC-PIPE-STORAGE-131.md) | \[Negative\] Validation of non-empty folder hard deletion for non-admin and non-owner user |
| [**TC-PIPE-STORAGE-132**](TC-PIPE-STORAGE-132.md) | \[Negative\] Try to mark for deletion unexisting file |
| [**TC-PIPE-STORAGE-133**](TC-PIPE-STORAGE-133.md) | \[Negative\] Try to hard delete unexisting file |
| [**TC-PIPE-STORAGE-134**](TC-PIPE-STORAGE-134.md) | \[Negative\] Try to restore unexisting file version |
| [**TC-PIPE-STORAGE-135**](TC-PIPE-STORAGE-135.md) | \[Negative\] Try to restore not removed file |
| [**TC-PIPE-STORAGE-136**](TC-PIPE-STORAGE-136.md) | \[Negative\] Try to restore the latest version of non-removed file |
