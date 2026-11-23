# [MANUAL] Bucket that haven't versioning creation

Test verifies that a bucket created with the **Enable versioning** flag left unchecked has no **Show file versions** entry in the presentation-actions dropdown menu.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Click **Create storage** button |  |
| 2 | Enter the storage address |  |
| 3 | Enter the storage name |  |
| 4 | Leave the **Enable versioning** flag unchecked |  |
| 5 | Click the bucket | The **Show file versions** entry in the presentation-actions (appstore icon) dropdown menu is not displayed |
