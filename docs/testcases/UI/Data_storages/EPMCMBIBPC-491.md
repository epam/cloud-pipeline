# Create existing bucket (negative)

Test verifies that creating a bucket with a path that already exists is rejected.

**Prerequisites**:
- The [EPMCMBIBPC-448](EPMCMBIBPC-448.md) storage is created

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open library |  |
| 2 | Hover the mouse pointer over the **+ Create v** button |  |
| 3 | Select **Storages** > **Create new object storage** |  |
| 4 | Input a value equal to the path entered at step 4 of [EPMCMBIBPC-448](EPMCMBIBPC-448.md) (`epmcmbi-test`) into the pop-up window |  |
| 5 | Click **Create** button | An error message like `Bucket with name 'epmcmbi-test' already exists` is displayed |
