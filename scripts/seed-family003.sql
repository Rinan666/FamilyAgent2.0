BEGIN;

-- FAMILY003 test accounts. Password for every account: Test@123456
INSERT INTO users (username, password_hash, nickname, email, role, status, metadata, created_at, updated_at)
VALUES
  ('family003_grandpa', '969fdf03e82fe9c964ed1f9177f5914246b0f30620a7c84180bbb42a65953224', '李明德', 'family003_grandpa@example.test', 'USER', 'ACTIVE', '{"inviteCode":"FAMILY003","inviteSource":"seed-family-003","profileType":"ELDER","relation":"外公","seed":"FAMILY003"}'::jsonb, NOW() - INTERVAL '40 days', NOW()),
  ('family003_mother', '969fdf03e82fe9c964ed1f9177f5914246b0f30620a7c84180bbb42a65953224', '林秋然', 'family003_mother@example.test', 'USER', 'ACTIVE', '{"inviteCode":"FAMILY003","inviteSource":"seed-family-003","profileType":"PARENT","relation":"母亲","seed":"FAMILY003"}'::jsonb, NOW() - INTERVAL '39 days', NOW()),
  ('family003_father', '969fdf03e82fe9c964ed1f9177f5914246b0f30620a7c84180bbb42a65953224', '陈远航', 'family003_father@example.test', 'USER', 'ACTIVE', '{"inviteCode":"FAMILY003","inviteSource":"seed-family-003","profileType":"PARENT","relation":"父亲","seed":"FAMILY003"}'::jsonb, NOW() - INTERVAL '38 days', NOW()),
  ('family003_aunt', '969fdf03e82fe9c964ed1f9177f5914246b0f30620a7c84180bbb42a65953224', '陈知微', 'family003_aunt@example.test', 'USER', 'ACTIVE', '{"inviteCode":"FAMILY003","inviteSource":"seed-family-003","profileType":"ADULT_MEMBER","relation":"姑姑","seed":"FAMILY003"}'::jsonb, NOW() - INTERVAL '37 days', NOW()),
  ('family003_student', '969fdf03e82fe9c964ed1f9177f5914246b0f30620a7c84180bbb42a65953224', '陈一诺', 'family003_student@example.test', 'USER', 'ACTIVE', '{"inviteCode":"FAMILY003","inviteSource":"seed-family-003","profileType":"LEARNER","relation":"孩子","grade":"七年级","seed":"FAMILY003"}'::jsonb, NOW() - INTERVAL '36 days', NOW())
ON CONFLICT (username) DO UPDATE SET
  nickname = EXCLUDED.nickname,
  email = EXCLUDED.email,
  status = 'ACTIVE',
  metadata = EXCLUDED.metadata,
  updated_at = NOW();

UPDATE invite_codes
SET used_count = 5,
    max_uses = GREATEST(max_uses, 5),
    status = 'ACTIVE',
    updated_at = NOW()
WHERE code = 'FAMILY003';

INSERT INTO families (name, description, invite_code, max_members, settings, created_by, created_at, updated_at)
SELECT
  '林陈家族测试空间',
  'FAMILY003 种子家族：用于测试家族日记、权限共享、家族经验、成长守护和镜像 Agent。',
  'FAMILY003',
  20,
  '{"seed":"FAMILY003","defaultVisibility":"FAMILY_VISIBLE","careRoles":["OWNER","ADMIN","GUARDIAN"],"mirrorDisclaimer":true}'::jsonb,
  id,
  NOW() - INTERVAL '35 days',
  NOW()
FROM users
WHERE username = 'family003_grandpa'
ON CONFLICT (invite_code) DO UPDATE SET
  name = EXCLUDED.name,
  description = EXCLUDED.description,
  max_members = EXCLUDED.max_members,
  settings = EXCLUDED.settings,
  created_by = EXCLUDED.created_by,
  updated_at = NOW();

