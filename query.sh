
echo ""
echo "Recommendations for user: u5, 5 recs"
echo ""
curl -H "Content-Type: application/json" -d '
{
    "user": "u5"
    "from": 0,
    "num": 5
}' http://localhost:8000/queries.json
echo ""

echo ""
echo "Recommendations for user: u5, paginated from 0 num 2"
echo ""
curl -H "Content-Type: application/json" -d '
{
    "user": "U 2"
    "from": 0,
    "num": 2
}' http://localhost:8000/queries.json
echo ""

echo ""
echo "Recommendations for user: u5, paginated from 2 num 2"
echo ""
curl -H "Content-Type: application/json" -d '
{
    "user": "U 2"
    "from": 2,
    "num": 2
}' http://localhost:8000/queries.json
echo ""

