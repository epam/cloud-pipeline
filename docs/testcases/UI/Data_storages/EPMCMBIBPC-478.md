# Validate of bucket deleting

Test verifies that deleting a bucket removes it from the library-tree and that accessing it afterward reports it doesn't exist.

**Prerequisites**:
- Perform [EPMCMBIBPC-470](EPMCMBIBPC-470.md)

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Click **Delete** button |  |
| 2 | Click **Delete** button in the appeared pop-up window | The storage doesn't display in the library-tree on the left panel |
| 3 | Perform [EPMCMBIBPC-477](EPMCMBIBPC-477.md) | An error message appears warning that the storage doesn't exist |
