#!/usr/bin/env bash

pio build
pio train -- --executor-memory 1g --driver-memory 1g --master local