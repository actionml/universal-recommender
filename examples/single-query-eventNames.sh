#!/usr/bin/env bash

echo "Recommendations for user: u5 all events"
echo ""
curl -H "Content-Type: application/json" -d '
{
    "user": "u5"
}' http://localhost:8000/queries.json
echo ""

echo "Recommendations for user: u5 purchase event, all from popular"
echo ""
curl -H "Content-Type: application/json" -d '
{
    "user": "u5",
    "eventNames": ["purchase"]
}' http://localhost:8000/queries.json
echo ""

echo "Recommendations for user: u5 from view event alone, should have some non-popular based recs"
echo ""
curl -H "Content-Type: application/json" -d '
{
    "user": "u5",
    "eventNames": ["view"]
}' http://localhost:8000/queries.json
echo ""

