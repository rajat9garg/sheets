#!/bin/bash

# Update cell directly by ID
curl -X POST "http://localhost:8080/v1/sheet/13/cell/13:0:0" \
  -H "X-User-ID: 13143540" \
  -H "Content-Type: application/json" \
  -d '{
    "value": "Updated via direct cell ID API"
  }'

# Update another cell with a numeric value
curl -X POST "http://localhost:8080/v1/sheet/13/cell/13:0:1" \
  -H "X-User-ID: 13143540" \
  -H "Content-Type: application/json" \
  -d '{
    "value": "100"
  }'

# Update a cell with an expression
curl -X POST "http://localhost:8080/v1/sheet/13/cell/13:0:2" \
  -H "X-User-ID: 13143540" \
  -H "Content-Type: application/json" \
  -d '{
    "value": "=13:0:1 * 2"
  }'
