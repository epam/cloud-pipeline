# Terminal of the Miew viewer for a PDB file

Test verifies that the terminal of the **Miew** viewer, opened for a `.pdb` file of a storage, fills the terminal panel and runs a Miew command.

**Prerequisites**:
- Non-admin user with read access to a test storage
- A `.pdb` file in the test storage, e.g. `1CRN.pdb` from `https://files.rcsb.org/download/1CRN.pdb`

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as the user from the prerequisites | |
| 2 | Open the **Library** page, open the test storage | |
| 3 | Find the `.pdb` file from the prerequisites | The row of the file has the **Miew** logo |
| 4 | Click the **Miew** logo | A new tab opens with the **Miew** viewer, which shows the molecule of the file |
| 5 | Click the code icon in the upper-left corner of the viewer | <li> The terminal panel opens on the left side, from the toolbar down to the bottom of the viewer <li> The terminal fills the whole panel <li> The terminal shows `Miew - 3D Molecular Viewer`, `Copyright © 2015-2017 EPAM Systems, Inc.` and the `miew>` prompt |
| 6 | Type `help` and press **Enter** | The terminal shows the help of Miew: it starts with `help (<cmd name>\| <path to property>)` and has `List of available commands:` |
| 7 | Click the code icon again | The terminal panel is hidden |
