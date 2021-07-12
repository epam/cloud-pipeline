#	Metadata entities display setting

Test verifies
- that the users can pre-configuring the display order and visibility of metadata columns for a large number of columns.

**Prerequisites**:

- Admin user
- Perform [_2023_1_](2023_1-Column_sorting_extension.md) case

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as admin user from the prerequisites | |
| 2 | Open the **Library** page | |
| 3 | Open `Project1` created in [_2023_1_](2023_1-Column_sorting_extension.md) | |
| 4 | Click the **Metadata** item | |
| 5 | Click the *Sample [...]* item in the list | <li> Sample metadata table opens <li> ***Show columns*** button is shown near the Search field |
| 6 | Click ***Show columns*** button | ***Show columns*** control is opened and contains a list of selectable metadata columns |
| 7 | Uncheck checkboxes for ***Created Date*** and ***R2_Fastq*** columns | Unchecked columns aren't shown in the table |
| 8 | Navigate to 2nd page of table | Unchecked columns aren't shown in the table |
| 9 | Click ***Show columns*** button | |
| 10 | Try to uncheck all checkboxes | Last unchecked checkbox is disabled |
| 11 |  Move ***Sample Name*** row to the top of list | ***Sample Name*** is shown as first column in the table |
| 12 | Navigate to 1st page of table | Columns order isn't changed |
| 13 | Navigate to any other entity and return to the Sample metadata table | All columns are shown in default order |
| 14 | Repeat steps 6-7, 11 | |
| 15 | Click **Reset columns** button on the ***Show columns*** control | All columns are shown in default order |