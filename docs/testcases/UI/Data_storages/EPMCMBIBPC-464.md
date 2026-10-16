# Validation of "Clear selection" button

Test verifies that **Clear selection** unchecks all selected items.

**Prerequisites**:
- Perform [EPMCMBIBPC-467](EPMCMBIBPC-467.md); once items are selected, a separate **Selection actions** button (showing the number of selected items) appears next to **Select page**, which becomes disabled

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Click the **Selection actions** button, then click **Clear selection** | <li> the checkboxes in front of all files and folders become unchecked <li> the **Selection actions** button doesn't display <li> the **Select page** button becomes enabled again |
