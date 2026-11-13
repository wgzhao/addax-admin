-- Curated demo content for buyer demos and documentation screenshots.
--
-- Unlike reset.sh (which clones a real database and layers a few scenarios on
-- top), this script replaces the collection content with a deterministic,
-- entirely fictional enterprise estate: 12 sources, 82 tables, three targets,
-- one year of daily statistics, execution snapshots, journeys, logs and a
-- curated notification inbox.
--
-- Safe to run repeatedly. It keeps users, dictionaries, targets' neighbours and
-- audit history untouched, and re-anchors every relative date to current_date.
--
-- Usage:
--   psql -v ON_ERROR_STOP=1 -d addax_admin_showcase -f scripts/demo/seed.sql
--
-- After running, point the backend at the database (DB_NAME=addax_admin_showcase)
-- and restart. Re-run this script after midnight to move the demo to "today".

BEGIN;

-- ---------------------------------------------------------------------------
-- 1. Clear collection content (FK-safe order)
-- ---------------------------------------------------------------------------
DELETE FROM addax_log;
DELETE FROM etl_failure;
DELETE FROM etl_table_change_log;
DELETE FROM etl_column_mapping;
DELETE FROM etl_job;
DELETE FROM etl_column;
DELETE FROM etl_jour;
DELETE FROM etl_statistic;
DELETE FROM etl_job_queue;
DELETE FROM etl_execution;
DELETE FROM etl_table;
DELETE FROM etl_source;
DELETE FROM etl_target;
DELETE FROM user_notification;
DELETE FROM schema_change_log;
DELETE FROM risk_log;

-- ---------------------------------------------------------------------------
-- 2. Sources: an enterprise landscape with mixed engines and schedules
-- ---------------------------------------------------------------------------
INSERT INTO etl_source
  (id, name, url, username, pass, start_at, remark, enabled, max_concurrency, db_type, calendar, connect_config)
VALUES
  (101, '客户中心',     'jdbc:mysql://10.20.8.11:3306/crm',        'etl_read', '******', '01:10', '集团客户主数据与销售过程数据', true, 10, 'mysql',         NULL,        '{}'::jsonb),
  (102, '交易中台',     'jdbc:mysql://10.20.8.12:3306/oms',        'etl_read', '******', '01:20', '订单、支付、退款与履约交易数据', true, 12, 'mysql',         NULL,        '{}'::jsonb),
  (103, '门店零售',     'jdbc:mysql://10.20.8.13:3306/pos',        'etl_read', '******', '02:00', '线下门店 POS 交易与会员数据', true,  8, 'mysql',         NULL,        '{}'::jsonb),
  (104, '仓储物流',     'jdbc:mysql://10.20.8.14:3306/wms',        'etl_read', '******', '02:15', '库存、出入库与承运数据', true,      8, 'mysql',         NULL,        '{}'::jsonb),
  (105, '集团 ERP',     'jdbc:mysql://10.20.8.15:3306/erp',        'etl_read', '******', '02:30', '物料、采购、生产与组织主数据', true,  8, 'mysql',         'WORK_DAY',  '{}'::jsonb),
  (106, '财务共享',     'jdbc:mysql://10.20.8.16:3306/fin',        'etl_read', '******', '02:45', '总账、应收应付与成本中心', true,      6, 'mysql',         'WORK_DAY',  '{}'::jsonb),
  (107, '营销平台',     'jdbc:postgresql://10.20.9.21:5432/marketing',                                  'etl_read', '******', '03:00', '活动、渠道与线索评分数据', true,      6, 'postgresql',    NULL,        '{}'::jsonb),
  (108, '供应链协同',   'jdbc:sqlserver://10.20.9.22:1433;databaseName=scm',                           'etl_read', '******', '03:10', '采购协同、交付计划与结算数据', true,  6, 'sqlserver',     NULL,        '{}'::jsonb),
  (109, '客户画像',     'mongodb://10.20.10.31:27017/customer_profile?authSource=admin',                NULL,       NULL,     '03:20', '标签画像与行为事件明细', true,        10, 'mongodb',       NULL,        '{"authDb":"admin"}'::jsonb),
  (110, '埋点日志集群', 'http://10.20.11.41:9200,http://10.20.11.42:9200',                            NULL,       NULL,     '03:30', 'APP、小程序与站点埋点日志', true,    16, 'elasticsearch', NULL,        '{}'::jsonb),
  (111, '集团数据仓库', 'jdbc:hive2://10.20.12.51:10000/warehouse',                                    'hive',     '******', '04:00', 'DWD/DWS/ADS 分层加工数据', true,     8, 'hive',          'TRADE_DAY', '{}'::jsonb),
  (112, '人力资源',     'jdbc:mysql://10.20.8.17:3306/hr',         'etl_read', '******', '02:50', '员工、组织与考勤数据（已停用调度）', false, 4, 'mysql',    'WORK_DAY',  '{}'::jsonb);

-- ---------------------------------------------------------------------------
-- 3. Targets
-- ---------------------------------------------------------------------------
INSERT INTO etl_target (id, code, name, target_type, enabled, is_default, remark, writer_template_key, connect_config)
VALUES
  (1, 'HDFS_EDW',  'HDFS 数仓',   'HDFS',  true,  true,  'ODS 层默认落 HDFS，ORC + SNAPPY', 'wH', '{"url":"jdbc:hive2://10.20.12.51:10000/warehouse","username":"hive","password":"******","defaultFS":"hdfs://nameservice1","hdfsPrefix":"/warehouse","storageFormat":"orc","compressFormat":"snappy"}'),
  (2, 'DORIS_ODS', 'Doris 数仓',  'MYSQL', true,  false, 'DWD/DWS/ADS 层，兼容 MySQL 协议', 'wR', '{"url":"jdbc:mysql://10.20.12.61:9030","username":"etl_write","password":"******"}'),
  (3, 'MYSQL_BIZ', '业务回写库',  'MYSQL', true,  false, '小维表回写业务库', 'wR', '{"url":"jdbc:mysql://10.20.8.20:3306","username":"etl_write","password":"******"}');

-- ---------------------------------------------------------------------------
-- 4. Tables: 82 definitions across the 12 sources
--    weight = GiB collected per day, base_secs = typical runtime
-- ---------------------------------------------------------------------------
CREATE TEMP TABLE seed_table (
  id            bigint PRIMARY KEY,
  sid           int NOT NULL,
  source_db     varchar(64) NOT NULL,
  source_table  varchar(255) NOT NULL,
  tbl_comment   varchar(500),
  category      varchar(30) NOT NULL,
  weight        numeric NOT NULL,
  base_secs     int NOT NULL,
  rec_bytes     int NOT NULL,
  status        char(1) NOT NULL,
  target_id     int NOT NULL,
  target_db     varchar(50) NOT NULL,
  ord           int NOT NULL
) ON COMMIT DROP;

INSERT INTO seed_table
  (id, sid, source_db, source_table, tbl_comment, category, weight, base_secs, rec_bytes, status, target_id, target_db, ord)
