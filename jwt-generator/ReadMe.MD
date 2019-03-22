JWT Generator is a command line tool to create JWT token for Pipeline API. Private key file is required to generate a token.

Example command to generate token for 100 days:

```bash
java -jar jwt-generator.jar --private D:\\Ubuntu\\private_key.pem --expires 8640000 --claim user_id=1 --claim user_name=user@epam.com --claim org_unit_id=EPAM --claim role=ROLE_ADMIN --claim group=ADMIN

```
