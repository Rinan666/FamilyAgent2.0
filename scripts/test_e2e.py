#!/usr/bin/env python3
"""End-to-end smoke test for the supported FamilyAgent tutor flow."""

import json
import sys
import urllib.error
import urllib.request

FRONTEND = "http://localhost:3000"
BACKEND = "http://localhost:8180"
AI_SERVICE = "http://localhost:8090"
TOKEN = None


def api(path, data=None, method="GET", use_backend=False):
    base = BACKEND if use_backend else FRONTEND
    url = f"{base}{path}"

    body = json.dumps(data, ensure_ascii=False).encode("utf-8") if data else None
    req = urllib.request.Request(url, data=body)
    req.add_header("Content-Type", "application/json;charset=UTF-8")
    if TOKEN:
        req.add_header("Authorization", TOKEN)
    if method != "POST":
        req.method = method

    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        body = exc.read().decode("utf-8", errors="replace")
        try:
            return exc.code, json.loads(body)
        except Exception:
            return exc.code, {"error": body[:200]}


def ai_stream(path, data=None):
    url = f"{AI_SERVICE}{path}"
    body = json.dumps(data, ensure_ascii=False).encode("utf-8") if data else None
    req = urllib.request.Request(url, data=body)
    req.add_header("Content-Type", "application/json;charset=UTF-8")
    if TOKEN:
        req.add_header("Authorization", TOKEN)

    with urllib.request.urlopen(req, timeout=45) as resp:
        yield from resp


def check(label, condition):
    status = "PASS" if condition else "FAIL"
    print(f"  [{status}] {label}")
    return condition


def step1_register_and_login():
    global TOKEN
    print("\n" + "=" * 60)
    print("Step 1: Register & Login")
    print("=" * 60)

    status, data = api(
        "/api/users/register",
        {"username": "e2e_test", "password": "test123456", "nickname": "E2E测试"},
        method="POST",
    )
    ok = check(f"Register (status={status})", data.get("code") in [200, 1002])
    if not ok:
        print(f"    Response: {json.dumps(data, ensure_ascii=False)[:200]}")

    status, data = api(
        "/api/users/login",
        {"username": "e2e_test", "password": "test123456"},
        method="POST",
    )
    ok = check("Login", status == 200 and data.get("code") == 200)
    if ok:
        TOKEN = data["data"]["token"]
        print(f"    User: {data['data']['nickname']} (id={data['data']['userId']})")
    else:
        print(f"    Response: {json.dumps(data, ensure_ascii=False)[:200]}")
    return ok


def step2_load_questions():
    print("\n" + "=" * 60)
    print("Step 2: Load Questions")
    print("=" * 60)

    status, data = api("/api/questions?page=1&size=10")
    ok = check("Load questions", status == 200 and data.get("code") == 200)
    if ok:
        items = data.get("data", {}).get("items", [])
        print(f"    Total questions: {len(items)}")
        for question in items[:3]:
            content = question.get("content", {})
            stem = content.get("stem", "") if isinstance(content, dict) else str(content)
            print(f"    [{question.get('id')}] D{question.get('difficulty')} {stem[:60]}...")
    else:
        print(f"    Response: {json.dumps(data, ensure_ascii=False)[:200]}")
    return ok


def step3_tutor_sse():
    print("\n" + "=" * 60)
    print("Step 3: AI Tutor SSE")
    print("=" * 60)

    payload = {
        "question_content": "解方程：2x + 5 = 13",
        "answer": "x = 4",
        "steps": "1. 移项，得到 2x = 8\n2. 两边同时除以 2，得到 x = 4",
        "student_message": "这一步为什么能移项？请一步一步引导我。",
        "grade": "初中",
        "subject": "数学",
        "knowledge_point": "一元一次方程",
        "mastery_level": "中",
    }

    try:
        buffer = ""
        chunks = 0
        full_text = ""
        byte_count = 0

        for raw_chunk in ai_stream("/ai/tutor/explain", payload):
            if not raw_chunk:
                continue
            byte_count += len(raw_chunk)
            buffer += raw_chunk.decode("utf-8", errors="replace")
            lines = buffer.split("\n")
            buffer = lines.pop() or ""

            for line in lines:
                if not line.startswith("data: "):
                    continue
                try:
                    parsed = json.loads(line[6:])
                except json.JSONDecodeError:
                    continue

                if parsed.get("done"):
                    chunks += 1
                elif parsed.get("content"):
                    full_text += parsed["content"]
                    chunks += 1

        ok = True
        ok &= check("SSE connection", byte_count > 0)
        ok &= check(f"Received {byte_count}B, {chunks} chunks", chunks > 3)
        ok &= check(f"Response length: {len(full_text)} chars", len(full_text) > 50)
        ok &= check("Contains guidance", any(word in full_text for word in ["先", "再", "移项", "方程"]))
        print(f"    Snippet: {full_text[:200].replace(chr(10), ' ')}...")
        return ok
    except Exception as exc:
        check(f"SSE streaming error: {exc}", False)
        return False


def main():
    print("=" * 60)
    print("  FamilyAgent End-to-End Smoke Test")
    print(f"  Frontend: {FRONTEND}")
    print(f"  Backend:  {BACKEND}")
    print(f"  AI:       {AI_SERVICE}")
    print("=" * 60)

    results = [
        step1_register_and_login(),
        step2_load_questions(),
        step3_tutor_sse(),
    ]

    print("\n" + "=" * 60)
    passed = sum(results)
    total = len(results)
    print(f"  Results: {passed}/{total} passed")
    print("=" * 60)

    sys.exit(0 if passed == total else 1)


if __name__ == "__main__":
    main()
