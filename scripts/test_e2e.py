#!/usr/bin/env python3
"""
家族教育Agent — 端到端测试

模拟完整用户流程：
1. 注册/登录
2. 加载题库
3. AI家教讲题（SSE流式）
4. 提交答案批改
"""
import json
import sys
import urllib.request
import urllib.error

FRONTEND = "http://localhost:3000"
BACKEND = "http://localhost:8080"
AI_SERVICE = "http://localhost:8000"
TOKEN = None


def api(path, data=None, method="GET", use_backend=False):
    """通用 API 调用，通过前端代理"""
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
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", errors="replace")
        try:
            return e.code, json.loads(body)
        except:
            return e.code, {"error": body[:200]}


def ai_api(path, data=None):
    """直接调用 Python AI 服务（SSE和批改）"""
    url = f"{AI_SERVICE}{path}"
    body = json.dumps(data, ensure_ascii=False).encode("utf-8") if data else None
    req = urllib.request.Request(url, data=body)
    req.add_header("Content-Type", "application/json;charset=UTF-8")

    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", errors="replace")
        try:
            return e.code, json.loads(body)
        except:
            return e.code, {"error": body[:200]}


def check(label, condition):
    """断言并打印"""
    status = "PASS" if condition else "FAIL"
    print(f"  [{status}] {label}")
    return condition


def step1_register_and_login():
    """步骤1：注册并登录"""
    global TOKEN
    print("\n" + "=" * 60)
    print("Step 1: Register & Login")
    print("=" * 60)

    # Register
    status, data = api("/api/users/register", {
        "username": "e2e_test",
        "password": "test123456",
        "nickname": "E2E测试"
    }, method="POST")
    ok = check(f"Register (status={status})", data.get("code") in [200, 1002])  # 1002=已存在
    if not ok:
        print(f"    Response: {json.dumps(data, ensure_ascii=False)[:200]}")

    # Login
    status, data = api("/api/users/login", {
        "username": "e2e_test",
        "password": "test123456"
    }, method="POST")
    ok = check("Login", status == 200 and data.get("code") == 200)
    if ok:
        TOKEN = data["data"]["token"]
        print(f"    Token: {TOKEN[:20]}...")
        print(f"    User: {data['data']['nickname']} (id={data['data']['userId']})")
    else:
        print(f"    Response: {json.dumps(data, ensure_ascii=False)[:200]}")
    return ok


def step2_load_questions():
    """步骤2：加载题库"""
    print("\n" + "=" * 60)
    print("Step 2: Load Questions")
    print("=" * 60)

    status, data = api("/api/questions?page=1&size=10")
    ok = check(f"Load questions", status == 200 and data.get("code") == 200)
    if ok:
        items = data.get("data", {}).get("items", [])
        print(f"    Total questions: {len(items)}")
        for q in items[:3]:
            content = q.get("content", {})
            stem = content.get("stem", "") if isinstance(content, dict) else str(content)
            print(f"    [{q.get('id')}] D{q.get('difficulty')} {stem[:60]}...")
    else:
        print(f"    Response: {json.dumps(data, ensure_ascii=False)[:200]}")
    return ok


def step3_tutor_sse():
    """步骤3：AI讲题（SSE流式）"""
    print("\n" + "=" * 60)
    print("Step 3: AI Tutor (SSE Streaming)")
    print("=" * 60)

    url = f"{AI_SERVICE}/ai/tutor/explain"
    body = json.dumps({
        "question_content": "解方程：2x + 5 = 13",
        "answer": "x = 4",
        "steps": "1. 移项：2x = 8\n2. 系数化为1：x = 4",
        "student_message": "老师好，这道题我不太懂，可以引导我吗？",
        "grade": "初中",
        "subject": "数学",
        "knowledge_point": "一元一次方程",
        "mastery_level": "中"
    }, ensure_ascii=False).encode("utf-8")

    req = urllib.request.Request(url, data=body)
    req.add_header("Content-Type", "application/json;charset=UTF-8")

    try:
        with urllib.request.urlopen(req, timeout=45) as resp:
            buffer = ""
            chunks = 0
            full_text = ""
            byte_count = 0

            while True:
                chunk = resp.read(64)
                if not chunk:
                    break
                byte_count += len(chunk)
                buffer += chunk.decode("utf-8", errors="replace")
                lines = buffer.split("\n")
                buffer = lines.pop() or ""

                for line in lines:
                    if line.startswith("data: "):
                        try:
                            parsed = json.loads(line[6:])
                            if parsed.get("done"):
                                chunks += 1
                            elif parsed.get("content"):
                                full_text += parsed["content"]
                                chunks += 1
                        except:
                            pass

            check("SSE connection", byte_count > 0)
            check(f"Received {byte_count}B, {chunks} chunks", chunks > 5)
            check(f"Response length: {len(full_text)} chars", len(full_text) > 50)

            # Quality checks
            has_question = "?" in full_text or "？" in full_text
            check("Contains guiding questions", has_question)

            # Print snippet
            snippet = full_text[:200].replace("\n", " ")
            print(f"    Snippet: {snippet}...")
            return True

    except Exception as e:
        check(f"SSE streaming: {e}", False)
        return False


