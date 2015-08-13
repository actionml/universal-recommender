#!/usr/bin/env bash

echo ""
echo "Recommendations for non-existant user: u10"
echo ""
curl -H "Content-Type: application/json" -d '
{
    "user": "u10",
    "num": 10
}' http://localhost:8000/queries.json
echo ""


echo ""
echo "Recommendations for non-existant user: u10, phone boost"
echo ""
curl -H "Content-Type: application/json" -d '
{
    "user": "u10",
    "num": 10,
    "fields": [{
        "name": "category",
        "values": ["phones"],
        "bias": 1.005
    }]
}' http://localhost:8000/queries.json
echo ""

echo ""
echo "Recommendations for non-existant user: u10, phone filter"
echo ""
curl -H "Content-Type: application/json" -d '
{
    "user": "u10",
    "num": 10,
    "fields": [{
        "name": "category",
        "values": ["phones"],
        "bias": -1
    }]
}' http://localhost:8000/queries.json
echo ""
