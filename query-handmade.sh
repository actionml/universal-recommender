#!/usr/bin/env bash

echo "recs 'u1'"
curl -H "Content-Type: application/json" -d '
{
    "user": "u1"
}' http://localhost:8000/queries.json
echo ""
