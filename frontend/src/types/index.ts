// ============================================
// FamilyAgent - 前端类型定义
// ============================================

// --- 用户 ---
export interface User {
  id: number;
  username: string;
  nickname: string;
  avatarUrl?: string;
  email?: string;
  phone?: string;
  role: string;
  status: string;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  userId: number;
  username: string;
  nickname: string;
  avatarUrl?: string;
  token: string;
  tokenName: string;
}

export interface RegisterRequest {
  username: string;
  password: string;
  nickname?: string;
  email?: string;
  inviteCode: string;
}

// --- 家族 ---
export interface Family {
  id: number;
  name: string;
  description?: string;
  avatarUrl?: string;
  inviteCode?: string;
  maxMembers: number;
  createdBy: number;
  createdAt: string;
}

export interface FamilyMember {
  id: number;
  familyId: number;
  userId: number;
  username?: string;
  nickname?: string;
  avatarUrl?: string;
  role: 'OWNER' | 'ADMIN' | 'MEMBER' | 'GUEST';
  joinedAt: string;
}

// --- 题库 ---
export interface KnowledgePoint {
  id: number;
  parentId?: number;
  subject: string;
  grade: string;
  name: string;
  description?: string;
  level: number;
  sortOrder: number;
  metadata?: TextbookMetadata;
  children?: KnowledgePoint[];
}

export interface TextbookMetadata {
  textbookVersion?: string;
  textbookName?: string;
  volume?: string;
  chapterCode?: string;
  chapterName?: string;
  sectionName?: string;
  lessonOrder?: number;
  [key: string]: unknown;
}

export interface Question {
  id: number;
  familyId?: number;
  kpId: number;
  subject: string;
  grade: string;
  type: 'CHOICE' | 'FILL' | 'CALCULATION' | 'PROOF';
  difficulty: number;
  content: QuestionContent;
  answer: QuestionAnswer;
  tags?: string[];
  source?: string;
  visibility?: string;
  usageCount?: number;
  correctRate?: number;
}

export interface QuestionContent {
  stem: string;
  options?: string[];
  figures?: string[];
}

export interface QuestionAnswer {
  value: string;
  steps?: string[];
  explanation?: string;
}

export interface CreateQuestionRequest {
  kpId?: number;
  subject: string;
  grade?: string;
  type: Question['type'];
  difficulty: number;
  content: QuestionContent;
  answer: QuestionAnswer;
  tags?: string[];
  source?: string;
}

// --- 评估 ---
export interface AbilityProfile {
  id: number;
  userId: number;
  familyId?: number;
  kpId: number;
  masteryProbability: number;
  totalAttempts: number;
  correctAttempts: number;
  consecutiveCorrect: number;
  visibility?: string;
  lastAttemptAt?: string;
}

export interface TestRecord {
  id: number;
  userId: number;
  familyId?: number;
  questionIds: number[];
  answers: Record<string, string>;
  scores: Record<string, number>;
  timeSpent: number[];
  totalScore: number;
  totalTime?: number;
  status: string;
  source?: string;
  visibility?: string;
  createdAt: string;
}

export interface TestRecordDetailItem {
  questionId: number;
  kpId?: number;
  question?: Question;
  studentAnswer: string;
  correctAnswer?: QuestionAnswer | unknown;
  score: number;
  correct: boolean;
  timeSpent?: number;
  wrong: boolean;
  wrongRecordId?: number;
  wrongStatus?: string;
  errorType?: string;
  feedback?: string;
  parentExplanation?: string;
  nextSuggestion?: string;
}

export interface TestRecordDetail {
  record: TestRecord;
  items: TestRecordDetailItem[];
}

export interface SubmitTestQuestionResult {
  questionId: number;
  kpId: number;
  answer: string;
  score: number;
  correct: boolean;
  errorType?: string;
  feedback?: string;
  parentExplanation?: string;
  nextSuggestion?: string;
  timeSpent?: number;
}

