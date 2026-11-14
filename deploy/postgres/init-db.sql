-- Runs once, when the postgres volume is created for the first time.
-- Replaced by deploy/postgres/init-db.sh in phase P2 (one DB user per service).
CREATE DATABASE nova_bank_account_db;
CREATE DATABASE nova_bank_transaction_db;
CREATE DATABASE nova_bank_notification_db;
