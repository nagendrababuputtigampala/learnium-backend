import copy
import unittest
from generation_quality import score


class QualityGateTest(unittest.TestCase):
    def report(self):
        return {"chapters": [{"contentHash": str(i), "jobId": str(i), "provider": "gemini",
                              "model": "release-model", "reviewer": "Teacher",
                              "requestedCounts": {"MCQ": 5},
                              "questions": [{"questionId": str(q), "approved": True} for q in range(4)]}
                             for i in range(3)]}

    def test_counts_missing_outputs_against_the_threshold(self):
        report = self.report()
        self.assertTrue(score(report)["passed"])
        report["chapters"][0]["questions"][0]["approved"] = False
        self.assertFalse(score(report)["passed"])

    def test_refuses_unreviewed_or_duplicate_chapters(self):
        report = self.report()
        report["chapters"][0]["questions"][0]["approved"] = None
        with self.assertRaises(ValueError):
            score(report)
        report = self.report()
        report["chapters"][1] = copy.deepcopy(report["chapters"][0])
        with self.assertRaises(ValueError):
            score(report)

    def test_refuses_mixed_models_or_missing_reviewer(self):
        report = self.report()
        report["chapters"][0]["model"] = "different-model"
        with self.assertRaises(ValueError):
            score(report)
        report = self.report()
        report["chapters"][0]["reviewer"] = ""
        with self.assertRaises(ValueError):
            score(report)


if __name__ == "__main__":
    unittest.main()
