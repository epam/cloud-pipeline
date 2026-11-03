# Check restrictions for read-only pipeline

Test verifies the pipeline UI available to a user with read-only permissions.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-546](EPMCMBIBPC-546.md) case |  |
| 2 | Open the **Library** |  |
| 3 | Click on the pipeline from step 1 |  |
| 4 | Click on the pipeline version | <li>The edit icon is not displayed in the upper-right corner <li>On the **DOCUMENTS** tab, the **Delete**, **Rename**, **Upload**, **RUN** buttons are not displayed |
| 5 | Click the **CODE** tab | On the **CODE** tab, the **Delete**, **Rename**, **Upload**, **+ NEW FILE**, **RUN** buttons are not displayed |
| 6 | Click the **STORAGE RULES** tab | On the **STORAGE RULES** tab, the **Delete**, **Add new rule**, **RUN** buttons are not displayed |
