#!/usr/bin/env python3
"""Extract statistics from MediScheduler Docker log files.

Usage:
    python logstats.py <path-to-docker-log.txt>
"""

import re
import sys
from collections import Counter, defaultdict
from datetime import datetime


# ---------------------------------------------------------------------------
# Patterns
# ---------------------------------------------------------------------------

# Structured log line:  timestamp | ISO_TS LEVEL PID --- [service] [thread] class : message
STRUCTURED_RE = re.compile(
    r"^(?P<ts>\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d+) \| "
    r"\S+\s+(?P<level>INFO|ERROR|WARN)\s+\d+ --- "
    r"\[(?P<service>[^\]]+)\]\s+\[(?P<thread>[^\]]+)\]\s+"
    r"(?P<class>\S+)\s+:\s+(?P<msg>.*)"
)

# -- Producer --
SENT_RE = re.compile(r"Sent to topic=(?P<topic>\S+) partition=(?P<part>\d+) offset=(?P<off>\d+)")
SEND_FAIL_RE = re.compile(r"Failed to send message: (?P<err>.+)")

# -- Route Service Consumer --
ROUTE_CONSUMER_RE = re.compile(r"\(Route Service Consumer\) Message received: (?P<payload>.+)")

# -- Scheduler Service Consumer --
SCHED_CONSUMER_RE = re.compile(r"\(Scheduler Service Consumer\) Message received: (?P<payload>.+)")

# -- Sink --
SINK_RE = re.compile(r"\(Sink\) Assignment notification received for client: (?P<client>\S+)")
SINK_WS_RE = re.compile(r"Sent (?P<n>\d+) assignments to WebSocket for client (?P<client>\S+)")

# -- Batch / RouteMatrix errors --
BATCH_ERR_RE = re.compile(r"Error processing batch (?P<batch>\S+): (?P<err>.+)")
ROUTE_MATRIX_ERR_RE = re.compile(r"Error computing route matrix for batch (?P<batch>\S+): (?P<err>.+)")

# -- Routes API results --
ROUTE_OK_RE = re.compile(r"Route Origin:(?P<orig>\S+) -> Destination:(?P<dest>\S+): (?P<m>\d+) meters, (?P<s>\d+) seconds")
ROUTE_NOVAL_RE = re.compile(r"No valid route between Origin:(?P<orig>\S+) and Destination:(?P<dest>\S+)")
ROUTE_WARN_RE = re.compile(r"Failed to compute route: (?P<err>.+)")

# -- Solver --
SOLVER_START_RE = re.compile(r"All routes computed for client (?P<client>\S+)")
SOLVER_OK_RE = re.compile(r"Solver completed successfully for client (?P<client>\S+) \((?P<n>\d+) assignments\)")
SOLVER_FAIL_RE = re.compile(r"Solver failed for client (?P<client>\S+)")
SOLVER_STATUS_RE = re.compile(r"Solver returned status (?P<status>\S+) for client (?P<client>\S+)")
PROGRESS_RE = re.compile(r"Progress for client (?P<client>\S+): (?P<done>\d+)/(?P<total>\d+) matches")

# -- Auth / Upload --
LOCK_ACQ_RE = re.compile(r"Lock acquired for client (?P<client>\S+)")
LOCK_FAIL_RE = re.compile(r"Failed to acquire lock for client (?P<client>\S+)")
MATCH_GEN_DONE_RE = re.compile(r"Match generation and lock release complete for client (?P<client>\S+)")
MATCH_GEN_FAIL_RE = re.compile(r"Match generation failed for client (?P<client>\S+)")
AUTH_RE = re.compile(r"User authenticated: (?P<user>.+?) \((?P<email>[^)]+)\)")

# -- Rate limit --
RATE_LIMIT_RE = re.compile(r"Rate limit reached, scheduling retry in (?P<sec>\d+) seconds")

# -- Generic errors (catch-all for app errors) --
GENERIC_ERR_RE = re.compile(r"Error handling incoming job for client (?P<client>\S+): (?P<err>.+)")
DESER_STUDENT_RE = re.compile(r"Failed to deserialize student (?P<id>\S+)")
DESER_TEACHER_RE = re.compile(r"Failed to deserialize teacher (?P<id>\S+)")
REDIS_STUDENT_RE = re.compile(r"Failed to store student data in redis")
REDIS_TEACHER_RE = re.compile(r"Failed to store teacher data in redis")
SERIALIZE_ERR_RE = re.compile(r"Serialization error for match")
PARSE_STUDENT_RE = re.compile(r"Failed to parse student map fields for (?P<id>\S+)")
PARSE_TEACHER_RE = re.compile(r"Failed to parse teacher map fields for (?P<id>\S+)")

