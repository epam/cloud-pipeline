# Validation of STORAGE RULES tab

Test verifies the default storage rule and layout of the STORAGE RULES tab for a new pipeline.

**Prerequisites**:
- Perform the [EPMCMBIBPC-343](EPMCMBIBPC-343.md) case

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Click on **STORAGE RULES** tab | A page appears containing: <li> the **Add new rule** button <li> a table with 4 columns: **Mask**, **Created**, **Move to Short-Term Storage**, a column for **Delete** hyperlinks <li> in that table, one record: **Mask** shows `*`, **Created** shows the time the mask was created, **Move to Short-Term Storage** shows a checked disabled checkbox, and a **Delete** hyperlink in the 4th column |
