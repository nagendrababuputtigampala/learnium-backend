#!/usr/bin/env python3
"""Synthetic M2 HTTP/SMTP checks. Run through compose_acceptance.py against disposable data."""
import argparse
import html
import json
import os
import re
import secrets
import time
import urllib.error
import urllib.parse
import urllib.request


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--mailpit-url", required=True)
    args = parser.parse_args()
    return run(args.base_url, args.mailpit_url)


def run(base_url, mailpit_url):
    args = argparse.Namespace(base_url=base_url, mailpit_url=mailpit_url)
    key = os.environ["ACCEPTANCE_PROVISIONING_KEY"]
    suffix = secrets.token_hex(5)
    password = "Acceptance@123-" + suffix

    def request(path, data=None, token=None, method=None, expected=200):
        headers = {"Content-Type": "application/json"}
        if token:
            headers["Authorization"] = "Bearer " + token
        if path == "/platform/centres":
            headers["X-Provisioning-Key"] = key
        req = urllib.request.Request(args.base_url.rstrip("/") + path,
                                     data=None if data is None else json.dumps(data).encode(),
                                     headers=headers, method=method)
        try:
            response = urllib.request.urlopen(req, timeout=15)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            body = response.read()
            if response.status != expected:
                code = json.loads(body).get("code", "unknown") if body else "empty"
                raise RuntimeError(f"{method or ('POST' if data is not None else 'GET')} {path}: "
                                   f"expected {expected}, got {response.status}, code={code}")
            return json.loads(body) if body else None

    def mail_token(email, exclude=None):
        query = urllib.parse.urlencode({"query": "to:" + email})
        for _ in range(150):
            with urllib.request.urlopen(args.mailpit_url + "/api/v1/search?" + query, timeout=10) as r:
                messages = json.load(r)["messages"]
            for message in messages:
                with urllib.request.urlopen(args.mailpit_url + "/api/v1/message/" + message["ID"], timeout=10) as r:
                    message_body = json.load(r)
                text = html.unescape(message_body.get("HTML", "") + " " + message_body.get("Text", ""))
                match = re.search(r"[?&]token=([A-Za-z0-9_-]+)", text)
                if match and match.group(1) != exclude:
                    return match.group(1)
            time.sleep(.2)
        raise RuntimeError("Expected synthetic invitation/reset email was not delivered")

    def onboard(label):
        email = label.lower().replace(" ", "-") + "-" + suffix + "@acceptance.invalid"
        centre = request("/platform/centres", {"name": label, "adminEmail": email,
                         "adminDisplayName": label}, expected=201)
        invite = mail_token(email)
        request("/auth/accept-invite", {"token": invite, "newPassword": password}, expected=204)
        request("/auth/accept-invite", {"token": invite, "newPassword": password}, expected=400)
        return centre, email, request("/auth/login", {"email": email, "password": password})

    centre, email, login = onboard("Centre A")
    admin = login["accessToken"]
    assert request("/batches", token=admin) == [], "Provisioned centre must be empty"
    request("/auth/login", {"email": email, "password": "wrong-password"}, expected=401)
    request("/centre", expected=401)
    rotated = request("/auth/refresh", {"refreshToken": login["refreshToken"]})
    admin = rotated["accessToken"]

    teacher_email = "teacher-" + suffix + "@acceptance.invalid"
    teacher = request("/admin/teachers", {"email": teacher_email, "displayName": "Acceptance Teacher"},
                      token=admin, expected=201)
    invitation = mail_token(teacher_email)
    request("/auth/accept-invite", {"token": invitation, "newPassword": password}, expected=204)
    teacher_login = request("/auth/login", {"email": teacher_email, "password": password})
    teacher_token = teacher_login["accessToken"]
    request("/admin/teachers", token=teacher_token, expected=403)
    subjects = request("/master/grade-subjects", token=admin)
    batch = request("/admin/batches", {"name": "Acceptance Batch", "gradeSubjectId": subjects[0]["gradeSubjectId"]},
                    token=admin, expected=201)
    batch_id = batch["batchId"]
    assert request("/batches", token=teacher_token) == []
    request("/admin/batches/" + batch_id + "/teacher", {"userId": teacher["userId"]}, token=admin, method="PUT")
    assert any(row["batchId"] == batch_id for row in request("/batches", token=teacher_token))
    roster = "/batches/" + batch_id + "/students"
    student = request(roster, {"fullName": "Acceptance Student"}, token=teacher_token, expected=201)
    assert all("pin" not in row for row in request(roster, token=teacher_token))
    bulk = request(roster + "/bulk", {"fullNames": ["Bulk One", "Bulk Two", "Bulk Three"]},
                   token=teacher_token, expected=201)
    assert len(bulk) == 3 and all(len(row["pin"]) == 4 for row in bulk)
    session = {"batchId": batch_id, "fullName": student["fullName"], "pin": student["pin"]}
    request("/auth/student-session", session)
    wrong_pin = "0000" if student["pin"] != "0000" else "9999"
    for _ in range(5):
        request("/auth/student-session", dict(session, pin=wrong_pin), expected=401)
    request("/auth/student-session", session, expected=403)
    new_pin = request(roster + "/" + student["membershipId"] + "/reset-pin", {}, token=teacher_token)["pin"]
    request("/auth/student-session", dict(session, pin=new_pin))
    request(roster + "/" + student["membershipId"], token=teacher_token, method="DELETE", expected=204)
    request("/auth/student-session", dict(session, pin=new_pin), expected=401)
    request("/centre", {"name": "Acceptance renamed", "logoUrl": "https://example.invalid/logo.png",
                        "contactEmail": email}, token=admin, method="PUT")
    assert request("/centre", token=admin)["name"] == "Acceptance renamed"

    _, _, other_login = onboard("Centre B")
    other = other_login["accessToken"]
    assert request("/batches", token=other) == []
    request(roster, token=other, expected=404)

    request("/auth/forgot-password", {"email": teacher_email}, expected=202)
    reset = mail_token(teacher_email, exclude=invitation)
    new_password = password + "-changed"
    request("/auth/reset-password", {"token": reset, "newPassword": new_password}, expected=204)
    request("/auth/reset-password", {"token": reset, "newPassword": new_password}, expected=400)
    request("/auth/login", {"email": teacher_email, "password": password}, expected=401)
    request("/auth/login", {"email": teacher_email, "password": new_password})
    request("/auth/refresh", {"refreshToken": teacher_login["refreshToken"]}, expected=401)
    print("PASS: onboarding, SMTP links, invite reuse, login/refresh, roles, batches, roster/PINs, "
          "lockout/reset/deactivation, centre settings, cross-centre isolation, password reset")

    return {"adminEmail": email, "password": password, "teacherEmail": teacher_email,
            "teacherPassword": new_password, "otherAdminToken": other}


if __name__ == "__main__":
    main()