# Student/teacher ID extraction from JSON payload
PAIR_RE = re.compile(r'"studentId"\s*:\s*"(?P<sid>[^"]+)"\s*,\s*"teacherId"\s*:\s*"(?P<tid>[^"]+)"')


def parse_ts(ts_str):
    try:
        return datetime.strptime(ts_str, "%Y-%m-%d %H:%M:%S.%f")
    except ValueError:
        return None


def analyze(path):
    # ---------------------------------------------------------------
    # Accumulators
    # ---------------------------------------------------------------
    lines_total = 0
    lines_app = 0  # structured app log lines (not stack traces)

    first_ts = None
    last_ts = None

    level_counts = Counter()          # INFO / ERROR / WARN
    service_counts = Counter()        # per-service line count
    class_counts = Counter()          # per-class line count

    # Kafka producer
    produced = Counter()              # topic -> count
    produce_fails = 0

    # Kafka consumers
    route_consumer_msgs = 0
    sched_consumer_msgs = 0
    sink_msgs = 0

    # Student-teacher pairs seen by route consumer
    pairs_seen = set()
    students_seen = set()
    teachers_seen = set()

    # Batching / Routes API
    batch_errors = Counter()          # error_msg -> count
    route_matrix_errors = Counter()
    routes_ok = 0
    routes_no_valid = 0
    routes_warn = 0
    rate_limit_hits = 0

    # Solver
    solver_starts = 0
    solver_successes = []             # (client, n_assignments)
    solver_failures = 0
    progress_snapshots = []           # (client, done, total)

    # Upload / Auth
    uploads_started = 0
    uploads_completed = 0
    uploads_failed = 0
    lock_failures = 0
    auth_events = []

    # WebSocket
    ws_sends = []                     # (client, n)

    # Deserialization / Redis errors
    deser_errors = Counter()          # category -> count
    redis_store_errors = 0
    parse_field_errors = Counter()    # "student"/"teacher" -> count

    # Generic handler errors
    handler_errors = Counter()        # error_msg -> count

    # Error deduplication
    unique_errors = Counter()         # (class, short_msg) -> count

    with open(path, "r", encoding="utf-8", errors="replace") as f:
        for raw_line in f:
            lines_total += 1
            m = STRUCTURED_RE.match(raw_line.strip())
            if not m:
                continue

            lines_app += 1
            ts_str = m.group("ts")
            level = m.group("level")
            service = m.group("service")
            cls = m.group("class")
            msg = m.group("msg")

            ts = parse_ts(ts_str)
            if ts:
                if first_ts is None:
                    first_ts = ts
                last_ts = ts

            level_counts[level] += 1
            service_counts[service] += 1
            class_counts[cls] += 1

            if level == "ERROR":
                short_msg = msg[:120]
                unique_errors[(cls, short_msg)] += 1

            # -- Producer --
            pm = SENT_RE.search(msg)
            if pm:
                produced[pm.group("topic")] += 1
                continue
            if SEND_FAIL_RE.search(msg):
                produce_fails += 1
                continue

            # -- Route Service Consumer --
            rm = ROUTE_CONSUMER_RE.search(msg)
            if rm:
                route_consumer_msgs += 1
                pm2 = PAIR_RE.search(rm.group("payload"))
                if pm2:
                    sid, tid = pm2.group("sid"), pm2.group("tid")
                    pairs_seen.add((sid, tid))
                    students_seen.add(sid)
                    teachers_seen.add(tid)
                continue

            # -- Scheduler Service Consumer --
            if SCHED_CONSUMER_RE.search(msg):
                sched_consumer_msgs += 1
                continue

            # -- Sink --
            if SINK_RE.search(msg):
                sink_msgs += 1
                continue
            wm = SINK_WS_RE.search(msg)
            if wm:
                ws_sends.append((wm.group("client"), int(wm.group("n"))))
                continue

            # -- Batch errors --
            bm = BATCH_ERR_RE.search(msg)
            if bm:
                batch_errors[_short_err(bm.group("err"))] += 1
                continue
            rmm = ROUTE_MATRIX_ERR_RE.search(msg)
            if rmm:
                route_matrix_errors[_short_err(rmm.group("err"))] += 1
                continue

            # -- Routes API results --
            if ROUTE_OK_RE.search(msg):
                routes_ok += 1
                continue
            if ROUTE_NOVAL_RE.search(msg):
                routes_no_valid += 1
                continue
            if ROUTE_WARN_RE.search(msg):
                routes_warn += 1
                continue

            # -- Solver --
            if SOLVER_START_RE.search(msg):
                solver_starts += 1
                continue
            sm = SOLVER_OK_RE.search(msg)
            if sm:
                solver_successes.append((sm.group("client"), int(sm.group("n"))))
                continue
            if SOLVER_FAIL_RE.search(msg):
                solver_failures += 1
                continue
            if SOLVER_STATUS_RE.search(msg):
                continue
            pm3 = PROGRESS_RE.search(msg)
            if pm3:
                progress_snapshots.append(
                    (pm3.group("client"), int(pm3.group("done")), int(pm3.group("total")))
                )
                continue

            # -- Upload / Auth --
            if LOCK_ACQ_RE.search(msg):
                uploads_started += 1
                continue
            if LOCK_FAIL_RE.search(msg):
                lock_failures += 1
                continue
            if MATCH_GEN_DONE_RE.search(msg):
                uploads_completed += 1
                continue
            if MATCH_GEN_FAIL_RE.search(msg):
                uploads_failed += 1
                continue
            if AUTH_RE.search(msg):
                am = AUTH_RE.search(msg)
                auth_events.append((am.group("user"), am.group("email")))
                continue

            # -- Rate limit --
            if RATE_LIMIT_RE.search(msg):
                rate_limit_hits += 1
                continue

            # -- Deser / parse / redis errors --
            if DESER_STUDENT_RE.search(msg):
                deser_errors["student"] += 1
                continue
            if DESER_TEACHER_RE.search(msg):
                deser_errors["teacher"] += 1
                continue
            if REDIS_STUDENT_RE.search(msg):
                redis_store_errors += 1
                continue
            if REDIS_TEACHER_RE.search(msg):
                redis_store_errors += 1
                continue
            if SERIALIZE_ERR_RE.search(msg):
                deser_errors["serialization"] += 1
                continue
            if PARSE_STUDENT_RE.search(msg):
                parse_field_errors["student"] += 1
                continue
            if PARSE_TEACHER_RE.search(msg):
                parse_field_errors["teacher"] += 1
                continue

            # -- Generic handler errors --
            gm = GENERIC_ERR_RE.search(msg)
            if gm:
                handler_errors[_short_err(gm.group("err"))] += 1
                continue

    # ---------------------------------------------------------------
    # Report
    # ---------------------------------------------------------------
    sep = "=" * 70
    thin = "-" * 70

    print(sep)
    print("  MEDISCHEDULER — DOCKER LOG ANALYSIS")
    print(sep)
    print()

    # -- Overview --
    _section("OVERVIEW")
    print(f"  Log file:           {path}")
    print(f"  Total lines:        {lines_total:,}")
    print(f"  App log lines:      {lines_app:,}")
    print(f"  Stack-trace lines:  {lines_total - lines_app:,}")
    if first_ts and last_ts:
        duration = last_ts - first_ts
        print(f"  Time span:          {first_ts} -> {last_ts}")
        print(f"  Duration:           {duration}")
    print()

    # -- Log levels --
    _section("LOG LEVELS")
    for lvl in ("INFO", "WARN", "ERROR"):
        if level_counts[lvl]:
            print(f"  {lvl:<8} {level_counts[lvl]:>6}")
    print()

    # -- Per-service breakdown --
    if len(service_counts) > 1:
        _section("PER-SERVICE LINE COUNTS")
        for svc, cnt in service_counts.most_common():
            print(f"  {svc:<45} {cnt:>6}")
        print()

    # -- Kafka flow --
    _section("KAFKA DATA FLOW")

    print("  Producer (KafkaProducerService):")
    if produced:
        for topic, cnt in produced.most_common():
            print(f"    -> {topic:<30} {cnt:>6} messages sent")
    else:
        print(f"    (no successful sends logged)")
    if produce_fails:
        print(f"    FAILED sends:                {produce_fails:>6}")
    print()

    print("  Consumers:")
    print(f"    Route Service  (MATCH_TOPIC):              {route_consumer_msgs:>6} messages received")
    print(f"    Scheduler Svc  (ROUTE_MATRIX_TOPIC):       {sched_consumer_msgs:>6} messages received")
    print(f"    Sink           (OPTIMAL_ASSIGNMENTS_TOPIC): {sink_msgs:>5} notifications received")
    print()

    if ws_sends:
        print("  WebSocket pushes:")
        for client, n in ws_sends:
            print(f"    client {client}: {n} assignments sent")
        print()

    # -- Message coverage --
    if pairs_seen:
        expected = len(students_seen) * len(teachers_seen)
        _section("MESSAGE COVERAGE (MATCH_TOPIC)")
        print(f"  Unique students:        {len(students_seen)}")
        print(f"  Unique teachers:        {len(teachers_seen)}")
        print(f"  Unique pairs received:  {len(pairs_seen)}")
        print(f"  Expected pairs (S*T):   {expected}")
        missing = expected - len(pairs_seen)
        if missing > 0:
            print(f"  MISSING pairs:          {missing}")
        elif missing == 0:
            print(f"  All expected pairs received.")
        print()

    # -- Upload lifecycle --
    if uploads_started or uploads_completed or uploads_failed or lock_failures:
        _section("UPLOAD LIFECYCLE")
        print(f"  Locks acquired (uploads started):  {uploads_started}")
        print(f"  Match generation completed:        {uploads_completed}")
        print(f"  Match generation failed:           {uploads_failed}")
        print(f"  Lock acquisition failures (429s):  {lock_failures}")
        print()

    # -- Auth --
    if auth_events:
        _section("AUTHENTICATION")
        for user, email in auth_events:
            print(f"  Authenticated: {user} ({email})")
        print()

    # -- Batching / Routes API --
    _section("ROUTES API & BATCHING")
    print(f"  Successful routes:    {routes_ok:>6}")
    print(f"  No-valid-route:       {routes_no_valid:>6}")
    print(f"  Route warnings:       {routes_warn:>6}")
    print(f"  Rate-limit retries:   {rate_limit_hits:>6}")
    print()

    if batch_errors:
        print("  BatchManager errors:")
        for err, cnt in batch_errors.most_common(5):
            print(f"    [{cnt:>4}x] {err}")
        print()
    if route_matrix_errors:
        print("  RouteMatrixBuilder errors:")
        for err, cnt in route_matrix_errors.most_common(5):
            print(f"    [{cnt:>4}x] {err}")
        print()

    # -- Solver --
    _section("SOLVER (OR-Tools)")
    print(f"  Solver invocations:   {solver_starts}")
    if solver_successes:
        for client, n in solver_successes:
            print(f"  SUCCESS: client {client} — {n} assignments")
    if solver_failures:
        print(f"  FAILURES:             {solver_failures}")
    if not solver_starts and not solver_successes and not solver_failures:
        print("  (solver was never triggered)")
    if progress_snapshots:
        last = progress_snapshots[-1]
        print(f"  Last progress:        {last[1]}/{last[2]} matches for client {last[0]}")
    print()

    # -- Deserialization / parse errors --
    if deser_errors or parse_field_errors or redis_store_errors:
        _section("DESERIALIZATION & STORAGE ERRORS")
        if deser_errors:
            for cat, cnt in deser_errors.most_common():
                print(f"  Deserialization ({cat}): {cnt}")
        if parse_field_errors:
            for cat, cnt in parse_field_errors.most_common():
                print(f"  Field parse ({cat}):     {cnt}")
        if redis_store_errors:
            print(f"  Redis store failures:     {redis_store_errors}")
        print()

    # -- Handler errors --
    if handler_errors:
        _section("INCOMING JOB HANDLER ERRORS")
        for err, cnt in handler_errors.most_common(5):
            print(f"    [{cnt:>4}x] {err}")
        print()

    # -- Top errors (deduplicated) --
    if unique_errors:
        _section("TOP ERRORS (deduplicated)")
        for (cls, short_msg), cnt in unique_errors.most_common(10):
            print(f"  [{cnt:>4}x] {cls}")
            print(f"          {short_msg}")
        print()

    # -- Data flow summary --
    _section("DATA FLOW SUMMARY")
    total_produced = sum(produced.values())

    stages = [
        ("POST /matches", "upload", uploads_started or "?"),
        ("KafkaProducer -> MATCH_TOPIC", "produced", total_produced or "(not in logs)"),
        ("KafkaStreamService <- MATCH_TOPIC", "consumed", route_consumer_msgs),
        ("BatchManager -> Routes API", "routed",
         f"{routes_ok} ok / {routes_no_valid} no-route / {sum(batch_errors.values())} errors"),
        ("BatchManager -> ROUTE_MATRIX_TOPIC", "batches", "(sent on batch complete)"),
        ("Scheduler <- ROUTE_MATRIX_TOPIC", "consumed", sched_consumer_msgs),
        ("OR-Tools solver", "solved",
         f"{len(solver_successes)} ok / {solver_failures} fail" if solver_starts else "not triggered"),
        ("Sink <- OPTIMAL_ASSIGNMENTS_TOPIC", "received", sink_msgs),
        ("WebSocket -> frontend", "pushed",
         f"{sum(n for _, n in ws_sends)} assignments" if ws_sends else "none"),
    ]

    max_label = max(len(s[0]) for s in stages)
    for label, tag, value in stages:
        print(f"  {label:<{max_label}}  =>  {value}")
        if label != stages[-1][0]:
            print(f"  {'':>{max_label // 2}}|")

    print()
    print(sep)
    print("  END OF REPORT")
    print(sep)


def _section(title):
    print(f"--- {title} ---")
    print()


def _short_err(err_str):
    """Truncate error for dedup bucketing."""
    # Strip UUIDs for grouping
    s = re.sub(r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", "<UUID>", err_str)
    return s[:100]


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print(f"Usage: {sys.argv[0]} <docker-log-file.txt>", file=sys.stderr)
        sys.exit(1)
    analyze(sys.argv[1])