export interface SubmitTestRequest {
  userId?: number;
  familyId?: number;
  results: SubmitTestQuestionResult[];
  totalTime?: number;
  source?: string;
}

// --- 家教 ---
export interface ChatMessage {
  id: string;
  role: 'user' | 'assistant' | 'system';
  content: string;
  timestamp: string;
}

export interface ChatSession {
  id: number;
  userId: number;
  familyId?: number;
  questionId?: number;
  subject?: string;
  knowledgePointId?: number;
  messages: ChatMessage[];
  summary?: string;
  status: 'ACTIVE' | 'ENDED';
  visibility?: string;
  source?: string;
  metadata?: Record<string, unknown>;
  startedAt: string;
  endedAt?: string;
}

export interface MemoryEntry {
  id: number;
  userId: number;
  familyId?: number;
  subject?: string;
  knowledgePointId?: number;
  type: 'LEARNING' | 'MISTAKE' | 'PREFERENCE' | 'PLAN' | string;
  scope: string;
  content: string;
  summary?: string;
  importance: number;
  confidence: number;
  sourceSessionId?: number;
  status: 'ACTIVE' | 'ARCHIVED' | string;
  metadata?: Record<string, unknown>;
  createdAt: string;
  updatedAt?: string;
}

export interface TutorExtractResult {
  filename: string;
  sourceType: string;
  contentType: string;
  text: string;
  structuredText: string;
  detectedQuestions: string[];
  detectedAnswers: string[];
  detectedSteps: string[];
  supported: boolean;
  message: string;
}

// --- 批改 ---
export interface GradeResult {
  overallScore: number;
  isCorrect: boolean;
  stepGrades: StepGrade[];
  errorAnalysis: ErrorAnalysis;
  overallFeedback: string;
}

export interface StepGrade {
  stepNumber: number;
  stepName: string;
  studentWork?: string;
  isCorrect: boolean;
  score: number;
  maxScore: number;
  errorType?: string;
  feedback: string;
}

export interface ErrorAnalysis {
  primaryErrorType: string;
  knowledgeGaps: string[];
  suggestion: string;
  parentExplanation?: string;
  nextSuggestion?: string;
}

// --- Skill Workflows ---
export interface MistakeReviewResult {
  error_category: string;
  correct_solution_summary: string;
  correction_note: string;
  error_pattern: string;
  similar_question_suggestions: string[];
  spaced_review_plan: { day_offset: number; action: string }[];
  parent_explanation: string;
  missing_info: string[];
}

export interface DailyPracticeResult {
  daily_goal: string;
  warmup_prompt: string;
  questions: {
    stem: string;
    answer: string;
    explanation: string;
    difficulty: number;
    error_tags: string[];
  }[];
  self_check: string[];
  next_review_action: string;
  missing_info: string[];
}

export interface ExamReviewResult {
  diagnosis: string;
  priority_weak_points: {
    knowledge_point: string;
    priority: '高' | '中' | '低';
    reason: string;
  }[];
  daily_plan: {
    day: number;
    focus: string;
    tasks: string[];
  }[];
  timed_practice: string[];
  mistake_review_actions: string[];
  next_retest: string;
  risks: string[];
  missing_info: string[];
}

export interface StudyPlanResult {
  plan_goal: string;
  priorities: {
    item: string;
    priority: '高' | '中' | '低';
    reason: string;
  }[];
  daily_tasks: {
    day: number;
    focus: string;
    tasks: string[];
    check_method: string;
  }[];
  review_questions: string[];
  parent_support: string[];
  missing_info: string[];
}

// --- API响应 ---
export interface ApiResult<T> {
  code: number;
  message: string;
  data: T;
  requestId?: string;
  timestamp: number;
}

export interface PageResult<T> {
  items: T[];
  page: number;
  pageSize: number;
  total: number;
  totalPages: number;
}
