#!/usr/bin/env python3
"""Smoke checks for the currently supported AI service routes."""

import json
import urllib.error
import urllib.request

BASE_URL = "http://localhost:8090"


def api_post(path, data=None):
    url = f"{BASE_URL}{path}"
    body = json.dumps(data, ensure_ascii=False).encode("utf-8") if data else None

    req = urllib.request.Request(url, data=body, method="POST")
    req.add_header("Content-Type", "application/json;charset=UTF-8")

    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        return exc.code, {"error": exc.read().decode("utf-8", errors="replace")}
    except Exception as exc:
        return 0, {"error": str(exc)}


def api_get(path):
    url = f"{BASE_URL}{path}"
    try:
        with urllib.request.urlopen(url, timeout=10) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8"))
    except Exception as exc:
        return 0, {"error": str(exc)}


def print_result(label, success=True):
    mark = "PASSED" if success else "FAILED"
    print(f"  [{mark}] {label}")


def test_health():
    _, data = api_get("/ai/health")
    print(f"\n[Health] status={data.get('status')}, model={data.get('default_model')}")
    return data.get("status") == "healthy"


def test_tutor_explain_sync():
    print("\n[Tutor] Calling supported sync explain route...")

    payload = {
        "question_content": "解方程：2x + 5 = 13",
        "answer": "x = 4",
        "steps": "1. 移项，得到 2x = 8\n2. 两边同时除以 2，得到 x = 4",
        "student_message": "我想知道为什么先移项，再除以 2。",
        "grade": "初中",
        "subject": "数学",
        "knowledge_point": "一元一次方程",
        "mastery_level": "中",
    }

    _, data = api_post("/ai/tutor/explain/sync", payload)
    if "error" in data:
        print(f"  [FAILED] Error: {data['error'][:200]}")
        return False

    content = data.get("content", "")
    checks = {
        "Contains explanation": len(content) > 50,
        "Chinese output": any("\u4e00" <= char <= "\u9fff" for char in content),
        "Keeps step guidance": any(word in content for word in ["移项", "方程", "先", "再"]),
    }
    print(f"  Response length: {len(content)} chars")
    for check, passed in checks.items():
        print_result(check, passed)
    print(f"  Content preview: {content[:300]}...")
    return all(checks.values())


def main():
    print("=" * 60)
    print("  AI Service Smoke Test")
    print("=" * 60)

    if not test_health():
        print("\n[FATAL] AI service not healthy, aborting")
        return

    results = [test_tutor_explain_sync()]

    print("\n" + "=" * 60)
    print(f"  Results: {sum(results)}/{len(results)} passed")
    print("=" * 60)


if __name__ == "__main__":
    main()
