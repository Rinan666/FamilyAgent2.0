-- Seed a deterministic four-person family for local development and UI testing.
-- The password hash below is BCrypt for the development-only password 123456.
WITH seed_users(username, nickname, role, metadata) AS (
    VALUES
        ('guo001', '郭明远', 'ADMIN', '{"gender":"MALE","birthDate":"1982-05-16","demoSeed":"guo-family"}'::jsonb),
        ('guo002', '林雅静', 'USER',  '{"gender":"FEMALE","birthDate":"1984-09-08","demoSeed":"guo-family"}'::jsonb),
        ('guo003', '郭子轩', 'USER',  '{"gender":"MALE","birthDate":"2008-03-21","demoSeed":"guo-family"}'::jsonb),
        ('guo004', '郭雨桐', 'USER',  '{"gender":"FEMALE","birthDate":"2012-11-02","demoSeed":"guo-family"}'::jsonb)
)
INSERT INTO users (username, password_hash, nickname, role, status, metadata)
SELECT username,
       '$2a$10$QCxSaWt/FuXxcOoxfGC0LuUI1kSE/16rgcRyOTQWoDl8IX478O6om',
       nickname,
       role,
       'ACTIVE',
       metadata
FROM seed_users
ON CONFLICT (username) DO UPDATE
SET password_hash = EXCLUDED.password_hash,
    nickname = EXCLUDED.nickname,
    role = EXCLUDED.role,
    status = EXCLUDED.status,
    metadata = COALESCE(users.metadata, '{}'::jsonb) || EXCLUDED.metadata,
    updated_at = NOW();

INSERT INTO families (name, description, invite_code, max_members, settings, created_by)
SELECT '郭家测试家庭',
       '用于本地开发和家庭关系、个人记忆权限测试的四人家庭。',
       'GUO-DEMO-001',
       10,
       '{"demoSeed":"guo-family"}'::jsonb,
       id
FROM users
WHERE username = 'guo001'
ON CONFLICT (invite_code) DO UPDATE
SET name = EXCLUDED.name,
    description = EXCLUDED.description,
    max_members = EXCLUDED.max_members,
    settings = COALESCE(families.settings, '{}'::jsonb) || EXCLUDED.settings,
    created_by = EXCLUDED.created_by,
    updated_at = NOW();

WITH demo_family AS (
    SELECT id FROM families WHERE invite_code = 'GUO-DEMO-001'
),
seed_members(username, family_role, permissions) AS (
    VALUES
        ('guo001', 'OWNER',  '{"manageFamily":true,"manageMembers":true}'::jsonb),
        ('guo002', 'MEMBER', '{"manageFamily":false}'::jsonb),
        ('guo003', 'MEMBER', '{"manageFamily":false}'::jsonb),
        ('guo004', 'MEMBER', '{"manageFamily":false}'::jsonb)
)
INSERT INTO family_members (family_id, user_id, role, permissions)
SELECT family.id, users.id, members.family_role, members.permissions
FROM seed_members members
JOIN users ON users.username = members.username
CROSS JOIN demo_family family
ON CONFLICT (family_id, user_id) DO UPDATE
SET role = EXCLUDED.role,
    permissions = EXCLUDED.permissions;

WITH demo_family AS (
    SELECT id FROM families WHERE invite_code = 'GUO-DEMO-001'
),
seed_relationships(from_username, to_username, label, reverse_label, note) AS (
    VALUES
        ('guo001', 'guo002', '妻子',   '丈夫',   '我的妻子，孩子们的妈妈'),
        ('guo001', 'guo003', '大儿子', '爸爸',   '我的大儿子'),
        ('guo001', 'guo004', '二女儿', '爸爸',   '我的二女儿'),
        ('guo002', 'guo001', '丈夫',   '妻子',   '我的丈夫，孩子们的爸爸'),
        ('guo002', 'guo003', '大儿子', '妈妈',   '我的大儿子'),
        ('guo002', 'guo004', '二女儿', '妈妈',   '我的二女儿'),
        ('guo003', 'guo001', '爸爸',   '大儿子', '我的爸爸'),
        ('guo003', 'guo002', '妈妈',   '大儿子', '我的妈妈'),
        ('guo003', 'guo004', '妹妹',   '哥哥',   '我的妹妹'),
        ('guo004', 'guo001', '爸爸',   '二女儿', '我的爸爸'),
        ('guo004', 'guo002', '妈妈',   '二女儿', '我的妈妈'),
        ('guo004', 'guo003', '哥哥',   '妹妹',   '我的哥哥')
)
INSERT INTO family_relationships (
    family_id, from_user_id, to_user_id, label, reverse_label, note, created_by, updated_by
)
SELECT family.id,
       viewer.id,
       target.id,
       relationships.label,
       relationships.reverse_label,
       relationships.note,
       viewer.id,
       viewer.id
