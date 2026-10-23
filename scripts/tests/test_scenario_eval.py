"""Offline scoring regressions; no Backend, database or model calls."""
import csv
import hashlib
import json
from pathlib import Path
import sys
import unittest

SCRIPTS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPTS))
from scenario_eval import run_scenario, submit_body, summarize, summarize_by_split


def row(expected="ASSIGNED", status="ASSIGNED", department="AVI", correct=True, trap=False):
    return dict(expected_status=expected, status=status, department=department,
                expected_department="AVI" if expected == "ASSIGNED" else None,
                acceptable=["AVI"] if expected == "ASSIGNED" else [], correct=correct,
                trap_cited=["MH-TRAP"] if trap else [], split="test",
                path="DIRECT", expected_path="DIRECT", timed_out=False)


class ScoringTests(unittest.TestCase):
    def run_with_trace(self, trace_status, trace, status="ASSIGNED"):
        class FakeApi:
            def token(self, email):
                return "offline-test"

            def call(self, method, path, body=None, token=None):
                if method == "POST":
                    return 201, {"id": "test-request", "status": status, "assigned_department": "AVI"}
                if isinstance(trace, Exception):
                    raise trace
                return trace_status, trace

        case = dict(id="TEST", tail_number="HL-TEST", airline_code="KAL", aircraft_model="A320-TEST",
                    symptom_text="test symptom", expected=dict(status=status, department="AVI", path="DIRECT",
                    acceptable_departments=["AVI"], must_not_cite=["MH-TRAP"]))
        return run_scenario(FakeApi(), case, 1)

    def test_trace_failures_cannot_pass_and_remain_in_assignment_denominator(self):
        for status, trace in [
            (500, {"error": "unavailable"}), (200, None), (200, {}),
            (200, {"history": [], "assessments": []}),
            (200, {"history": [], "assessments": [{"stage": "FINAL", "output": {}}]}),
            (200, {"history": [], "assessments": [{"stage": "FINAL", "output": {"evidence": [{}]}}]}),
            (200, OSError("connection failed")), (200, ValueError("invalid JSON")),
        ]:
            with self.subTest(status=status, trace=trace):
                result = self.run_with_trace(status, trace)
                self.assertFalse(result["correct"])
                self.assertTrue(result["evaluation_error"])
                summary = summarize([result], [])
                self.assertEqual(summary["자동 배정 정밀도 (자동 배정 중 정답)"], "0/1 (0.0%)")
                self.assertEqual(summary["추적 조회·근거 확인 오류"], 1)
                self.assertEqual(summary["시간 초과·실패"], 1)
        self.assertFalse(self.run_with_trace(500, {}, status="HUMAN_REVIEW")["correct"])

    def test_valid_final_checks_forbidden_history(self):
        for evidence, correct in [([], True), ([{"ref_id": "MH-OK"}], True), ([{"ref_id": "MH-TRAP"}], False)]:
            with self.subTest(evidence=evidence):
                trace = {"history": [{"to_status": "ASSIGNED"}], "assessments": [
                    {"stage": "FINAL", "output": {"evidence": evidence}}]}
                result = self.run_with_trace(200, trace)
                self.assertEqual(result["correct"], correct)
                self.assertNotIn("evaluation_error", result)

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