VALUES
  -- CRM
  (1001, 101, 'crm', 'crm_customer',          '客户主档',                 'customer',   0.32,  45, 900, 'Y', 1, 'ods_crm', 1),
  (1002, 101, 'crm', 'crm_contact',           '联系人信息',               'customer',   0.18,  28, 700, 'Y', 1, 'ods_crm', 2),
  (1003, 101, 'crm', 'crm_lead',              '销售线索',                 'generic',    0.12,  22, 600, 'Y', 1, 'ods_crm', 3),
  (1004, 101, 'crm', 'crm_opportunity',       '商机跟进',                 'generic',    0.10,  20, 650, 'Y', 1, 'ods_crm', 4),
  (1005, 101, 'crm', 'crm_activity',          '客户活动记录',             'log_event',  0.35,  38, 500, 'Y', 1, 'ods_crm', 5),
  (1006, 101, 'crm', 'crm_tag',               '客户标签定义',             'generic',    0.02,  10, 300, 'Y', 1, 'ods_crm', 6),
  (1007, 101, 'crm', 'crm_customer_tag',      '客户标签关系',             'generic',    0.22,  26, 260, 'Y', 1, 'ods_crm', 7),
  (1008, 101, 'crm', 'crm_follow_up',         '销售跟进记录',             'log_event',  0.28,  32, 520, 'Y', 1, 'ods_crm', 8),
  (1009, 101, 'crm', 'crm_contract',          '客户合同',                 'finance',    0.08,  22, 800, 'Y', 1, 'ods_crm', 9),
  (1010, 101, 'crm', 'crm_service_ticket',    '服务工单',                 'generic',    0.06,  18, 600, 'Y', 1, 'ods_crm', 10),
  (1011, 101, 'crm', 'crm_campaign_member',   '营销活动名单',             'generic',    0.15,  28, 420, 'Y', 1, 'ods_crm', 11),
  (1012, 101, 'crm', 'crm_address',           '客户地址',                 'generic',    0.04,  12, 380, 'Y', 1, 'ods_crm', 12),
  -- OMS
  (1013, 102, 'oms', 'oms_order',             '订单主表',                 'order_main', 5.20, 120, 520, 'E', 1, 'ods_oms', 1),
  (1014, 102, 'oms', 'oms_order_item',        '订单明细',                 'order_item', 7.80, 280, 380, 'Y', 1, 'ods_oms', 2),
  (1015, 102, 'oms', 'oms_payment',           '支付流水',                 'finance',    1.60,  60, 340, 'Y', 1, 'ods_oms', 3),
  (1016, 102, 'oms', 'oms_refund',            '退款单',                   'generic',    0.22,  24, 420, 'Y', 1, 'ods_oms', 4),
  (1017, 102, 'oms', 'oms_refund_item',       '退款明细',                 'order_item', 0.15,  22, 360, 'Y', 1, 'ods_oms', 5),
  (1018, 102, 'oms', 'oms_delivery',          '发货单',                   'generic',    0.95,  48, 480, 'Y', 1, 'ods_oms', 6),
  (1019, 102, 'oms', 'oms_after_sale',        '售后工单',                 'generic',    0.35,  34, 560, 'Y', 1, 'ods_oms', 7),
  (1020, 102, 'oms', 'oms_cart',              '购物车快照',               'generic',    0.80,  42, 440, 'Y', 1, 'ods_oms', 8),
  (1021, 102, 'oms', 'oms_coupon',            '优惠券',                   'generic',    0.12,  18, 380, 'Y', 1, 'ods_oms', 9),
  (1022, 102, 'oms', 'oms_promotion',         '促销活动',                 'generic',    0.08,  16, 420, 'Y', 1, 'ods_oms', 10),
  (1023, 102, 'oms', 'oms_invoice',           '电子发票',                 'finance',    0.18,  22, 380, 'Y', 1, 'ods_oms', 11),
  (1024, 102, 'oms', 'oms_order_ext',         '订单扩展信息',             'generic',    0.45,  30, 480, 'Y', 1, 'ods_oms', 12),
  -- POS
  (1025, 103, 'pos', 'pos_sale_bill',         '销售小票',                 'generic',    0.85,  40, 460, 'Y', 1, 'ods_pos', 1),
  (1026, 103, 'pos', 'pos_sale_item',         '小票明细',                 'order_item', 1.40,  62, 360, 'Y', 1, 'ods_pos', 2),
  (1027, 103, 'pos', 'pos_payment_detail',    '支付明细',                 'finance',    0.32,  26, 320, 'Y', 1, 'ods_pos', 3),
  (1028, 103, 'pos', 'pos_member',            '会员档案',                 'customer',   0.12,  22, 800, 'N', 1, 'ods_pos', 4),
  (1029, 103, 'pos', 'pos_member_points',     '积分流水',                 'log_event',  0.28,  30, 280, 'N', 1, 'ods_pos', 5),
  (1030, 103, 'pos', 'pos_store',             '门店档案',                 'generic',    0.01,   8, 520, 'N', 1, 'ods_pos', 6),
  (1031, 103, 'pos', 'pos_discount',          '折扣规则',                 'generic',    0.02,   8, 360, 'Y', 1, 'ods_pos', 7),
  (1032, 103, 'pos', 'pos_return',            '退货单',                   'generic',    0.09,  18, 440, 'Y', 1, 'ods_pos', 8),
  -- WMS
  (1033, 104, 'wms', 'wms_inventory',         '库存余额',                 'inventory',  0.90,  52, 260, 'Y', 1, 'ods_wms', 1),
  (1034, 104, 'wms', 'wms_inbound_order',     '入库单',                   'generic',    0.18,  24, 440, 'Y', 1, 'ods_wms', 2),
  (1035, 104, 'wms', 'wms_inbound_detail',    '入库明细',                 'order_item', 0.30,  30, 320, 'Y', 1, 'ods_wms', 3),
  (1036, 104, 'wms', 'wms_outbound_order',    '出库单',                   'generic',    0.26,  30, 440, 'E', 1, 'ods_wms', 4),
  (1037, 104, 'wms', 'wms_outbound_detail',   '出库明细',                 'order_item', 0.42,  34, 320, 'Y', 1, 'ods_wms', 5),
  (1038, 104, 'wms', 'wms_stock_move',        '库存移动流水',             'log_event',  0.55,  40, 300, 'Y', 1, 'ods_wms', 6),
  (1039, 104, 'wms', 'wms_warehouse',         '仓库档案',                 'generic',    0.01,   8, 400, 'Y', 1, 'ods_wms', 7),
  (1040, 104, 'wms', 'wms_carrier',           '承运商档案',               'generic',    0.01,   8, 400, 'Y', 1, 'ods_wms', 8),
  -- ERP
  (1041, 105, 'erp', 'erp_material_master',   '物料主数据',               'generic',    0.22,  32, 620, 'Y', 1, 'ods_erp', 1),
  (1042, 105, 'erp', 'erp_supplier',          '供应商主数据',             'customer',   0.05,  16, 720, 'Y', 1, 'ods_erp', 2),
  (1043, 105, 'erp', 'erp_purchase_order',    '采购订单',                 'generic',    0.35,  36, 520, 'Y', 1, 'ods_erp', 3),
  (1044, 105, 'erp', 'erp_purchase_detail',   '采购订单明细',             'order_item', 0.60,  42, 360, 'Y', 1, 'ods_erp', 4),
  (1045, 105, 'erp', 'erp_bom',               'BOM 清单',                 'generic',    0.08,  20, 480, 'Y', 1, 'ods_erp', 5),
  (1046, 105, 'erp', 'erp_production_order',  '生产工单',                 'generic',    0.20,  26, 520, 'Y', 1, 'ods_erp', 6),
  (1047, 105, 'erp', 'erp_org_unit',          '组织架构',                 'generic',    0.01,   8, 380, 'Y', 1, 'ods_erp', 7),
  (1048, 105, 'erp', 'erp_currency_rate',     '汇率',                     'generic',    0.005,  6, 180, 'Y', 1, 'ods_erp', 8),
  -- FIN
  (1049, 106, 'fin', 'fin_voucher',           '凭证主表',                 'finance',    0.12,  24, 420, 'Y', 1, 'ods_fin', 1),
  (1050, 106, 'fin', 'fin_voucher_entry',     '凭证明细',                 'finance',    0.28,  30, 320, 'Y', 1, 'ods_fin', 2),
  (1051, 106, 'fin', 'fin_gl_account',        '会计科目',                 'generic',    0.01,   8, 400, 'Y', 1, 'ods_fin', 3),
  (1052, 106, 'fin', 'fin_ar_invoice',        '应收发票',                 'finance',    0.06,  18, 380, 'Y', 1, 'ods_fin', 4),
  (1053, 106, 'fin', 'fin_ap_invoice',        '应付发票',                 'finance',    0.05,  18, 380, 'Y', 1, 'ods_fin', 5),
  (1054, 106, 'fin', 'fin_cost_center',       '成本中心',                 'generic',    0.005,  6, 360, 'Y', 1, 'ods_fin', 6),
  -- MKT
  (1055, 107, 'marketing', 'mkt_campaign',           '营销活动',         'generic',    0.03,  12, 520, 'Y', 1, 'ods_mkt', 1),
  (1056, 107, 'marketing', 'mkt_campaign_stat',      '活动效果日汇总',   'generic',    0.12,  20, 420, 'Y', 1, 'ods_mkt', 2),
  (1057, 107, 'marketing', 'mkt_channel',            '投放渠道',         'generic',    0.01,   8, 360, 'Y', 1, 'ods_mkt', 3),
  (1058, 107, 'marketing', 'mkt_lead_score',         '线索评分',         'generic',    0.18,  24, 380, 'Y', 1, 'ods_mkt', 4),
  (1059, 107, 'marketing', 'mkt_event_track',        '活动埋点',         'log_event',  0.50,  38, 480, 'Y', 1, 'ods_mkt', 5),
  (1060, 107, 'marketing', 'mkt_audience',           '人群包',           'generic',    0.07,  16, 420, 'Y', 1, 'ods_mkt', 6),
  -- SCM
  (1061, 108, 'scm', 'scm_supplier_contract', '供应商合同',               'generic',    0.04,  14, 560, 'Y', 1, 'ods_scm', 1),
  (1062, 108, 'scm', 'scm_delivery_plan',     '交付计划',                 'generic',    0.16,  24, 440, 'Y', 1, 'ods_scm', 2),
  (1063, 108, 'scm', 'scm_po_line',           '采购订单行',               'order_item', 0.48,  38, 360, 'Y', 1, 'ods_scm', 3),
  (1064, 108, 'scm', 'scm_inventory_plan',    '库存计划',                 'inventory',  0.22,  26, 300, 'Y', 1, 'ods_scm', 4),
  (1065, 108, 'scm', 'scm_price_agreement',   '价格协议',                 'generic',    0.03,  12, 420, 'Y', 1, 'ods_scm', 5),
  (1066, 108, 'scm', 'scm_settlement',        '结算单',                   'finance',    0.09,  18, 380, 'Y', 1, 'ods_scm', 6),
  -- CDP
  (1067, 109, 'customer_profile', 'cdp_customer_profile', '客户画像宽表',    'customer',  2.60, 180, 1200, 'Y', 1, 'ods_cdp', 1),
  (1068, 109, 'customer_profile', 'cdp_tag_definition',   '标签定义',        'generic',   0.02,  10, 360, 'Y', 1, 'ods_cdp', 2),
  (1069, 109, 'customer_profile', 'cdp_customer_tag',     '客户标签宽表',    'generic',   1.20, 120, 420, 'Y', 1, 'ods_cdp', 3),
  (1070, 109, 'customer_profile', 'cdp_behavior_event',   '行为事件明细',    'log_event', 1.80, 150, 560, 'W', 1, 'ods_cdp', 4),
  -- LOG
  (1071, 110, 'logs', 'log_app_event',        '应用事件日志',             'log_event', 12.50, 240, 640, 'R', 1, 'ods_log', 1),
  (1072, 110, 'logs', 'log_page_view',        '页面浏览日志',             'log_event',  8.20, 180, 720, 'Y', 1, 'ods_log', 2),
  (1073, 110, 'logs', 'log_search_keyword',   '搜索关键词日志',           'log_event',  1.10,  60, 480, 'Y', 1, 'ods_log', 3),
  (1074, 110, 'logs', 'log_device_info',      '设备信息',                 'generic',    0.60,  42, 520, 'Y', 1, 'ods_log', 4),
  -- DWH
  (1075, 111, 'warehouse', 'dwd_trade_order_di',    '交易订单明细日增量', 'order_item', 3.60, 160, 420, 'Y', 2, 'dwd', 1),
  (1076, 111, 'warehouse', 'dwd_customer_di',       '客户明细日增量',     'customer',   1.10,  90, 880, 'Y', 2, 'dwd', 2),
  (1077, 111, 'warehouse', 'dws_sales_1d',          '销售汇总日表',       'generic',    0.35,  40, 640, 'U', 2, 'dws', 3),
  (1078, 111, 'warehouse', 'ads_sales_dashboard_d', '销售看板日表',       'generic',    0.12,  28, 720, 'Y', 2, 'ads', 4),
  -- HR (source disabled)
  (1079, 112, 'hr', 'hr_employee',            '员工档案',                 'customer',   0.05,  16, 820, 'Y', 1, 'ods_hr', 1),
  (1080, 112, 'hr', 'hr_department',          '部门架构',                 'generic',    0.01,   8, 420, 'Y', 1, 'ods_hr', 2),
  (1081, 112, 'hr', 'hr_position',            '岗位信息',                 'generic',    0.01,   8, 420, 'Y', 1, 'ods_hr', 3),
  (1082, 112, 'hr', 'hr_attendance',          '考勤记录',                 'log_event',  0.30,  32, 320, 'X', 1, 'ods_hr', 4);

