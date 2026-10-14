# Check versions if user update file

Test verifies that re-uploading a file with the same name and a different size adds a new `latest` version alongside the earlier one.

**Prerequisites**:
- Perform [EPMCMBIBPC-816](EPMCMBIBPC-816.md)

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Upload a file with the name equal to the file name from the [EPMCMBIBPC-816](EPMCMBIBPC-816.md) case (the size of the uploaded file must differ) |  |
| 2 | Logout |  |
| 3 | Login as Admin |  |
| 4 | Set the **Show files versions** checkbox |  |
| 5 | Click the `+` button in front of the updated file | <li> a new record with the label `latest` is displayed <li> an upload time and updated size are displayed <li> an upload time and the size of the earlier uploaded file are displayed <li> the **Edit** button is displayed opposite the file name |
