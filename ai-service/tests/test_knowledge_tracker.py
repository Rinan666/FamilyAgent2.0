"""
贝叶斯知识追踪引擎测试
"""
from app.engine.knowledge_tracker import BayesianKnowledgeTracker


class TestBayesianKnowledgeTracker:
    """BKT引擎单元测试"""

    def setup_method(self):
        self.tracker = BayesianKnowledgeTracker()

    def test_prior_initialization(self):
        """初始掌握概率应为0.5"""
        assert self.tracker.p_learn == 0.15

    def test_correct_answer_increases_mastery(self):
        """答对应该提高掌握概率"""
        posterior = self.tracker.update(prior_mastery=0.5, is_correct=True)
        assert posterior > 0.5

    def test_wrong_answer_decreases_mastery(self):
        """答错应该降低掌握概率"""
        posterior = self.tracker.update(prior_mastery=0.5, is_correct=False)
        assert posterior < 0.5

    def test_consecutive_correct(self):
        """连续答对应该持续提升"""
        mastery = 0.5
        for _ in range(5):
            mastery = self.tracker.update(prior_mastery=mastery, is_correct=True)
        assert mastery > 0.8

    def test_consecutive_wrong(self):
        """连续答错应该持续下降"""
        mastery = 0.5
        for _ in range(5):
            mastery = self.tracker.update(prior_mastery=mastery, is_correct=False)
        assert mastery < 0.2

    def test_forgetting_effect(self):
        """长时间不练习应该遗忘"""
        mastery = 0.9
        mastery_after_30_days = self.tracker.update(
            prior_mastery=mastery, is_correct=True, days_since_last=30
        )
        assert mastery_after_30_days < mastery

    def test_mastery_bounded(self):
        """掌握概率应该在0.01-0.99之间"""
        # 接近上限
        posterior = self.tracker.update(prior_mastery=0.99, is_correct=True)
        assert posterior <= 0.99

        # 接近下限
        posterior = self.tracker.update(prior_mastery=0.01, is_correct=False)
        assert posterior >= 0.01

    def test_zpd_identification(self):
        """应该正确识别最近发展区"""
        profiles = {
            "kp1": 0.1,  # 太低
            "kp2": 0.5,  # ZPD
            "kp3": 0.6,  # ZPD
            "kp4": 0.9,  # 已掌握
        }
        zpd = self.tracker.get_zpd(profiles)
        assert "kp2" in zpd
        assert "kp3" in zpd
        assert "kp1" not in zpd
        assert "kp4" not in zpd

    def test_mastery_level_labels(self):
        """掌握等级标签应该正确"""
        assert self.tracker.get_mastery_level(0.1) == "弱"
        assert self.tracker.get_mastery_level(0.45) == "中"
        assert self.tracker.get_mastery_level(0.7) == "强"
        assert self.tracker.get_mastery_level(0.9) == "精通"

    def test_adaptive_select(self):
        """自适应抽题：优先ZPD"""
        pool = [
            {"id": 1, "kp_id": "kp1", "difficulty": 2},
            {"id": 2, "kp_id": "kp2", "difficulty": 3},
            {"id": 3, "kp_id": "kp3", "difficulty": 1},
            {"id": 4, "kp_id": "kp4", "difficulty": 4},
        ]
        profiles = {"kp1": 0.1, "kp2": 0.5, "kp3": 0.6, "kp4": 0.9}

        selected = self.tracker.adaptive_select(pool, profiles, n=2, zpd_ratio=1.0)

        # 所有选中题目应该来自ZPD
        for q in selected:
            assert profiles[q["kp_id"]] >= 0.3
            assert profiles[q["kp_id"]] <= 0.7