INSERT INTO etl_table
  (id, source_db, source_table, target_db, target_table, filter, kind, retry_cnt,
   start_time, end_time, max_runtime, sid, duration, tbl_comment, status, split_pk,
   auto_pk, write_mode, start_at, target_id, created_at, updated_at,
   schema_management_mode)
SELECT
  s.id,
  s.source_db,
  s.source_table,
  s.target_db,
  s.source_table,
  '1=1',
  'A',
  3,
  current_date + src.start_at,
  current_date + src.start_at + make_interval(mins => (s.ord - 1) * 4) + make_interval(secs => s.base_secs),
  2000,
  s.sid,
  s.base_secs,
  s.tbl_comment,
  s.status,
  CASE WHEN s.category IN ('order_main', 'customer', 'inventory') THEN 'id' ELSE NULL END,
  true,
  'overwrite',
  src.start_at + make_interval(mins => (s.ord - 1) * 4),
  s.target_id,
  now() - interval '180 days' + make_interval(days => (s.id % 90)::int),
  now() - make_interval(days => (s.id % 12)::int),
  'MANAGED'
FROM seed_table s
JOIN etl_source src ON src.id = s.sid;

-- ---------------------------------------------------------------------------
-- 5. Column sets per table category (source -> Hive/ODS types)
-- ---------------------------------------------------------------------------
CREATE TEMP TABLE seed_column (
  category        varchar(30) NOT NULL,
  ord             int NOT NULL,
  column_name     varchar(255) NOT NULL,
  source_type     varchar(64),
  target_type     varchar(50) NOT NULL,
  target_type_full varchar(100),
  col_comment     varchar(4000)
) ON COMMIT DROP;

