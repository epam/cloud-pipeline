# Metadata entities autofill
Test verifies
- that the users can fill metadata entity with data that are based on data in other entity automatically.

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
| 8 | Hover over a cell with value `BCL` and drag the fill handle on the several cells in the column | Selected cells are filled with value `BCL` |
| 9 | Click on selected cells content menu (icon in the bottom corner of the selected area) | Content menu has items *Copy cells* and *Revert* |
| 10 | Select *Revert* option | All values exclude first value are removed |
| 11 | Change value for key:value pair `BCL:BCL` to `BCL004` | |
| 12 | Repeat step 8 | Selected cells are filled with the following values in order - i.e. `BCL004`, `BCL005`, `BCL006`, etc. |
| 13 | Click on selected cells content menu | Content menu has items *Fill cells*, *Copy cells* and *Revert* |
| 14 | Select *Copy cells* option | Selected cells are filled with value `BCL004` |
| 15 | Select *Fill cells* option | Selected cells are filled with the following values in order - i.e. `BCL004`, `BCL005`, `BCL006`, etc. |