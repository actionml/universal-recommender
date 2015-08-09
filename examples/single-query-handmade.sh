#!/usr/bin/env bash

echo ""
echo "Recommendations for user: u1 with no date range, should be: galaxy, nexus, surface"
echo ""
curl -H "Content-Type: application/json" -d '
{
    "user": "u1",
    "num": 10
}' http://localhost:8000/queries.json
echo ""

echo ""
echo "Recommendations for user: u1, should return nexus only. Note that items with a missign range in their properties will never be returned by this query"
curl -H "Content-Type: application/json" -d '
{
    "user": "u1",
    "num": 10,
    "currentDate": "2015-09-02T12:24:41-07:00"
}' http://localhost:8000/queries.json
echo ""

echo ""
echo "Recommendations for user: u1, should return nexus only. Note that items with a missign range in their properties will never be returned by this query"
curl -H "Content-Type: application/json" -d '
{
    "user": "u1",
    "num": 10,
    "currentDate": "2015-08-30T12:24:41-07:00"
}' http://localhost:8000/queries.json
echo ""