INSERT INTO seed_column (category, ord, column_name, source_type, target_type, target_type_full, col_comment) VALUES
  -- order_main
  ('order_main', 1,  'id',                'bigint',        'bigint',       'bigint',       '订单主键'),
  ('order_main', 2,  'order_no',          'varchar(32)',   'string',       'string',       '订单号'),
  ('order_main', 3,  'order_type',        'tinyint',       'int',          'int',          '订单类型 1：销售 2：预售 3：换货'),
  ('order_main', 4,  'customer_id',       'bigint',        'bigint',       'bigint',       '客户 ID'),
  ('order_main', 5,  'store_id',          'bigint',        'bigint',       'bigint',       '门店 ID'),
  ('order_main', 6,  'channel_code',      'varchar(16)',   'string',       'string',       '渠道编码 APP/XCX/POS/WEB'),
  ('order_main', 7,  'total_amount',      'decimal(18,2)', 'decimal(18,2)','decimal(18,2)','订单总额'),
  ('order_main', 8,  'discount_amount',   'decimal(18,2)', 'decimal(18,2)','decimal(18,2)','优惠金额'),
  ('order_main', 9,  'pay_amount',        'decimal(18,2)', 'decimal(18,2)','decimal(18,2)','实付金额'),
  ('order_main', 10, 'order_status',      'varchar(16)',   'string',       'string',       '订单状态'),
  ('order_main', 11, 'pay_status',        'varchar(16)',   'string',       'string',       '支付状态'),
  ('order_main', 12, 'receiver_name',     'varchar(64)',   'string',       'string',       '收货人'),
  ('order_main', 13, 'receiver_mobile',   'varchar(32)',   'string',       'string',       '收货手机号'),
  ('order_main', 14, 'province',          'varchar(32)',   'string',       'string',       '省份'),
  ('order_main', 15, 'city',              'varchar(32)',   'string',       'string',       '城市'),
  ('order_main', 16, 'detail_address',    'varchar(255)',  'string',       'string',       '详细地址'),
  ('order_main', 17, 'created_at',        'datetime',      'timestamp',    'timestamp',    '下单时间'),
  ('order_main', 18, 'paid_at',           'datetime',      'timestamp',    'timestamp',    '支付时间'),
  ('order_main', 19, 'updated_at',        'datetime',      'timestamp',    'timestamp',    '更新时间'),
  ('order_main', 20, 'is_deleted',        'tinyint',       'int',          'int',          '逻辑删除标记'),
  -- order_item
  ('order_item', 1, 'id',                 'bigint',        'bigint',       'bigint',       '明细主键'),
  ('order_item', 2, 'order_id',           'bigint',        'bigint',       'bigint',       '订单 ID'),
  ('order_item', 3, 'order_no',           'varchar(32)',   'string',       'string',       '订单号'),
  ('order_item', 4, 'sku_id',             'bigint',        'bigint',       'bigint',       '商品 SKU'),
  ('order_item', 5, 'sku_name',           'varchar(200)',  'string',       'string',       '商品名称'),
  ('order_item', 6, 'category_id',        'bigint',        'bigint',       'bigint',       '末级类目 ID'),
  ('order_item', 7, 'quantity',           'int',           'int',          'int',          '购买数量'),
  ('order_item', 8, 'unit_price',         'decimal(18,2)', 'decimal(18,2)','decimal(18,2)','成交单价'),
  ('order_item', 9, 'item_amount',        'decimal(18,2)', 'decimal(18,2)','decimal(18,2)','行金额'),
  ('order_item', 10,'discount_amount',    'decimal(18,2)', 'decimal(18,2)','decimal(18,2)','行优惠金额'),
  ('order_item', 11,'created_at',         'datetime',      'timestamp',    'timestamp',    '创建时间'),
  -- customer
  ('customer', 1, 'id',                   'bigint',        'bigint',       'bigint',       '客户主键'),
  ('customer', 2, 'customer_no',          'varchar(32)',   'string',       'string',       '客户编号'),
  ('customer', 3, 'customer_name',        'varchar(100)',  'string',       'string',       '客户名称'),
  ('customer', 4, 'customer_type',        'tinyint',       'int',          'int',          '客户类型 1：个人 2：企业'),
  ('customer', 5, 'mobile',               'varchar(32)',   'string',       'string',       '手机号'),
  ('customer', 6, 'email',                'varchar(128)',  'string',       'string',       '邮箱'),
  ('customer', 7, 'gender',               'tinyint',       'int',          'int',          '性别 0：未知 1：男 2：女'),
  ('customer', 8, 'birthday',             'date',          'string',       'string',       '生日'),
  ('customer', 9, 'register_channel',     'varchar(16)',   'string',       'string',       '注册渠道'),
  ('customer', 10,'member_level',         'varchar(16)',   'string',       'string',       '会员等级'),
  ('customer', 11,'city',                 'varchar(32)',   'string',       'string',       '常驻城市'),
  ('customer', 12,'created_at',           'datetime',      'timestamp',    'timestamp',    '注册时间'),
  ('customer', 13,'updated_at',           'datetime',      'timestamp',    'timestamp',    '更新时间'),
  -- inventory
  ('inventory', 1, 'id',                  'bigint',        'bigint',       'bigint',       '库存主键'),
  ('inventory', 2, 'warehouse_id',        'bigint',        'bigint',       'bigint',       '仓库 ID'),
  ('inventory', 3, 'sku_id',              'bigint',        'bigint',       'bigint',       '商品 SKU'),
  ('inventory', 4, 'batch_no',            'varchar(32)',   'string',       'string',       '批次号'),
  ('inventory', 5, 'available_qty',       'int',           'int',          'int',          '可用库存'),
  ('inventory', 6, 'locked_qty',          'int',           'int',          'int',          '锁定库存'),
  ('inventory', 7, 'in_transit_qty',      'int',           'int',          'int',          '在途库存'),
  ('inventory', 8, 'last_in_at',          'datetime',      'timestamp',    'timestamp',    '最近入库时间'),
  ('inventory', 9, 'last_out_at',         'datetime',      'timestamp',    'timestamp',    '最近出库时间'),
  ('inventory', 10,'updated_at',          'datetime',      'timestamp',    'timestamp',    '更新时间'),
  -- finance
  ('finance', 1, 'id',                    'bigint',        'bigint',       'bigint',       '主键'),
  ('finance', 2, 'voucher_no',            'varchar(32)',   'string',       'string',       '单据编号'),
  ('finance', 3, 'account_code',          'varchar(32)',   'string',       'string',       '会计科目编码'),
  ('finance', 4, 'account_name',          'varchar(100)',  'string',       'string',       '会计科目名称'),
  ('finance', 5, 'debit_amount',          'decimal(18,2)', 'decimal(18,2)','decimal(18,2)','借方金额'),
  ('finance', 6, 'credit_amount',         'decimal(18,2)', 'decimal(18,2)','decimal(18,2)','贷方金额'),
  ('finance', 7, 'currency',              'varchar(8)',    'string',       'string',       '币种'),
  ('finance', 8, 'biz_date',              'date',          'string',       'string',       '业务日期'),
  ('finance', 9, 'created_at',            'datetime',      'timestamp',    'timestamp',    '创建时间'),
  -- log_event
  ('log_event', 1, 'id',                  'bigint',        'bigint',       'bigint',       '主键'),
  ('log_event', 2, 'event_id',            'varchar(64)',   'string',       'string',       '事件 ID'),
  ('log_event', 3, 'event_time',          'datetime',      'timestamp',    'timestamp',    '事件时间'),
  ('log_event', 4, 'user_id',             'varchar(64)',   'string',       'string',       '用户标识'),
  ('log_event', 5, 'device_id',           'varchar(64)',   'string',       'string',       '设备标识'),
  ('log_event', 6, 'event_type',          'varchar(64)',   'string',       'string',       '事件类型'),
  ('log_event', 7, 'page_id',             'varchar(64)',   'string',       'string',       '页面/模块标识'),
  ('log_event', 8, 'properties',          'text',          'string',       'string',       '事件属性 JSON'),
  ('log_event', 9, 'created_at',          'datetime',      'timestamp',    'timestamp',    '入库时间'),
  -- generic
  ('generic', 1, 'id',                    'bigint',        'bigint',       'bigint',       '主键'),
  ('generic', 2, 'biz_no',                'varchar(64)',   'string',       'string',       '业务编号'),
  ('generic', 3, 'biz_type',              'varchar(32)',   'string',       'string',       '业务类型'),
  ('generic', 4, 'status',                'varchar(16)',   'string',       'string',       '状态'),
  ('generic', 5, 'amount',                'decimal(18,2)', 'decimal(18,2)','decimal(18,2)','金额'),
  ('generic', 6, 'remark',                'varchar(500)',  'string',       'string',       '备注'),
  ('generic', 7, 'created_at',            'datetime',      'timestamp',    'timestamp',    '创建时间'),
  ('generic', 8, 'updated_at',            'datetime',      'timestamp',    'timestamp',    '更新时间');

INSERT INTO etl_column
  (tid, column_name, column_id, source_type, data_length, data_precision, data_scale,
   target_type, target_type_full, col_comment, update_at)
SELECT t.id, c.column_name, c.ord, c.source_type, 0, 0, 0,
       c.target_type, c.target_type_full, c.col_comment, now() - interval '3 days'
FROM seed_table t
JOIN seed_column c ON c.category = t.category;

-- ---------------------------------------------------------------------------
-- 6. One year of daily statistics
--    Every enabled table has one row per day; today is skipped for the
--    tables that are not supposed to have finished yet (N/W/U).
-- ---------------------------------------------------------------------------
INSERT INTO etl_statistic
  (tid, start_at, end_at, take_secs, total_bytes, byte_speed, rec_speed,
   total_recs, total_errors, biz_date, execution_id)
