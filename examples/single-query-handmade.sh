#!/usr/bin/env bash


echo ""
echo "============ dateRange filter ============"
echo ""
BEFORE=`date -v +1d +"%Y-%m-%dT%H:%M:%SZ"`
AFTER=`date -v -1d +"%Y-%m-%dT%H:%M:%SZ"`
#echo "before: $BEFORE after: $AFTER"
echo "Recommendations for user: u1"
echo ""
curl -H "Content-Type: application/json" -d "
{
    \"user\": \"u1\",
    \"dateRange\": {
        \"name\": \"date\",
        \"before\": \"$BEFORE\",
        \"after\": \"$AFTER\"
    }
}" http://localhost:8000/queries.json
echo ""
