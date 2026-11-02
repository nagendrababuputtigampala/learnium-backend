#!/usr/bin/env python3
"""Real HTTP review/bank acceptance using synthetic sessions and the local model fixture."""
import json
import time
import urllib.error
import urllib.request


def run(base, sessions):
    def request(path, token=None, data=None, method=None, expected=200):
        headers = {"Content-Type": "application/json"}
        if token:
            headers["Authorization"] = "Bearer " + token
        req = urllib.request.Request(base + path, headers=headers, method=method,
                                     data=None if data is None else json.dumps(data).encode())
        try:
            response = urllib.request.urlopen(req, timeout=20)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            body = response.read()
            assert response.status == expected, f"Review acceptance {path}: HTTP {response.status}"
            return json.loads(body) if body else None

    admin = request("/auth/login", data={"email": sessions["adminEmail"], "password": sessions["password"]})["accessToken"]
    teacher_login = request("/auth/login", data={"email": sessions["teacherEmail"], "password": sessions["teacherPassword"]})
    teacher = teacher_login["accessToken"]
    other = sessions["otherAdminToken"]
    pairings = request("/master/grade-subjects", admin)
    pairing = next(p for p in pairings if p["gradeCode"] == "G8" and p["subjectName"] == "Mathematics")
    topic = request("/master/grade-subjects/" + pairing["gradeSubjectId"] + "/topics", admin,
                    {"name": "Acceptance Fractions"}, expected=201)
    material = request("/materials/text", admin,
                       {"title": "Review acceptance chapter", "gradeSubjectId": pairing["gradeSubjectId"],
                        "text": ("Fractions represent parts of a whole. One half is two quarters. "
                                 "Equivalent fractions name the same value. ") * 20}, expected=201)
    job = request("/materials/" + material["materialId"] + "/generate", admin,
                  {"topicId": topic["topicId"], "counts": {"MCQ": 1, "WRITTEN": 1}}, expected=202)
    for _ in range(100):
        detail = request("/generation-jobs/" + job["id"], admin)
        if detail["status"] in ("SUCCEEDED", "FAILED"):
            break
        time.sleep(.2)
    assert detail["status"] == "SUCCEEDED", "Recorded model fixture must generate two valid questions"
    drafts = request("/questions?status=DRAFT&materialId=" + material["materialId"], admin)["content"]
    assert len(drafts) == 2
    assert request("/questions?status=DRAFT", teacher)["totalElements"] == 0
    mcq = next(q for q in drafts if q["type"] == "MCQ")
    original_id = mcq["questionId"]
    mcq = request("/questions/" + original_id, admin)
    request("/questions/" + original_id, teacher, expected=404)
    content = dict(mcq["payload"], correctIndex=0, stem="Which fraction is one half?")
    edit = {"revision": mcq["revision"], "topicId": mcq["topicId"], "difficulty": 3, "content": content}
    saved = request("/questions/" + original_id, admin, edit, method="PUT")
    stale = request("/questions/" + original_id, admin, edit, method="PUT", expected=409)
    assert stale["code"] == "LRN-QST-004"
    approved = request("/questions/" + original_id + "/approve", admin, {"revision": saved["revision"]})
    assert approved["status"] == "APPROVED" and approved["approvedAt"] and approved["approvedBy"]
    written = next(q for q in drafts if q["type"] == "WRITTEN")
    written = request("/questions/" + written["questionId"], admin)
    request("/questions/" + written["questionId"] + "/approve", admin, {"revision": written["revision"]})
    request("/questions/" + original_id, teacher, edit, method="PUT", expected=409)
    request("/materials/" + material["materialId"], teacher, expected=404)
    bank = request("/questions?status=APPROVED", teacher)
    assert bank["totalElements"] == 2
    assert all(q["materialId"] is None and q["materialTitle"] is None for q in bank["content"])
    fork = request("/questions/" + original_id + "/forks", teacher, {}, expected=201)
    assert fork["sourceQuestionId"] == original_id and fork["materialId"] is None
    assert fork["createdBy"] == teacher_login["user"]["userId"] and fork["approvedAt"] is None
    invalid = request("/questions/" + fork["questionId"], teacher,
                      dict(edit, revision=fork["revision"], content={}), method="PUT", expected=422)
    assert "content.stem" in invalid["fieldErrors"]
    rejected = request("/questions/" + fork["questionId"] + "/reject", teacher, {"revision": fork["revision"]})
    assert rejected["status"] == "REJECTED"
    assert request("/questions?status=REJECTED", teacher)["totalElements"] == 1
    assert request("/questions?status=APPROVED", other)["totalElements"] == 0
    request("/questions/" + original_id, other, expected=404)
    print("PASS: M4 generation-to-review, edit/revision, approval, shared bank, private PDF, "
          "fork lineage, validation, rejection and cross-centre isolation")
