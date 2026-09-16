-- Initialize databases for Payment and Ledger microservices
CREATE DATABASE payment_db;
CREATE DATABASE ledger_db;

\connect payment_db;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

\connect ledger_db;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
