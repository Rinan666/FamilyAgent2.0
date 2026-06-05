-- 教材同步元数据迁移
-- 用法：
--   docker exec -i familyagent-postgres psql -U familyagent -d familyagent < scripts/migrate-textbook-sync.sql
--
-- 当前先复用 knowledge_points.metadata，避免新增表影响题库、测评和 BKT 链路。

BEGIN;

UPDATE knowledge_points
SET
    metadata = COALESCE(metadata, '{}'::jsonb) || jsonb_build_object(
        'textbookVersion', 'PEP',
        'textbookName', '人教版数学',
        'volume', '七年级上册',
        'chapterCode', 'G7A-C1',
        'chapterName', '有理数与整式初步',
        'sectionName', name,
        'lessonOrder', sort_order
    ),
    updated_at = NOW()
WHERE id IN (1, 2, 3);

UPDATE knowledge_points
SET
    metadata = COALESCE(metadata, '{}'::jsonb) || jsonb_build_object(
        'textbookVersion', 'PEP',
        'textbookName', '人教版数学',
        'volume', '七年级上册',
        'chapterCode', 'G7A-C2',
        'chapterName', '一元一次方程与不等式',
        'sectionName', name,
        'lessonOrder', sort_order
    ),
    updated_at = NOW()
WHERE id IN (5, 6, 7);

UPDATE knowledge_points
SET
    metadata = COALESCE(metadata, '{}'::jsonb) || jsonb_build_object(
        'textbookVersion', 'PEP',
        'textbookName', '人教版数学',
        'volume', '七年级上册',
        'chapterCode', 'G7A-C3',
        'chapterName', '几何初步',
        'sectionName', name,
        'lessonOrder', sort_order
    ),
    updated_at = NOW()
WHERE id IN (9, 10);

UPDATE knowledge_points
SET
    metadata = COALESCE(metadata, '{}'::jsonb) || jsonb_build_object(
        'textbookVersion', 'PEP',
        'textbookName', '人教版数学',
        'volume', '八年级上册',
        'chapterCode', 'G8A-C1',
        'chapterName', '整式乘除与因式分解',
        'sectionName', name,
        'lessonOrder', sort_order
    ),
    updated_at = NOW()
WHERE id = 4;

UPDATE knowledge_points
SET
    metadata = COALESCE(metadata, '{}'::jsonb) || jsonb_build_object(
        'textbookVersion', 'PEP',
        'textbookName', '人教版数学',
        'volume', '八年级上册',
        'chapterCode', 'G8A-C2',
        'chapterName', '二元一次方程组',
        'sectionName', name,
        'lessonOrder', sort_order
    ),
    updated_at = NOW()
WHERE id = 8;

UPDATE knowledge_points
SET
    metadata = COALESCE(metadata, '{}'::jsonb) || jsonb_build_object(
        'textbookVersion', 'PEP',
        'textbookName', '人教版数学',
        'volume', '八年级上册',
        'chapterCode', 'G8A-C3',
        'chapterName', '三角形与四边形',
        'sectionName', name,
        'lessonOrder', sort_order
    ),
    updated_at = NOW()
WHERE id IN (11, 12);

UPDATE knowledge_points
SET
    metadata = COALESCE(metadata, '{}'::jsonb) || jsonb_build_object(
        'textbookVersion', 'PEP',
        'textbookName', '人教版数学',
        'volume', '八年级上册',
        'chapterCode', 'G8A-C4',
        'chapterName', '平面直角坐标系与一次函数',
        'sectionName', name,
        'lessonOrder', sort_order
    ),
    updated_at = NOW()
WHERE id IN (13, 14, 15);

COMMIT;
