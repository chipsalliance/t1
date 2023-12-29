#!/usr/bin/env bash

nix shell .#netcat -c bash -c 'nc localhost 11451 < /dev/null'
