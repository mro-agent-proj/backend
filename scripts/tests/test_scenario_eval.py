"""Offline scoring regressions; no Backend, database or model calls."""
import csv
import hashlib
import json
from pathlib import Path
import sys
import unittest

SCRIPTS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPTS))
from scenario_eval import submit_body, summarize, summarize_by_split


def row(expected="ASSIGNED", status="ASSIGNED", department="AVI", correct=True, trap=False):
    return dict(expected_status=expected, status=status, department=department,
                expected_department="AVI" if expected == "ASSIGNED" else None,
                acceptable=["AVI"] if expected == "ASSIGNED" else [], correct=correct,
                trap_cited=["MH-TRAP"] if trap else [], split="test",
                path="DIRECT", expected_path="DIRECT", timed_out=False)


class ScoringTests(unittest.TestCase):
    def test_precision_includes_expected_human_assignments_and_separates_traps(self):
        cases = [row(), row(department="ENG", correct=False), row(correct=False, trap=True),
                 row(department="ENG", correct=False, trap=True),
                 row(expected="HUMAN_REVIEW", correct=False),
                 row(expected="HUMAN_REVIEW", status="HUMAN_REVIEW", department=None),
                 row(status="HUMAN_REVIEW", department=None, correct=False),
                 dict(expected_status="ASSIGNED", expected_department="AVI", correct=False,
                      error="submission failed", split="dev")]
        result = summarize_by_split(cases, [])
        self.assertEqual(result["자동 배정 정밀도 (자동 배정 중 정답)"], "1/5 (20.0%)")
        self.assertEqual(result["전체 평가 통과율"], "2/8 (25.0%)")
        self.assertEqual(result["오배정률 (틀린 부서로 자동 배정)"], "2/6 (33.3%)")
        self.assertEqual(result["자동 배정 평가 실패 (부서·근거·기대 상태)"], 4)
        self.assertEqual(result["부서는 일치하나 금지 이력 인용"], 1)
        self.assertEqual(result["사람 검토 기대인데 자동 배정"], 1)
        self.assertEqual(result["사람 검토 재현율 (사람 검토 기대 문항 중)"], "1/2 (50.0%)")
        self.assertEqual(result["시간 초과·실패"], 1)
        self.assertEqual(result["split별"]["test"]["자동 배정 정밀도 (자동 배정 중 정답)"], "1/5 (20.0%)")

    def test_empty_and_no_assignment(self):
        self.assertEqual(summarize([], [])["자동 배정 정밀도 (자동 배정 중 정답)"], "0/0")
        result = summarize([row(status="HUMAN_REVIEW", department=None, correct=False)], [])
        self.assertEqual(result["자동 배정 정밀도 (자동 배정 중 정답)"], "0/0")

    def test_labels_are_not_submitted_to_model(self):
        case = dict(tail_number="HL-TEST", airline_code="KAL", aircraft_model="A320-TEST",
                    symptom_text="test symptom", expected={"department": "SECRET"},
                    source={"label": "SECRET"}, label_check={"human": "decided"})
        self.assertEqual(set(submit_body(case)),
                         {"tail_number", "airline_code", "aircraft_model", "symptom_text"})

    def test_manifest_identifies_exact_dataset_and_seed(self):
        root = SCRIPTS.parent
        service = root / "data/eval/service"
        manifest = json.loads((service / "manifest.json").read_text())
        cases = service / "scenarios.jsonl"
        seed = root / "data/seed" / manifest["history_file"]
        self.assertEqual(hashlib.sha256(cases.read_bytes()).hexdigest(), manifest["dataset_sha256"])
        self.assertEqual(hashlib.sha256(seed.read_bytes()).hexdigest(), manifest["history_sha256"])
        self.assertEqual(len(cases.read_text().splitlines()), manifest["scenario_count"])
        with seed.open(encoding="utf-8-sig") as handle:
            self.assertEqual(len(list(csv.DictReader(handle))), manifest["history_count"])


if __name__ == "__main__":
    unittest.main()
