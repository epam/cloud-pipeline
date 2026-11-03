# Validate of existing bucket adding

Test verifies re-adding a previously unregistered storage by its path.

**Prerequisites**:
- Perform [EPMCMBIBPC-476](EPMCMBIBPC-476.md)

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Click **+ Create v** button |  |
| 2 | Hover over the **Storages** item in the appeared list and select **Add existing object storage** |  |
| 3 | Specify the path to the storage that was unregistered in the [EPMCMBIBPC-476](EPMCMBIBPC-476.md) case in the appeared pop-up window |  |
| 4 | Input a value in the **Alias** field |  |
| 5 | Click **Create** button | The new storage with the name specified at step 4 appears in the library-tree on the left panel. The content of that storage is displayed on the right panel |
