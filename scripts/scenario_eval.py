#!/usr/bin/env python3
"""서비스 평가셋(data/eval/service)을 실제 스택(backend + MCP + AI 서버)에 보내 결과를 채점합니다.

사용:
  python3 scripts/scenario_eval.py                       # 서비스 시나리오 전체 + 접수 검증 6건
  python3 scripts/scenario_eval.py --split test --workers 4   # 확인용(test)만, 4건씩 동시에
  python3 scripts/scenario_eval.py --group A,D --only SV-A-001,V2
  python3 scripts/scenario_eval.py --base-url http://127.0.0.1:8080 --out data/eval/service/results/run.json

- 정답: 기대가 ASSIGNED면 acceptable_departments 중 한 부서로 배정되고 must_not_cite 이력을 인용하지 않아야 합니다.
  기대가 HUMAN_REVIEW면 사람 검토로 가야 합니다.
- 접수 검증(422)은 data/eval/scenario/validation.jsonl을 씁니다.
- 수리이력 시드가 평가셋과 같은 파일(manifest.json의 history_file)인 DB에서 돌려야 합니다.

backend를 AI_MODE=rabbit으로 띄우고 MCP 두 개와 AI 서버가 떠 있어야 합니다(mock이면 키워드 판단 결과가 채점됩니다).
실행마다 실제 요청이 DB에 쌓이고 OpenAI 호출 비용이 듭니다. 표준 라이브러리만 씁니다.
"""

import argparse
import json
import math
import statistics
import sys
import threading
import time
from collections import defaultdict
from concurrent.futures import ThreadPoolExecutor
import urllib.error
import urllib.request
from datetime import datetime
from pathlib import Path

DATA = Path(__file__).resolve().parent.parent / "data" / "eval"
SERVICE = DATA / "service"
VALIDATION = DATA / "scenario" / "validation.jsonl"
PASSWORD = "test1234"
ADMIN = "admin@mro.test"  # trace(판단 경로·근거)는 관리자만 조회
REPORTER = {"KAL": "reporter1@kal.test", "AAR": "reporter1@aar.test", "JNA": "reporter1@jna.test"}
IN_PROGRESS = {"QUEUED", "CLASSIFYING", "INVESTIGATING", "REVIEWING"}


class Api:
    def __init__(self, base_url):
        self.base = base_url.rstrip("/") + "/api/v1"
        self.tokens = {}
        self.lock = threading.Lock()

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
        with self.lock:
            return self._token(email)

    def _token(self, email):
        if email not in self.tokens:
            status, body = self.call("POST", "/auth/login", {"email": email, "password": PASSWORD})
            if status != 200:
                sys.exit(f"로그인 실패 {email}: {status} {body}")
            self.tokens[email] = body["access_token"]
        return self.tokens[email]


def load(path):
    return [json.loads(line) for line in Path(path).read_text(encoding="utf-8").splitlines() if line.strip()]


def submit_body(case):
    return {k: case[k] for k in ("tail_number", "airline_code", "aircraft_model", "symptom_text")}


