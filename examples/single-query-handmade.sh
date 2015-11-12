#!/usr/bin/env bash

echo "Recommendations for user: u1"
echo ""
curl -H "Content-Type: application/json" -d '
{
    "user": "u1"
}' http://localhost:8000/queries.json
echo ""

