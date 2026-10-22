#!/usr/bin/env python3
"""FAA 공개 SDR(2019~2020)로 단위 평가셋을 만듭니다. 결과: data/eval/sdr/

  python3 scripts/build_sdr_eval.py                 # 다운로드 → 추출 → 근거 묶음 → 번역 → 저장
  python3 scripts/build_sdr_eval.py --no-translate  # 번역 없이 (symptom_en/ko 비움)

- 2019~2020년은 검색 대상(2021~2026 corpus)과 기간이 겹치지 않아, 정답이 검색 결과에 섞이지 않습니다.
- 정답 라벨은 신고자가 붙인 JASC → ATA(앞 두 자리) → 부서(data/seed/departments.csv)입니다.
- 근거 묶음(department.jsonl)은 로컬 DB의 corpus에서 rag-mcp R02와 같은 방식(같은 기종, 단어 OR 전문검색)으로 뽑아 고정합니다.
- 번역은 OpenAI(LLM_API_KEY 또는 OPENAI_API_KEY)를 씁니다. 키는 파일에 남기지 않습니다.
표준 라이브러리와 psql만 씁니다. 같은 입력이면 같은 결과가 나오도록 시드를 고정합니다.
"""

import argparse
import csv
import hashlib
import json
import os
import random
import re
import subprocess
import sys
import time
import urllib.request
from collections import defaultdict
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "data" / "eval" / "sdr"
YEARS = (2019, 2020)
SOURCE_URL = "https://external.apic4e.faa.gov/sdrs/retrieve/SDR-{year}.csv"
FAMILY = ("A319", "A320", "A321")
SEED = 20261022
PER_DEPT = {"search": 50, "triage": 25, "department": 25}
PACK_SIZE = 5
TRANSLATE_MODEL = "gpt-5.4-mini"

csv.field_size_limit(10**9)

# 정답이 문장에 그대로 드러나는 매뉴얼·장(chapter) 참조를 지웁니다 (예: "IAW AMM 32-41-00", "ATA 32").
SCRUB = [
    re.compile(r"\b(IAW|PER|REF(ER)?( TO)?|ACCORDING TO|IN ACCORDANCE WITH)\b[^.;]*?\b(AMM|TSM|IPC|SRM|CMM|FIM|MEL|TASK|MM)\b[\s:#.-]*[\w.-]*", re.I),
    re.compile(r"\b(AMM|TSM|IPC|SRM|CMM|FIM|MEL|TASK|MM|ATA|CHAPTER|CH)\b[\s:#.-]*\d[\w.-]*", re.I),
    re.compile(r"\b\d{2}-\d{2}(-\d{2})+(-\d+)*\b"),
    re.compile(r"\b\d{6}-\d{2,3}\b"),  # 붙여 쓴 task 번호 (281100-01)
    re.compile(r"\b(AMM|TSM|IPC|SRM|CMM|FIM)\b( REF\b)?", re.I),
]


def scrub(text):
    for pattern in SCRUB:
        text = pattern.sub(" ", text)
    text = re.sub(r"\s+([.,;:])", r"\1", re.sub(r"\s+", " ", text))
    return text.strip(" .,;:-")


def norm_hash(text):
    return hashlib.md5(re.sub(r"\s+", " ", text or "").upper().encode()).hexdigest()


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def psql(dsn, sql):
    out = subprocess.run(["psql", dsn, "-t", "--csv", "-c", sql], check=True, capture_output=True, text=True)
    return [row for row in csv.reader(out.stdout.splitlines(keepends=True)) if row]


def sql_text(value):
    return "'" + value.replace("'", "''") + "'"


def download(cache):
    cache.mkdir(parents=True, exist_ok=True)
    files = {}
    for year in YEARS:
        path = cache / f"SDR-{year}.csv"
        if not path.exists():
            print(f"다운로드 SDR-{year}.csv", flush=True)
            req = urllib.request.Request(SOURCE_URL.format(year=year), headers={"User-Agent": "Mozilla/5.0"})
            with urllib.request.urlopen(req, timeout=300) as r, open(path, "wb") as f:
                f.write(r.read())
        files[year] = path
    return files


def departments():
    ata_to_dept, names = {}, {}
    for row in csv.DictReader(open(ROOT / "data" / "seed" / "departments.csv", encoding="utf-8")):
        names[row["code"]] = row["name"]
        for ch in row["ata_chapters"].split(","):
            ata_to_dept[ch.strip()] = row["code"]
    return ata_to_dept, names


