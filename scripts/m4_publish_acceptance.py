#!/usr/bin/env python3
"""Publishing acceptance through real authenticated HTTP; no student-player/marking trigger."""
import datetime
import json
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
            assert response.status == expected, f"Publish acceptance {path}: HTTP {response.status}"
            return json.loads(body) if body else None

    admin = request("/auth/login", data={"email": sessions["adminEmail"], "password": sessions["password"]})["accessToken"]
    login = request("/auth/login", data={"email": sessions["teacherEmail"], "password": sessions["teacherPassword"]})
    teacher = login["accessToken"]
    bank = request("/questions?status=APPROVED", teacher)["content"]
    assert len(bank) == 2
    pairing = bank[0]["gradeSubjectId"]
    batch = request("/admin/batches", admin, {"name": "Publish acceptance batch", "gradeSubjectId": pairing}, expected=201)
    request("/admin/batches/" + batch["batchId"] + "/teacher", admin, {"userId": login["user"]["userId"]}, method="PUT")
    roster = "/batches/" + batch["batchId"] + "/students"
    request(roster, teacher, {"fullName": "Publish Student"}, expected=201)
    definition = {"name": "Fractions check", "gradeSubjectId": pairing, "kind": "SCORED_TEST",
                  "items": [{"questionId": row["questionId"], "marks": marks} for row, marks in zip(bank, [2.5, .75])]}
    saved = request("/tests", teacher, definition, expected=201)
    assert saved["totalMarks"] == 3.25
    request("/tests/" + saved["id"], sessions["otherAdminToken"], expected=404)
    closes = (datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(hours=1)).isoformat()
    publish = {"batchId": batch["batchId"], "closesAt": closes, "maxAttempts": 2,
               "answerReleasePolicy": "ON_SUBMIT", "integrityEnabled": True, "cameraMode": "ON",
               "items": list(reversed(definition["items"]))}
    first = request("/tests/" + saved["id"] + "/publish", teacher, publish, expected=201)
    assert len(first["students"]) == 1 and first["maxAttempts"] == 2 and len(first["joinCode"]) == 8
    assert first["items"][0]["questionId"] == definition["items"][1]["questionId"]
    revised = dict(definition, name="Later definition", items=[dict(definition["items"][0], marks=.75)])
    request("/tests/" + saved["id"], teacher, revised, method="PUT")
    second = request("/tests/" + saved["id"] + "/publish", teacher,
                     {"batchId": batch["batchId"], "closesAt": closes}, expected=201)
    assert second["items"][0]["marks"] == .75 and second["maxAttempts"] == 1
    assert request("/instances/" + first["id"], teacher) == first
    request("/tests/" + saved["id"], teacher, method="DELETE", expected=409)
    late = request(roster, teacher, {"fullName": "Late Student"}, expected=201)
    assert len(request("/instances/" + first["id"], teacher)["students"]) == 1
    for _ in range(2):
        added = request("/instances/" + first["id"] + "/students", teacher, {"userId": late["userId"]})
        assert len(added["students"]) == 2
    new_code = request("/instances/" + first["id"] + "/regenerate-code", teacher, {})["joinCode"]
    assert new_code != first["joinCode"]
    closed = request("/instances/" + first["id"] + "/close", teacher, {})
    assert closed["status"] == "CLOSED" and closed["closedAt"]
    request("/instances/" + first["id"] + "/students", teacher, {"userId": late["userId"]}, expected=400)
    empty = request("/admin/batches", admin, {"name": "Empty publish batch", "gradeSubjectId": pairing}, expected=201)
    request("/admin/batches/" + empty["batchId"] + "/teacher", admin, {"userId": login["user"]["userId"]}, method="PUT")
    before = len(request("/instances", teacher))
    request("/tests/" + saved["id"] + "/publish", teacher, {"batchId": empty["batchId"], "closesAt": closes}, expected=400)
    assert len(request("/instances", teacher)) == before
    print("PASS: M4 owned definitions, decimal marks, per-publish review, frozen content/roster, "
          "republish, defaults, late-add idempotence, code rotation, close and empty-roster rollback")
