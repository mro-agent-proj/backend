#!/bin/sh
# Postgres 볼륨을 처음 만들 때 한 번만 실행됩니다. MCP 서버용 조회 전용 계정을 비밀번호와 함께 만듭니다.
# 권한(GRANT)은 Spring이 시작할 때 Flyway V4가 부여합니다.
set -eu
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
    -v rag_password="$MRO_RAG_READER_PASSWORD" \
    -v business_password="$MRO_BUSINESS_READER_PASSWORD" <<'SQL'
CREATE ROLE mro_reader LOGIN PASSWORD :'rag_password' NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;
CREATE ROLE mro_business_reader LOGIN PASSWORD :'business_password' NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;
SQL
