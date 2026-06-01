#!/bin/sh
set -eu

test_db_name="${MPFM_TEST_DB_NAME:-mpfm_test}"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
  CREATE DATABASE "$test_db_name";
EOSQL