def run_scenario(api, case, timeout):
    token = api.token(REPORTER[case["airline_code"]])
    started = time.monotonic()
    status, body = api.call("POST", "/requests", submit_body(case), token)
    if status not in (200, 201, 202):  # 집계에서 빠지지 않게 기대값을 함께 남김
        exp = case["expected"]
        return {"id": case["id"], "split": case.get("split"), "group": case.get("group"), "error": f"접수 실패 {status}: {body}",
                "expected_status": exp["status"], "expected_department": exp["department"], "expected_path": exp["path"],
                "correct": False, "timed_out": False}
    rid = body["id"]
    while body["status"] in IN_PROGRESS and time.monotonic() - started < timeout:
        time.sleep(2)
        _, body = api.call("GET", f"/requests/{rid}", token=token)
    elapsed = round(time.monotonic() - started, 1)
    exp = case["expected"]
    acceptable = exp.get("acceptable_departments") or ([exp["department"]] if exp.get("department") else [])

    def trace_error(message):
        # 관측된 배정은 분모에 남기되, 확인하지 못한 근거를 통과로 취급하지 않는다.
        return {
            "id": case["id"], "split": case.get("split"), "group": case.get("group"), "request_id": rid,
            "status": body["status"], "department": body.get("assigned_department"),
            "review_reason": body.get("review_reason"), "expected_status": exp["status"],
            "expected_department": exp["department"], "acceptable": acceptable, "expected_path": exp["path"],
            "correct": False, "evaluation_error": True, "error": message,
            "seconds": elapsed, "timed_out": body["status"] in IN_PROGRESS,
        }

    try:
        trace_status, trace = api.call("GET", f"/requests/{rid}/trace", token=api.token(ADMIN))
    except (OSError, ValueError) as exc:
        return trace_error(f"추적 조회 실패: {type(exc).__name__}")
    if trace_status != 200:
        return trace_error(f"추적 조회 실패 HTTP {trace_status}")
    if not isinstance(trace, dict) or not isinstance(trace.get("history"), list) or not isinstance(trace.get("assessments"), list):
        return trace_error("추적 응답 형식 오류")
    if any(not isinstance(h, dict) or "to_status" not in h for h in trace["history"]) or any(
            not isinstance(a, dict) for a in trace["assessments"]):
        return trace_error("추적 항목 형식 오류")

    statuses = [h["to_status"] for h in trace.get("history", []) if h["to_status"] != "QUEUED"]
    final = next((a.get("output") for a in reversed(trace["assessments"])
                  if a.get("stage") == "FINAL"), None)
    if not isinstance(final, dict) or final.get("outcome") not in ("ASSIGN", "HUMAN_REVIEW"):
        return trace_error("FINAL 판단 누락·형식 오류")
    # AI human_payload는 정상 HUMAN_REVIEW 완료 시 evidence 필드를 생략한다.
    final_evidence = (final.get("evidence", [])
                      if body["status"] == "HUMAN_REVIEW" and final["outcome"] == "HUMAN_REVIEW"
                      else final.get("evidence"))
    if not isinstance(final_evidence, list):
        return trace_error("FINAL 근거 목록 누락·형식 오류")
    if any(not isinstance(e, dict) or not isinstance(e.get("ref_id"), str) for e in final_evidence):
        return trace_error("FINAL 근거 항목 형식 오류")
    cited = [e["ref_id"] for e in final_evidence]
    path = ("HUMAN_REVIEW" if body["status"] == "HUMAN_REVIEW"
            else "INVESTIGATE" if "INVESTIGATING" in statuses else "DIRECT")

    trap_hit = sorted(set(cited) & set(exp.get("must_not_cite", [])))
    if body["status"] == "ASSIGNED" and final["outcome"] != "ASSIGN":
        return trace_error("배정 상태와 FINAL outcome 불일치")
    if exp["status"] == "ASSIGNED":
        correct = body["status"] == "ASSIGNED" and body["assigned_department"] in acceptable and not trap_hit
    else:
        correct = body["status"] == "HUMAN_REVIEW"
    evidence = exp.get("history_evidence", [])
    return {
        "id": case["id"], "split": case.get("split"), "group": case.get("group"), "request_id": rid,
        "status": body["status"], "department": body["assigned_department"], "review_reason": body.get("review_reason"),
        "expected_status": exp["status"], "expected_department": exp["department"], "acceptable": acceptable,
        "path": path, "expected_path": exp["path"], "statuses": statuses, "cited": cited,
        "correct": correct, "trap_cited": trap_hit,
        "evidence_cited": bool(set(cited) & set(evidence)) if evidence else None,
        "seconds": elapsed, "timed_out": body["status"] in IN_PROGRESS,
    }


def run_validation(api, case):
    status, body = api.call("POST", "/requests", submit_body(case), api.token(case["reporter"]))
    want = case["expected"]["detail"]
    details = ((body or {}).get("error") or {}).get("details") or []
    ok = status == case["expected"]["http_status"] and any(
        all(d.get(k) == v for k, v in want.items()) for d in details)
    return {"id": case["id"], "http_status": status, "details": details, "expected": want, "correct": ok}


def summarize(scenarios, validations):
    """전체 자동배정의 부서·근거 조건을 채점하고, 보류 적절성은 별도 판정한다."""
    pct = lambda a, b: f"{a}/{b}" + (f" ({a / b:.1%})" if b else "")
    assign = [r for r in scenarios if r.get("expected_status") == "ASSIGNED"]
    review = [r for r in scenarios if r.get("expected_status") == "HUMAN_REVIEW"]
    auto = [r for r in scenarios if r.get("status") == "ASSIGNED"]
    assign_auto = [r for r in assign if r.get("status") == "ASSIGNED"]
    wrong = [r for r in auto if not r["correct"]]
    wrong_department = [r for r in assign_auto if r.get("department") not in
                        (r.get("acceptable") or [r.get("expected_department")])]
    done = sorted(r["seconds"] for r in scenarios if "seconds" in r and not r["timed_out"])
    with_evidence = [r for r in assign if r.get("evidence_cited") is not None]

    def table(key):
        rows = defaultdict(list)
        for r in assign:
            rows[r.get(key) or "-"].append(r)
        return {k: pct(sum(r["correct"] for r in v), len(v)) for k, v in sorted(rows.items(), key=lambda kv: str(kv[0]))}

    return {
        "전체 평가 통과율": pct(sum(r["correct"] for r in scenarios), len(scenarios)),
        "오배정률 (틀린 부서로 자동 배정)": pct(len(wrong_department), len(assign)),
        "자동 배정 정밀도 (자동 배정 중 정답)": pct(len(auto) - len(wrong), len(auto)),
        "자동 배정률 (전체 문항 중)": pct(len(auto), len(scenarios)),
        "자동 배정률 (배정 기대 문항 중)": pct(len(assign_auto), len(assign)),
        "자동 배정 평가 실패 (부서·근거·기대 상태)": len(wrong),
        "배정 평가 통과율 (부서·근거 조건)": pct(sum(r["correct"] for r in assign), len(assign)),
        "부서는 일치하나 금지 이력 인용": sum(not r["correct"] and bool(r.get("trap_cited"))
            and r not in wrong_department for r in assign_auto),
        "사람 검토 전환": sum(r.get("status") == "HUMAN_REVIEW" for r in scenarios),
        "사람 검토 재현율 (사람 검토 기대 문항 중)": pct(sum(r["correct"] for r in review), len(review)),
        "사람 검토 기대인데 자동 배정": sum(r.get("status") == "ASSIGNED" for r in review),
        "경로 일치": pct(sum(r.get("path") == r.get("expected_path") for r in scenarios), len(scenarios)),
        "정답 근거 이력 인용률 (반복 결함)": pct(sum(r["evidence_cited"] for r in with_evidence), len(with_evidence)),
        "함정 이력 인용": sum(bool(r.get("trap_cited")) for r in scenarios),
        "추적 조회·근거 확인 오류": sum(bool(r.get("evaluation_error")) for r in scenarios),
        "시간 초과·실패": sum(r.get("timed_out", False) or "error" in r for r in scenarios),
        "소요 중앙값(초)": statistics.median(done) if done else None,
        "소요 상위 5%(초)": done[math.ceil(len(done) * 0.95) - 1] if done else None,
        "접수 검증 일치": pct(sum(r["correct"] for r in validations), len(validations)),
        "부서별 정답": table("expected_department"),
        "묶음별 정답": table("group"),
    }


