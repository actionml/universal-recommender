#!/usr/bin/env bash

echo ""
echo "Recommendations for user: u1, one rec requested"
curl -H "Content-Type: application/json" -d '
{
    "user": "u1",
    "num": 1
}' http://localhost:8000/queries.json
echo ""