FROM seed_relationships relationships
JOIN users viewer ON viewer.username = relationships.from_username
JOIN users target ON target.username = relationships.to_username
CROSS JOIN demo_family family
ON CONFLICT (family_id, from_user_id, to_user_id) DO UPDATE
SET label = EXCLUDED.label,
    reverse_label = EXCLUDED.reverse_label,
    note = EXCLUDED.note,
    updated_by = EXCLUDED.updated_by,
    updated_at = NOW();

DROP TABLE IF EXISTS demo_prepared_memories;
DROP TABLE IF EXISTS demo_memory_seed;

CREATE TEMP TABLE demo_memory_seed (
    username VARCHAR(50) NOT NULL,
    sequence_no INTEGER NOT NULL,
    type VARCHAR(50) NOT NULL,
    title VARCHAR(120) NOT NULL,
    content TEXT NOT NULL,
    summary TEXT NOT NULL,
    importance INTEGER NOT NULL,
    tags TEXT[] NOT NULL
);

INSERT INTO demo_memory_seed (username, sequence_no, type, title, content, summary, importance, tags)
VALUES
    ('guo001', 1,  'PREFERENCE',  '晨间喝茶习惯',       '我早晨习惯喝一杯不加糖的龙井茶，再开始处理当天的事情。',                       '早晨喜欢喝无糖龙井茶。',                         3, ARRAY['饮食', '习惯']),
    ('guo001', 2,  'EXPERIENCE',  '第一次全家自驾游',   '2019年暑假我带全家自驾去了青岛，孩子们第一次一起看到了大海。',                   '2019年全家第一次自驾去青岛看海。',               5, ARRAY['旅行', '家庭']),
    ('guo001', 3,  'KNOWLEDGE',   '家庭电器维修经验',   '家里的插座或小电器出现问题时，我会先断电，再用万用表检查，安全永远放在第一位。',   '维修电器前必须先断电并检查。',                   4, ARRAY['维修', '安全']),
    ('guo001', 4,  'PLAN',        '年度体检计划',       '我计划每年十月完成一次常规体检，并重点关注血压和颈椎情况。',                     '每年十月体检并关注血压和颈椎。',                 4, ARRAY['健康', '计划']),
    ('guo001', 5,  'INSIGHT',     '与孩子沟通的体会',   '和孩子讨论学习问题时，先听完他们的想法，再给建议，沟通效果会更好。',             '先倾听再建议更适合亲子沟通。',                   5, ARRAY['亲子', '沟通']),
    ('guo001', 6,  'NOTE',        '常用物品位置',       '备用车钥匙放在书房第二层抽屉里的蓝色收纳盒中。',                                 '备用车钥匙在书房蓝色收纳盒。',                   2, ARRAY['家庭', '物品']),
    ('guo001', 7,  'OBSERVATION', '大儿子的学习节奏',   '子轩在晚饭后休息二十分钟再学习时，专注度通常更高。',                             '子轩晚饭后短暂休息再学习更专注。',               4, ARRAY['大儿子', '学习']),
    ('guo001', 8,  'PREFERENCE',  '周末下厨偏好',       '周六有空时我喜欢给家人做红烧鱼，口味会少盐少辣。',                               '周六喜欢做少盐少辣的红烧鱼。',                   3, ARRAY['烹饪', '家庭']),
    ('guo001', 9,  'EXPERIENCE',  '第一份工作的经历',   '我毕业后的第一份工作是软件实施工程师，那段经历让我学会了耐心排查问题。',         '第一份工作培养了耐心排查问题的习惯。',           4, ARRAY['工作', '成长']),
    ('guo001', 10, 'PLAN',        '结婚纪念日安排',     '今年结婚纪念日准备提前订一家安静的餐厅，并和雅静一起散步。',                     '结婚纪念日准备餐厅和散步安排。',                 5, ARRAY['夫妻', '纪念日']),
    ('guo002', 1,  'PREFERENCE',  '喜欢的花',           '我最喜欢淡黄色的向日葵，家里花瓶适合每次插五到七枝。',                           '喜欢淡黄色向日葵。',                             3, ARRAY['花卉', '偏好']),
    ('guo002', 2,  'KNOWLEDGE',   '家庭月度预算方法',   '每月先预留固定储蓄和教育支出，再安排日常开销，月底复盘一次。',                   '先储蓄再消费并做月末复盘。',                     4, ARRAY['财务', '家庭']),
    ('guo002', 3,  'EXPERIENCE',  '外婆教的饺子馅',     '白菜猪肉馅要先给白菜撒盐出水，再和肉馅混合，口感会更好。',                       '白菜先出水再调饺子馅。',                         4, ARRAY['食谱', '传承']),
    ('guo002', 4,  'PLAN',        '每周瑜伽安排',       '我计划每周二和周四晚上练四十分钟瑜伽，周末做一次拉伸。',                         '每周两次瑜伽和一次拉伸。',                       3, ARRAY['健康', '运动']),
    ('guo002', 5,  'OBSERVATION', '二女儿的绘画兴趣',   '雨桐在画动物和自然场景时很投入，适合多准备水彩纸和暖色颜料。',                   '雨桐喜欢画动物和自然场景。',                     5, ARRAY['二女儿', '绘画']),
    ('guo002', 6,  'PREFERENCE',  '旅行住宿偏好',       '全家旅行时我更喜欢安静、带早餐并且离公共交通较近的住宿。',                       '旅行偏好安静且交通方便的住宿。',                 3, ARRAY['旅行', '偏好']),
    ('guo002', 7,  'NOTE',        '季节性过敏提醒',     '每年春季花粉多的时候我容易鼻子过敏，外出需要带好口罩。',                         '春季花粉期容易过敏。',                           5, ARRAY['健康', '过敏']),
    ('guo002', 8,  'EXPERIENCE',  '睡前共读时光',       '孩子们小时候，我常在睡前给他们读二十分钟故事，这是全家都珍惜的回忆。',           '孩子小时候有固定的睡前共读时间。',               5, ARRAY['亲子', '阅读']),
    ('guo002', 9,  'INSIGHT',     '家庭分工的体会',     '把家务事项明确说出来并轮流承担，比临时催促更能减少误会。',                       '明确轮流分工可以减少家庭误会。',                 4, ARRAY['家庭', '沟通']),
    ('guo002', 10, 'PLAN',        '整理家庭相册',       '暑假前要把近三年的家庭照片按年份和活动分类，挑选一部分制作纸质相册。',           '暑假前整理近三年家庭照片。',                     4, ARRAY['照片', '计划']),
    ('guo003', 1,  'PREFERENCE',  '篮球运动偏好',       '我最喜欢打控球后卫，周日下午通常会和同学在社区球场练球。',                       '喜欢打控球后卫。',                               4, ARRAY['篮球', '运动']),
    ('guo003', 2,  'EXPERIENCE',  '数学竞赛经历',       '初二参加校内数学竞赛时我获得二等奖，也发现自己在几何题上需要加强。',             '数学竞赛二等奖，几何仍需加强。',                 5, ARRAY['数学', '比赛']),
    ('guo003', 3,  'KNOWLEDGE',   '编程学习方法',       '学习新算法时，先手写一个小例子，再编码验证，比只看答案更容易理解。',             '算法学习要先手推再编码。',                       4, ARRAY['编程', '学习']),
    ('guo003', 4,  'PREFERENCE',  '不喜欢香菜',         '我不喜欢香菜的味道，点面条或火锅蘸料时希望不要放香菜。',                         '饮食中不要放香菜。',                             3, ARRAY['饮食', '偏好']),
    ('guo003', 5,  'EXPERIENCE',  '和爸爸骑行',         '去年秋天我和爸爸沿河骑行了三十公里，返程时虽然很累但很有成就感。',               '和爸爸完成过三十公里骑行。',                     5, ARRAY['爸爸', '骑行']),
    ('guo003', 6,  'PLAN',        '期末复习目标',       '期末前每天安排四十分钟复习英语词汇，并把数学错题重新做一遍。',                   '每天复习英语并重做数学错题。',                   4, ARRAY['考试', '计划']),
    ('guo003', 7,  'NOTE',        '妹妹的生日礼物',     '雨桐下次生日想要一盒彩色马克笔，我准备提前用零花钱购买。',                       '准备给妹妹买彩色马克笔。',                       4, ARRAY['妹妹', '生日']),
    ('guo003', 8,  'INSIGHT',     '保持专注的方法',     '手机放到客厅并使用二十五分钟计时学习时，我更不容易分心。',                       '远离手机并定时学习更专注。',                     4, ARRAY['学习', '专注']),
    ('guo003', 9,  'PREFERENCE',  '睡眠环境偏好',       '睡觉时我喜欢房间稍微凉一点，并保留很轻的白噪音。',                               '偏好微凉且有白噪音的睡眠环境。',                 2, ARRAY['睡眠', '偏好']),
    ('guo003', 10, 'PLAN',        '暑期编程项目',       '暑假想完成一个家庭值日安排小程序，让每个人都能查看和勾选任务。',                 '暑假制作家庭值日小程序。',                       5, ARRAY['编程', '项目']),
    ('guo004', 1,  'PREFERENCE',  '水彩画偏好',         '我最喜欢用暖色水彩画小动物，尤其喜欢画猫和兔子。',                               '喜欢用暖色水彩画小动物。',                       4, ARRAY['绘画', '动物']),
    ('guo004', 2,  'EXPERIENCE',  '第一次钢琴表演',     '第一次参加钢琴汇演时我很紧张，但弹完后听到家人鼓掌特别开心。',                   '第一次钢琴汇演获得家人鼓励。',                   5, ARRAY['钢琴', '成长']),
    ('guo004', 3,  'PREFERENCE',  '喜欢的甜点',         '我最喜欢草莓奶油蛋糕，但奶油不要太甜，草莓要多一点。',                           '喜欢少糖多草莓的蛋糕。',                         3, ARRAY['甜点', '偏好']),
    ('guo004', 4,  'EXPERIENCE',  '动物园家庭日',       '去年春天全家去动物园时，我第一次近距离看到了长颈鹿。',                           '和家人去动物园看长颈鹿。',                       4, ARRAY['旅行', '家庭']),
    ('guo004', 5,  'KNOWLEDGE',   '阅读生词的方法',     '读故事书遇到生词时，先根据上下文猜意思，再查词典会记得更牢。',                   '生词先猜后查更容易记住。',                       4, ARRAY['阅读', '学习']),
    ('guo004', 6,  'PLAN',        '数学练习目标',       '这个月要把分数应用题每天练两道，并把不会的题标记后问哥哥。',                     '每天练两道分数应用题。',                         4, ARRAY['数学', '计划']),
    ('guo004', 7,  'EXPERIENCE',  '和妈妈烤饼干',       '我和妈妈一起烤过小熊形状的黄油饼干，负责压模和装饰糖粒。',                       '和妈妈一起做过小熊饼干。',                       5, ARRAY['妈妈', '烘焙']),
    ('guo004', 8,  'PREFERENCE',  '想养的宠物',         '如果以后条件允许，我想养一只性格温和的橘猫，并给它取名叫橙子。',                 '希望以后养一只叫橙子的橘猫。',                   3, ARRAY['宠物', '愿望']),
    ('guo004', 9,  'OBSERVATION', '上台紧张表现',       '上台前如果做三次深呼吸并先看向家人，我会感觉没有那么紧张。',                     '深呼吸和看到家人能缓解紧张。',                   4, ARRAY['情绪', '成长']),
    ('guo004', 10, 'PLAN',        '爸爸生日卡片',       '爸爸下次生日时，我想亲手画一张全家福卡片送给他。',                               '准备亲手画全家福生日卡片。',                     5, ARRAY['爸爸', '生日']);

