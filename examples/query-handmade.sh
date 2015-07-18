#!/usr/bin/env bash

echo ""
echo "Recommendations for user: u1"
curl -H "Content-Type: application/json" -d '
{
    "user": "u1"
}' http://localhost:8000/queries.json
echo ""
#sleep 2

echo ""
echo "Recommendations for user: u2"
curl -H "Content-Type: application/json" -d '
{
    "user": "u2"
}' http://localhost:8000/queries.json
echo ""
#sleep 2

echo ""
echo "Recommendations for user: u3"
curl -H "Content-Type: application/json" -d '
{
    "user": "u3"
}' http://localhost:8000/queries.json
echo ""
#sleep 2

echo ""
echo "Recommendations for user: u4"
curl -H "Content-Type: application/json" -d '
{
    "user": "u4"
}' http://localhost:8000/queries.json
echo ""
#sleep 2

echo ""
echo "Recommendations for user: u5"
curl -H "Content-Type: application/json" -d '
{
    "user": "u5"
}' http://localhost:8000/queries.json
echo ""
#sleep 2

echo ""
echo "Recommendations for user: u1 with a tablets boost"
curl -H "Content-Type: application/json" -d '
{
    "user": "u1",
    "num": 4
    "fields": [{
        "name": "category",
        "values": ["tablets"],
        "bias": 2
    }]
}' http://localhost:8000/queries.json
echo ""
#sleep 2

echo ""
echo "Recommendations for user: u1 with a phones boost"
curl -H "Content-Type: application/json" -d '
{
    "user": "u1",
    "num": 4
    "fields": [{
        "name": "category",
        "values": ["phones"],
        "bias": 2
    }]
}' http://localhost:8000/queries.json
echo ""
#sleep 2

echo ""
echo "Recommendations for user: u1 with a tablets filter"
curl -H "Content-Type: application/json" -d '
{
    "user": "u1",
    "num": 4
    "fields": [{
        "name": "category",
        "values": ["tablets"],
        "bias": -2
    }]
}' http://localhost:8000/queries.json
echo ""
#sleep 2

echo ""
echo "Recommendations for user: u1 with a phones filter"
curl -H "Content-Type: application/json" -d '
{
    "user": "u1",
    "num": 4
    "fields": [{
        "name": "category",
        "values": ["phones"],
        "bias": -2
    }]
}' http://localhost:8000/queries.json
echo ""
#sleep 2

echo ""
echo "Recommendations for user: u1, max of one rec requested but due to blacklisting, may see none"
curl -H "Content-Type: application/json" -d '
{
    "user": "u1",
    "num": 1
}' http://localhost:8000/queries.json
echo ""
#sleep 2

echo ""
echo "Recommendations for item: surface, should return none."
curl -H "Content-Type: application/json" -d '
{
    "item": "surface",
    "num": 4
}' http://localhost:8000/queries.json
echo ""
#sleep 2

echo ""
echo "Recommendations for user: u3 who would not get recs without multiple action types"
curl -H "Content-Type: application/json" -d '
{
    "user": "u3",
    "num": 4
}' http://localhost:8000/queries.json
echo ""
#sleep 2

echo ""
echo "Recommendations for user: u1 with a tablets boost, blacklist 'ipad'"
curl -H "Content-Type: application/json" -d '
{
    "user": "u1",
    "num": 4
    "fields": [{
        "name": "category",
        "values": ["tablets"],
        "bias": 2
    }],
    "blacklistItems": ["ipad"]

}' http://localhost:8000/queries.json
echo ""
#sleep 2

echo ""
echo "Recommendations for user: u1 with a phones boost, blacklist 'ipad'"
curl -H "Content-Type: application/json" -d '
{
    "user": "u1",
    "num": 4
    "fields": [{
        "name": "category",
        "values": ["phones"],
        "bias": 2
    }],
    "blacklistItems": ["ipad"]
}' http://localhost:8000/queries.json
echo ""
#sleep 2

echo ""
echo "Recommendations for user: u1 with a tablets filter, blacklist 'ipad'"
curl -H "Content-Type: application/json" -d '
{
    "user": "u1",
    "num": 4
    "fields": [{
        "name": "category",
        "values": ["tablets"],
        "bias": -2
    }],
    "blacklistItems": ["ipad"]

}' http://localhost:8000/queries.json
echo ""
#sleep 2

echo ""
echo "Recommendations for user: u1 with a phones filter, blacklist 'ipad'"
curl -H "Content-Type: application/json" -d '
{
    "user": "u1",
    "num": 4
    "fields": [{
        "name": "category",
        "values": ["phones"],
        "bias": -2
    }],
    "blacklistItems": ["ipad"]


}' http://localhost:8000/queries.json
echo ""
#sleep 2

echo ""
echo "Recommendations for user: u1, blacklist 'ipad'"
curl -H "Content-Type: application/json" -d '
{
    "user": "u1",
    "blacklistItems": ["ipad"]

}' http://localhost:8000/queries.json
echo ""
#sleep 2

echo ""
echo "Recommendations for item: surface, should return none, blacklist 'ipad'"
curl -H "Content-Type: application/json" -d '
{
    "item": "surface",
    "num": 4,
    "blacklistItems": ["ipad"]

}' http://localhost:8000/queries.json
echo ""
#sleep 2

echo ""
echo "Recommendations for user: u3 who would not get recs without multiple action types, blacklist 'ipad'"
curl -H "Content-Type: application/json" -d '
{
    "user": "u3",
    "num": 4,
    "blacklistItems": ["ipad"]

}' http://localhost:8000/queries.json
echo ""
#sleep 2


echo ""
echo "Recommendations for item: galaxy"
curl -H "Content-Type: application/json" -d '
{
    "item": "galaxy"
}' http://localhost:8000/queries.json
echo ""
#sleep 2

echo ""
echo "Recommendations for user: u1"
curl -H "Content-Type: application/json" -d '
{
    "user": "u1"
}' http://localhost:8000/queries.json
echo ""
#sleep 2

echo ""
echo "Recommendations for user: u1, 'galaxy' blacklisted"
curl -H "Content-Type: application/json" -d '
{
    "user": "u1",
    "blacklistItems": ["galaxy"]
}' http://localhost:8000/queries.json
echo ""
#sleep 2





