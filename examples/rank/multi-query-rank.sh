#!/usr/bin/env bash

echo ""
echo "Queries to illustrate many use cases on a small standard dataset and for an automated integration test."
echo ""
echo "WARNING: for this to produce the correct result you must:"
echo "  1. Import data with"
echo "     $ python3 examples/import_rank.py --access_key <your-app-accesskey>"
echo "  2. Copy rank-engine.json to engine.json."
echo "  3. Run 'pio build', 'pio train', and 'pio deploy'"
echo "  4. The queries must be run the same day as the import was done because date filters are part of the test."

echo ""
echo "============ simple user recs ============"
echo ""
echo "Recommendations for user: user-1"
echo ""
curl -H "Content-Type: application/json" -d '
{
    "user": "user-1"
}' http://localhost:8000/queries.json
echo ""

echo ""
echo "Recommendations for user: user-2"
echo ""
curl -H "Content-Type: application/json" -d '
{
    "user": "user-2"
}' http://localhost:8000/queries.json
echo ""

echo ""
echo "Recommendations for user: user-3"
echo ""
curl -H "Content-Type: application/json" -d '
{
    "user": "user-3"
}' http://localhost:8000/queries.json
echo ""

echo ""
echo "Recommendations for user: user-4"
echo ""
curl -H "Content-Type: application/json" -d '
{
    "user": "user-4"
}' http://localhost:8000/queries.json
echo ""

echo ""
echo "Recommendations for user: user-5"
echo ""
curl -H "Content-Type: application/json" -d '
{
    "user": "user-5"
}' http://localhost:8000/queries.json
echo ""

echo ""
echo "============ simple similar item recs ============"
echo ""
echo "Recommendations for item: product-1"
echo ""
curl -H "Content-Type: application/json" -d '
{
    "item": "product-1"
}' http://localhost:8000/queries.json
echo ""

echo ""
echo "Recommendations for item: product-2"
echo ""
curl -H "Content-Type: application/json" -d '
{
    "item": "product-2"
}' http://localhost:8000/queries.json
echo ""

echo ""
echo "Recommendations for item: product-3"
echo ""
curl -H "Content-Type: application/json" -d '
{
    "item": "product-3"
}' http://localhost:8000/queries.json
echo ""

echo ""
echo "Recommendations for item: product-4"
echo ""
curl -H "Content-Type: application/json" -d '
{
    "item": "product-4"
}' http://localhost:8000/queries.json
echo ""

echo ""
echo "Recommendations for item: product-5"
echo ""
curl -H "Content-Type: application/json" -d '
{
    "item": "product-5"
}' http://localhost:8000/queries.json
echo ""

echo ""
echo "============ popular item recs only ============"
echo ""
echo "query with no item or user id, ordered by popularity"
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
echo "Recommendations for non-existant item: xyz, all from popularity"
echo ""
curl -H "Content-Type: application/json" -d '
{
    "item": "xyz"
}' http://localhost:8000/queries.json
echo ""


echo ""
echo "Recommendations for no user no item, all from popularity, red color filter"
echo ""
curl -H "Content-Type: application/json" -d '
{
    "fields": [{
        "name": "color",
        "values": ["red"],
        "bias": -1
    }]
}' http://localhost:8000/queries.json
echo ""


echo ""
echo "Recommendations for no user no item, all from popularity, green boost"
echo ""
curl -H "Content-Type: application/json" -d '
{
    "fields": [{
        "name": "color",
        "values": ["green"],
        "bias": 1.05
    }]
}' http://localhost:8000/queries.json
echo ""


echo ""
echo "Recommendations for no user no item, all from popularity, red color boost, S size filter"
echo ""
curl -H "Content-Type: application/json" -d '
{
    "fields": [{
        "name": "color",
        "values": ["red"],
        "bias": 1.05
    }, {
        "name": "size",
        "values": ["S"],
        "bias": -1
    }]
}' http://localhost:8000/queries.json
echo ""


echo ""
echo "============ dateRange filter ============"
echo ""
if [[ "$OSTYPE" == "linux-gnu" ]]; then
  BEFORE=`date --date="tomorrow" --iso-8601=seconds`
  AFTER=`date --date="1 day ago" --iso-8601=seconds`
else
  BEFORE=`date -v +1d +"%Y-%m-%dT%H:%M:%SZ"`
  AFTER=`date -v -1d +"%Y-%m-%dT%H:%M:%SZ"`
fi
#echo "before: $BEFORE after: $AFTER"
echo "Recommendations for user: user-1"
echo ""
curl -H "Content-Type: application/json" -d "
{
    \"user\": \"user-1\",
    \"dateRange\": {
        \"name\": \"date\",
        \"before\": \"$BEFORE\",
        \"after\": \"$AFTER\"
    }
}" http://localhost:8000/queries.json
echo ""

echo ""
echo "============ query with item and user *EXPERIMENTAL* ============"
# This is experimental, use at your own risk, not well founded in theory
echo ""
echo "Recommendations for user-1 & product-1"
echo ""
curl -H "Content-Type: application/json" -d '
{
    "user": "user-1",
    "item": "product-1"
}' http://localhost:8000/queries.json
echo ""

