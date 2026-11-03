# [MANUAL] Create file pop-up focus validation

Test verifies that the create-file pop-up opens with focus on the name field and that pressing Enter creates the file.

**Prerequisites**:
- At least one pipeline created

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open the pipeline |  |
| 2 | Switch to the **Code** tab |  |
| 3 | Press **+ NEW FILE** button located above the file and folder list in the right corner | Pop-up appears with focus on the **Name** field (cursor is already there, field edges are highlighted blue) |
| 4 | Start entering a new file name |  |
| 5 | Press **Enter** key when finished | After the **Enter** key is pressed, a new file appears in the list with the corresponding name |
