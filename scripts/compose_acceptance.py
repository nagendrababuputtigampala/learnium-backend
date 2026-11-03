#!/usr/bin/env python3
"""Build an isolated stack, exercise real HTTP/SMTP, and always remove its resources."""
import os
from pathlib import Path
import secrets
import subprocess
import time
import urllib.request
from m2_acceptance import run as m2_acceptance
from m4_review_acceptance import run as m4_acceptance
from m4_publish_acceptance import run as publish_acceptance

ROOT = Path(__file__).resolve().parents[1]


def main():
    env = dict(os.environ, ACCEPTANCE_PROVISIONING_KEY=secrets.token_urlsafe(32))
    command = ["docker", "compose", "-p", "learnium-acceptance-" + secrets.token_hex(4),
               "-f", str(ROOT / "compose.acceptance.yml")]

    def compose(*args, capture=False):
        return subprocess.run(command + list(args), cwd=ROOT, env=env, check=True,
                              text=True, stdout=subprocess.PIPE if capture else None)

    def endpoint(service, port):
        address = compose("port", service, str(port), capture=True).stdout.strip()
        return "http://" + address

    try:
        compose("up", "--build", "-d")
        backend = endpoint("backend", 8080)
        frontend = endpoint("frontend", 80)
        mailpit = endpoint("mailpit", 8025)
        for attempt in range(120):
            try:
                with urllib.request.urlopen(backend + "/actuator/health", timeout=3) as response:
                    if response.status == 200:
                        break
            except (OSError, TimeoutError):
                pass
            time.sleep(1)
        else:
            raise RuntimeError("Acceptance backend did not become healthy")
        # Exercise nginx's API proxy and SPA deep-link fallback, not only the backend port.
        for path in ["/login", "/join", "/api/v1/system/ping"]:
            with urllib.request.urlopen(frontend + path, timeout=10) as response:
                if response.status != 200:
                    raise RuntimeError("Frontend route/proxy check failed")
        # Share synthetic credentials only in process memory; never print or persist them.
        os.environ["ACCEPTANCE_PROVISIONING_KEY"] = env["ACCEPTANCE_PROVISIONING_KEY"]
        sessions = m2_acceptance(frontend + "/api/v1", mailpit)
        m4_acceptance(frontend + "/api/v1", sessions)
        publish_acceptance(frontend + "/api/v1", sessions)
        print("PASS: Compose build, fresh PostgreSQL migrations, nginx routes/proxy, M2 HTTP/SMTP and M4 review/bank/publish")
    finally:
        compose("down", "--volumes", "--remove-orphans")


if __name__ == "__main__":
    main()
