#!/bin/bash
# MegaApp Build Monitor - Runner script
# Usage: ./monitor.sh [start|stop|status|logs|fix-history]

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$(dirname "$SCRIPT_DIR")")"
MONITOR_PY="$SCRIPT_DIR/build_monitor.py"
PID_FILE="$PROJECT_DIR/.build-logs/monitor.pid"
LOG_FILE="$PROJECT_DIR/.build-logs/monitor_$(date +%Y%m%d).log"

case "${1:-start}" in
    start)
        if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
            echo "Monitor already running (PID: $(cat $PID_FILE))"
            exit 1
        fi
        echo "Starting build monitor..."
        cd "$PROJECT_DIR"
        nohup python3 "$MONITOR_PY" >> "$LOG_FILE" 2>&1 &
        echo $! > "$PID_FILE"
        echo "Monitor started (PID: $!)"
        echo "Logs: tail -f $LOG_FILE"
        ;;
    stop)
        if [ -f "$PID_FILE" ]; then
            PID=$(cat "$PID_FILE")
            kill "$PID" 2>/dev/null && echo "Monitor stopped (PID: $PID)" || echo "Monitor not running"
            rm -f "$PID_FILE"
        else
            echo "No PID file found"
        fi
        ;;
    status)
        if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
            echo "Monitor RUNNING (PID: $(cat $PID_FILE))"
        else
            echo "Monitor STOPPED"
        fi
        ;;
    logs)
        tail -f "$LOG_FILE" 2>/dev/null || echo "No log file yet"
        ;;
    fix-history)
        echo "=== Fix History ==="
        find "$PROJECT_DIR/.build-logs" -name "report_*.json" -exec jq -r '. | "Run #\(.run_id) | \(.status) | Fixes: \(.fixes_analyzed | length) | Applied: \(.fixes_applied | length)"' {} \; 2>/dev/null | sort -r
        ;;
    current-run)
        cd "$PROJECT_DIR"
        gh run list --workflow=android-build.yml --limit=5 --json runNumber,status,conclusion,createdAt,headBranch --jq '.[] | "Run #\(.runNumber) | \(.status) | \(.conclusion // "running") | \(.createdAt) | \(.headBranch)"'
        ;;
    watch)
        cd "$PROJECT_DIR"
        RUN_ID=$(gh run list --workflow=android-build.yml --limit=1 --json runNumber --jq '.[0].runNumber')
        if [ -n "$RUN_ID" ]; then
            echo "Watching run #$RUN_ID..."
            gh run watch "$RUN_ID"
        else
            echo "No runs found"
        fi
        ;;
    *)
        echo "Usage: $0 {start|stop|status|logs|fix-history|current-run|watch}"
        exit 1
        ;;
esac