CREATE TEMP TABLE demo_prepared_memories AS
SELECT users.id AS user_id,
       seed.sequence_no,
       seed.type,
       seed.title,
       seed.content,
       seed.summary,
       seed.importance,
       seed.tags,
       'guo-family:' || seed.username || ':' || seed.sequence_no AS seed_key,
       CASE MOD(ABS(HASHTEXT(seed.username || ':' || seed.sequence_no)::BIGINT), 4)
           WHEN 0 THEN 'PRIVATE'
           WHEN 1 THEN 'ALL_FAMILIES_VISIBLE'
           WHEN 2 THEN 'SELECTED_FAMILIES_VISIBLE'
           ELSE 'CARE_VISIBLE'
       END AS visibility,
       NOW() - (seed.sequence_no || ' days')::INTERVAL AS occurred_at
FROM demo_memory_seed seed
JOIN users ON users.username = seed.username;

UPDATE memory_entries memory
SET user_id = seed.user_id,
    family_id = NULL,
    library_kind = 'PERSONAL',
    title = seed.title,
    related_user_id = NULL,
    subject = NULL,
    type = seed.type,
    scope = seed.visibility,
    content = seed.content,
    summary = seed.summary,
    importance = seed.importance,
    confidence = 0.8500,
    source_session_id = NULL,
    status = 'ACTIVE',
    occurred_at = seed.occurred_at,
    origin_type = NULL,
    origin_id = NULL,
    tags = seed.tags,
    metadata = jsonb_build_object(
        'source', 'PERSONAL_ENTRY',
        'seedDataset', 'guo-family',
        'seedKey', seed.seed_key
    ),
    updated_at = NOW()
