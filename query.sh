#!/usr/bin/env bash
curl -H "Content-Type: application/json" -d '
{
    "user": "1",
    "num": 10
}' http://localhost:8000/queries.json
sleep 2

curl -H "Content-Type: application/json" -d '
{
    "item": "62",
    "num": 15
}' http://localhost:8000/queries.json
sleep 2

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


