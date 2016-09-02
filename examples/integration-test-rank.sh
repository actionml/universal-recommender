#!/usr/bin/env bash

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
NC='\033[0m' # No Color

APP_NAME='default-rank'
APP_ACCESS_KEY='123456789'

LINE="=================================================================="
# exit on any error
set -e

echo -e "${YELLOW}${LINE}"
echo -e "Integration test for The Universal Recommender."
echo -e "If some step fails check that your engine.json file has been restored"
echo -e "or look for it in 'user-engine.json'"
echo -e "${LINE}${NC}"

echo -e "Checking for needed files"
if [ ! -f examples/rank-engine.json ]; then
    echo -e "File not found: examples/rank-engine.json"
    exit 1
fi

if [ ! -f data/sample-default-rank-data.txt ]; then
    echo -e "File not found: data/sample-default-rank-data.txt"
    exit 1
fi

if [ -f user-engine.json ]; then
    echo -e "File user-engine.json found, this may be an error so we cannot replace engine.json"
    exit 1
fi

#if [ ! -f data/integration-test-rank-expected.txt ]; then
#    echo -e "File not found: data/integration-test-rank-expected.txt"
#    exit 1
#fi

echo -e "${GREEN}${LINE}"
echo -e "Checking status, should exit if pio is not running."
echo -e "${LINE}${NC}"
pio status
pio app new ${APP_NAME} --access-key ${APP_ACCESS_KEY} || true

echo -e "${GREEN}${LINE}"
echo -e "Checking to see if ${APP_NAME} app exists, should exit if not."
echo -e "${LINE}${NC}"
pio app show default-rank

echo -e "${GREEN}${LINE}"
echo -e "Moving engine.json to user-engine.json"
echo -e "${LINE}${NC}"
cp -n engine.json user-engine.json

echo -e "${GREEN}${LINE}"
echo -e "Moving examples/rank-engine.json to engine.json for integration test."
echo -e "${LINE}${NC}"
cp examples/rank-engine.json engine.json

echo -e "${GREEN}${LINE}"
echo -e "Deleting ${APP_NAME} app data since the test is date dependent"
echo -e "${LINE}${NC}"
pio app data-delete ${APP_NAME} -f

echo -e "${GREEN}${LINE}"
echo -e "Importing data for integration test"
echo -e "${LINE}${NC}"
python examples/import_default_rank.py --access_key ${APP_ACCESS_KEY}

echo -e "${GREEN}${LINE}"
echo -e "Building and delpoying model"
echo -e "${LINE}${NC}"
pio build
pio train -- --executor-memory 1g --driver-memory 1g --master local
#echo -e "Model will remain deployed after this test"
#nohup pio deploy > deploy.out &
#echo -e "Waiting 30 seconds for the server to start"
#sleep 30

#echo -e "${GREEN}${LINE}"
#echo -e "Running test query."
#./examples/multi-query-handmade.sh > test.out

#this is due bug where first query had bad results
#TODO: Investigate and squash

#./examples/multi-query-handmade.sh > test.out

echo -e "${GREEN}${LINE}"
echo -e "Restoring engine.json"
echo -e "${LINE}${NC}"
mv user-engine.json engine.json

#echo -e "${GREEN}${LINE}"
#echo -e "Differences between expected and actual results, none is a passing test."
#echo -e "Note: differences in ordering of results with the same score is allowed."
#echo -e "${LINE}${NC}"
#diff data/integration-test-expected.txt test.out

deploy_pid=`jps -lm | grep "onsole deploy" | cut -f 1 -d ' '`
echo -e "${GREEN}${LINE}"
echo -e "Killing the deployed test PredictionServer"
echo -e "${LINE}${NC}"
kill "$deploy_pid"



