#!/bin/bash

# Simple PostgreSQL Docker container for Gerrit development
# Usage: ./start-postgres.sh

CONTAINER_NAME="gerrit-postgres"
DB_NAME="gerrit"
DB_USER="gerrit"
DB_PASSWORD="gerrit_password"
DB_PORT="5432"

echo "Starting PostgreSQL container..."

# Stop and remove existing container if it exists
docker stop $CONTAINER_NAME 2>/dev/null || true
docker rm $CONTAINER_NAME 2>/dev/null || true

# Run PostgreSQL container
docker run -d \
  --name $CONTAINER_NAME \
  -p $DB_PORT:5432 \
  -e POSTGRES_DB=$DB_NAME \
  -e POSTGRES_USER=$DB_USER \
  -e POSTGRES_PASSWORD=$DB_PASSWORD \
  -v gerrit_postgres_data:/var/lib/postgresql/data \
  postgres:16-alpine

echo "PostgreSQL container started!"
echo "Connection details:"
echo "  Host: localhost"
echo "  Port: $DB_PORT"
echo "  Database: $DB_NAME"
echo "  User: $DB_USER"
echo "  Password: $DB_PASSWORD"
echo ""
echo "Connection URL: jdbc:postgresql://localhost:$DB_PORT/$DB_NAME"
echo ""
echo "To connect with psql:"
echo "  docker exec -it $CONTAINER_NAME psql -U $DB_USER -d $DB_NAME"
echo ""
echo "To stop the container:"
echo "  docker stop $CONTAINER_NAME"
