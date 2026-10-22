#!/usr/bin/env python3
"""data/eval/scenario 평가셋을 실제 스택(backend + MCP + AI 서버)에 보내 결과를 채점합니다.

사용:
  python3 scripts/scenario_eval.py                       # 시나리오 23건 + 접수 검증 6건
  python3 scripts/scenario_eval.py --only S01,S10,V2     # 일부만
  python3 scripts/scenario_eval.py --base-url http://127.0.0.1:8080 --out data/eval/scenario/results/run.json

backend를 AI_MODE=rabbit으로 띄우고 MCP 두 개와 AI 서버가 떠 있어야 합니다(mock이면 키워드 판단 결과가 채점됩니다).
실행마다 실제 요청이 DB에 쌓이고 OpenAI 호출 비용이 듭니다. 표준 라이브러리만 씁니다.
"""

import argparse
import json
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime
from pathlib import Path

EVAL_DIR = Path(__file__).resolve().parent.parent / "data" / "eval" / "scenario"
PASSWORD = "test1234"
ADMIN = "admin@mro.test"  # trace(판단 경로·근거)는 관리자만 조회
REPORTER = {"KAL": "reporter1@kal.test", "AAR": "reporter1@aar.test", "JNA": "reporter1@jna.test"}
IN_PROGRESS = {"QUEUED", "CLASSIFYING", "INVESTIGATING", "REVIEWING"}


class Api:
    def __init__(self, base_url):
        self.base = base_url.rstrip("/") + "/api/v1"
        self.tokens = {}

    def call(self, method, path, body=None, token=None):
        req = urllib.request.Request(self.base + path, method=method,
                                     data=json.dumps(body).encode() if body is not None else None)
        req.add_header("Content-Type", "application/json")
        if token:
            req.add_header("Authorization", f"Bearer {token}")
        try:
            with urllib.request.urlopen(req, timeout=30) as r:
                return r.status, json.loads(r.read() or "null")
        except urllib.error.HTTPError as e:
            return e.code, json.loads(e.read() or "null")

    def token(self, email):
        if email not in self.tokens:
            status, body = self.call("POST", "/auth/login", {"email": email, "password": PASSWORD})
            if status != 200:
                sys.exit(f"로그인 실패 {email}: {status} {body}")
            self.tokens[email] = body["access_token"]
        return self.tokens[email]


def load(name):
    return [json.loads(line) for line in (EVAL_DIR / name).read_text(encoding="utf-8").splitlines() if line.strip()]


def submit_body(case):
    return {k: case[k] for k in ("tail_number", "airline_code", "aircraft_model", "symptom_text")}


def run_scenario(api, case, timeout):
    token = api.token(REPORTER[case["airline_code"]])
    started = time.monotonic()
    status, body = api.call("POST", "/requests", submit_body(case), token)
    if status not in (200, 201, 202):
        return {"id": case["id"], "error": f"접수 실패 {status}: {body}", "correct": False}
    rid = body["id"]
    while body["status"] in IN_PROGRESS and time.monotonic() - started < timeout:
        time.sleep(2)
        _, body = api.call("GET", f"/requests/{rid}", token=token)
    elapsed = round(time.monotonic() - started, 1)
    _, trace = api.call("GET", f"/requests/{rid}/trace", token=api.token(ADMIN))

    statuses = [h["to_status"] for h in trace.get("history", []) if h["to_status"] != "QUEUED"]
    final = next((a["output"] for a in reversed(trace.get("assessments", []))
                  if a["stage"] == "FINAL" and a.get("output")), None) or {}
    cited = [e.get("ref_id") for e in final.get("evidence", [])]
    path = ("HUMAN_REVIEW" if body["status"] == "HUMAN_REVIEW"
            else "INVESTIGATE" if "INVESTIGATING" in statuses else "DIRECT")

    exp = case["expected"]
    if exp["status"] == "ASSIGNED":
        correct = body["status"] == "ASSIGNED" and body["assigned_department"] == exp["department"]
    else:
        correct = body["status"] == "HUMAN_REVIEW"
    trap_hit = sorted(set(cited) & set(exp.get("must_not_cite", [])))
    return {
        "id": case["id"], "request_id": rid, "status": body["status"],
        "department": body["assigned_department"], "review_reason": body.get("review_reason"),
        "expected_status": exp["status"], "expected_department": exp["department"],
        "path": path, "expected_path": exp["path"], "statuses": statuses, "cited": cited,
        "correct": correct and not trap_hit, "trap_cited": trap_hit, "seconds": elapsed,
        "timed_out": body["status"] in IN_PROGRESS,
    }