FROM demo_prepared_memories seed
WHERE memory.metadata->>'seedKey' = seed.seed_key;

INSERT INTO memory_entries (
    user_id, family_id, library_kind, title, related_user_id, subject, type, scope,
    content, summary, importance, confidence, source_session_id, status, occurred_at,
    origin_type, origin_id, tags, metadata, created_at, updated_at
)
SELECT seed.user_id,
       NULL,
       'PERSONAL',
       seed.title,
       NULL,
       NULL,
       seed.type,
       seed.visibility,
       seed.content,
       seed.summary,
       seed.importance,
       0.8500,
       NULL,
       'ACTIVE',
       seed.occurred_at,
       NULL,
       NULL,
       seed.tags,
       jsonb_build_object(
           'source', 'PERSONAL_ENTRY',
           'seedDataset', 'guo-family',
           'seedKey', seed.seed_key
       ),
       seed.occurred_at,
       NOW()
FROM demo_prepared_memories seed
WHERE NOT EXISTS (
    SELECT 1
    FROM memory_entries memory
    WHERE memory.metadata->>'seedKey' = seed.seed_key
);

DELETE FROM personal_memory_family_grants grants
USING memory_entries memory
WHERE grants.memory_id = memory.id
  AND memory.metadata->>'seedDataset' = 'guo-family';

INSERT INTO personal_memory_family_grants (memory_id, family_id, granted_by)
SELECT memory.id, family.id, memory.user_id
FROM memory_entries memory
CROSS JOIN families family
WHERE memory.metadata->>'seedDataset' = 'guo-family'
  AND family.invite_code = 'GUO-DEMO-001'
  AND memory.scope IN ('ALL_FAMILIES_VISIBLE', 'SELECTED_FAMILIES_VISIBLE')
ON CONFLICT (memory_id, family_id) DO UPDATE
SET granted_by = EXCLUDED.granted_by;

DROP TABLE demo_prepared_memories;
DROP TABLE demo_memory_seed;
