#!/usr/bin/env bash

echo ""
echo "Recommendations for item: surface, should return weak results."
curl -H "Content-Type: application/json" -d '
{
    "item": "surface",
    "num": 4
}' http://localhost:8000/queries.json
echo ""
