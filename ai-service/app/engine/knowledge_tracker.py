"""
贝叶斯知识追踪引擎

基于答题历史，动态更新学生对每个知识点的掌握概率
"""
import logging
from typing import Optional

logger = logging.getLogger("familyagent.ai.tracker")


class BayesianKnowledgeTracker:
    """
    贝叶斯知识追踪 (BKT) 引擎

    核心参数：
    - p_learn: 学习概率（通过一次正确练习掌握的概率）
    - p_guess: 猜测概率（未掌握但猜对的概率）
    - p_slip: 失误概率（已掌握但答错的概率）
    - p_forget: 遗忘概率（随时间遗忘的概率）
    """

    def __init__(
        self,
        p_learn: float = 0.15,
        p_guess: float = 0.20,
        p_slip: float = 0.10,
        p_forget: float = 0.03,
    ):
        self.p_learn = p_learn
        self.p_guess = p_guess
        self.p_slip = p_slip
        self.p_forget = p_forget

    def update(
        self,
        prior_mastery: float,
        is_correct: bool,
        days_since_last: int = 0,
    ) -> float:
        """
        根据答题结果更新掌握概率

        贝叶斯公式：
        P(mastered|correct) = P(mastered)*(1-P(slip))
                              / (P(mastered)*(1-P(slip)) + (1-P(mastered))*P(guess))

        P(mastered|wrong)   = P(mastered)*P(slip)
                              / (P(mastered)*P(slip) + (1-P(mastered))*(1-P(guess)))

        Args:
            prior_mastery: 先验掌握概率 (0-1)
            is_correct: 本次答题是否正确
            days_since_last: 距上次答题天数

        Returns:
            float: 后验掌握概率
        """
        # 应用遗忘
        adjusted_prior = prior_mastery * (1 - self.p_forget) ** days_since_last

        if is_correct:
            numerator = adjusted_prior * (1 - self.p_slip)
            denominator = numerator + (1 - adjusted_prior) * self.p_guess
        else:
            numerator = adjusted_prior * self.p_slip
            denominator = numerator + (1 - adjusted_prior) * (1 - self.p_guess)

        if denominator == 0:
            return adjusted_prior

        posterior = numerator / denominator

        # 夹紧到 [0.01, 0.99]
        return min(0.99, max(0.01, posterior))

    def get_zpd(
        self,
        profiles: dict[str, float],
        zpd_low: float = 0.30,
        zpd_high: float = 0.70,
    ) -> list[str]:
        """
        获取最近发展区 (Zone of Proximal Development) 的知识点

        ZPD: 掌握概率在 30%-70% 之间的知识点
        ——这些知识点最适合教学投入

        Args:
            profiles: {kp_id: mastery_probability}
            zpd_low: ZPD下限
            zpd_high: ZPD上限

        Returns:
            list[str]: ZPD 知识点ID列表
        """
        return [
            kp for kp, prob in profiles.items()
            if zpd_low <= prob <= zpd_high
        ]

    def get_mastery_level(self, probability: float) -> str:
        """将掌握概率转化为可读等级"""
        if probability < 0.30:
            return "弱"
        elif probability < 0.60:
            return "中"
        elif probability < 0.85:
            return "强"
        else:
            return "精通"

    def adaptive_select(
        self,
        question_pool: list[dict],
        profiles: dict[str, float],
        n: int = 5,
        zpd_ratio: float = 0.7,
    ) -> list[dict]:
        """
        自适应抽题：优先最近发展区，兼顾复习巩固

        Args:
            question_pool: 题目池 [{"id": ..., "kp_id": ..., "difficulty": ...}, ...]
            profiles: 学力档案 {kp_id: mastery_probability, ...}
            n: 抽取数量
            zpd_ratio: ZPD题目占比（0-1）

        Returns:
            list[dict]: 抽取的题目
        """
        import random

        zpd_kps = set(self.get_zpd(profiles))

        zpd_pool = [q for q in question_pool if q.get("kp_id") in zpd_kps]
        review_pool = [q for q in question_pool if q.get("kp_id") not in zpd_kps]

        n_zpd = min(int(n * zpd_ratio), len(zpd_pool))
        n_review = n - n_zpd

        selected = []
        if n_zpd > 0:
            selected.extend(random.sample(zpd_pool, min(n_zpd, len(zpd_pool))))
        if n_review > 0 and review_pool:
            selected.extend(random.sample(review_pool, min(n_review, len(review_pool))))

        random.shuffle(selected)
        return selected

    def generate_radar_data(
        self,
        profiles: dict[str, float],
        kp_names: dict[str, str],
    ) -> dict:
        """
        生成雷达图数据

        Returns:
            dict: {
                "labels": ["知识点1", "知识点2", ...],
                "values": [0.85, 0.45, ...],
                "levels": ["精通", "中", ...],
            }
        """
        labels = []
        values = []
        levels = []

        for kp_id, prob in sorted(profiles.items(), key=lambda x: x[1]):
            labels.append(kp_names.get(kp_id, f"KP-{kp_id}"))
            values.append(round(prob, 3))
            levels.append(self.get_mastery_level(prob))

        return {
            "labels": labels,
            "values": values,
            "levels": levels,
        }


# 全局单例
knowledge_tracker = BayesianKnowledgeTracker()
