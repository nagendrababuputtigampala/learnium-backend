#!/usr/bin/env python3
"""Recorded local-only Ollama response for the disposable acceptance stack, never production."""
from http.server import BaseHTTPRequestHandler, HTTPServer
import json

QUESTIONS = [
    {"type": "MCQ", "stem": "Which fraction represents one half?",
     "options": [{"text": "1/2"}, {"text": "1/3"}, {"text": "1/4"}, {"text": "1/5"}], "correctIndex": 0},
    {"type": "WRITTEN", "stem": "Explain why one half equals two quarters.",
     "modelAnswer": "Dividing both parts of two quarters by two gives one half.",
     "rubric": [{"criterion": "Identifies two quarters", "maxScore": 1},
                {"criterion": "Divides numerator and denominator by two", "maxScore": 1},
                {"criterion": "Obtains one half", "maxScore": 1}]},
]


class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        self.rfile.read(int(self.headers.get("Content-Length", 0)))
        body = json.dumps({"message": {"role": "assistant", "content": json.dumps({"questions": QUESTIONS})},
                           "done": True, "done_reason": "stop", "prompt_eval_count": 100, "eval_count": 100}).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *args):
        pass  # Do not log source prompts or request bodies.


if __name__ == "__main__":
    HTTPServer(("0.0.0.0", 11434), Handler).serve_forever()
