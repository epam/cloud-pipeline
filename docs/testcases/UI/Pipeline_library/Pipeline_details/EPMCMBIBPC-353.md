# Validation of STORAGE RULES tab

Test verifies the default storage rule and layout of the STORAGE RULES tab for a new pipeline.

**Prerequisites**:
- Perform the [EPMCMBIBPC-285](../Pipeline_editing/EPMCMBIBPC-285.md) case

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Click on **STORAGE RULES** tab | A page appears containing: <li> the **Add new rule** button <li> a table with 6 columns: **Name**, **Mask**, **Created**, **Move to Short-Term Storage**, **Pipeline Results**, a column for **Delete** hyperlinks <li> in that table, one record: the **Name** column is empty, **Mask** shows `*`, **Created** shows the time the mask was created, **Move to Short-Term Storage** shows a checked disabled checkbox, **Pipeline Results** shows an unchecked disabled checkbox, and a **Delete** hyperlink in the 6th column |