def run_validation(api, case):
    status, body = api.call("POST", "/requests", submit_body(case), api.token(case["reporter"]))
    want = case["expected"]["detail"]
    details = ((body or {}).get("error") or {}).get("details") or []
    ok = status == case["expected"]["http_status"] and any(
        all(d.get(k) == v for k, v in want.items()) for d in details)
    return {"id": case["id"], "http_status": status, "details": details, "expected": want, "correct": ok}


def summarize(scenarios, validations):
    assign = [r for r in scenarios if r.get("expected_status") == "ASSIGNED"]
    review = [r for r in scenarios if r.get("expected_status") == "HUMAN_REVIEW"]
    done = [r for r in scenarios if "seconds" in r and not r["timed_out"]]
    pct = lambda a, b: f"{a}/{b}" + (f" ({a / b:.0%})" if b else "")
    return {
        "배정 정답 (기대 부서로 배정)": pct(sum(r["correct"] for r in assign), len(assign)),
        "사람 검토 전환 (모호한 증상)": pct(sum(r["correct"] for r in review), len(review)),
        "경로 일치": pct(sum(r.get("path") == r.get("expected_path") for r in scenarios), len(scenarios)),
        "자동 배정률": pct(sum(r.get("status") == "ASSIGNED" for r in scenarios), len(scenarios)),
        "함정 이력 인용": sum(bool(r.get("trap_cited")) for r in scenarios),
        "평균 소요(초)": round(sum(r["seconds"] for r in done) / len(done), 1) if done else None,
        "접수 검증 일치": pct(sum(r["correct"] for r in validations), len(validations)),
    }


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--base-url", default="http://127.0.0.1:8080")
    ap.add_argument("--only", help="쉼표로 구분한 ID (예: S01,S10,V2)")
    ap.add_argument("--timeout", type=int, default=300, help="요청당 최대 대기(초), backend 큐 TTL과 같게")
    ap.add_argument("--out", type=Path, help="결과 JSON 경로 (기본: data/eval/scenario/results/<시각>.json)")
    args = ap.parse_args()

    only = set(args.only.split(",")) if args.only else None
    pick = lambda cases: [c for c in cases if not only or c["id"] in only]
    api = Api(args.base_url)

    scenarios = []
    for case in pick(load("scenarios.jsonl")):
        r = run_scenario(api, case, args.timeout)
        scenarios.append(r)
        mark = "OK" if r["correct"] else "NG"
        print(f'{r["id"]} {mark} {r.get("status")} {r.get("department")} (기대 {case["expected"]["department"] or "사람 검토"}) '
              f'{" > ".join(r.get("statuses", []))} {r.get("seconds")}s 인용={r.get("cited")}', flush=True)
    validations = []
    for case in pick(load("validation.jsonl")):
        r = run_validation(api, case)
        validations.append(r)
        print(f'{r["id"]} {"OK" if r["correct"] else "NG"} {r["http_status"]} {r["details"]}', flush=True)

    summary = summarize(scenarios, validations)
    out = args.out or EVAL_DIR / "results" / f"{datetime.now():%Y%m%d-%H%M%S}.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps({"base_url": args.base_url, "ran_at": datetime.now().isoformat(timespec="seconds"),
                               "summary": summary, "scenarios": scenarios, "validations": validations},
                              ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    print(f"저장: {out}")


if __name__ == "__main__":
    main()
