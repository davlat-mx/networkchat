#!/bin/sh
set -e
CLASS="${1:-$MAIN_CLASS}"
exec java -cp app.jar "$CLASS"