INSERT INTO family_members (family_id, user_id, role, permissions, joined_at)
SELECT f.id, u.id, m.role, m.permissions::jsonb, m.joined_at
FROM families f
JOIN (
  VALUES
    ('family003_grandpa', 'OWNER',    '{"relation":"外公","canManageMembers":true,"canViewCareRecords":true,"canContributeHeritage":true}'::text, NOW() - INTERVAL '35 days'),
    ('family003_mother',  'GUARDIAN', '{"relation":"母亲","canViewCareRecords":true,"canCreateCareReports":true,"canContributeHeritage":true}'::text, NOW() - INTERVAL '34 days'),
    ('family003_father',  'ADMIN',    '{"relation":"父亲","canManageMembers":true,"canViewCareRecords":true,"canContributeHeritage":true}'::text, NOW() - INTERVAL '34 days'),
    ('family003_aunt',    'MEMBER',   '{"relation":"姑姑","canViewCareRecords":false,"canContributeHeritage":true}'::text, NOW() - INTERVAL '33 days'),
    ('family003_student', 'STUDENT',  '{"relation":"孩子","learner":true,"grade":"七年级"}'::text, NOW() - INTERVAL '32 days')
) AS m(username, role, permissions, joined_at) ON TRUE
JOIN users u ON u.username = m.username
WHERE f.invite_code = 'FAMILY003'
ON CONFLICT (family_id, user_id) DO UPDATE SET
  role = EXCLUDED.role,
  permissions = EXCLUDED.permissions;

CREATE TABLE IF NOT EXISTS care_authorizations (
  id BIGSERIAL PRIMARY KEY,
  family_id BIGINT NOT NULL REFERENCES families(id) ON DELETE CASCADE,
  subject_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  caregiver_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  scope VARCHAR(40) NOT NULL DEFAULT 'GROWTH_GUARD',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  created_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
  updated_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
  expires_at TIMESTAMP,
  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
  UNIQUE(family_id, subject_user_id, caregiver_user_id, scope)
);

DELETE FROM care_authorizations
WHERE family_id = (SELECT id FROM families WHERE invite_code = 'FAMILY003');

INSERT INTO care_authorizations
  (family_id, subject_user_id, caregiver_user_id, scope, status, created_by, updated_by, created_at, updated_at)
SELECT f.id, subject.id, caregiver.id, scope.scope, 'ACTIVE', creator.id, creator.id, NOW() - INTERVAL '30 days', NOW()
FROM families f
JOIN users creator ON creator.username = 'family003_student'
JOIN users subject ON subject.username = 'family003_student'
JOIN (
  VALUES
    ('family003_mother'),
    ('family003_father'),
    ('family003_grandpa')
) AS c(username) ON TRUE
JOIN users caregiver ON caregiver.username = c.username
JOIN (
  VALUES
    ('DIARY'),
    ('GROWTH_GUARD')
) AS scope(scope) ON TRUE
WHERE f.invite_code = 'FAMILY003'
ON CONFLICT (family_id, subject_user_id, caregiver_user_id, scope) DO UPDATE SET
  status = 'ACTIVE',
  updated_by = EXCLUDED.updated_by,
  updated_at = NOW();

DELETE FROM growth_guard_reports
WHERE family_id = (SELECT id FROM families WHERE invite_code = 'FAMILY003')
  AND metadata ->> 'seed' = 'FAMILY003';

DELETE FROM growth_guard_records
WHERE family_id = (SELECT id FROM families WHERE invite_code = 'FAMILY003')
  AND metadata ->> 'seed' = 'FAMILY003';

DELETE FROM memory_entries
WHERE family_id = (SELECT id FROM families WHERE invite_code = 'FAMILY003')
  AND metadata ->> 'seed' = 'FAMILY003';

DELETE FROM diary_entries
WHERE family_id = (SELECT id FROM families WHERE invite_code = 'FAMILY003')
  AND metadata ->> 'seed' = 'FAMILY003';

INSERT INTO diary_entries
  (user_id, family_id, raw_text, structured, mood, tags, privacy_level, visibility, permission_scope, source, metadata, created_at, updated_at)