SELECT
  s.id,
  v.start_at,
  v.start_at + make_interval(secs => v.take_secs),
  v.take_secs,
  v.bytes,
  GREATEST((v.bytes / GREATEST(v.take_secs, 1))::bigint, 1),
  GREATEST((v.recs / GREATEST(v.take_secs, 1))::bigint, 1),
  v.recs,
  0,
  d.day::date,
  NULL
FROM seed_table s
JOIN etl_source src ON src.id = s.sid
CROSS JOIN generate_series(current_date - 364, current_date, interval '1 day') AS d(day)
CROSS JOIN LATERAL (
  SELECT
    (d.day::date + src.start_at + make_interval(mins => (s.ord - 1) * 4)) AS start_at,
    (s.base_secs + ((s.id * 7 + extract(doy FROM d.day)::int * 3) % 11))::int AS take_secs,
    round(
      s.weight * 1073741824.0
      -- gradual 16% growth across the year
      * (1 + 0.16 * ((d.day::date - (current_date - 364))::numeric / 364.0))
      -- deterministic ±4% daily noise
      * (1 + ((s.id * 17 + extract(doy FROM d.day)::int * 29) % 9 - 4) * 0.01)
      -- retail/log traffic peaks at weekends
      * CASE WHEN src.id IN (102, 103, 104, 110) AND extract(isodow FROM d.day) IN (6, 7)
             THEN 1.10 ELSE 1 END
    )::bigint AS bytes,
    round(
      s.weight * 1073741824.0 / s.rec_bytes
      * (1 + ((s.id * 23 + extract(doy FROM d.day)::int * 31) % 7 - 3) * 0.01)
    )::bigint AS recs
) v
WHERE src.enabled = true
  AND NOT (d.day::date = current_date AND s.status IN ('N', 'W', 'U'));

-- Today's running ES job: it has not finished yet, so it has consumed no
-- completed runtime; keep the partial volume and let the pulse draw it to now.
UPDATE etl_statistic st
SET take_secs = 0,
    end_at = now(),
    total_bytes = round(total_bytes * 0.62),
    total_recs = round(total_recs * 0.62)
WHERE st.biz_date = current_date
  AND st.tid = (SELECT id FROM seed_table WHERE source_table = 'log_app_event');

-- Error stories: two failed jobs plus three data-quality incidents.
UPDATE etl_statistic st
SET total_errors = 1800
WHERE st.biz_date = current_date
  AND st.tid = (SELECT id FROM seed_table WHERE source_table = 'oms_order');

UPDATE etl_statistic st
SET total_errors = 900
WHERE st.biz_date = current_date
  AND st.tid = (SELECT id FROM seed_table WHERE source_table = 'wms_outbound_order');

UPDATE etl_statistic st
SET total_errors = 68000
WHERE st.biz_date = current_date - 7
  AND st.tid = (SELECT id FROM seed_table WHERE source_table = 'oms_order_item');

UPDATE etl_statistic st
SET total_errors = 42000
WHERE st.biz_date = current_date - 5
  AND st.tid = (SELECT id FROM seed_table WHERE source_table = 'pos_sale_item');

UPDATE etl_statistic st
SET total_errors = 26000
WHERE st.biz_date = current_date - 13
  AND st.tid = (SELECT id FROM seed_table WHERE source_table = 'crm_contact');

-- Missing-collection story: one table lost two partitions; five tables have not
-- produced today's row yet (three待运行, one waiting, one schema update).
DELETE FROM etl_statistic st
USING seed_table s
WHERE st.tid = s.id
  AND s.source_table = 'crm_contact'
  AND st.biz_date IN (current_date - 1, current_date - 2);

-- ---------------------------------------------------------------------------
-- 7. Execution snapshots for today and yesterday (linked to statistics)
-- ---------------------------------------------------------------------------
INSERT INTO etl_execution
  (id, tid, job_queue_id, attempt_no, biz_date, source_db, source_table,
   source_name, target_db, target_table, target_code, target_name,
   status, started_at, finished_at, snapshot_version, config_snapshot, job_digest, error_message)
SELECT
  100000 + row_number() OVER (ORDER BY s.id, e.day),
  s.id,
  NULL,
  1,
  e.day,
  s.source_db,
  s.source_table,
  src.name,
  s.target_db,
  s.source_table,
  tgt.code,
  tgt.name,
  CASE
    WHEN e.day = current_date AND s.status = 'E' THEN 'failed'
    WHEN e.day = current_date AND s.status = 'R' THEN 'running'
    WHEN (s.source_table = 'oms_order' AND e.day::date IN (current_date - 3, current_date - 9))
      OR (s.source_table = 'wms_outbound_order' AND e.day::date IN (current_date - 2, current_date - 8))
      OR (s.source_table = 'fin_ap_invoice' AND e.day::date IN (current_date - 4, current_date - 9))
      THEN 'failed'
    ELSE 'succeeded'
  END,
  e.day + src.start_at + make_interval(mins => (s.ord - 1) * 4),
  CASE
    WHEN e.day = current_date AND s.status = 'R' THEN NULL
    ELSE e.day + src.start_at + make_interval(mins => (s.ord - 1) * 4)
         + make_interval(secs => s.base_secs)
  END,
  1,
  jsonb_build_object(
    'reader', jsonb_build_object('name', src.db_type || 'reader', 'parameter', jsonb_build_object('table', s.source_table)),
    'writer', jsonb_build_object('name', CASE WHEN tgt.target_type = 'HDFS' THEN 'hdfswriter' ELSE 'mysqlwriter' END,
                                'parameter', jsonb_build_object('path', '/warehouse/' || s.target_db || '/' || s.source_table))
  ),
  md5(s.id::text || e.day::text),
  CASE
    WHEN s.source_table = 'oms_order'
      AND (e.day = current_date AND s.status = 'E'
           OR e.day::date IN (current_date - 3, current_date - 9))
      THEN 'HDFS write failed: Permission denied: user=hive, access=WRITE, inode="/warehouse/ods/oms/oms_order"'
    WHEN s.source_table = 'wms_outbound_order'
      AND (e.day = current_date AND s.status = 'E'
           OR e.day::date IN (current_date - 2, current_date - 8))
      THEN 'java.sql.SQLException: Lock wait timeout exceeded; try restarting transaction'
    WHEN s.source_table = 'fin_ap_invoice'
      AND e.day::date IN (current_date - 4, current_date - 9)
      THEN 'Read timed out after 600s while scanning table fin_ap_invoice'
    ELSE NULL
  END
FROM seed_table s
JOIN etl_source src ON src.id = s.sid
JOIN etl_target tgt ON tgt.id = s.target_id
CROSS JOIN generate_series(current_date - 9, current_date, interval '1 day') AS e(day)
WHERE src.enabled = true
  AND NOT (e.day = current_date AND s.status IN ('N', 'W', 'U'));

UPDATE etl_statistic st
SET execution_id = e.id
FROM etl_execution e
WHERE e.tid = st.tid
  AND e.biz_date = st.biz_date
  AND st.biz_date >= current_date - 9;

-- ---------------------------------------------------------------------------
-- 8. Journeys: today's runs plus the failures behind the risk queue
-- ---------------------------------------------------------------------------
INSERT INTO etl_jour (tid, kind, start_at, duration, status, cmd, error_msg, execution_id)
SELECT
  e.tid,
  'COLLECT',
  e.started_at + interval '5 seconds',
  GREATEST(extract(epoch FROM (coalesce(e.finished_at, now()) - e.started_at))::int, 1),
  CASE WHEN e.tid = (SELECT id FROM seed_table WHERE source_table = 'oms_order') THEN true
       ELSE e.status <> 'failed' END,
  'addax job',
  CASE WHEN e.tid = (SELECT id FROM seed_table WHERE source_table = 'oms_order') THEN NULL
       WHEN e.status = 'failed' THEN e.error_message
       ELSE NULL END,
  e.id
FROM etl_execution e
WHERE e.biz_date = current_date
  AND e.status <> 'running';

