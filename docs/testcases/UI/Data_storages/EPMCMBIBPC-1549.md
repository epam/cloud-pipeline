# [MANUAL] Create nameless folder

Test verifies that creating a file with an empty name segment in its path is rejected.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Create a bucket |  |
| 2 | Hover over **+ Create** |  |
| 3 | Click **File** |  |
| 4 | Enter a value like `//test/test.txt` into the **Name** field |  |
| 5 | Click **OK** | An inline validation error is shown (`Name can contain only letters, digits, spaces, '_', '-', '@' and '.'.`) and **OK** is blocked; the file cannot be created |
