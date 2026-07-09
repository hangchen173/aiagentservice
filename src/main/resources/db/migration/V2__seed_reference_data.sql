INSERT INTO sys_users (username, password, display_name, role, online)
SELECT 'admin', '{noop}admin123', '系统管理员', 'ADMIN', false
WHERE NOT EXISTS (SELECT 1 FROM sys_users WHERE username = 'admin');

INSERT INTO sys_users (username, password, display_name, role, online)
SELECT 'agent', '{noop}agent123', '客服坐席', 'AGENT', true
WHERE NOT EXISTS (SELECT 1 FROM sys_users WHERE username = 'agent');

INSERT INTO sys_users (username, password, display_name, role, online)
SELECT 'visitor', '{noop}visitor123', '演示访客', 'VISITOR', false
WHERE NOT EXISTS (SELECT 1 FROM sys_users WHERE username = 'visitor');

INSERT INTO ai_models (provider, model_name, temperature, max_tokens, enabled)
SELECT 'DASHSCOPE', 'qwen3.7-plus', 0.70, 1200, true
WHERE NOT EXISTS (
    SELECT 1 FROM ai_models WHERE provider = 'DASHSCOPE' AND model_name = 'qwen3.7-plus'
);

INSERT INTO ai_agents (code, name, scenario, prompt, priority, enabled, trigger_keywords)
SELECT 'general', '通用客服智能体', '通用咨询与兜底回复',
       '你是 NexusMind 通用客服智能体。请用简洁、友善、可执行的中文回答用户问题。',
       100, true, '你好,咨询,帮助'
WHERE NOT EXISTS (SELECT 1 FROM ai_agents WHERE code = 'general');

INSERT INTO ai_agents (code, name, scenario, prompt, priority, enabled, trigger_keywords)
SELECT 'presales', '售前咨询智能体', '产品、价格、方案咨询',
       '你是 NexusMind 售前咨询智能体。请识别用户需求，给出清晰方案，并在必要时建议联系人工顾问。',
       20, true, '价格,方案,购买,试用,报价'
WHERE NOT EXISTS (SELECT 1 FROM ai_agents WHERE code = 'presales');

INSERT INTO ai_agents (code, name, scenario, prompt, priority, enabled, trigger_keywords)
SELECT 'aftersales', '售后支持智能体', '订单、故障、退款等售后问题',
       '你是 NexusMind 售后支持智能体。请先安抚用户，再收集订单号、问题现象和期望处理方式。',
       30, true, '订单,故障,退款,退货,售后,无法使用'
WHERE NOT EXISTS (SELECT 1 FROM ai_agents WHERE code = 'aftersales');

INSERT INTO ai_agents (code, name, scenario, prompt, priority, enabled, trigger_keywords)
SELECT 'complaint', '投诉处理智能体', '投诉与升级处理',
       '你是 NexusMind 投诉处理智能体。请表达歉意，确认问题事实，并建议转人工或创建工单跟进。',
       10, true, '投诉,不满意,举报,差评,人工'
WHERE NOT EXISTS (SELECT 1 FROM ai_agents WHERE code = 'complaint');

INSERT INTO ai_agents (code, name, scenario, prompt, priority, enabled, trigger_keywords)
SELECT 'handoff', '转人工判断智能体', '识别需要人工介入的会话',
       '你是 NexusMind 转人工判断智能体。若用户明确要求人工、投诉、退款、升级处理，请建议转人工并生成工单。',
       5, true, '人工,转人工,客服,升级,主管'
WHERE NOT EXISTS (SELECT 1 FROM ai_agents WHERE code = 'handoff');
