#!/usr/bin/env bash

echo ""
echo "Recommendations for popular using default pop model"
echo ""
curl -H "Content-Type: application/json" -d '
{
}' http://localhost:8000/queries.json
echo ""

