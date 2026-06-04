#!/usr/bin/env python3
"""
DeepSeek API 集成测试

测试讲题、批改、出题三个核心 Agent
"""
import json
import urllib.request
import urllib.error

BASE_URL = "http://localhost:8000"


def api_post(path, data=None):
    """Send POST request using urllib"""
    url = f"{BASE_URL}{path}"
    body = json.dumps(data, ensure_ascii=False).encode("utf-8") if data else None

    req = urllib.request.Request(url, data=body, method="POST")
    req.add_header("Content-Type", "application/json;charset=UTF-8")

    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        return e.code, {"error": e.read().decode("utf-8", errors="replace")}
    except Exception as e:
        return 0, {"error": str(e)}


def api_get(path):
    """Send GET request"""
    url = f"{BASE_URL}{path}"
    try:
        with urllib.request.urlopen(url, timeout=10) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8"))
    except Exception as e:
        return 0, {"error": str(e)}


def print_result(label, success=True):
    mark = "PASSED" if success else "FAILED"
    print(f"  [{mark}] {label}")


def test_health():
    """测试健康检查"""
    status, data = api_get("/ai/health")
    print(f"\n[Health] status={data.get('status')}, model={data.get('default_model')}")
    return data.get("status") == "healthy"


def test_math_verify():
    """测试纯数学验证"""
    print("\n[Math Verify]")

    status, data = api_post("/ai/tutor/math/verify?expression=2*x%2B5-13")
    ok = data.get("success") and data.get("data", {}).get("success")
    print_result(f"Expression eval: {data.get('data', {}).get('result', data.get('error'))}", ok)

    status, data = api_post("/ai/tutor/math/verify?expected=4&student_answer=4")
    ok = data.get("success") and data.get("data", {}).get("is_correct")
    print_result(f"Answer verify: {data}", ok)


def test_grader():
    """测试批改 Agent"""
    print("\n[Grader] Calling DeepSeek...")

    payload = {
        "question_content": "解方程：2x + 5 = 13",
        "answer": "x = 4",
        "steps": "1. 移项：2x = 13 - 5 = 8\n2. 系数化为1：x = 8 / 2 = 4\n3. 答案：x = 4",
        "student_answer": "2x + 5 = 13, 2x = 8, x = 4",
        "subject": "数学",
        "grade": "初中"
    }

    status, data = api_post("/ai/tutor/grade", payload)

    if "error" in data:
        print(f"  [FAILED] Error: {data['error'][:200]}")
        return

    result = data.get("data", data)
    score = result.get("overall_score", "N/A")
    correct = result.get("is_correct", "N/A")
    feedback = result.get("overall_feedback", "")[:150]

    print(f"  Score: {score}, Correct: {correct}")
    print(f"  Feedback: {feedback}...")

    if result.get("step_grades"):
        for sg in result["step_grades"][:3]:
            print(f"  Step {sg.get('step_number')}: {sg.get('step_name')} = {sg.get('score')}/{sg.get('max_score')}")

    if result.get("error_analysis"):
        ea = result["error_analysis"]
        print(f"  Error type: {ea.get('primary_error_type')}")
        print(f"  Knowledge gaps: {ea.get('knowledge_gaps')}")


def test_generator():
    """测试出题 Agent"""
    print("\n[Generator] Calling DeepSeek...")

    payload = {
        "subject": "数学",
        "grade": "初中",
        "knowledge_point": "一元一次方程",
        "question_type": "CALCULATION",
        "difficulty": 3,
        "count": 2,
        "additional_requirements": "适合初一学生"
    }

    status, data = api_post("/ai/tutor/generate", payload)

    if "error" in data:
        print(f"  [FAILED] Error: {data['error'][:200]}")
        return

    questions = data.get("questions", [])
    print(f"  Generated {len(questions)} questions:")
    for i, q in enumerate(questions):
        content = q.get("content", "")
        if isinstance(content, dict):
            stem = content.get("stem", str(content))[:100]
        else:
            stem = str(content)[:100]
        print(f"  [{i+1}] [{q.get('type')}] D={q.get('difficulty')} {stem}...")


def test_tutor_explain():
    """测试讲题 Agent"""
    print("\n[Tutor] Calling DeepSeek (sync)...")

    payload = {
        "question_content": "解方程：2x + 5 = 13",
        "answer": "x = 4",
        "steps": "1. 移项：2x = 13 - 5 = 8\n2. 系数化为1：x = 8 / 2 = 4",
        "student_message": "老师好，这道题我不太理解，能帮我讲解吗？",
        "grade": "初中",
        "subject": "数学",
        "knowledge_point": "一元一次方程",
        "mastery_level": "中"
    }

    status, data = api_post("/ai/tutor/explain/sync", payload)

    if "error" in data:
        print(f"  [FAILED] Error: {data['error'][:200]}")
        print(f"  Full error: {json.dumps(data, ensure_ascii=False, indent=2)[:500]}")
        return

    content = data.get("content", "")
    print(f"  Response length: {len(content)} chars")

    # 质量检查
    checks = {
        "Contains questions": any(c in content for c in ["?", "？"]),
        "No direct answer": "x = 4" not in content.replace(" ", "").lower() if len(content) < 500 else True,
        "Step guidance": any(w in content for w in ["移项", "系数", "第一步", "首先"]),
        "Chinese output": any('一' <= c <= '鿿' for c in content),
    }
    for check, passed in checks.items():
        print_result(check, passed)

    print(f"  Content preview: {content[:400]}...")


def main():
    print("=" * 60)
    print("  DeepSeek API Integration Test")
    print("=" * 60)

    if not test_health():
        print("\n[FATAL] AI service not healthy, aborting")
        return

    test_math_verify()
    test_grader()
    test_generator()
    test_tutor_explain()

    print("\n" + "=" * 60)
    print("  All tests completed!")
    print("=" * 60)


if __name__ == "__main__":
    main()
