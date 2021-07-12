# Metadata entities autofill
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
| 6 | Click any Sample | Attributes section opens |
| 7 | Add a new key:value pair `BCL:BCL` | |
| 8 | Click on a cell with value `BCL` and drag the fill handle on the several cells in the column | Selected cells are filled with value `BCL` |
| 9 | Click on selected cells content menu | Content menu has items *Copy cells* and *Revert* |
| 10 | Select *Revert* option | All values exclude first value are removed |
| 11 | Change value for key:value pair `BCL:BCL` to `BCL004` | |
| 12 | Repeat step 8 | Selected cells are filled with the following values in order |
| 13 | Click on selected cells content menu | Content menu has items *Fill cells*, *Copy cells* and *Revert* |
| 14 | Select *Copy cells* option | Selected cells are filled with value `BCL004` |
| 15 | Select *Fill cells* option | Selected cells are filled with the following values in order |