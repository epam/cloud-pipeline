# [Negative] Add wrong key-value attributes to file

Test verifies that adding a file attribute with an empty key, or an empty value, is rejected.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-1159](EPMCMBIBPC-1159.md) case |  |
| 2 | Click **+ Add** |  |
| 3 | Enter an empty value into the **Key** field |  |
| 4 | Enter an empty value into the **Value** field |  |
| 5 | Click **Add** | The message "Enter key" appears |
| 6 | Enter an empty value into the **Key** field |  |
| 7 | Enter a non-empty value into the **Value** field |  |
| 8 | Click **Add** | The message "Enter key" appears |
| 9 | Enter a non-empty value into the **Key** field |  |
| 10 | Enter an empty value into the **Value** field |  |
| 11 | Click **Add** | The message "Enter value" appears |
