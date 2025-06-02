#!/bin/bash

# Update cell by row and column
curl -X POST "http://localhost:8080/v1/sheet/13/cell" \
  -H "X-User-ID: 123" \
  -H "Content-Type: application/json" \
  -d '{
    "row": 0,
    "column": 0,
    "data": "Updated via row/column API"
  }'

# Update cell with a numeric value
curl -X POST "http://localhost:8080/v1/sheet/13/cell" \
  -H "X-User-ID: 123" \
  -H "Content-Type: application/json" \
  -d '{
    "row": 0,
    "column": 1,
    "data": "42"
  }'

# Update cell with an expression
curl -X POST "http://localhost:8080/v1/sheet/13/cell" \
  -H "X-User-ID: 123" \
  -H "Content-Type: application/json" \
  -d '{
    "row": 0,
    "column": 2,
    "data": "=0:1 * 2"
  }'
