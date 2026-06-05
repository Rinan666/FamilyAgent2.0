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

export interface SubmitTestQuestionResult {
  questionId: number;
  kpId: number;
  answer: string;
  score: number;
  correct: boolean;
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