INSERT INTO etl_jour (tid, kind, start_at, duration, status, cmd, error_msg, execution_id)
SELECT
  s.id,
  'PARTITION',
  e.finished_at + interval '3 seconds',
  2,
  CASE WHEN s.source_table = 'oms_order' THEN false ELSE true END,
  'alter table add partition',
  CASE WHEN s.source_table = 'oms_order'
       THEN 'HDFS write failed: Permission denied: user=hive, access=WRITE, inode="/warehouse/ods/oms/oms_order/dt=' || current_date || '":hdfs:hdfs:drwxr-xr-x'
       ELSE NULL END,
  e.id
FROM etl_execution e
JOIN seed_table s ON s.id = e.tid
JOIN etl_target tgt ON tgt.id = s.target_id
WHERE e.biz_date = current_date
  AND tgt.target_type = 'HDFS'
  AND e.status <> 'running';

-- Historical failures (14-day window): connect timeouts, lock waits, bad data.
INSERT INTO etl_jour (tid, kind, start_at, duration, status, cmd, error_msg, execution_id)
SELECT
  (SELECT id FROM seed_table WHERE source_table = v.table_name),
  v.kind,
  (current_date - v.days_ago) + v.at_time,
  v.duration,
  false,
  v.cmd,
  v.error_msg,
  NULL
FROM (VALUES
  ('oms_order',              'COLLECT',   3,  '01:24:12'::time, 8,  'addax job',              'CommunicationsException: Communications link failure; connect timed out after 10000ms'),
  ('oms_order',              'PARTITION', 3,  '01:31:40'::time, 2,  'alter table add partition', 'HDFS write failed: Permission denied: user=hive, access=WRITE, inode="/warehouse/ods/oms/oms_order"'),
  ('oms_order',              'COLLECT',   9,  '01:25:03'::time, 12, 'addax job',              'CommunicationsException: Communications link failure; connect timed out after 10000ms'),
  ('wms_outbound_order',     'COLLECT',   2,  '02:26:48'::time, 30, 'addax job',              'java.sql.SQLException: Lock wait timeout exceeded; try restarting transaction'),
  ('wms_outbound_order',     'PARTITION', 8,  '02:29:11'::time, 2,  'alter table add partition', 'HDFS write failed: Permission denied: user=hive, access=WRITE, inode="/warehouse/ods/wms/wms_outbound_order"'),
  ('fin_ap_invoice',         'COLLECT',   4,  '02:52:33'::time, 600,'addax job',              'Read timed out after 600s while scanning table fin_ap_invoice'),
  ('fin_ap_invoice',         'COLLECT',   9,  '02:51:57'::time, 600,'addax job',              'Read timed out after 600s while scanning table fin_ap_invoice'),
  ('crm_campaign_member',    'COLLECT',   5,  '01:33:09'::time, 18, 'addax job',              'Data truncation: Incorrect datetime value: ''0000-00-00 00:00:00'' for column ''last_follow_at'''),
  ('crm_campaign_member',    'COLLECT',   12, '01:34:22'::time, 22, 'addax job',              'Data truncation: Incorrect datetime value: ''0000-00-00 00:00:00'' for column ''last_follow_at''')
) AS v(table_name, kind, days_ago, at_time, duration, cmd, error_msg);

-- Failure facts for the demo error center. Mirrors the failed journeys above;
-- the real application records these through FailureRecorder at runtime.
INSERT INTO etl_failure (
  tid, execution_id, occurred_at, stage, category, error_class, summary, detail,
  fingerprint, attempt_no, max_attempts, final_attempt, run_type, biz_date,
  source_id, source_db, source_table, target_db, target_table, legacy_jour_id
)
SELECT
  j.tid,
  j.execution_id,
  j.start_at + make_interval(secs => coalesce(j.duration, 0)),
  CASE j.kind
    WHEN 'COLLECT' THEN 'COLLECT'
    WHEN 'PARTITION' THEN 'PARTITION'
    WHEN 'UPDATE_TABLE' THEN 'TARGET_SCHEMA'
    WHEN 'UPDATE_COLUMN' THEN 'SOURCE_SCHEMA'
    ELSE 'PREPARE'
  END,
  CASE
    WHEN lower(j.error_msg) LIKE '%permission denied%' THEN 'AUTH'
    WHEN lower(j.error_msg) LIKE '%timed out%' THEN 'TIMEOUT'
    WHEN lower(j.error_msg) LIKE '%data truncation%' THEN 'DATA'
    WHEN lower(j.error_msg) LIKE '%lock wait%' THEN 'DATA'
    ELSE 'UNKNOWN'
  END,
  'DEMO',
  left(regexp_replace(j.error_msg, '\s+', ' ', 'g'), 1000),
  j.error_msg,
  md5(concat_ws('|', j.tid, j.kind, 'DEMO',
    lower(left(regexp_replace(j.error_msg, '\s+', ' ', 'g'), 1000)))),
  coalesce(x.attempt_no, 1),
  1,
  true,
  'NORMAL',
  x.biz_date,
  v.sid, v.source_db, v.source_table, v.target_db, v.target_table,
  j.id
FROM etl_jour j
LEFT JOIN etl_execution x ON x.id = j.execution_id
LEFT JOIN vw_etl_table_with_source v ON v.id = j.tid
WHERE j.status = false
  AND j.error_msg IS NOT NULL;

-- ---------------------------------------------------------------------------
-- 9. Addax job templates and raw logs for the last three runs of every table
-- ---------------------------------------------------------------------------
INSERT INTO etl_job (tid, job)
SELECT
  t.id,
  jsonb_pretty(jsonb_build_object(
    'job', jsonb_build_object(
      'setting', jsonb_build_object(
        'speed', jsonb_build_object('channel', 4),
        'errorLimit', jsonb_build_object('record', 1000, 'percentage', 0.02)
      ),
      'content', jsonb_build_array(
        jsonb_build_object('reader', jsonb_build_object(
          'name', src.db_type || 'reader',
          'parameter', jsonb_build_object(
            'username', src.username,
            'password', '******',
            'column', (SELECT jsonb_agg(c.column_name ORDER BY c.column_id) FROM etl_column c WHERE c.tid = t.id),
            'splitPk', t.split_pk,
            'connection', jsonb_build_array(jsonb_build_object(
              'table', jsonb_build_array(t.source_table),
              'jdbcUrl', jsonb_build_array(src.url)
            ))
          )
        )),
        jsonb_build_object('writer', jsonb_build_object(
          'name', CASE WHEN tgt.target_type = 'HDFS' THEN 'hdfswriter' ELSE 'mysqlwriter' END,
          'parameter', CASE WHEN tgt.target_type = 'HDFS'
            THEN jsonb_build_object(
              'defaultFS', 'hdfs://nameservice1',
              'fileType', 'orc',
              'path', '/warehouse/' || t.target_db || '/' || t.target_table,
              'fileName', t.target_table,
              'column', (SELECT jsonb_agg(jsonb_build_object('name', c.column_name, 'type', c.target_type) ORDER BY c.column_id) FROM etl_column c WHERE c.tid = t.id),
              'writeMode', 'truncate',
              'compress', 'SNAPPY'
            )
            ELSE jsonb_build_object(
              'username', 'etl_write',
              'password', '******',
              'column', (SELECT jsonb_agg(c.column_name ORDER BY c.column_id) FROM etl_column c WHERE c.tid = t.id),
              'connection', jsonb_build_array(jsonb_build_object(
                'table', jsonb_build_array(t.target_table),
                'jdbcUrl', jsonb_build_array(tgt.connect_config::jsonb ->> 'url')
              ))
            )
          END
        ))
      )
    )
  ))
FROM etl_table t
JOIN etl_source src ON src.id = t.sid
JOIN etl_target tgt ON tgt.id = t.target_id
WHERE t.retired_at IS NULL
  AND src.enabled = true
  AND src.db_type <> 'mongodb'
  AND src.db_type <> 'elasticsearch';

