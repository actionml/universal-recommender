#!/usr/bin/env bash
echo ""
echo "Simple personalized query"
echo ""
curl -H "Content-Type: application/json" -d '
{
    "user": "1",
    "num": 10
}' http://localhost:8000/queries.json
echo ""

#sleep 2

echo ""
echo "Simple similar item query"
echo ""
curl -H "Content-Type: application/json" -d '
{
    "item": "62",
    "num": 15
}' http://localhost:8000/queries.json
echo ""

#sleep 2

echo ""
echo "Simple personalized query with category boost"
echo ""
curl -H "Content-Type: application/json" -d '
{
    "user": "1",
    "num": 20,
    "fields": [{
        "name": "category",
        "values": ["cat5"],
        "bias": 1.005
    }]
}' http://localhost:8000/queries.json
echo ""
echo ""
