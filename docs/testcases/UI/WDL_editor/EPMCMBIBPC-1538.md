# Closing pop-up after WDL pipeline commit

Test verifies that the commit pop-up closes after committing a WDL graph edit.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open **Library** page |  |
| 2 | Create a pipeline from the WDL template |  |
| 3 | Open the created pipeline, click on the pipeline version |  |
| 4 | Click **GRAPH** tab |  |
| 5 | Edit the `workflow` object or any task |  |
| 6 | Click **Save** button |  |
| 7 | Enter a commit message in the appeared pop-up |  |
| 8 | Click **Commit** button | The commit pop-up is closed |
