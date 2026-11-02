#!/usr/bin/env python3
"""Collect three completed chapter jobs for teacher review; score only explicit human decisions."""
import argparse
import json
import os
from pathlib import Path
import urllib.request


def score(report):
    chapters = report["chapters"]
    if len(chapters) != 3 or len({c["contentHash"] for c in chapters}) != 3:
        raise ValueError("The quality gate requires three distinct chapter content hashes")
    if len({(c["provider"], c["model"]) for c in chapters}) != 1:
        raise ValueError("All chapters must use the same release provider/model")
    results = []
    for chapter in chapters:
        questions = chapter["questions"]
        requested = sum(chapter["requestedCounts"].values())
        if requested <= 0 or len(questions) > requested:
            raise ValueError("Invalid requested/generated question counts")
        if len({q["questionId"] for q in questions}) != len(questions):
            raise ValueError("Duplicate question IDs in review")
        if any(type(q.get("approved")) is not bool for q in questions):
            raise ValueError("Every generated question needs a teacher's true/false approval")
        if not chapter.get("reviewer", "").strip():
            raise ValueError("Record the teacher reviewer for every chapter")
        approved = sum(q["approved"] for q in questions)
        # Conservative denominator: missing or structurally dropped outputs cannot pass the gate.
        rate = approved / requested
        results.append({"jobId": chapter["jobId"], "approved": approved,
                        "requested": requested, "approvalRate": rate, "passed": rate >= .8})
    return {"passed": all(row["passed"] for row in results), "chapters": results}


def collect(base_url, job_ids):
    token = os.environ["LEARNIUM_ACCESS_TOKEN"]

    def get(path):
        request = urllib.request.Request(base_url.rstrip("/") + path,
                                         headers={"Authorization": "Bearer " + token})
        with urllib.request.urlopen(request, timeout=20) as response:
            return json.load(response)

    chapters = []
    for job_id in job_ids:
        job = get("/generation-jobs/" + job_id)
        if job["status"] not in ("SUCCEEDED", "FAILED"):
            raise ValueError("Wait for generation jobs to finish before collecting evidence")
        questions = []
        page = 0
        while True:
            result = get("/materials/" + job["materialId"] + "/questions?size=100&page=" + str(page))
            questions.extend(dict(q, approved=None, reviewNotes="") for q in result["content"]
                             if q["generationJobId"] == job_id)
            page += 1
            if page >= result["totalPages"]:
                break
        chapters.append({"jobId": job_id, "materialId": job["materialId"],
                         "contentHash": job["contentHash"], "provider": job["provider"],
                         "model": job["model"], "promptVersion": job["promptVersion"],
                         "payloadSchemaVersion": job["payloadSchemaVersion"],
                         "status": job["status"], "failureKind": job["failureKind"],
                         "requestedCounts": job["requestedCounts"], "resultCounts": job["resultCounts"],
                         "reviewer": "", "questions": questions})
    if len({c["contentHash"] for c in chapters}) != 3:
        raise ValueError("Choose jobs from three distinct real chapters")
    if len({(c["provider"], c["model"]) for c in chapters}) != 1:
        raise ValueError("Choose three jobs from the same release provider/model")
    return {"schemaVersion": 1, "threshold": .8, "denominator": "requested questions per chapter",
            "chapters": chapters}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    collector = commands.add_parser("collect")
    collector.add_argument("--base-url", default="http://localhost:8080/api/v1")
    collector.add_argument("--jobs", nargs=3, required=True)
    collector.add_argument("--output", type=Path, required=True)
    scorer = commands.add_parser("score")
    scorer.add_argument("report", type=Path)
    args = parser.parse_args()
    try:
        if args.command == "collect":
            report = collect(args.base_url, args.jobs)
            # Review evidence must never be silently overwritten.
            with args.output.open("x") as output:
                json.dump(report, output, indent=2)
            print("Collected three chapters. Teacher approval is still required; the gate is pending.")
        else:
            result = score(json.loads(args.report.read_text()))
            print(json.dumps(result, indent=2))
            return 0 if result["passed"] else 1
    except (ValueError, KeyError, OSError) as error:
        print("Quality gate incomplete: " + str(error))
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