def summarize_by_split(scenarios, validations):
    """test(확인용)가 공식 수치. dev(조정용)는 개선 작업 재료로만 봅니다."""
    summary = summarize(scenarios, validations)
    splits = sorted({r.get("split") for r in scenarios if r.get("split")})
    if len(splits) > 1:
        keep = ("전체 평가", "오배정률", "자동 배정", "배정 평가", "부서는 일치", "사람 검토", "경로 일치", "정답 근거")
        summary["split별"] = {s: {k: v for k, v in summarize([r for r in scenarios if r.get("split") == s], []).items()
                                 if k.startswith(keep)} for s in splits}
    return summary


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--base-url", default="http://127.0.0.1:8080")
    ap.add_argument("--dataset", type=Path, default=SERVICE / "scenarios.jsonl", help="시나리오 JSONL")
    ap.add_argument("--split", choices=["dev", "test", "all"], default="all", help="dev(조정용)·test(확인용)")
    ap.add_argument("--group", help="쉼표로 구분한 묶음 (예: A,D)")
    ap.add_argument("--only", help="쉼표로 구분한 ID (예: SV-A-001,V2)")
    ap.add_argument("--workers", type=int, default=1, help="동시에 접수할 건수")
    ap.add_argument("--no-validation", action="store_true", help="접수 검증(422) 건너뜀")
    ap.add_argument("--timeout", type=int, default=300, help="요청당 최대 대기(초), backend 큐 TTL과 같게")
    ap.add_argument("--out", type=Path, help="결과 JSON 경로 (기본: data/eval/service/results/<시각>.json)")
    args = ap.parse_args()

    only = set(args.only.split(",")) if args.only else None
    groups = set(args.group.split(",")) if args.group else None
    pick = lambda cases: [c for c in cases if (not only or c["id"] in only)
                          and (args.split == "all" or c.get("split", args.split) == args.split)
                          and (not groups or c.get("group") in groups)]
    api = Api(args.base_url)
    cases = pick(load(args.dataset))
    manifest_path = args.dataset.parent / "manifest.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8")) if manifest_path.exists() else {}
    print(f"시나리오 {len(cases)}건 ({args.dataset.name}, split={args.split}, 이력 {manifest.get('history_file', '?')}), "
          f"동시 {args.workers}건", flush=True)

    def one(case):
        r = run_scenario(api, case, args.timeout)
        mark = "OK" if r["correct"] else "NG"
        print(f'{r["id"]} {mark} {r.get("status")} {r.get("department")} '
              f'(기대 {"/".join(r.get("acceptable") or []) or "사람 검토"}) '
              f'{" > ".join(r.get("statuses", []))} {r.get("seconds")}s 인용={r.get("cited")}', flush=True)
        return r

    with ThreadPoolExecutor(max_workers=max(1, args.workers)) as pool:
        scenarios = list(pool.map(one, cases))
    validations = []
    if not args.no_validation:
        for case in [c for c in load(VALIDATION) if not only or c["id"] in only]:
            r = run_validation(api, case)
            validations.append(r)
            print(f'{r["id"]} {"OK" if r["correct"] else "NG"} {r["http_status"]} {r["details"]}', flush=True)

    summary = summarize_by_split(scenarios, validations)
    out = args.out or SERVICE / "results" / f"{datetime.now():%Y%m%d-%H%M%S}.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps({"base_url": args.base_url, "ran_at": datetime.now().isoformat(timespec="seconds"),
                               "dataset": str(args.dataset), "split": args.split, "history_file": manifest.get("history_file"),
                               "summary": summary, "scenarios": scenarios, "validations": validations},
                              ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    print(f"저장: {out}")


if __name__ == "__main__":
    main()
