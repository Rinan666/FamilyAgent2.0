// ============================================
// FamilyAgent - frontend type definitions
// ============================================

// --- Users ---
export interface User {
  id: number;
  username: string;
  nickname: string;
  avatarUrl?: string;
  email?: string;
  phone?: string;
  role: string;
  status: string;
  birthDate?: string;
  birthYear?: string;
  metadata?: Record<string, unknown>;
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
  birthDate?: string;
  birthYear?: string;
  metadata?: Record<string, unknown>;
  token: string;
  tokenName: string;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

export interface UpdateProfileRequest {
  birthDate?: string;
}

export interface RegisterRequest {
  username: string;
  password: string;
  inviteCode: string;
  nickname?: string;
  email?: string;
}

export type AgentMode = 'family' | 'mirror';
export type AgentResponseMode = 'quick' | 'think';
export type WriteCategory = 'RECORD' | 'EXPERIENCE' | 'OBSERVATION';

export interface MirrorSourceRef {
  code: string;
  title: string;
  sourceLabel: string;
  temporalLabel: string;
  toneClass: string;
}

export interface AgentSessionMetadata extends Record<string, unknown> {
  entry?: string;
  contextLabel?: string;
  agentMode?: AgentMode;
  targetUserId?: number | null;
  targetMemberName?: string | null;
  hasTargetSwitches?: boolean;
}

// --- Families ---
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

export type FamilyTab = 'stream' | 'members';

export interface FamilyMember {
  id: number;
  familyId: number;
  userId: number;
  username?: string;
  nickname?: string;
  avatarUrl?: string;
  role: 'OWNER' | 'ADMIN' | 'GUARDIAN' | 'MEMBER' | 'GUEST';
  relationshipLabel?: string;
  reverseRelationshipLabel?: string;
  birthDate?: string;
  birthYear?: string;
  metadata?: Record<string, unknown>;
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

// --- Family diary / life records ---
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

export interface ChatMessage {
  id: string;
  role: 'user' | 'assistant' | 'system';
  content: string;
  timestamp: string;
  metadata?: {
    webSearch?: {
      needed: boolean;
      used: boolean;
      pending?: boolean;
      resultCount: number;
      sources: { title: string; url: string; snippet?: string }[];
    };
    responseMode?: AgentResponseMode;
    thinkingSummary?: string;
    rag?: {
      retrievalMode?: string;
      embeddingReadyCount?: number;
      diaryCount: number;
      memoryCount: number;
      growthRecordCount?: number;
      libraryCount?: number;
      heritageTaskCount?: number;
      sessionSavedCount?: number;
      totalReferenceCount?: number;
      sources: RagRecallSource[];
    };
    agentMode?: AgentMode;
    targetUserId?: number | null;
    targetMemberName?: string | null;
    hasTargetSwitches?: boolean;
    switchMarker?: boolean;
    sessionContextPatch?: AgentSessionMetadata;
    sourceRefs?: MirrorSourceRef[];
    sourceSummary?: string;
    insufficientSources?: boolean;
    retrievalQuery?: string;
  } & Record<string, unknown>;
}

export interface ChatSessionSummary {
  id: number;
  userId: number;
  familyId?: number;
  subject?: string;
  title?: string;
  summary?: string;
  status: 'ACTIVE' | 'ENDED';
  visibility?: string;
  source?: string;
  messageCount?: number;
  tokenCount?: number;
  lastMessageAt?: string;
  metadata?: AgentSessionMetadata;
  startedAt: string;
  endedAt?: string;
}

export interface ChatSessionArchiveSummary {
  id: number;
  sessionId: number;
  startSeq: number;
  endSeq: number;
  summary?: string;
  objectKey?: string;
  messageCount?: number;
  tokenCount?: number;
  createdAt: string;
  metadata?: Record<string, unknown>;
}

export interface ChatSessionArchiveMetadata {
  lastArchiveId?: number;
  lastArchiveAt?: string;
  lastArchiveRange?: string;
  storageVersion: number;
}

export interface ChatSessionDetail extends ChatSessionSummary {
  archivedBeforeSeq?: number;
  archiveStatus?: string;
  archiveMetadata?: ChatSessionArchiveMetadata;
  archives?: ChatSessionArchiveSummary[];
}

export interface ChatSessionMessageItem {
  seq?: number;
  id?: string;
  role: ChatMessage['role'] | string;
  content: string;
  toolName?: string;
  metadata?: ChatMessage['metadata'];
  createdAt: string;
  tokenCount?: number;
}

export interface ChatSessionMessagePage {
  items: ChatSessionMessageItem[];
  hasMore: boolean;
  nextBeforeSeq?: number;
}

export interface ChatSessionArchiveDetail extends ChatSessionArchiveSummary {
  transcript: ChatSessionMessageItem[];
}

export interface MemoryEntry {
  id: number;
  userId: number;
  familyId?: number;
  subject?: string;
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

export type MemoryVoteType = 'UP' | 'DOWN';

export interface MemoryVoteStats {
  memoryId: number;
  upVotes: number;
  downVotes: number;
  voteScore: number;
  consensusWeight: number;
  myVote?: MemoryVoteType | '' | string;
}

export interface AuthorizedMemoryRecallResult {
  diaries: DiaryEntry[];
  memories: MemoryEntry[];
  growthRecords?: GrowthGuardRecord[];
  diaryCount?: number;
  memoryCount?: number;
  growthRecordCount?: number;
  sources?: RagRecallSource[];
  retrievalMode?: string;
  query?: string;
  embeddingReadyCount?: number;
}

export interface RagRecallSource {
  id: string;
  sourceType: 'LIFE_RECORD' | 'FAMILY_EXPERIENCE' | string;
  title: string;
  snippet: string;
  visibility?: string;
  temporalLayer?: string;
  topics?: string[];
  scenes?: string[];
}

export interface RebuildMemoryIndexResult {
  familyId: number;
  diaryCount: number;
  memoryCount: number;
  growthRecordCount?: number;
  scheduledCount?: number;
  indexedCount?: number;
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

export type MemoryLibraryItemType = 'LIFE_RECORD' | 'FAMILY_EXPERIENCE' | 'GROWTH_OBSERVATION' | 'AI_SUMMARY';

export interface MemoryLibraryItem {
  id: string;
  sourceType: MemoryLibraryItemType;
  type: string;
  title: string;
  body: string;
  familyId: number;
  memberUserId?: number;
  memberName: string;
  visibility: string;
  tags: string[];
  metadata?: Record<string, unknown>;
  createdAt: string;
  updatedAt?: string;
}

export type MemoryMaintenanceAction = 'MERGE_REVIEW' | 'ARCHIVE_REVIEW' | 'DELETE_REVIEW' | string;

export interface MemoryMaintenanceSuggestion {
  action: MemoryMaintenanceAction;
  score: number;
  title: string;
  reason: string;
  reasons: string[];
  items: MemoryLibraryItem[];
}

export interface FamilyMemoryCard {
  title: string;
  theme: string;
  summary: string;
  motto?: string;
  risk_points: string[];
  action_suggestions: string[];
  suitable_for: string[];
  sensitivity: 'LOW' | 'MEDIUM' | 'HIGH' | string;
  safety_note: string;
}

export interface HeritageClassicalDraft {
  title: string;
  classicalText: string;
  plainSummary: string;
  styleNote: string;
}

export type HeritageTaskStatus = 'PENDING' | 'DONE' | 'ARCHIVED' | string;

export interface HeritageTask {
  id: number;
  familyId: number;
  memoryId?: number;
  createdBy: number;
  title: string;
  action: string;
  targetLabel?: string;
  dueDate?: string;
  status: HeritageTaskStatus;
  completionNote?: string;
  completedBy?: number;
  completedAt?: string;
  metadata?: Record<string, unknown>;
  createdAt: string;
  updatedAt?: string;
}

export interface CreateHeritageTaskRequest {
  familyId: number;
  memoryId?: number;
  title: string;
  action: string;
  targetLabel?: string;
  dueDate?: string;
  metadata?: Record<string, unknown>;
}

export interface HeritageTaskDraft {
  title: string;
  action: string;
  target_label: string;
  due_days: number;
  completion_prompt: string;
  reason: string;
}

export interface FamilyWeeklyDigest {
  title: string;
  summary: string;
  memory_highlights: string[];
  family_experience_refs: string[];
  growth_signals: string[];
  suggested_actions: string[];
  questions_for_family: string[];
  missing_records: string[];
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

export interface WriteMemoryRequest {
  familyId: number;
  writeCategory: WriteCategory;
  content: string;
  title?: string;
  tags?: string[];
  visibility?: DiaryVisibility | MemoryScope;
  relatedUserId?: number;
  diaryEntryType?: DiaryEntryType;
  memoryType?: MemoryEntryType;
  growthCategory?: GrowthGuardCategory;
  growthSeverity?: number;
  metadata?: Record<string, unknown>;
}

export interface WriteMemoryResult {
  savedRecordType: 'DIARY_ENTRY' | 'FAMILY_MEMORY' | 'GROWTH_GUARD' | string;
  savedRecordId: number;
  writeCategory: WriteCategory | string;
  visibility: string;
  title: string;
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

export interface HeritageSaveJudge {
  should_save: boolean;
  learning_value_score: number;
  descendant_value: string;
  reason: string;
  suggested_revision: string;
  missing_elements: string[];
  sensitivity: 'LOW' | 'MEDIUM' | 'HIGH' | string;
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
  metadata?: Record<string, unknown> & {
    followUpStatus?: GrowthFollowUpStatus | string;
    stalenessStats?: GrowthStalenessStats;
  };
  createdAt: string;
  updatedAt?: string;
}

export interface GrowthStalenessStats {
  recordId: number;
  staleVotes: number;
  stalenessWeight: number;
  myVoted?: boolean;
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
  uncertainty_notes?: string[];
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

export type SkillRunStatus = 'PLANNED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELED' | string;

export interface SkillRun {
  id: number;
  familyId: number;
  triggeredBy: number;
  skillName: string;
  status: SkillRunStatus;
  source: string;
  inputSummary?: string;
  outputSummary?: string;
  saved: boolean;
  usedSources?: Record<string, unknown>[];
  metadata?: Record<string, unknown>;
  createdAt: string;
  updatedAt?: string;
}

export interface CreateSkillRunRequest {
  familyId: number;
  skillName: string;
  status?: SkillRunStatus;
  source?: string;
  inputSummary?: string;
  outputSummary?: string;
  saved?: boolean;
  usedSources?: Record<string, unknown>[];
  metadata?: Record<string, unknown>;
}

export interface UpdateSkillRunRequest {
  status?: SkillRunStatus;
  outputSummary?: string;
  saved?: boolean;
  usedSources?: Record<string, unknown>[];
  metadata?: Record<string, unknown>;
}

export interface MirrorContextResponse {
  familyId: number;
  viewerUserId: number;
  targetMember: FamilyMember;
  diaries: DiaryEntry[];
  memories: MemoryEntry[];
  libraryItems?: MemoryLibraryItem[];
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


// --- Admin database health ---
export interface DatabaseTableCount {
  tableName: string;
  label: string;
  count: number;
  legacy: boolean;
}

export interface EmbeddingStatusSummary {
  familyId: number;
  sourceType: string;
  status: string;
  count: number;
  lastUpdatedAt?: string;
}

export interface FamilyDatabaseSummary {
  familyId: number;
  familyName: string;
  ownerUserId?: number;
  ownerDisplayName?: string;
  ownerMissing: boolean;
  memberCount: number;
  diaryCount: number;
  memoryCount: number;
  growthRecordCount: number;
  skillRunCount: number;
  failedSkillRunCount: number;
  readyEmbeddingCount: number;
  failedEmbeddingCount: number;
}

export interface FailedEmbeddingSummary {
  id: number;
  familyId: number;
  sourceType: string;
  sourceId: number;
  error?: string;
  updatedAt?: string;
}

export interface FailedSkillRunSummary {
  id: number;
  familyId: number;
  triggeredBy: number;
  skillName: string;
  source: string;
  inputSummary?: string;
  outputSummary?: string;
  updatedAt?: string;
}

export interface SuspiciousFamilySummary {
  familyId: number;
  familyName?: string;
  memberCount: number;
  ownerCount: number;
}

export interface SessionStorageHealthSummary {
  sessionId: number;
  familyId?: number;
  messageCount: number;
  archivedBeforeSeq: number;
  archiveStatus?: string;
  liveMessageRows: number;
  archivedMessageRows: number;
  totalMaterializedRows: number;
}

export interface SessionArchiveRangeSummary {
  sessionId: number;
  archiveId: number;
  startSeq: number;
  endSeq: number;
  messageCount: number;
  createdAt?: string;
}

export interface AdminUserSummary {
  id: number;
  username: string;
  nickname?: string;
  role?: string;
  status?: string;
}

export interface DatabaseHealthResponse {
  generatedAt: string;
  databaseName: string;
  pgvectorInstalled: boolean;
  totalUsers: number;
  totalFamilies: number;
  totalCoreRecords: number;
  totalSkillRuns: number;
  failedSkillRuns: number;
  totalEmbeddings: number;
  readyEmbeddings: number;
  failedEmbeddings: number;
  tableCounts: DatabaseTableCount[];
  embeddingStatuses: EmbeddingStatusSummary[];
  families: FamilyDatabaseSummary[];
  suspiciousFamilies?: SuspiciousFamilySummary[];
  sessionStorageHealth?: SessionStorageHealthSummary[];
  sessionArchiveRanges?: SessionArchiveRangeSummary[];
  recentFailedEmbeddings: FailedEmbeddingSummary[];
  recentFailedSkillRuns: FailedSkillRunSummary[];
}

export interface MemoryRecallDiagnosticRequest {
  familyId: number;
  viewerUserId: number;
  query: string;
  diaryLimit?: number;
  memoryLimit?: number;
}

export interface MemoryRecallDiagnosticResponse {
  familyId: number;
  viewerUserId: number;
  query: string;
  retrievalMode: string;
  embeddingReadyCount: number;
  diaryCount: number;
  memoryCount: number;
  growthRecordCount: number;
  sources: RagRecallSource[];
}

// --- API responses ---
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
