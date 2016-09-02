#!/usr/bin/env bash

pio app data-delete default-rank -f
python examples/import_default_rank.py --access_key '123456789' --file './data/sample-rank-data.txt'
#python examples/import_handmade.py --access_key '123456789' --file './data/sample-handmade-data.txt'
