#!/usr/bin/env python3
"""
题库种子数据生成脚本

使用LLM批量生成初中数学题目，并入库。
运行方式: python scripts/seed-questions.py --count 100 --subject math --grade grade7
"""
import argparse
import json
import sys
import os
from pathlib import Path

# 添加 ai-service 到路径
sys.path.insert(0, str(Path(__file__).parent.parent / "ai-service"))

# 知识点映射
KP_MAP = {
    # 数与式
    "有理数": {"kp_id": 2, "grade": "grade7", "subject": "math"},
    "整式的加减": {"kp_id": 3, "grade": "grade7", "subject": "math"},
    "整式的乘除": {"kp_id": 4, "grade": "grade8", "subject": "math"},
    # 方程与不等式
    "一元一次方程": {"kp_id": 6, "grade": "grade7", "subject": "math"},
    "一元一次不等式": {"kp_id": 7, "grade": "grade7", "subject": "math"},
    "二元一次方程组": {"kp_id": 8, "grade": "grade8", "subject": "math"},
    # 几何
    "线段与角": {"kp_id": 10, "grade": "grade7", "subject": "math"},
    "三角形": {"kp_id": 11, "grade": "grade8", "subject": "math"},
    "四边形": {"kp_id": 12, "grade": "grade8", "subject": "math"},
    # 函数
    "平面直角坐标系": {"kp_id": 14, "grade": "grade8", "subject": "math"},
    "一次函数": {"kp_id": 15, "grade": "grade8", "subject": "math"},
}

QUESTION_TYPES = ["CHOICE", "FILL", "CALCULATION"]
DIFFICULTIES = [1, 2, 3, 4, 5]


async def generate_batch(knowledge_point: str, count: int, dry_run: bool = False):
    """批量生成题目"""
    from app.agents.generator_agent import generator_agent

    kp_info = KP_MAP.get(knowledge_point)
    if not kp_info:
        print(f"未知知识点: {knowledge_point}")
        print(f"可用知识点: {list(KP_MAP.keys())}")
        return []

    all_questions = []
    batch_size = 5  # 每次生成5道

    for i in range(0, count, batch_size):
        n = min(batch_size, count - i)

        for qtype in QUESTION_TYPES:
            for diff in DIFFICULTIES:
                if len(all_questions) >= count:
                    break

                print(f"生成 {knowledge_point} | {qtype} | 难度{diff} | {n}道...")

                if dry_run:
                    all_questions.append({
                        "subject": kp_info["subject"],
                        "grade": kp_info["grade"],
                        "type": qtype,
                        "difficulty": diff,
                        "kp_id": kp_info["kp_id"],
                        "content": {"stem": f"[示例题目] {knowledge_point}"},
                        "answer": {"value": "42", "steps": ["步骤1", "步骤2"]},
                        "tags": [knowledge_point],
                        "source": "AI_GENERATED",
                    })
                else:
                    try:
                        questions = await generator_agent.generate(
                            subject=kp_info["subject"],
                            grade=kp_info["grade"],
                            knowledge_point=knowledge_point,
                            question_type=qtype,
                            difficulty=diff,
                            count=n,
                        )
                        for q in questions:
                            q["kp_id"] = kp_info["kp_id"]
                            q["source"] = "AI_GENERATED"
                            q["tags"] = [knowledge_point]
                        all_questions.extend(questions)
                        print(f"  ✓ 生成 {len(questions)} 道")
                    except Exception as e:
                        print(f"  ✗ 失败: {e}")

    return all_questions


def save_to_json(questions: list[dict], output_path: str):
    """保存到JSON文件"""
    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(questions, f, ensure_ascii=False, indent=2)
    print(f"\n已保存 {len(questions)} 道题目到 {output_path}")


def print_stats(questions: list[dict]):
    """打印统计信息"""
    by_type = {}
    by_difficulty = {}
    by_kp = {}

    for q in questions:
        t = q.get("type", "unknown")
        d = q.get("difficulty", 0)
        kp = q.get("tags", ["unknown"])[0] if q.get("tags") else "unknown"

        by_type[t] = by_type.get(t, 0) + 1
        by_difficulty[d] = by_difficulty.get(d, 0) + 1
        by_kp[kp] = by_kp.get(kp, 0) + 1

    print("\n=== 题目统计 ===")
    print(f"总计: {len(questions)} 道")
    print(f"\n按题型: {json.dumps(by_type, ensure_ascii=False)}")
    print(f"按难度: {json.dumps(by_difficulty, ensure_ascii=False)}")
    print(f"按知识点: {json.dumps(by_kp, ensure_ascii=False)}")


async def main():
    parser = argparse.ArgumentParser(description="题库种子数据生成")
    parser.add_argument("--count", type=int, default=100, help="总题目数")
    parser.add_argument("--subject", default="math", help="学科")
    parser.add_argument("--grade", default="grade7", help="年级")
    parser.add_argument("--kp", default=None, help="知识点（不指定则覆盖全部）")
    parser.add_argument("--output", default="data/seed-questions.json", help="输出文件")
    parser.add_argument("--dry-run", action="store_true", help="空跑（不调用LLM）")
    parser.add_argument("--verbose", action="store_true", help="详细输出")

    args = parser.parse_args()

    # 确定要生成的知识点
    if args.kp:
        kps = [args.kp]
    else:
        kps = [k for k, v in KP_MAP.items() if v["grade"] == args.grade]

    per_kp = max(5, args.count // len(kps))

    print(f"学科: {args.subject} | 年级: {args.grade}")
    print(f"知识点: {len(kps)}个 | 每个约{per_kp}道 | 目标{args.count}道")
    print(f"{'空跑模式' if args.dry_run else '正式生成'}")
    print()

    all_questions = []
    for kp in kps:
        questions = await generate_batch(kp, per_kp, args.dry_run)
        all_questions.extend(questions)
        if len(all_questions) >= args.count:
            break

    # 输出
    os.makedirs(os.path.dirname(args.output), exist_ok=True)
    save_to_json(all_questions[:args.count], args.output)
    print_stats(all_questions)


if __name__ == "__main__":
    import asyncio
    asyncio.run(main())