def build_pool(files, dsn, ata_to_dept):
    jasc = {code: title for code, title in psql(dsn, "SELECT code, title FROM jasc_codes")}
    models = {m for (m,) in psql(dsn, "SELECT DISTINCT model_key FROM sdr_cases")}
    corpus_ocn = {o for (o,) in psql(dsn, "SELECT operator_control_number FROM sdr_cases WHERE operator_control_number IS NOT NULL")}
    corpus_text = {h for (h,) in psql(dsn, "SELECT md5(upper(regexp_replace(narrative_raw, '\\s+', ' ', 'g'))) FROM sdr_cases")}
    pool, seen, dropped = [], set(), defaultdict(int)
    for year, path in files.items():
        for no, r in enumerate(csv.DictReader(open(path, encoding="utf-8", errors="replace")), start=1):
            model, code, text = r["AircraftModel"].strip(), r["JASCCode"].strip(), (r["Discrepancy"] or "").strip()
            if "AIRBUS" not in r["AircraftMake"].upper() or not model.startswith(FAMILY):
                continue
            reason = (
                "model_not_in_corpus" if model not in models
                else "jasc_unknown" if code not in jasc
                else "no_department" if code[:2] not in ata_to_dept
                else "in_corpus" if r["OperatorControlNumber"] in corpus_ocn or norm_hash(text) in corpus_text
                else "duplicate" if norm_hash(text) in seen
                else None
            )
            query = scrub(text)
            if reason is None and not 40 <= len(query) <= 600:
                reason = "length"
            if reason:
                dropped[reason] += 1
                continue
            seen.add(norm_hash(text))
            pool.append({
                "source": {"file": f"SDR-{year}.csv", "record_no": no, "operator_control_number": r["OperatorControlNumber"]},
                "aircraft_model": model, "jasc": code, "jasc_title": jasc[code], "ata": code[:2],
                "department": ata_to_dept[code[:2]], "narrative_raw": text, "query": query,
            })
    return pool, dict(dropped)


def sample(pool, names):
    """부서마다 JASC를 돌아가며 뽑아 한 코드에 몰리지 않게 하고, 세 평가셋이 겹치지 않게 나눕니다."""
    rng = random.Random(SEED)
    by_dept = defaultdict(lambda: defaultdict(list))
    for item in pool:
        by_dept[item["department"]][item["jasc"]].append(item)
    need = sum(PER_DEPT.values())
    sets = {name: [] for name in PER_DEPT}
    for dept in sorted(names):
        codes = by_dept[dept]
        for items in codes.values():
            rng.shuffle(items)
        order = sorted(codes)
        rng.shuffle(order)
        picked = []
        while len(picked) < need and any(codes[c] for c in order):
            for c in order:
                if codes[c] and len(picked) < need:
                    picked.append(codes[c].pop())
        if len(picked) < need:
            sys.exit(f"{dept} 부서 후보가 부족합니다: {len(picked)}/{need}")
        start = 0
        for name, n in PER_DEPT.items():
            sets[name] += picked[start:start + n]
            start += n
    for name, items in sets.items():
        for i, item in enumerate(items, start=1):
            item["id"] = f"{name[:3].upper()}-{i:03d}"
    return sets


def evidence_pack(dsn, item):
    """rag-mcp R02 explore 모드와 같은 조건: 같은 기종, 단어 하나라도 맞으면 많이 맞는 순."""
    words = re.findall(r"[A-Za-z0-9]+", item["query"])
    ts = f"websearch_to_tsquery('english', {sql_text(' or '.join(words))})"
    rows = psql(dsn, f"""
        SELECT case_id, reported_jasc, left(narrative_raw, 400), round(ts_rank_cd(search_document, {ts})::numeric, 4)
        FROM sdr_cases WHERE model_key = {sql_text(item['aircraft_model'])} AND search_eligible AND search_document @@ {ts}
        ORDER BY ts_rank_cd(search_document, {ts}) DESC, case_id LIMIT {PACK_SIZE}""")
    return [{"case_id": c, "reported_jasc": j, "narrative": n, "rank": float(s)} for c, j, n, s in rows]


TRANSLATE_PROMPT = """You rewrite FAA Service Difficulty Report narratives into the symptom a reporter would submit in a maintenance request.
For each item return:
- "en": the reported symptom/finding only, in plain English (1-2 sentences). Drop corrective actions, part numbers,
  manual/AMM/TSM/IPC references and ATA chapter numbers. Keep displayed messages (ECAM, cautions) and component names.
- "ko": the same symptom in natural Korean sentences, as a Korean line mechanic or crew member would write it
  (e.g. "비상구 표시등이 켜짐", "2번 엔진 오일 온도가 높게 표시됨"). Translate component and condition words into
  Korean (emergency light → 비상등, landing gear door → 착륙장치 도어, missing → 없어짐, inoperative → 작동하지 않음).
  Keep in English only the exact displayed message text (e.g. ECAM LANDING GEAR DOORS NOT CLOSED) and standard
  abbreviations such as ECAM, APU, PACK, ENG 1, FADEC, seat/row numbers.
Never add facts that are not in the narrative. Answer as JSON: {"items": [{"id": ..., "en": ..., "ko": ...}, ...]}"""