INSERT INTO addax_log (tid, run_at, run_date, log, execution_id)
SELECT
  st.tid,
  st.start_at + interval '2 minutes',
  st.biz_date,
  format(
    E'%s [        main] INFO  Engine               - \n  ___      _     _            \n / _ \\    | |   | |           \n/ /_\\ \\ __| | __| | __ ___  __\n|  _  |/ _` |/ _` |/ _` \\ \\/ /\n| | | | (_| | (_| | (_| |>  < \n\\_| |_/\\__,_|\\__,_|\\__,_/_/\\_\\\n:: Addax version ::    (v6.2.1-SNAPSHOT)\n\n%s [        main] INFO  JobContainer         - Addax jobContainer starts job.\n%s [        main] INFO  JobContainer         - Set jobId = 0\n%s [        main] INFO  JobContainer         - Job set Channel-Number to 4 channels.\n%s [      job-0] INFO  StandAloneJobContainerCommunicator - Total %s records, %s bytes | Speed %sMB/s, %s records/s | Error %s records, 0 bytes |  All Task WaitWriterTime 12.35s |  All Task WaitReaderTime 1.68s | Percentage 100.00%%\n%s [        main] INFO  JobContainer         - \n任务启动时刻                    : %s\n任务结束时刻                    : %s\n任务总计耗时                    : %ss\n任务平均流量                    : %sMB/s\n记录写入速度                    : %s rec/s\n读出记录总数                    : %s\n读写失败总数                    : %s\n\n%s',
    to_char(st.start_at + interval '2 minutes', 'YYYY-MM-DD HH24:MI:SS.MS'),
    to_char(st.start_at + interval '2 minutes', 'YYYY-MM-DD HH24:MI:SS.MS'),
    to_char(st.start_at + interval '2 minutes', 'YYYY-MM-DD HH24:MI:SS.MS'),
    to_char(st.start_at + interval '2 minutes', 'YYYY-MM-DD HH24:MI:SS.MS'),
    to_char(st.start_at + interval '2 minutes', 'YYYY-MM-DD HH24:MI:SS.MS'),
    to_char(st.total_recs, 'FM999G999G999G999'),
    to_char(st.total_bytes, 'FM999G999G999G999'),
    to_char(st.byte_speed / 1024.0 / 1024.0, 'FM999990.00'),
    to_char(GREATEST(st.rec_speed, 1), 'FM999G999'),
    st.total_errors,
    to_char(st.end_at, 'YYYY-MM-DD HH24:MI:SS.MS'),
    to_char(st.start_at, 'YYYY-MM-DD HH24:MI'),
    to_char(st.end_at, 'YYYY-MM-DD HH24:MI'),
    st.take_secs,
    to_char(st.byte_speed / 1024.0 / 1024.0, 'FM999990.00'),
    to_char(GREATEST(st.rec_speed, 1), 'FM999G999'),
    to_char(st.total_recs, 'FM999G999G999'),
    st.total_errors,
    CASE WHEN st.total_errors > 0
         THEN E'\n\n' || CASE
                WHEN st.tid = (SELECT id FROM seed_table WHERE source_table = 'oms_order')
                  THEN 'Caused by: org.apache.hadoop.security.AccessControlException: Permission denied: user=hive, access=WRITE, inode="/warehouse/ods/oms/oms_order":hdfs:hdfs:drwxr-xr-x'
                WHEN st.tid = (SELECT id FROM seed_table WHERE source_table = 'wms_outbound_order')
                  THEN 'Caused by: java.sql.SQLException: Lock wait timeout exceeded; try restarting transaction'
                ELSE 'Caused by: java.io.IOException: Read timed out while scanning source table'
              END
         ELSE E'\nAddax job completed successfully.'
    END
  ),
  (SELECT e.id FROM etl_execution e WHERE e.tid = st.tid AND e.biz_date = st.biz_date)
FROM etl_statistic st
WHERE st.biz_date >= current_date - 2
  AND st.biz_date <= current_date
  -- a job that is still running has no final log yet
  AND NOT (st.biz_date = current_date
           AND st.tid = (SELECT id FROM seed_table WHERE source_table = 'log_app_event'));

-- ---------------------------------------------------------------------------
-- 10. Change history, schema changes and notifications
-- ---------------------------------------------------------------------------
INSERT INTO etl_table_change_log
  (tid, source_db, source_table, target_db, target_table, changed_fields, old_values, new_values, changed_at, changed_by)
SELECT
  (SELECT id FROM seed_table WHERE source_table = 'oms_order'),
  'oms', 'oms_order', 'ods_oms', 'oms_order',
  '["filter"]'::jsonb,
  '{"filter": "1=1"}'::jsonb,
  '{"filter": "dt >= ''${bizdate}''"}'::jsonb,
  now() - interval '12 days',
  'admin'
UNION ALL
SELECT
  (SELECT id FROM seed_table WHERE source_table = 'oms_order'),
  'oms', 'oms_order', 'ods_oms', 'oms_order',
  '["target_options"]'::jsonb,
  '{"compression": "NONE"}'::jsonb,
  '{"compression": "SNAPPY"}'::jsonb,
  now() - interval '30 days',
  'admin';

INSERT INTO schema_change_log
  (tid, source_db, source_table, column_name, change_type, old_source_type, new_source_type,
   old_data_length, new_data_length, old_data_precision, new_data_precision, old_data_scale,
   new_data_scale, old_col_comment, new_col_comment, change_at)
SELECT
  (SELECT id FROM seed_table WHERE source_table = 'oms_order'),
  'oms', 'oms_order', 'channel_code', 'ADD', NULL, 'varchar(16)',
  NULL::int, 16, NULL::int, NULL::int, NULL::int, NULL::int, NULL, '渠道编码 APP/XCX/POS/WEB',
  now() - interval '4 days'
UNION ALL
SELECT
  (SELECT id FROM seed_table WHERE source_table = 'oms_order'),
  'oms', 'oms_order', 'total_amount', 'TYPE_CHANGE', 'decimal(16,2)', 'decimal(18,2)',
  NULL, NULL, 16, 18, 2, 2, '订单总额', '订单总额',
  now() - interval '18 days'
UNION ALL
SELECT
  (SELECT id FROM seed_table WHERE source_table = 'oms_order'),
  'oms', 'oms_order', 'legacy_flag', 'DELETE', 'tinyint', NULL,
  NULL::int, NULL::int, NULL::int, NULL::int, NULL::int, NULL::int, '历史遗留标记', NULL,
  now() - interval '36 days';

INSERT INTO user_notification (username, title, content, type, status, ref_type, ref_id, created_at)
SELECT
  COALESCE((SELECT username FROM users ORDER BY username LIMIT 1), 'admin'),
  '采集任务失败',
  '交易中台 oms.oms_order 分区写入失败：HDFS 权限不足，请检查 /warehouse/ods/oms 目录授权。',
  'ERROR', 'UNREAD', 'TABLE',
  (SELECT id::text FROM seed_table WHERE source_table = 'oms_order'),
  now() - interval '12 minutes'
UNION ALL
SELECT
  COALESCE((SELECT username FROM users ORDER BY username LIMIT 1), 'admin'),
  '采集缺失提醒',
  '近 7 天共有 7 个表·天未采集，其中 5 张表今日尚未产出，请关注未运行与等待更新的任务。',
  'WARNING', 'UNREAD', 'TABLE', NULL,
  now() - interval '40 minutes'
UNION ALL
SELECT
  COALESCE((SELECT username FROM users ORDER BY username LIMIT 1), 'admin'),
  '采集任务完成',
  '今日 70 张采集表已完成，数据量 62.9 GiB，平均单表耗时 51 秒。',
  'SUCCESS', 'READ', NULL, NULL,
  now() - interval '2 hours'
UNION ALL
SELECT
  COALESCE((SELECT username FROM users ORDER BY username LIMIT 1), 'admin'),
  '慢任务提醒',
  '订单明细 oms.oms_order_item 近 7 天平均耗时 281 秒，已进入慢表清单。',
  'WARNING', 'READ', 'TABLE',
  (SELECT id::text FROM seed_table WHERE source_table = 'oms_order_item'),
  now() - interval '3 hours';

INSERT INTO risk_log (risk_level, source, message, tid, created_at)
VALUES
  ('WARN', 'SCHEDULER', '交易中台今日 01:20 批次已重试 1 次仍失败，等待人工介入', 1013, now() - interval '9 hours'),
  ('INFO', 'SCHEMA',    'oms.oms_order 检测到新增字段 channel_code，已同步至 ODS', 1013, now() - interval '4 days');