VALUES
  ((SELECT id FROM users WHERE username = 'family003_grandpa'), (SELECT id FROM families WHERE invite_code = 'FAMILY003'),
   '今天整理旧相册，想起一诺妈妈小时候学骑车摔了三次。那时我没有立刻扶她，而是先确认没有受伤，再让她自己站起来。家里的孩子遇到小挫折，最需要的是有人在旁边稳稳看着，而不是马上替他完成。',
   '{"title":"学骑车的旧照片","summary":"长辈关于挫折陪伴的经验","entryType":"FAMILY_STORY"}'::jsonb,
   '怀念', ARRAY['家族故事','挫折教育','陪伴'], 'FAMILY_VISIBLE', 'FAMILY_VISIBLE', '{"familyId":"FAMILY003"}'::jsonb, 'SEED',
   '{"seed":"FAMILY003","authorRole":"OWNER"}'::jsonb, NOW() - INTERVAL '14 days', NOW() - INTERVAL '14 days'),
  ((SELECT id FROM users WHERE username = 'family003_grandpa'), (SELECT id FROM families WHERE invite_code = 'FAMILY003'),
   '我年轻时牙不好，后来才知道小问题拖久了会变成大麻烦。孩子换牙和正畸窗口不要错过，半年一次检查比临时补救省心很多。',
   '{"title":"牙齿这件事不要拖","summary":"关于牙齿检查和正畸窗口的提醒","entryType":"ELDER_ADVICE"}'::jsonb,
   '认真', ARRAY['牙齿健康','长辈经验'], 'FAMILY_VISIBLE', 'CARE_VISIBLE', '{"careRoles":["OWNER","ADMIN","GUARDIAN"]}'::jsonb, 'SEED',
   '{"seed":"FAMILY003","authorRole":"OWNER"}'::jsonb, NOW() - INTERVAL '11 days', NOW() - INTERVAL '11 days'),
  ((SELECT id FROM users WHERE username = 'family003_mother'), (SELECT id FROM families WHERE invite_code = 'FAMILY003'),
   '一诺最近写作业时总把头压得很低，提醒后能坐直几分钟，但很快又弯下去。准备把台灯和椅子高度重新调一下，周末顺便约一次视力检查。',
   '{"title":"写作业姿势观察","summary":"母亲记录孩子坐姿和视力相关观察","entryType":"CARE_OBSERVATION"}'::jsonb,
   '担心', ARRAY['体态','视力','作业'], 'CARE_VISIBLE', 'CARE_VISIBLE', '{"careRoles":["OWNER","ADMIN","GUARDIAN"]}'::jsonb, 'SEED',
   '{"seed":"FAMILY003","target":"family003_student","followUpStatus":"WATCHING"}'::jsonb, NOW() - INTERVAL '9 days', NOW() - INTERVAL '9 days'),
  ((SELECT id FROM users WHERE username = 'family003_mother'), (SELECT id FROM families WHERE invite_code = 'FAMILY003'),
   '今天和一诺聊到班级里换座位，他说不想坐第一排，怕同学觉得自己太特殊。我先没有评价，只问他最担心哪一点。他说其实是怕看不清黑板被别人发现。',
   '{"title":"关于座位的小对话","summary":"孩子关于视力和同伴评价的真实担心","entryType":"DIARY"}'::jsonb,
   '理解', ARRAY['沟通','视力','情绪'], 'CARE_VISIBLE', 'CARE_VISIBLE', '{"careRoles":["OWNER","ADMIN","GUARDIAN"]}'::jsonb, 'SEED',
   '{"seed":"FAMILY003","target":"family003_student","privacyNote":"care-summary"}'::jsonb, NOW() - INTERVAL '7 days', NOW() - INTERVAL '7 days'),
  ((SELECT id FROM users WHERE username = 'family003_father'), (SELECT id FROM families WHERE invite_code = 'FAMILY003'),
   '晚上陪一诺打羽毛球，发现他跑动时有点含胸，挥拍动作也容易耸肩。比起直接纠正动作，先让他觉得运动是开心的，再慢慢加一点拉伸和核心训练。',
   '{"title":"羽毛球和含胸观察","summary":"父亲记录运动中的体态观察","entryType":"CARE_OBSERVATION"}'::jsonb,
   '平静', ARRAY['运动','体态','亲子'], 'CARE_VISIBLE', 'CARE_VISIBLE', '{"careRoles":["OWNER","ADMIN","GUARDIAN"]}'::jsonb, 'SEED',
   '{"seed":"FAMILY003","target":"family003_student","followUpStatus":"PENDING"}'::jsonb, NOW() - INTERVAL '6 days', NOW() - INTERVAL '6 days'),
  ((SELECT id FROM users WHERE username = 'family003_aunt'), (SELECT id FROM families WHERE invite_code = 'FAMILY003'),
   '今天视频里看一诺讲学校科技节，眼睛是亮的。他说想做一个自动浇花的小装置。建议家里别急着帮他做成品，可以让他先画草图、列材料，保留一点笨拙的探索。',
   '{"title":"科技节的自动浇花想法","summary":"姑姑记录孩子兴趣点","entryType":"DIARY"}'::jsonb,
   '开心', ARRAY['兴趣','创造力','科技节'], 'FAMILY_VISIBLE', 'FAMILY_VISIBLE', '{"familyId":"FAMILY003"}'::jsonb, 'SEED',
   '{"seed":"FAMILY003","target":"family003_student"}'::jsonb, NOW() - INTERVAL '5 days', NOW() - INTERVAL '5 days'),
  ((SELECT id FROM users WHERE username = 'family003_student'), (SELECT id FROM families WHERE invite_code = 'FAMILY003'),
   '今天数学小测有一道题我会做但是写慢了，最后没检查。其实我不是不会，就是一看到时间少就慌。下次想先把会的题写稳，再回头做难题。',
   '{"title":"数学小测后的复盘","summary":"孩子关于时间压力和做题策略的自我记录","entryType":"LEARNING_REFLECTION"}'::jsonb,
   '有点懊恼', ARRAY['学习','数学','复盘'], 'FAMILY_VISIBLE', 'FAMILY_VISIBLE', '{"familyId":"FAMILY003"}'::jsonb, 'SEED',
   '{"seed":"FAMILY003","selfReflection":true}'::jsonb, NOW() - INTERVAL '4 days', NOW() - INTERVAL '4 days'),
  ((SELECT id FROM users WHERE username = 'family003_student'), (SELECT id FROM families WHERE invite_code = 'FAMILY003'),
   '我不太想让大家一直说我的坐姿，但我知道他们是关心我。希望提醒的时候不要当着别人说，可以回家再说。',
   '{"title":"关于坐姿提醒的想法","summary":"孩子授权给监护人看的感受摘要","entryType":"DIARY"}'::jsonb,
   '别扭', ARRAY['体态','沟通','边界'], 'CARE_VISIBLE', 'CARE_VISIBLE', '{"careRoles":["OWNER","ADMIN","GUARDIAN"]}'::jsonb, 'SEED',
   '{"seed":"FAMILY003","privacyNote":"child-authorized-care-note"}'::jsonb, NOW() - INTERVAL '3 days', NOW() - INTERVAL '3 days'),
  ((SELECT id FROM users WHERE username = 'family003_student'), (SELECT id FROM families WHERE invite_code = 'FAMILY003'),
   '今天想到一个秘密目标：期末前把自动浇花装置做出来。如果做成了，想先给外公看。',
   '{"title":"自动浇花的小目标","summary":"孩子给家族成员看的兴趣目标","entryType":"GOAL"}'::jsonb,
   '期待', ARRAY['目标','创造力','家族'], 'FAMILY_VISIBLE', 'FAMILY_VISIBLE', '{"familyId":"FAMILY003"}'::jsonb, 'SEED',
   '{"seed":"FAMILY003","selfReflection":true}'::jsonb, NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days');

INSERT INTO memory_entries
  (user_id, family_id, type, scope, content, summary, importance, confidence, status, metadata, created_at, updated_at)
VALUES
  ((SELECT id FROM users WHERE username = 'family003_grandpa'), (SELECT id FROM families WHERE invite_code = 'FAMILY003'),
   'ELDER_ADVICE', 'FAMILY_VISIBLE',
   '孩子遇到挫折时，家长先确认安全，再给他自己站起来的时间。能自己完成的部分，不要因为心疼而替他做完。',
   '挫折陪伴：确认安全、留出尝试空间', 5, 0.9000, 'ACTIVE',
   '{"seed":"FAMILY003","scenario":"挫折、失败、比赛失利","source":"HERITAGE_ENTRY"}'::jsonb, NOW() - INTERVAL '13 days', NOW() - INTERVAL '13 days'),
  ((SELECT id FROM users WHERE username = 'family003_grandpa'), (SELECT id FROM families WHERE invite_code = 'FAMILY003'),
   'HEALTH_REMINDER', 'CARE_VISIBLE',
   '牙齿健康不要等疼了再处理。换牙、咬合、正畸窗口期要定期检查，半年一次比临时补救更可靠。',
   '牙齿检查和正畸窗口提醒', 5, 0.9200, 'ACTIVE',
   '{"seed":"FAMILY003","scenario":"牙齿健康、正畸、换牙","source":"HERITAGE_ENTRY"}'::jsonb, NOW() - INTERVAL '11 days', NOW() - INTERVAL '11 days'),
  ((SELECT id FROM users WHERE username = 'family003_mother'), (SELECT id FROM families WHERE invite_code = 'FAMILY003'),
   'GROWTH_RISK', 'CARE_VISIBLE',
   '一诺写作业时头压得低，可能和桌椅高度、视力、疲劳有关。提醒方式要私下、具体、短句，不要公开批评。',
   '坐姿和视力相关的观察提醒', 4, 0.8400, 'ACTIVE',
   '{"seed":"FAMILY003","scenario":"体态、视力、写作业","targetUsername":"family003_student","source":"HERITAGE_ENTRY"}'::jsonb, NOW() - INTERVAL '8 days', NOW() - INTERVAL '8 days'),
  ((SELECT id FROM users WHERE username = 'family003_father'), (SELECT id FROM families WHERE invite_code = 'FAMILY003'),
   'HEALTH_REMINDER', 'FAMILY_VISIBLE',
   '运动习惯要先保留快乐感，再逐步加入拉伸、核心和肩背放松。不要把所有运动都变成纠错。',
   '运动和体态管理的家庭原则', 4, 0.8200, 'ACTIVE',
   '{"seed":"FAMILY003","scenario":"运动、体态管理、亲子陪伴","source":"HERITAGE_ENTRY"}'::jsonb, NOW() - INTERVAL '6 days', NOW() - INTERVAL '6 days'),
  ((SELECT id FROM users WHERE username = 'family003_aunt'), (SELECT id FROM families WHERE invite_code = 'FAMILY003'),
   'VALUE', 'FAMILY_VISIBLE',
   '当孩子出现兴趣苗头时，家人先帮他把想法说清楚、画出来、拆成小步骤，不要立刻替他做成完美作品。',
   '保护兴趣的家庭做法', 4, 0.8000, 'ACTIVE',
   '{"seed":"FAMILY003","scenario":"兴趣、创造力、项目实践","source":"HERITAGE_ENTRY"}'::jsonb, NOW() - INTERVAL '5 days', NOW() - INTERVAL '5 days'),
  ((SELECT id FROM users WHERE username = 'family003_student'), (SELECT id FROM families WHERE invite_code = 'FAMILY003'),
   'LEARNING', 'FAMILY_VISIBLE',
   '数学小测中，时间压力会影响检查质量。适合练习先稳住会做题，再处理难题的顺序策略。',
   '数学小测时间管理复盘', 3, 0.7600, 'ACTIVE',
   '{"seed":"FAMILY003","subject":"math","scenario":"学习复盘","source":"DIARY"}'::jsonb, NOW() - INTERVAL '4 days', NOW() - INTERVAL '4 days');

INSERT INTO growth_guard_records
  (family_id, target_user_id, created_by, category, content, severity, observed_at, follow_up_at, visibility, status, metadata, created_at, updated_at)
VALUES
  ((SELECT id FROM families WHERE invite_code = 'FAMILY003'), (SELECT id FROM users WHERE username = 'family003_student'), (SELECT id FROM users WHERE username = 'family003_mother'),
   'VISION', '写作业时头压得低，提到怕看不清黑板被同学发现。建议调整桌椅灯光，并预约视力检查。', 4, CURRENT_DATE - 7, CURRENT_DATE + 7, 'CARE_VISIBLE', 'ACTIVE',
   '{"seed":"FAMILY003","followUpStatus":"WATCHING","source":"diary","suggestion":"调整桌椅灯光，预约视力检查"}'::jsonb, NOW() - INTERVAL '7 days', NOW() - INTERVAL '7 days'),
  ((SELECT id FROM families WHERE invite_code = 'FAMILY003'), (SELECT id FROM users WHERE username = 'family003_student'), (SELECT id FROM users WHERE username = 'family003_father'),
   'POSTURE', '羽毛球跑动时含胸、耸肩明显。先保持运动兴趣，再加入肩背放松和核心训练。', 3, CURRENT_DATE - 6, CURRENT_DATE + 10, 'CARE_VISIBLE', 'ACTIVE',
   '{"seed":"FAMILY003","followUpStatus":"PENDING","source":"sports-observation","suggestion":"每晚 8 分钟拉伸和靠墙站立"}'::jsonb, NOW() - INTERVAL '6 days', NOW() - INTERVAL '6 days'),
  ((SELECT id FROM families WHERE invite_code = 'FAMILY003'), (SELECT id FROM users WHERE username = 'family003_student'), (SELECT id FROM users WHERE username = 'family003_grandpa'),
   'DENTAL', '外公提醒换牙和正畸窗口不要拖，建议建立半年一次牙科检查习惯。', 3, CURRENT_DATE - 5, CURRENT_DATE + 20, 'CARE_VISIBLE', 'ACTIVE',
   '{"seed":"FAMILY003","followUpStatus":"PENDING","source":"elder-advice","suggestion":"确认最近一次牙科检查时间"}'::jsonb, NOW() - INTERVAL '5 days', NOW() - INTERVAL '5 days');

INSERT INTO growth_guard_reports
  (family_id, target_user_id, created_by, week_start, week_end, title, summary, visibility, status, report, metadata, created_at, updated_at)
VALUES
  ((SELECT id FROM families WHERE invite_code = 'FAMILY003'), (SELECT id FROM users WHERE username = 'family003_student'), (SELECT id FROM users WHERE username = 'family003_mother'),
   CURRENT_DATE - 7, CURRENT_DATE, '一诺本周成长守护摘要',
   '本周重点不是制造焦虑，而是把视力、坐姿和兴趣保护放进轻量家庭行动中。孩子愿意表达边界，说明沟通窗口仍然打开。',
   'CARE_VISIBLE', 'ACTIVE',
   '{
      "title":"一诺本周成长守护摘要",
      "summary":"孩子在学习复盘、体态提醒和兴趣表达上都有可跟进信号。建议家人用私下、具体、短句的方式提醒，避免公开评价。",
      "affirmations":["愿意复盘数学小测的时间策略","愿意表达对坐姿提醒方式的边界","对自动浇花项目有持续兴趣"],
      "concerns":["写作业时头压得低，需排查视力和桌椅灯光","运动中含胸耸肩，需要温和体态管理","牙齿检查时间需要确认"],
      "suggested_actions":["本周预约一次视力检查或至少完成家庭视力表自测","把书桌灯光、椅子高度和屏幕距离调到固定方案","用项目清单支持自动浇花想法，不替孩子完成成品"],
      "privacy_note":"该报告只输出授权范围内的成长摘要，不展开孩子未授权的情绪隐私。"
    }'::jsonb,
   '{"seed":"FAMILY003","targetUsername":"family003_student","generatedBy":"seed"}'::jsonb, NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day');

INSERT INTO mirror_agent_data (user_id, primary_family_id, traits, visibility, permission_scope, memory_scope, interaction_count, last_updated_at, created_at)
SELECT u.id, f.id, m.traits::jsonb, m.visibility, m.permission_scope::jsonb, m.memory_scope::jsonb, 0, NOW(), NOW()
FROM families f
JOIN (
  VALUES
    ('family003_grandpa', '{"tone":"稳、慢、有生活经验","values":["先确认安全","给孩子自己站起来的时间","小问题不要拖成大问题"]}'::text, 'FAMILY_VISIBLE', '{"familyId":"FAMILY003"}'::text, '{"diary":true,"memory":true}'::text),
    ('family003_mother',  '{"tone":"细致、尊重边界、重视观察","values":["私下提醒","不制造焦虑","把问题拆成小行动"]}'::text, 'CARE_VISIBLE', '{"requiresCareAuthorization":["DIARY","ALL"]}'::text, '{"diary":true,"memory":true}'::text),
    ('family003_father',  '{"tone":"行动导向、轻松、保护兴趣","values":["先保留快乐感","用运动建立连接","少说多陪"]}'::text, 'FAMILY_VISIBLE', '{"familyId":"FAMILY003"}'::text, '{"diary":true,"memory":true}'::text),
    ('family003_aunt',    '{"tone":"鼓励探索、重视表达","values":["保护兴趣苗头","先画草图再做成品","允许笨拙"]}'::text, 'FAMILY_VISIBLE', '{"familyId":"FAMILY003"}'::text, '{"diary":true,"memory":true}'::text),
    ('family003_student', '{"tone":"直接、有点敏感但愿意复盘","values":["希望被私下提醒","喜欢动手项目","想自己把事情做好"]}'::text, 'CARE_VISIBLE', '{"requiresCareAuthorization":["DIARY","ALL"]}'::text, '{"diary":true,"memory":true}'::text)
) AS m(username, traits, visibility, permission_scope, memory_scope) ON TRUE
JOIN users u ON u.username = m.username
WHERE f.invite_code = 'FAMILY003'
ON CONFLICT (user_id) DO UPDATE SET
  primary_family_id = EXCLUDED.primary_family_id,
  traits = EXCLUDED.traits,
  visibility = EXCLUDED.visibility,
  permission_scope = EXCLUDED.permission_scope,
  memory_scope = EXCLUDED.memory_scope,
  last_updated_at = NOW();

COMMIT;