def translate(items, key, batch=20):
    for start in range(0, len(items), batch):
        chunk = items[start:start + batch]
        body = {
            "model": TRANSLATE_MODEL,
            "response_format": {"type": "json_object"},
            "messages": [
                {"role": "system", "content": TRANSLATE_PROMPT},
                {"role": "user", "content": json.dumps([{"id": i["id"], "narrative": i["narrative_raw"]} for i in chunk], ensure_ascii=False)},
            ],
        }
        req = urllib.request.Request("https://api.openai.com/v1/chat/completions", data=json.dumps(body).encode(),
                                     headers={"Authorization": f"Bearer {key}", "Content-Type": "application/json"})
        for attempt in range(3):
            try:
                with urllib.request.urlopen(req, timeout=180) as r:
                    content = json.loads(r.read())["choices"][0]["message"]["content"]
                got = {x["id"]: x for x in json.loads(content)["items"]}
                if all(i["id"] in got for i in chunk):
                    break
            except Exception as e:  # noqa: BLE001 - 재시도 후 실패 처리
                print(f"  번역 재시도 {attempt + 1}: {type(e).__name__}", flush=True)
                time.sleep(3)
        else:
            sys.exit("번역 실패")
        for i in chunk:
            i["symptom_en"], i["symptom_ko"] = got[i["id"]]["en"].strip(), got[i["id"]]["ko"].strip()
        print(f"  번역 {min(start + batch, len(items))}/{len(items)}", flush=True)


def write_jsonl(path, rows):
    with open(path, "w", encoding="utf-8") as f:
        for row in rows:
            f.write(json.dumps(row, ensure_ascii=False) + "\n")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--dsn", default=os.environ.get("EVAL_DSN", "postgresql://mro:mro@127.0.0.1:5432/mro"))
    ap.add_argument("--cache", type=Path, default=ROOT / "data" / "eval" / ".faa-cache", help="FAA 원본 CSV 보관 위치 (git 제외)")
    ap.add_argument("--no-translate", action="store_true")
    args = ap.parse_args()

    ata_to_dept, names = departments()
    files = download(args.cache)
    pool, dropped = build_pool(files, args.dsn, ata_to_dept)
    print(f"후보 {len(pool)}건, 제외 {dropped}", flush=True)
    sets = sample(pool, names)

    for item in sets["department"]:
        item["evidence"] = evidence_pack(args.dsn, item)
    llm_items = sets["triage"] + sets["department"]
    if args.no_translate:
        for i in llm_items:
            i["symptom_en"] = i["symptom_ko"] = None
    else:
        key = os.environ.get("LLM_API_KEY") or os.environ.get("OPENAI_API_KEY")
        if not key:
            sys.exit("LLM_API_KEY 또는 OPENAI_API_KEY가 필요합니다 (--no-translate로 건너뛸 수 있음)")
        translate(llm_items, key)

    OUT.mkdir(parents=True, exist_ok=True)
    label = lambda i: {"jasc": i["jasc"], "jasc_title": i["jasc_title"], "ata": i["ata"], "department": i["department"]}
    write_jsonl(OUT / "search.jsonl", [
        {"id": i["id"], "aircraft_model": i["aircraft_model"], "query": i["query"], "expected": label(i), "source": i["source"]}
        for i in sets["search"]])
    write_jsonl(OUT / "triage.jsonl", [
        {"id": i["id"], "aircraft_model": i["aircraft_model"], "symptom_en": i["symptom_en"], "symptom_ko": i["symptom_ko"],
         "expected": label(i), "narrative_raw": i["narrative_raw"], "source": i["source"]}
        for i in sets["triage"]])
    write_jsonl(OUT / "department.jsonl", [
        {"id": i["id"], "aircraft_model": i["aircraft_model"], "symptom_en": i["symptom_en"], "symptom_ko": i["symptom_ko"],
         "evidence": i["evidence"], "expected": label(i), "narrative_raw": i["narrative_raw"], "source": i["source"]}
        for i in sets["department"]])
    jasc_rows = psql(args.dsn, "SELECT code, title FROM jasc_codes ORDER BY code")
    write_jsonl(OUT / "jasc_to_department.jsonl", [
        {"jasc": c, "jasc_title": t, "ata": c[:2], "expected_department": ata_to_dept.get(c[:2])} for c, t in jasc_rows])

    manifest = {
        "built_on": date.today().isoformat(), "seed": SEED,
        "source": [{"file": p.name, "url": SOURCE_URL.format(year=y), "sha256": sha256(p)} for y, p in files.items()],
        "filters": {
            "aircraft": "AIRBUS A319/A320/A321, model_key가 corpus에 있는 기종",
            "label": "JASC가 jasc_codes에 있고 ATA(앞 두 자리)가 부서에 매핑되는 것",
            "leakage": "corpus와 OperatorControlNumber 또는 원문이 같은 것 제외, 원문 중복 제외",
            "query": "매뉴얼·장 참조(AMM 32-41-00, ATA 32 등)를 지운 원문, 40~600자",
        },
        "pool_size": len(pool), "dropped": dropped, "per_department": PER_DEPT,
        "evidence_pack": f"corpus에서 같은 기종·단어 OR 전문검색(rag-mcp R02 explore와 같은 조건) 상위 {PACK_SIZE}건",
        "translation": None if args.no_translate else {"model": TRANSLATE_MODEL, "fields": ["symptom_en", "symptom_ko"]},
        "counts": {name: len(items) for name, items in sets.items()} | {"jasc_to_department": len(jasc_rows)},
    }
    (OUT / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(manifest["counts"], ensure_ascii=False))


if __name__ == "__main__":
    main()