def step4_grade():
    """步骤4：批改"""
    print("\n" + "=" * 60)
    print("Step 4: Grade Answer")
    print("=" * 60)

    status, data = ai_api("/ai/tutor/grade", {
        "question_content": "解方程：2x + 5 = 13",
        "answer": "x = 4",
        "steps": "1. 移项：2x = 8\n2. 系数化为1：x = 4",
        "student_answer": "x = 3",
        "subject": "数学",
        "grade": "初中"
    })

    ok = check("Grade API call", data.get("success"))
    if ok:
        result = data.get("data", {})
        score = result.get("overall_score", "N/A")
        correct = result.get("is_correct", "N/A")
        check(f"Score={score}, Correct={correct}", score is not None)

        feedback = result.get("overall_feedback", "")[:100]
        print(f"    Feedback: {feedback}...")

        steps = result.get("step_grades", [])
        for s in steps[:3]:
            print(f"    Step{s.get('step_number')} '{s.get('step_name')}': {s.get('score')}/{s.get('max_score')}")

        if result.get("error_analysis"):
            ea = result["error_analysis"]
            if ea.get("primary_error_type") and ea["primary_error_type"] != "无":
                print(f"    Error: {ea['primary_error_type']}")
    return ok


def step5_math_verify():
    """步骤5：数学验证"""
    print("\n" + "=" * 60)
    print("Step 5: Math Verification (sympy)")
    print("=" * 60)

    # Use direct Python call with proper body format
    import urllib.parse
    status, data = ai_api("/ai/tutor/math/verify?expression=2*3%2B5")
    ok1 = check("Expression eval", data.get("success") == True)
    if ok1:
        print(f"    Result: {data.get('data', {}).get('result', data.get('detail', 'N/A'))}")

    status, data = ai_api("/ai/tutor/math/verify?expected=4&student_answer=4")
    ok2 = check("Answer verify (correct)", data.get("success") == True and data.get("data", {}).get("is_correct") == True)

    status, data = ai_api("/ai/tutor/math/verify?expected=4&student_answer=5")
    ok3 = check("Answer verify (wrong)", data.get("success") == True and data.get("data", {}).get("is_correct") == False)

    return ok1 and ok2 and ok3


def main():
    print("=" * 60)
    print("  FamilyAgent End-to-End Test")
    print(f"  Frontend: {FRONTEND}")
    print(f"  Backend:  {BACKEND}")
    print(f"  AI:       {AI_SERVICE}")
    print("=" * 60)

    # Check all services
    print("\n--- Service Health ---")
    try:
        _, data = urllib.request.urlopen(f"{BACKEND}/actuator/health")
        print(f"  Backend:  {json.loads(data)}")
    except:
        print("  Backend:  DOWN!")

    try:
        _, data = urllib.request.urlopen(f"{AI_SERVICE}/ai/health")
        print(f"  AI:       {json.loads(data)}")
    except:
        print("  AI:       DOWN!")

    try:
        urllib.request.urlopen(f"{FRONTEND}/login")
        print("  Frontend: UP")
    except:
        print("  Frontend: DOWN!")

    # Run tests
    results = []
    results.append(step1_register_and_login())
    results.append(step2_load_questions())
    results.append(step3_tutor_sse())
    results.append(step4_grade())
    results.append(step5_math_verify())

    # Summary
    print("\n" + "=" * 60)
    passed = sum(results)
    total = len(results)
    print(f"  Results: {passed}/{total} passed")
    print("=" * 60)

    sys.exit(0 if passed == total else 1)


if __name__ == "__main__":
    main()
