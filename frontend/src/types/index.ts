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
  role?: string;
  status?: string;
  token: string;
  tokenName: string;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
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
  role: 'OWNER' | 'ADMIN' | 'GUARDIAN' | 'MEMBER' | 'STUDENT' | 'GUEST';
  relationshipLabel?: string;
  reverseRelationshipLabel?: string;
  joinedAt: string;
}

export interface FamilyRelationship {
  id: number;
  familyId: number;
  fromUserId: number;
  toUserId: number;
  label: string;
  reverseLabel?: string;
  note?: string;
  createdAt: string;
  updatedAt: string;
}

export type CareAuthorizationScope = 'ALL' | 'GROWTH_GUARD' | string;
export type CareAuthorizationStatus = 'ACTIVE' | 'REVOKED' | string;

export interface CareAuthorization {
  id: number;
  familyId: number;
  subjectUserId: number;
  caregiverUserId: number;
  scope: CareAuthorizationScope;
  status: CareAuthorizationStatus;
  expiresAt?: string;
  createdAt: string;
  updatedAt: string;
}

// --- 家族日记 / 人生记录 ---
export type DiaryEntryType =
  | 'DAILY'
  | 'IMPORTANT_EVENT'
  | 'LESSON'
  | 'EMOTION'
  | 'MESSAGE_TO_FAMILY'
  | 'SELF_REFLECTION';

export type DiaryVisibility = 'PRIVATE' | 'FAMILY_VISIBLE' | 'CARE_VISIBLE' | 'LEGACY_VISIBLE';

export interface DiaryEntry {
  id: number;
  userId: number;
  familyId?: number;
  rawText: string;
  structured?: {
    entryType?: DiaryEntryType | string;
    title?: string;
    summary?: string;
    [key: string]: unknown;
  };
  mood?: string;
  tags?: string[];
  privacyLevel?: string;
  visibility: DiaryVisibility | string;
  metadata?: Record<string, unknown>;
  createdAt: string;
  updatedAt?: string;
}

export interface CreateDiaryEntryRequest {
  familyId: number;
  content: string;
  entryType?: DiaryEntryType;
  title?: string;
  mood?: string;
  tags?: string[];
  visibility?: DiaryVisibility;
  metadata?: Record<string, unknown>;
}

export type UpdateDiaryEntryRequest = Omit<CreateDiaryEntryRequest, 'familyId'>;

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
  type: MemoryEntryType;
  scope: MemoryScope | string;
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

export type MemoryEntryType =
  | 'LEARNING'
  | 'MISTAKE'
  | 'PREFERENCE'
  | 'PLAN'
  | 'FAMILY_STORY'
  | 'ELDER_ADVICE'
  | 'HEALTH_REMINDER'
  | 'GROWTH_RISK'
  | 'VALUE'
  | string;

export type MemoryScope = 'PRIVATE' | 'PARENT_VISIBLE' | 'CARE_VISIBLE' | 'FAMILY_VISIBLE';

export interface FamilyMemoryCard {
  title: string;
  theme: string;
  summary: string;
  risk_points: string[];
  action_suggestions: string[];
  suitable_for: string[];
  sensitivity: 'LOW' | 'MEDIUM' | 'HIGH' | string;
  safety_note: string;
}

export interface CreateFamilyMemoryRequest {
  familyId: number;
  content: string;
  type?: MemoryEntryType;
  scope?: MemoryScope;
  summary?: string;
  importance?: number;
  memoryCard?: FamilyMemoryCard;
  metadata?: Record<string, unknown>;
}

export type AgentSaveTool = 'NONE' | 'DIARY' | 'FAMILY_MEMORY' | 'GROWTH_GUARD';

export interface AgentSaveToolPlan {
  should_save: boolean;
  tool: AgentSaveTool;
  content: string;
  title: string;
  summary: string;
  visibility: DiaryVisibility | MemoryScope | string;
  entry_type: DiaryEntryType | string;
  memory_type: MemoryEntryType;
  scope: MemoryScope | string;
  category: GrowthGuardCategory | string;
  severity: number;
  importance: number;
  tags: string[];
  reason: string;
  confirmation_message: string;
}

export type AgentDraftScene = 'DIARY' | 'HERITAGE' | 'GROWTH_GUARD';

export interface AgentOrganizedDraft {
  title: string;
  content: string;
  tags: string[];
  diary_entry_type: DiaryEntryType | string;
  diary_visibility: DiaryVisibility | string;
  memory_type: MemoryEntryType;
  memory_scope: MemoryScope | string;
  growth_category: GrowthGuardCategory | string;
  growth_severity: number;
  scenario: string;
  reason: string;
}

export type GrowthGuardCategory =
  | 'POSTURE'
  | 'DENTAL'
  | 'VISION'
  | 'SLEEP'
  | 'EXERCISE'
  | 'SCREEN_TIME'
  | 'EMOTION'
  | 'COMMUNICATION'
  | 'OTHER';

export type GrowthFollowUpStatus = 'PENDING' | 'WATCHING' | 'IMPROVED' | 'ARCHIVED';

export interface GrowthGuardRecord {
  id: number;
  familyId: number;
  targetUserId?: number;
  createdBy: number;
  category: GrowthGuardCategory | string;
  content: string;
  severity: number;
  observedAt: string;
  followUpAt?: string;
  visibility: MemoryScope | string;
  status: 'ACTIVE' | 'ARCHIVED' | string;
  metadata?: Record<string, unknown> & { followUpStatus?: GrowthFollowUpStatus | string };
  createdAt: string;
  updatedAt?: string;
}

export interface CreateGrowthGuardRecordRequest {
  familyId: number;
  targetUserId?: number;
  category: GrowthGuardCategory;
  content: string;
  severity?: number;
  observedAt?: string;
  followUpAt?: string;
  visibility?: MemoryScope;
  metadata?: Record<string, unknown>;
}

export interface WeeklyGrowthReport {
  title: string;
  summary: string;
  affirmations?: string[];
  concerns?: string[];
  signals: string[];
  family_experience_refs: string[];
  suggested_actions: string[];
  follow_up_questions: string[];
  safety_note: string;
}

export interface GrowthGuardReport {
  id: number;
  familyId: number;
  targetUserId?: number;
  createdBy: number;
  weekStart: string;
  weekEnd: string;
  title: string;
  summary?: string;
  visibility: MemoryScope | string;
  status: 'ACTIVE' | 'ARCHIVED' | string;
  report: WeeklyGrowthReport | Record<string, unknown>;
  metadata?: Record<string, unknown>;
  createdAt: string;
  updatedAt?: string;
}

export interface CreateGrowthGuardReportRequest {
  familyId: number;
  targetUserId?: number;
  weekStart?: string;
  weekEnd?: string;
  title: string;
  summary?: string;
  visibility?: MemoryScope;
  report: WeeklyGrowthReport;
  metadata?: Record<string, unknown>;
}

export interface MirrorContextResponse {
  familyId: number;
  viewerUserId: number;
  targetMember: FamilyMember;
  diaries: DiaryEntry[];
  memories: MemoryEntry[];
  mirrorProfile?: Record<string, unknown>;
  memoryContext: string;
  disclaimer: string;
  insufficientRecords: boolean;
  sourceSummary?: string;
  retrievalMode?: string;
  retrievalQuery?: string;
  embeddingReadyCount?: number;
  suggestedQuestions?: string[];
  missingRecordSuggestions?: string[];
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
