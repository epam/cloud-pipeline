# Introduce extended attributes support in pipe fuse

Test verifies that
- extended attribute calls are supported
- by default only attributes with user prefix are processed, all other attributes are ignored.

**Prerequisites**:
- Admin user

**Preparations**:
1. Create object storage `storage1`
2. In the `storage1` create `file1`

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as the admin user from the prerequisites | |
| 2 | Open the Tools page | |
| 3 | Select `centos` tool | |
| 4 | Run tool with *Custom settings* | |
| 5 | At the Runs page, click the just-launched run | |
| 6 | Wait until the SSH hyperlink appears | |
| 7 | Click the SSH hyperlink | |
| 8 | In the opened tab, enter and perform the command `yum install attr` | |
| 9 | In the opened tab, enter and perform the command `setfattr -n user.key1 -v value1 cloud-data/storage1/file1` | |
| 10 | In the opened tab, enter and perform the command `setfattr -n user.key2 -v value2 cloud-data/storage1/file1` | |
| 11 | Enter and perform the command `getfattr cloud-data/storage1/file1` | The output contains <br> `file: cloud-data/storage1/file1` <br> `user.key` <br> `user.key2` |
| 12 | Enter and perform the command `getfattr -n user.key1 cloud-data/storage1/file1` | The output contains <br> `file: cloud-data/storage1/file1` <br> `user.key1="value1"` |
| 13 | Enter and perform the command `setfattr -n another.key1 -v value1 cloud-data/storage1/file1` |  The output contains `setfattr: cloud-data/storage1/file1: Operation not supported` |
| 14 | Enter and perform the command `getfattr -n another.key1 cloud-data/storage1/file1` | The output contains <br> `getfattr: cloud-data/storage1/file1: Operation not supported` |

**After:**
 - Stop the run launched at step 4
