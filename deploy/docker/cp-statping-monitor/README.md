# Statping description

## Prepare Postgres DB configuration
```bash
# Drop the existing user and database
psql -U postgres -c "DROP DATABASE $DB_NAME;"
psql -U postgres -c "DROP OWNED BY $DB_USERNAME;"
psql -U postgres -c "DROP USER $DB_USERNAME;"

# Create a new db user
psql -U postgres -c "CREATE USER $DB_USERNAME CREATEDB;"
psql -U postgres -c "ALTER USER $DB_USERNAME WITH SUPERUSER;"

# Set a password for the newly created db user
psql -U postgres -c "ALTER USER $DB_USERNAME WITH PASSWORD '$DB_PASSWORD';"

# Create a new database
psql -U postgres -c "CREATE DATABASE $DB_NAME OWNER $DB_USERNAME;"
```
