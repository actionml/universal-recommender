#!/usr/bin/env bash

echo ""
echo "Recommendations for user: u1, should return nexus only since it is the only one not seen and in surrent date is between available and expired."
curl -H "Content-Type: application/json" -d '
{
    "user": "u1",
    "num": 4,
    "currentDate": "2015-08-30T12:24:41-07:00"
}' http://localhost:8000/queries.json
echo ""

echo ""
echo "Recommendations for user: u1, should return nexus only since it is the only one not seen and in surrent date is between available and expired."
curl -H "Content-Type: application/json" -d '
{
    "user": "u1",
    "num": 4,
    "currentDate": "2015-08-30T12:24:41-07:00"
}' http://localhost:8000/queries.json
echo ""
