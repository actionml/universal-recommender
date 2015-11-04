#!/usr/bin/env bash

echo ""
echo "Queries illustrate use of date filters but must be adjusted to the current date to work properly"
echo ""

echo ""
echo "Recommendations for user: u1"
echo ""
curl -H "Content-Type: application/json" -d '
{
    "user": "u1"
}' http://localhost:8000/queries.json
echo ""


echo ""
echo "Recommendations for item: iphone 4"
echo ""
curl -H "Content-Type: application/json" -d '
{
    "item": "Iphone 4"
}' http://localhost:8000/queries.json
echo ""

echo ""
echo "Recommendations for item: ipad-retina"
echo ""
curl -H "Content-Type: application/json" -d '
{
    "item": "Ipad-retina"
}' http://localhost:8000/queries.json
echo ""

echo ""
echo "query with no item or user id, order inserted into index"
echo ""
curl -H "Content-Type: application/json" -d '
{
}' http://localhost:8000/queries.json
echo ""


echo ""
echo "Recommendations for non-existant user: xyz, all from popularity"
echo ""
curl -H "Content-Type: application/json" -d '
{
    "user": "xyz"
}' http://localhost:8000/queries.json
echo ""


echo ""
echo "Recommendations for non-existant user: xyz, all from popularity, tablets filter"
echo ""
curl -H "Content-Type: application/json" -d '
{
    "item": "xyz",
    "fields": [{
        "name": "category",
        "values": ["Tablets"],
        "bias": -1
    }]
}' http://localhost:8000/queries.json
echo ""


echo ""
echo "Recommendations for no user no item, all from popularity"
echo ""
curl -H "Content-Type: application/json" -d '
{
}' http://localhost:8000/queries.json
echo ""


echo ""
echo "Recommendations for no user no item, all from popularity, tablets filter"
echo ""
curl -H "Content-Type: application/json" -d '
{
    "fields": [{
        "name": "category",
        "values": ["Tablets"],
        "bias": -1
    }]
}' http://localhost:8000/queries.json
echo ""