-- ---------------------------------------------------------------------------
-- 11. Curated audit trail (append-only table, so fixed event ids keep it idempotent)
-- ---------------------------------------------------------------------------
INSERT INTO audit_event
  (event_id, occurred_at, request_id, actor_type, actor_username, actor_roles,
   action, category, severity, resource_type, resource_id, resource_name,
   outcome, http_method, request_path, client_ip, node_id, changed_fields, metadata)
VALUES
  ('a0000000-0000-4000-8000-000000000001', now() - interval '47 minutes',  '7f3c1a2b', 'USER', 'admin', '["admin"]'::jsonb,
   'TABLE_COLUMN_MAPPINGS_UPDATED', 'COLLECTION_DEFINITION', 'WARNING', 'ETL_TABLE', '1013', 'oms_order',
   'SUCCESS', 'PUT', '/api/v1/tables/1013/column-mappings', '10.20.1.25', 'etl-test-1', '["channel_code"]'::jsonb, '{}'::jsonb),
  ('a0000000-0000-4000-8000-000000000002', now() - interval '2 hours',  '8a4d2e6f', 'USER', 'admin', '["admin"]'::jsonb,
   'TABLE_JOB_OVERRIDDEN', 'COLLECTION_DEFINITION', 'WARNING', 'ETL_TABLE', '1013', 'oms_order',
   'SUCCESS', 'PUT', '/api/v1/tables/1013/addax-job', '10.20.1.25', 'etl-test-1', '[]'::jsonb, '{"afterLength": 4821}'::jsonb),
  ('a0000000-0000-4000-8000-000000000003', now() - interval '5 hours',  '9b5e3f7a', 'USER', 'wgzhao', '["admin"]'::jsonb,
   'SOURCE_UPDATED', 'CONFIG', 'WARNING', 'ETL_SOURCE', '102', '交易中台',
   'SUCCESS', 'PUT', '/api/v1/sources/102', '10.20.1.31', 'etl-test-1', '["maxConcurrency"]'::jsonb, '{}'::jsonb),
  ('a0000000-0000-4000-8000-000000000004', now() - interval '8 hours',  '1c6f4a8b', 'USER', 'admin', '["admin"]'::jsonb,
   'SYSTEM_CONFIG_UPDATED', 'CONFIG', 'CRITICAL', 'SYSTEM_CONFIG', 'QUEUE', '采集队列长度',
   'SUCCESS', 'PUT', '/api/v1/settings', '10.20.1.25', 'etl-test-1', '["queueLength"]'::jsonb, '{}'::jsonb),
  ('a0000000-0000-4000-8000-000000000005', now() - interval '11 hours', '2d7a5b9c', 'SYSTEM', 'scheduler', '[]'::jsonb,
   'COLLECTION_SUBMITTED', 'SCHEDULING', 'WARNING', 'ETL_TABLE', '1013', 'oms_order',
   'ACCEPTED', 'POST', '/api/v1/tasks/1013/executions', '10.20.8.12', 'etl-test-1', '[]'::jsonb, '{}'::jsonb),
  ('a0000000-0000-4000-8000-000000000006', now() - interval '15 hours', '3e8b6cad', 'USER', 'analyst', '["readonly"]'::jsonb,
   'WRITE_ACCESS_DENIED', 'ACCESS', 'WARNING', 'ETL_TABLE', '1014', 'oms_order_item',
   'DENIED', 'PUT', '/api/v1/tables/1014', '10.20.1.44', 'etl-test-1', '[]'::jsonb, '{"requiredRole":"admin"}'::jsonb),
  ('a0000000-0000-4000-8000-000000000007', now() - interval '1 day 2 hours', '4f9c7dbe', 'USER', 'admin', '["admin"]'::jsonb,
   'TABLE_RETIRED', 'COLLECTION_DEFINITION', 'WARNING', 'ETL_TABLE', '1082', 'hr_attendance',
   'SUCCESS', 'DELETE', '/api/v1/tables/1082', '10.20.1.25', 'etl-test-1', '[]'::jsonb, '{}'::jsonb),
  ('a0000000-0000-4000-8000-000000000008', now() - interval '1 day 6 hours', '5a0d8ecf', 'USER', 'admin', '["admin"]'::jsonb,
   'USER_CREATED', 'IDENTITY', 'CRITICAL', 'USER', 'analyst', 'analyst',
   'SUCCESS', 'POST', '/api/v1/users', '10.20.1.25', 'etl-test-1', '[]'::jsonb, '{}'::jsonb),
  ('a0000000-0000-4000-8000-000000000009', now() - interval '2 days',  '6b1e9fda', 'USER', 'wgzhao', '["admin"]'::jsonb,
   'AUTH_PASSWORD_CHANGED', 'AUTH', 'WARNING', 'USER', 'wgzhao', 'wgzhao',
   'SUCCESS', 'POST', '/api/v1/auth/change-password', '10.20.1.31', 'etl-test-1', '[]'::jsonb, '{}'::jsonb),
  ('a0000000-0000-4000-8000-00000000000a', now() - interval '3 days',  '7c2fa0eb', 'USER', 'admin', '["admin"]'::jsonb,
   'TARGET_CREATED', 'CONFIG', 'WARNING', 'ETL_TARGET', '2', 'Doris 数仓',
   'SUCCESS', 'POST', '/api/v1/targets', '10.20.1.25', 'etl-test-1', '[]'::jsonb, '{}'::jsonb),
  ('a0000000-0000-4000-8000-00000000000b', now() - interval '4 days',  '8d3ab1fc', 'SYSTEM', 'maintenance', '[]'::jsonb,
   'COLLECTION_LOGS_CLEANED', 'CONFIG', 'CRITICAL', 'COLLECTION_LOG', '2026-10', '2026-10',
   'SUCCESS', 'DELETE', '/api/v1/logs/cleanup', '10.20.1.25', 'etl-test-1', '[]'::jsonb, '{"removed": 1820}'::jsonb),
  ('a0000000-0000-4000-8000-00000000000c', now() - interval '5 days',  '9e4bc20d', 'USER', 'admin', '["admin"]'::jsonb,
   'AUTH_LOGIN_FAILED', 'AUTH', 'WARNING', 'USER', 'admin', 'admin',
   'DENIED', 'POST', '/api/v1/auth/login', '10.20.1.99', 'etl-test-1', '[]'::jsonb, '{"reason":"bad credentials"}'::jsonb)
ON CONFLICT (event_id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 12. Keep sequences ahead of the explicit IDs
-- ---------------------------------------------------------------------------
SELECT setval(pg_get_serial_sequence('etl_source', 'id'), (SELECT max(id) FROM etl_source));
SELECT setval(pg_get_serial_sequence('etl_target', 'id'), (SELECT max(id) FROM etl_target));
SELECT setval('tb_imp_etl_tid_seq', (SELECT max(id) FROM etl_table));
SELECT setval(pg_get_serial_sequence('etl_execution', 'id'), (SELECT max(id) FROM etl_execution));

COMMIT;

-- Verification summary
SELECT 'sources' AS metric, count(*) AS value FROM etl_source
UNION ALL SELECT 'enabled_sources', count(*) FROM etl_source WHERE enabled
UNION ALL SELECT 'tables', count(*) FROM etl_table WHERE retired_at IS NULL
UNION ALL SELECT 'active_tables', count(*) FROM vw_etl_table_with_source WHERE enabled AND status <> 'X' AND retired_at IS NULL
UNION ALL SELECT 'stat_days', count(DISTINCT biz_date) FROM etl_statistic
UNION ALL SELECT 'stat_rows', count(*) FROM etl_statistic
UNION ALL SELECT 'today_rows', count(*) FROM etl_statistic WHERE biz_date = current_date
UNION ALL SELECT 'journeys_today', count(*) FROM etl_jour WHERE start_at::date = current_date
UNION ALL SELECT 'failed_journeys_14d', count(*) FROM etl_jour WHERE status = false AND start_at >= current_date - 13
UNION ALL SELECT 'executions_today', count(*) FROM etl_execution WHERE biz_date = current_date
UNION ALL SELECT 'logs', count(*) FROM addax_log
UNION ALL SELECT 'unread_notifications', count(*) FROM user_notification WHERE status = 'UNREAD';
