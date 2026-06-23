INSERT INTO knowledge_document
  (title, content, version, updated_by_subject, updated_by_name, created_at, updated_at)
VALUES
  ('社团活动管理手册', '# 社团活动管理手册\n\n用于统一活动立项、审批、执行和复盘流程。', 1, 'system', '系统', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('新成员入门指南', '# 新成员入门指南\n\n欢迎加入 OpenAtom 社团。', 1, 'system', '系统', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO wiki_page
  (parent_id, title, type, content, sort_order, updated_by_name, created_at, updated_at)
VALUES
  (NULL, '活动管理', 'category', '', 1, '系统', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  (NULL, '技术资料', 'category', '', 2, '系统', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  (NULL, '项目文档', 'category', '', 3, '系统', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  (NULL, '会议记录', 'category', '', 4, '系统', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO wiki_page
  (parent_id, title, type, content, sort_order, updated_by_name, created_at, updated_at)
SELECT id, '活动立项流程', 'page',
       '# 活动立项流程\n\n负责人应在计划执行日期至少两周前提交提案，包含目标、流程、预算和风险预案。',
       1, '系统', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM wiki_page WHERE title = '活动管理';
