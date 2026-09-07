-- =====================================================================
-- 自研电商ERP erp 建表脚本 v0.1(Boot 4 骨架配套)
-- 字符集 utf8mb4;金额一律 DECIMAL(12,4);时间 DATETIME
-- 规约:表/列必须带 COMMENT 中文释义(erp-codegen 以 COMMENT 生成实体 javadoc)
-- 与已生成 CRUD 对齐的表:sys_user/sys_role/sys_dict/shop/brand/
--   product_category/product/product_sku;其余为 docs/03 设计的核心表
-- =====================================================================

CREATE DATABASE IF NOT EXISTS erp DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE erp;

-- ---------------- 系统管理 ----------------
CREATE TABLE IF NOT EXISTS sys_user (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    username    VARCHAR(64)  NOT NULL COMMENT '登录名',
    password    VARCHAR(128) NOT NULL COMMENT 'BCrypt哈希',
    nickname    VARCHAR(64)  NULL COMMENT '昵称',
    email       VARCHAR(128) NULL COMMENT '邮箱',
    phone       VARCHAR(32)  NULL COMMENT '手机号',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用0禁用',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_username (username)
) COMMENT '系统用户';

CREATE TABLE IF NOT EXISTS sys_role (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    role_name  VARCHAR(64) NOT NULL COMMENT '角色名称',
    role_key   VARCHAR(64) NOT NULL COMMENT '权限标识',
    status     TINYINT     NOT NULL DEFAULT 1 COMMENT '1启用0禁用',
    remark     VARCHAR(255) NULL COMMENT '备注',
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_role_key (role_key)
) COMMENT '角色';

-- RBAC:菜单 / 角色-菜单 / 用户-角色(TODO#1 已落地;时间戳两列 2026-09-03 补齐,规约见 docs/07 §6.1)
CREATE TABLE IF NOT EXISTS sys_menu (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    parent_id  BIGINT       NOT NULL DEFAULT 0 COMMENT '父菜单ID,0=根',
    menu_name  VARCHAR(64)  NOT NULL COMMENT '菜单名称',
    menu_type  TINYINT      NOT NULL DEFAULT 2 COMMENT '1目录 2菜单 3按钮',
    perm_key   VARCHAR(128) NULL COMMENT '权限标识,如 system:user:list;按钮级必填,目录/菜单可空',
    path       VARCHAR(255) NULL COMMENT '前端路由路径',
    component  VARCHAR(255) NULL COMMENT '前端组件路径',
    icon       VARCHAR(64)  NULL COMMENT '图标',
    sort       INT          NOT NULL DEFAULT 0 COMMENT '同级排序,小在前',
    visible    TINYINT      NOT NULL DEFAULT 1 COMMENT '1显示 0隐藏',
    status     TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0禁用',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_parent (parent_id)
) COMMENT '菜单/权限';

CREATE TABLE IF NOT EXISTS sys_user_role (
    user_id    BIGINT   NOT NULL COMMENT '用户ID(sys_user.id)',
    role_id    BIGINT   NOT NULL COMMENT '角色ID(sys_role.id)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (user_id, role_id)
) COMMENT '用户-角色关联';

CREATE TABLE IF NOT EXISTS sys_role_menu (
    role_id    BIGINT   NOT NULL COMMENT '角色ID(sys_role.id)',
    menu_id    BIGINT   NOT NULL COMMENT '菜单ID(sys_menu.id)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (role_id, menu_id)
) COMMENT '角色-菜单关联';

-- 初始化数据(INSERT IGNORE 幂等,重跑脚本不重复):admin 角色 + admin 用户 + 菜单骨架
-- admin 默认密码 admin@123(下方为 BCrypt 哈希),首次登录后请立即改密
-- 若此前已手工建过 admin(明文密码),执行:
--   UPDATE sys_user SET password='$2a$10$RUQnRQ.nZl96aGYpIqYSfOcBL4arfmpxEvtUuF8qQ05EMJ7hRKY5e' WHERE username='admin';
INSERT IGNORE INTO sys_role (id, role_name, role_key, status, remark) VALUES
(1, '超级管理员', 'admin', 1, '内置角色,拥有全部菜单,禁删');
INSERT IGNORE INTO sys_user (id, username, password, nickname, status) VALUES
(1, 'admin', '$2a$10$RUQnRQ.nZl96aGYpIqYSfOcBL4arfmpxEvtUuF8qQ05EMJ7hRKY5e', '管理员', 1);
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (1, 1);
-- 菜单种子(#16 前端工程收敛:页面 component 权威源 = erp-web/tools/specs/*;按钮 id 段 200+ 与 menuId*100+n(1100+,gen:page 生成段),按父菜单分组;
-- icon 仅限 @element-plus/icons-vue 导出图标名(如 User/Menu/Tools,kebab 亦可解析),自造名如 peoples/tree/tool 前端不渲染)
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, perm_key, path, component, icon, sort) VALUES
(1, 0, '系统管理', 1, NULL, '/system', NULL, 'setting', 7),
(2, 1, '用户管理', 2, 'system:user:list', '/system/users', 'system/user/index', 'user', 1),
(3, 1, '角色管理', 2, 'system:role:list', '/system/roles', 'system/role/index', 'Avatar', 2),
(4, 1, '菜单管理', 2, 'system:menu:list', '/system/menus', 'system/menu/index', 'Menu', 3),
(5, 6, '店铺管理', 2, 'shop:list', '/shop', 'shop/index', 'Shop', 1),
(6, 0, '商品中心', 1, NULL, '/goods', NULL, 'list', 2),
(7, 6, '商品管理', 2, 'goods:list', '/goods/product', 'goods/product/index', 'Goods', 2),
(8, 0, '订单中心', 1, NULL, '/order', NULL, 'Tickets', 3),
(9, 8, '订单管理', 2, 'order:list', '/order/list', 'order/index', 'Memo', 1),
(100, 1, '字典管理', 2, 'system:dict:list', '/system/dicts', 'system/dict/index', 'notebook', 4),
(101, 6, '平台商品', 2, 'shop:product:list', '/goods/shop-products', 'shop/shop-product/index', 'list', 3),
-- 采购域(#10 前端三页 2026-09-05:supplier 全 CRUD / 采购单 audit·close / 入库单 confirm·cancel;specs=purchase-*.txt)
(10, 0, '采购管理', 1, NULL, '/purchase', NULL, 'suitcase', 4),
(11, 10, '供应商管理', 2, 'purchase:supplier:list', '/purchase/suppliers', 'purchase/supplier/index', 'office-building', 1),
(12, 10, '采购单', 2, 'purchase:order:list', '/purchase/orders', 'purchase/order/index', 'tickets', 2),
(13, 10, '入库单', 2, 'purchase:inbound:list', '/purchase/inbounds', 'purchase/inbound/index', 'box', 3),
-- 发货单(#11 前端页 2026-09-06:列表 + ship/deliver/cancel + 明细展开,订单中心下;spec=delivery.txt)
(14, 8, '发货单', 2, 'fulfill:delivery:list', '/fulfill/delivery-orders', 'fulfill/delivery/index', 'van', 2),
-- 售后单(#12 前端页 2026-09-06:列表 + 五动作人工处理 + 收退件复合表单,订单中心下;spec=aftersale-order.txt)
(15, 8, '售后单', 2, 'aftersale:order:list', '/aftersale/orders', 'aftersale/order/index', 'service', 3),
-- SKU匹配(#5 前端页 2026-09-06:待匹配列表 + 人工绑定,商品中心下;spec=shop-product-sku.txt)
(16, 6, 'SKU匹配', 2, 'shop:product-sku:list', '/goods/shop-product-skus', 'shop/shop-product-sku/index', 'connection', 4),
-- 库存管理(#7 前端两页 2026-09-06:查询 + 流水,均为只读;specs=inventory.txt/inventory-flow.txt)
(17, 0, '库存管理', 1, NULL, '/inventory', NULL, 'house', 5),
(18, 17, '库存查询', 2, 'inventory:list', '/inventory/inventories', 'inventory/inventory/index', 'grid', 1),
(19, 17, '库存流水', 2, 'inventory:flow:list', '/inventory/flows', 'inventory/flow/index', 'document', 2),
-- 仓库管理(#16 前端页 2026-09-06:全 CRUD,库存管理目录下;spec=warehouse.txt)
(20, 17, '仓库管理', 2, 'warehouse:list', '/inventory/warehouses', 'warehouse/warehouse/index', 'box', 3),
-- 拉单日志(#4 前端观测页 2026-09-06:pull_log 只读列表,2026-09-07 菜单整理:系统管理 -> 订单中心;spec=pull-log.txt)
(21, 8, '拉单日志', 2, 'shop:pulllog:list', '/system/pull-logs', 'shop/pull-log/index', 'document', 4),
-- 通知中心(#14 完整列表面 2026-09-06:铃铛下拉之外的全量分页 + 已读过滤;页面动作仅本人已读状态无 permKey;
--   2026-09-07 菜单整理:系统管理 -> 顶级 sort=6,path 保持 /system/notifications 不变——页面 page-id 与之绑定,改 path 会使列配置失联)
(22, 0, '通知中心', 2, 'system:notification:list', '/system/notifications', 'system/notification/index', 'bell', 6),
-- 商品分类(#5 前端树形页 2026-09-06:手写页,gen:page 不适用——树形域无分页端点;编辑态禁改父级,后端成环校验 TODO#7 待补)
(23, 6, '分类管理', 2, 'goods:category:list', '/goods/categories', 'goods/category/index', 'Collection', 5),
-- 品牌管理(#5 gen:page 生成 2026-09-06,spec=goods-brand.txt;契约无业务过滤字段,页无搜索表单)
(24, 6, '品牌管理', 2, 'goods:brand:list', '/goods/brands', 'goods/brand/index', 'CollectionTag', 6),
-- AI助手(#6 前端三页:对话/建议 2026-09-06,智能体 2026-09-07;对话与智能体均手写页(SSE 流式会话,gen:page 不适用),
-- 建议为 gen:page 生成,spec=ai-suggestion.txt;后端登录即可,页面级 perm_key 同通知中心口径,无按钮级种子;
-- 2026-09-07 菜单整理:AI助手顶级 sort 6 -> 1(排最前),与系统管理互换)
(25, 0, 'AI助手', 1, NULL, '/ai', NULL, 'cpu', 1),
(26, 25, 'AI对话', 2, 'ai:chat:list', '/ai/chat', 'ai/chat/index', 'chat-dot-round', 1),
(27, 25, 'AI建议', 2, 'ai:suggestion:list', '/ai/suggestions', 'ai/ai-suggestion/index', 'magic-stick', 2),
(28, 25, 'AI智能体', 2, 'ai:agent:list', '/ai/agent', 'ai/agent/index', 'chat-line-round', 3),
-- 系统设置(TODO#18 前端页 2026-09-07:sys_config 分组面板,手写页(gen:page 不适用——非 CRUD 列表页,
--   只读拉全量+保存动作,后端 admin 双闸);按钮组仅“保存参数”一个动作,幂等 upsert)
(29, 1, '系统设置', 2, 'system:config:list', '/system/configs', 'system/config/index', 'Tools', 5);
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, perm_key) VALUES
(200, 2, '新增', 3, 'system:user:add'),
(201, 2, '编辑', 3, 'system:user:edit'),
(202, 2, '删除', 3, 'system:user:remove'),
(203, 2, '角色分配', 3, 'system:user:roles'),
(204, 2, '重置密码', 3, 'system:user:password'),
(210, 3, '新增', 3, 'system:role:add'),
(211, 3, '编辑', 3, 'system:role:edit'),
(212, 3, '删除', 3, 'system:role:remove'),
(213, 3, '菜单授权', 3, 'system:role:menus'),
(220, 4, '新增', 3, 'system:menu:add'),
(221, 4, '编辑', 3, 'system:menu:edit'),
(222, 4, '删除', 3, 'system:menu:remove'),
(230, 5, '新增', 3, 'shop:add'),
(231, 5, '编辑', 3, 'shop:edit'),
(232, 5, '删除', 3, 'shop:remove'),
(290, 29, '保存参数', 3, 'system:config:save');
(233, 5, '平台授权', 3, 'shop:auth-url'),
(240, 100, '新增', 3, 'system:dict:add'),
(241, 100, '编辑', 3, 'system:dict:edit'),
(242, 100, '删除', 3, 'system:dict:remove'),
-- 采购域按钮(#10 前端三页;id 段 = menuId*100+n,audit/close 后端另有 @PreAuthorize hasRole('admin') 双闸)
(1101, 11, '新增', 3, 'purchase:supplier:add'),
(1102, 11, '编辑', 3, 'purchase:supplier:edit'),
(1103, 11, '删除', 3, 'purchase:supplier:remove'),
(1201, 12, '新增', 3, 'purchase:order:add'),
(1202, 12, '编辑', 3, 'purchase:order:edit'),
(1203, 12, '删除', 3, 'purchase:order:remove'),
(1204, 12, '审核', 3, 'purchase:order:audit'),
(1205, 12, '关闭', 3, 'purchase:order:close'),
(1301, 13, '确认入库', 3, 'purchase:inbound:confirm'),
(1302, 13, '取消', 3, 'purchase:inbound:cancel'),
-- 新建入库单(#10 建单表单人工扩展槽 2026-09-06,form=InboundCreateForm;已建库直接跑新增段)
(1303, 13, '新建入库单', 3, 'purchase:inbound:add'),
-- 发货单按钮(#11 前端页;id 段 = menuId*100+n,条件更新即守卫,后端不限 admin)
(1401, 14, '确认发货', 3, 'fulfill:delivery:ship'),
(1402, 14, '标记签收', 3, 'fulfill:delivery:deliver'),
(1403, 14, '取消', 3, 'fulfill:delivery:cancel'),
-- 新建发货单(#11 建单表单人工扩展槽 2026-09-06,form=DeliveryCreateForm;已建库直接跑新增段)
(1404, 14, '新建发货单', 3, 'fulfill:delivery:add'),
-- 编辑发货单(#11 后补物流信息槽位 2026-09-06,form=DeliveryEditForm;仅 PENDING,明细整体替换并重算占用)
(1405, 14, '编辑发货单', 3, 'fulfill:delivery:edit'),
-- 售后单按钮(#12 前端页,五动作按状态机裁剪)
(1501, 15, '同意', 3, 'aftersale:order:agree'),
(1502, 15, '拒绝', 3, 'aftersale:order:reject'),
(1503, 15, '收退件', 3, 'aftersale:order:receive-return'),
(1504, 15, '退款', 3, 'aftersale:order:refund'),
(1505, 15, '完成', 3, 'aftersale:order:complete'),
-- SKU匹配按钮(#5 前端页;sku_id 存在性校验在后端)
(1601, 16, '人工绑定', 3, 'shop:product-sku:bind'),
-- 仓库管理按钮(#16 前端页;id 段 = menuId*100+n)
(2001, 20, '新增', 3, 'warehouse:add'),
(2002, 20, '编辑', 3, 'warehouse:edit'),
(2003, 20, '删除', 3, 'warehouse:remove'),
-- 商品分类按钮(#5 前端树形页 2026-09-06)
(2301, 23, '新增', 3, 'goods:category:add'),
(2302, 23, '编辑', 3, 'goods:category:edit'),
(2303, 23, '删除', 3, 'goods:category:remove'),
-- 品牌管理按钮(#5 gen:page 生成 2026-09-06)
(2401, 24, '新增', 3, 'goods:brand:add'),
(2402, 24, '编辑', 3, 'goods:brand:edit'),
(2403, 24, '删除', 3, 'goods:brand:remove');
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES
(1,1),(1,2),(1,3),(1,4),(1,5),(1,6),(1,7),(1,8),(1,9),(1,100),(1,101),
(1,200),(1,201),(1,202),(1,203),(1,204),(1,210),(1,211),(1,212),(1,213),
(1,220),(1,221),(1,222),(1,230),(1,231),(1,232),(1,233),(1,240),(1,241),(1,242),
(1,10),(1,11),(1,12),(1,13),(1,14),(1,15),(1,16),(1,17),(1,18),(1,19),
(1,1101),(1,1102),(1,1103),(1,1201),(1,1202),(1,1203),(1,1204),(1,1205),(1,1301),(1,1302),
(1,1401),(1,1402),(1,1403),(1,1404),(1,1405),
(1,1501),(1,1502),(1,1503),(1,1504),(1,1505),
(1,1601),
(1,20),(1,2001),(1,2002),(1,2003),(1,21),(1,22),
(1,23),(1,2301),(1,2302),(1,2303),(1,24),(1,2401),(1,2402),(1,2403),
(1,25),(1,26),(1,27),(1,28),(1,29),(1,290);
-- ⚠️ 已建库(旧种子已插入)需手工执行对齐 --
-- UPDATE sys_menu SET path='/goods/product', component='goods/product/index' WHERE id=7;   -- IGNORE 不更新存量行
-- 再执行上面对应新增段(各新增段均为全新 id,含后续追加的按钮/页面/授权行,整段重跑 INSERT IGNORE 即可,幂等);
-- 菜单变更后重新登录生效
-- TODO#18(2026-09-07):已建库手工补齐 = 重跑上面 sys_menu 29/290 段 + sys_role_menu (1,29),(1,290) 段
--   + CREATE TABLE sys_config 段(全部 INSERT IGNORE / CREATE IF NOT EXISTS 幂等,整段重跑即可)
-- 菜单整理(2026-09-07,两次合并最终态):AI助手顶级置顶 + 系统管理瘦身(店铺管理->商品中心,拉单日志->订单中心,通知中心->顶级);
--   最终顶级排序:AI助手/商品中心/订单中心/采购管理/库存管理/通知中心/系统管理;系统管理仅剩用户/角色/菜单/字典/系统设置;
--   已建库手工对齐 = 执行下面 12 条(全绝对值幂等,与执行顺序无关),或跑 python scripts/menu_tool.py reorg(自动读 local.properties 连接),改完重新登录生效:
--   UPDATE sys_menu SET sort = 1 WHERE id = 25;                          -- AI助手   -> 顶级第1
--   UPDATE sys_menu SET parent_id = 6, sort = 1 WHERE id = 5;            -- 店铺管理 -> 商品中心第1
--   UPDATE sys_menu SET sort = 2 WHERE id = 7;                           -- 商品管理
--   UPDATE sys_menu SET sort = 3 WHERE id = 101;                         -- 平台商品
--   UPDATE sys_menu SET sort = 4 WHERE id = 16;                          -- SKU匹配
--   UPDATE sys_menu SET sort = 5 WHERE id = 23;                          -- 分类管理
--   UPDATE sys_menu SET sort = 6 WHERE id = 24;                          -- 品牌管理
--   UPDATE sys_menu SET parent_id = 8, sort = 4 WHERE id = 21;           -- 拉单日志 -> 订单中心第4
--   UPDATE sys_menu SET parent_id = 0, sort = 6 WHERE id = 22;           -- 通知中心 -> 顶级第6
--   UPDATE sys_menu SET sort = 4 WHERE id = 100;                         -- 字典管理
--   UPDATE sys_menu SET sort = 5 WHERE id = 29;                          -- 系统设置(系统管理第5)
--   UPDATE sys_menu SET sort = 7 WHERE id = 1;                           -- 系统管理 -> 顶级第7(最后)

CREATE TABLE IF NOT EXISTS sys_dict (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    dict_type  VARCHAR(64)  NOT NULL COMMENT '字典类型',
    dict_label VARCHAR(128) NOT NULL COMMENT '显示名',
    dict_value VARCHAR(128) NOT NULL COMMENT '存储值',
    sort       INT          NOT NULL DEFAULT 0 COMMENT '同级排序,小在前',
    status     TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用0禁用',
    remark     VARCHAR(255) NULL COMMENT '备注',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_dict_type (dict_type)
) COMMENT '数据字典';

-- 字典种子:shop_platform(店铺下拉全端共用;dict_value = PlatformType 枚举名,#16 P6)
INSERT IGNORE INTO sys_dict (id, dict_type, dict_label, dict_value, sort, status, remark) VALUES
(1,  'shop_platform', '淘宝',        'TAOBAO',        1,  1, '国内'),
(2,  'shop_platform', '京东',        'JD',            2,  1, '国内'),
(3,  'shop_platform', '拼多多',      'PDD',           3,  1, '国内'),
(4,  'shop_platform', '抖店',        'DOUYIN',        4,  1, '国内'),
(5,  'shop_platform', '微信小店',    'WECHAT_SHOP',   5,  1, '国内'),
(6,  'shop_platform', '快手小店',    'KUAISHOU',      6,  1, '国内'),
(7,  'shop_platform', '小红书',      'XHS',           7,  1, '国内'),
(8,  'shop_platform', '亚马逊',      'AMAZON',        8,  1, '跨境'),
(9,  'shop_platform', 'eBay',        'EBAY',          9,  1, '跨境'),
(10, 'shop_platform', 'Shopee',      'SHOPEE',        10, 1, '跨境'),
(11, 'shop_platform', 'Lazada',      'LAZADA',        11, 1, '跨境'),
(12, 'shop_platform', 'TikTok Shop', 'TIKTOK_GLOBAL', 12, 1, '跨境'),
(13, 'shop_platform', '速卖通',      'ALIEXPRESS',    13, 1, '跨境'),
(14, 'shop_platform', 'Temu',        'TEMU',          14, 1, '跨境');
-- ⚠️ 已建库(空表)可直接执行上面 INSERT IGNORE 段补种子;


-- 站内通知(TODO#14,2026-09-04:系统写入表,告警由任务扇出写入,对外只读查询 + 用户已读状态接口)
CREATE TABLE IF NOT EXISTS sys_notification (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    user_id     BIGINT       NOT NULL COMMENT '接收用户ID(sys_user.id),写侧扇出到全部启用用户',
    title       VARCHAR(128) NOT NULL COMMENT '通知标题',
    content     VARCHAR(1024) NULL COMMENT '通知内容(写侧截断)',
    notify_type VARCHAR(32)  NOT NULL COMMENT '通知类型:PULL_FAIL=拉单连续失败告警;LOW_STOCK=低库存/SHIP_TIMEOUT=发货超时/REFUND_ABNORMAL=退款异常/SLOW_MOVING=滞销/OVERSTOCK=积压(#6 预警引擎)',
    biz_type    VARCHAR(32)  NULL COMMENT '关联业务类型:SHOP等',
    biz_id      BIGINT       NULL COMMENT '关联业务ID(如店铺ID)',
    read_status TINYINT      NOT NULL DEFAULT 0 COMMENT '已读状态:0未读 1已读',
    read_at     DATETIME     NULL COMMENT '已读时间',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_user_read (user_id, read_status),
    KEY idx_created (created_at)
) COMMENT '站内通知(系统告警扇出+用户已读状态)';

-- 系统参数(TODO#18,2026-09-07 拍板:大模型/预警/AI 工作流等启动后可变项前端可配):
--   键值对单表 + GROUP 分组(前端按组渲染面板);值一律 VARCHAR(1024) 文本存储,数字/布尔由消费侧解析,
--   解析失败回落代码默认值(禁浮点/金额键,金额走 DECIMAL 表列);config_key 唯一;
--   全键种子默认值落库(下方 INSERT 段,DB 即生效值,面板/直查均可见);
--   凭证类(AI api-key/平台密钥)禁入本表——安全红线 docs/07 §7,凭证只走环境变量/local.properties
CREATE TABLE IF NOT EXISTS sys_config (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    config_group VARCHAR(32)   NOT NULL COMMENT '参数组:AI=大模型与AI工作流(含对话/Agent提示词与连接) ALERT=库存预警 SALES=销量统计',
    config_key   VARCHAR(64)   NOT NULL COMMENT '参数键(与 yml relaxed-binding 键同名,如 erp.ai.replenish.low-stock-threshold),唯一',
    config_value VARCHAR(1024) NULL COMMENT '参数值(文本存储,数字/布尔由消费侧解析;凭证类禁入本表)',
    remark       VARCHAR(255)  NULL COMMENT '参数说明(前端表单旁展示)',
    created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_config_key (config_key)
) COMMENT '系统参数(启动后可变项键值对:大模型/预警阈值/AI工作流参数等)';

-- 参数种子(#18 2026-09-07 拍板:白名单全键落默认值,DB 即生效值,后续调参在面板改即可;
--   值与代码默认值逐字对齐 ErpAiProperties/ErpAlertProperties/ErpSalesProperties,多行 prompt 以 \n 转义;
--   模型连接三键(model/agent.model/agent.base-url)值落 NULL:实际值随部署环境变量(OPENAI_BASE_URL/AI_MODEL)浮动,
--   写死种子值会压掉部署侧配置,DashScope 等环境会被打回 deepseek 缺省;
--   新增白名单键必须同步补种子行;prompt 超长上限 VARCHAR(1024),改提示词超限先扩列)
INSERT IGNORE INTO sys_config (config_group, config_key, config_value, remark) VALUES
('AI', 'erp.ai.model', NULL, 'AI 模型名覆盖(对话/AI 工作流共用);未配置回落环境变量 AI_MODEL(缺省 deepseek-chat)'),
('AI', 'erp.ai.system-prompt', '你是电商 ERP 智能助手。规则:\n1. 只能通过提供的只读工具查询数据回答问题,禁止编造或估算数据;查不到就如实说明。\n2. 你没有任何写操作能力:库存调整、改价、发货、售后处理等必须提示用户在系统页面人工操作。\n3. 金额均为原币金额,注意说明币种;不要自行换算汇率。\n4. 回答用中文,先给结论再给依据;列表类回答不超过 20 条,数据多时提示用户细化过滤条件。', 'chat 对话 system 提示词(只读工具查数/禁编造/禁写操作/中文先结论)'),
('AI', 'erp.ai.tool-audit-max-length', '500', '工具调用审计行入参 JSON 截断长度(防超长入参撑爆审计表)'),
('AI', 'erp.ai.agent.base-url', NULL, 'Agent 模型服务地址(OpenAI 兼容);未配置回落环境变量 OPENAI_BASE_URL(缺省 https://api.deepseek.com)'),
('AI', 'erp.ai.agent.model', NULL, 'Agent 模型名覆盖;未配置回落环境变量 AI_MODEL(缺省 deepseek-chat)'),
('AI', 'erp.ai.agent.max-iters', '10', 'Agent ReAct 单轮最大思考-行动循环次数(防死循环护栏)'),
('AI', 'erp.ai.agent.history-max-messages', '40', '多轮会话历史重放行数上限(仅重放最近 N 行 USER/AI,防长会话 token 膨胀)'),
('AI', 'erp.ai.agent.support-prompt', '你是电商 ERP 智能客服助手。规则:\n1. 只能通过提供的只读工具查询数据回答问题,禁止编造或估算数据;查不到就如实说明。\n2. 你没有任何写操作能力:库存调整、改价、发货、售后处理等必须提示用户在系统页面人工操作。\n3. 金额均为原币金额,注意说明币种;不要自行换算汇率。\n4. 回答用中文,先给结论再给依据。', 'Agent 客服角色 system 提示词(全量只读工具:订单/库存/商品/售后)'),
('AI', 'erp.ai.agent.ops-prompt', '你是电商 ERP 运营助手,专注库存与商品盘面。规则:\n1. 只能通过提供的只读工具查询数据,禁止编造或估算;查不到就如实说明。\n2. 你没有任何写操作能力:补货下单、库存调整等提示用户走采购/库存页面人工操作。\n3. 回答用中文,先给结论再给依据;涉及库存时说明口径(在库/占用/在途/可用)。', 'Agent 运营角色 system 提示词(库存/商品盘面工具)'),
('AI', 'erp.ai.replenish.low-stock-threshold', '10', '补货:低库存阈值(库存可用 ≤ 此值参与补货建议)'),
('AI', 'erp.ai.replenish.coverage-days', '14', '补货:目标覆盖天数(建议量使库存可支撑 N 天)'),
('AI', 'erp.ai.replenish.sales-window-days', '30', '补货:动销统计窗口(天,日均销量=窗口销量/窗口天数;窗口内零动销 SKU 不硬补)'),
('AI', 'erp.ai.replenish.min-suggest-qty', '10', '补货:最小建议量下限(仅对有动销 SKU 起下限作用)'),
('AI', 'erp.ai.replenish.summary-prompt', '你是电商 ERP 的补货分析助手。根据给定的库存与建议补货量,为每个 SKU 写一句简短中文摘要,说明补货理由(如缺货风险/覆盖天数)。只输出 JSON 数组,不输出任何其他文字。', '补货摘要节点 system 提示词'),
('AI', 'erp.ai.anomaly.big-order-amount', '10000', '异常检测:大额订单阈值(本位币,下单金额×汇率 ≥ 此值命中)'),
('AI', 'erp.ai.anomaly.unpaid-hours', '48', '异常检测:未支付超时(小时,待支付超 N 小时命中)'),
('AI', 'erp.ai.anomaly.high-discount-ratio', '0.5', '异常检测:高折扣比率(0~1,折扣金额≥下单金额×此比率命中)'),
('AI', 'erp.ai.anomaly.llm-max-items', '20', '异常检测:单轮送 LLM 评分上限(超限按基线风险降序截断,成本护栏)'),
('AI', 'erp.ai.anomaly.score-prompt', '你是电商 ERP 的订单风控助手。根据给定的订单信息与其命中的规则,为每张可疑订单评估风险等级(只能取 LOW/MID/HIGH 之一)并给一句不超过 40 字的中文理由。只输出 JSON 数组,元素形如 {"orderId":1,"riskLevel":"MID","reason":"..."},不输出任何其他文字。', '异常评分节点 system 提示词'),
('ALERT', 'erp.alert.enabled', 'true', '库存预警总开关(false 时预警任务直接返回不扫描)'),
('ALERT', 'erp.alert.quiet-hours', '24', '预警静默期(小时,同类型告警窗口内只发一条防刷屏)'),
('ALERT', 'erp.alert.low-stock-threshold', '10', '预警:低库存阈值(可用库存 ≤ 此值命中)'),
('ALERT', 'erp.alert.ship-timeout-hours', '48', '预警:发货超时(小时,待发货超 N 小时命中)'),
('ALERT', 'erp.alert.refund-window-hours', '24', '预警:退款统计窗口(小时,仅统计窗口内创建的退款单)'),
('ALERT', 'erp.alert.refund-count-threshold', '5', '预警:退款次数阈值(单店铺窗口内退款单数 ≥ 此值命中)'),
('ALERT', 'erp.alert.top-n', '5', '预警:通知内容明细最大条数(超出以"等"收尾)'),
('ALERT', 'erp.alert.slow-moving-days', '30', '预警:滞销判定窗口(天,窗口内零销量且有库存判滞销)'),
('ALERT', 'erp.alert.overstock-days', '90', '预警:积压阈值(天,可用库存/日均销量 ≥ 此值判积压)'),
('SALES', 'erp.sales.enabled', 'true', '销量日统计总开关(false 时任务直接返回不重算)'),
('SALES', 'erp.sales.rebuild-days', '30', '销量:回溯重算天数(每日 upsert 近 N 天,含今日,覆盖状态回传/取消修正)');
-- ⚠️ 已建库(sys_config 已建表)手工补齐 = 直接执行上面 INSERT IGNORE 段(uk_config_key 冲突即跳过,幂等;
--   已人工改过值的键不会被种子覆盖);模型连接三键种子为 NULL 属预期,值回落部署环境变量


-- ---------------- 店铺/平台 ----------------
CREATE TABLE IF NOT EXISTS shop (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    merchant_id    BIGINT      NOT NULL DEFAULT 1 COMMENT '商户ID,一期固定1',
    platform       VARCHAR(32) NOT NULL COMMENT 'PlatformType枚举名:TAOBAO/AMAZON/...',
    shop_name      VARCHAR(128) NOT NULL COMMENT '店铺名称',
    seller_id      VARCHAR(64)  NULL COMMENT '平台侧店铺标识',
    app_key        VARCHAR(128) NULL COMMENT '平台应用Key,明文存(非机密,参与签名)',
    app_secret     VARCHAR(512) NULL COMMENT 'AES-GCM 密文(密钥 ERP_TOKEN_KEY)',
    access_token   VARCHAR(2048) NULL COMMENT 'AES-GCM 密文(密钥 ERP_TOKEN_KEY)',
    refresh_token  VARCHAR(2048) NULL COMMENT 'AES-GCM 密文(密钥 ERP_TOKEN_KEY)',
    token_expire_at DATETIME    NULL COMMENT '令牌过期时间',
    status         TINYINT     NOT NULL DEFAULT 1 COMMENT '1启用0禁用',
    created_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_merchant_platform (merchant_id, platform),
    UNIQUE KEY uk_platform_seller (platform, seller_id)
) COMMENT '店铺(防重复建店;seller_id为NULL时不约束)';

CREATE TABLE IF NOT EXISTS pull_log (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    shop_id       BIGINT      NOT NULL COMMENT '店铺ID(shop.id)',
    data_type     VARCHAR(32) NOT NULL COMMENT 'ORDER/PRODUCT/REFUND',
    window_start  DATETIME    NOT NULL COMMENT '拉取窗口起点(含)',
    window_end    DATETIME    NOT NULL COMMENT '拉取窗口终点(含);游标=最近成功记录的window_end',
    pulled_count  INT         NOT NULL DEFAULT 0 COMMENT '本次拉取条数',
    success       TINYINT     NOT NULL DEFAULT 1 COMMENT '1成功0失败',
    error_msg     TEXT        NULL COMMENT '失败原因',
    duration_ms   INT         NULL COMMENT '本次拉取耗时(毫秒),观测慢店铺/慢接口',
    pull_way      VARCHAR(16) NULL COMMENT '触发方式:JOB定时/MANUAL手动',
    created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_shop_type_time (shop_id, data_type, window_end)
) COMMENT '平台拉取日志(增量游标依据:取成功记录的window_end,左叠5分钟)';

-- ---------------- 商品 ----------------
CREATE TABLE IF NOT EXISTS brand (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    name       VARCHAR(128) NOT NULL COMMENT '品牌名称',
    logo_url   VARCHAR(512) NULL COMMENT '品牌LOGO地址',
    remark     VARCHAR(255) NULL COMMENT '备注',
    status     TINYINT     NOT NULL DEFAULT 1 COMMENT '1启用0禁用',
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) COMMENT '品牌';

CREATE TABLE IF NOT EXISTS product_category (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    parent_id  BIGINT      NOT NULL DEFAULT 0 COMMENT '父分类ID,0=根',
    name       VARCHAR(128) NOT NULL COMMENT '分类名称',
    sort       INT         NOT NULL DEFAULT 0 COMMENT '同级排序,小在前',
    status     TINYINT     NOT NULL DEFAULT 1 COMMENT '1启用0禁用',
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_parent (parent_id)
) COMMENT '商品分类';

CREATE TABLE IF NOT EXISTS product (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    spu_code   VARCHAR(64)  NOT NULL COMMENT '内部SPU编码',
    name       VARCHAR(255) NOT NULL COMMENT '商品名称',
    category_id BIGINT      NULL COMMENT '分类ID(product_category.id)',
    brand_id   BIGINT       NULL COMMENT '品牌ID(brand.id)',
    attrs_json JSON         NULL COMMENT '销售属性',
    status     TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用0禁用',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_spu (spu_code)
) COMMENT '商品SPU';

CREATE TABLE IF NOT EXISTS product_sku (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    product_id     BIGINT      NOT NULL COMMENT '所属SPU(product.id)',
    sku_code       VARCHAR(64) NOT NULL COMMENT '内部SKU编码,唯一',
    barcode        VARCHAR(64) NULL COMMENT '条形码(EAN/UPC)',
    attrs_json     JSON        NULL COMMENT '规格值',
    cost_price     DECIMAL(12,4) NULL COMMENT '成本价',
    weight_g       INT         NULL COMMENT '重量(克),跨境物流计费依据',
    hs_code        VARCHAR(32) NULL COMMENT '跨境HS编码',
    declared_value DECIMAL(12,4) NULL COMMENT '申报价值',
    battery        TINYINT     NOT NULL DEFAULT 0 COMMENT '是否含电池',
    status         TINYINT     NOT NULL DEFAULT 1 COMMENT '1启用0禁用',
    created_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_sku (sku_code),
    KEY idx_product (product_id)
) COMMENT '商品SKU';

-- SKU映射:平台 seller_sku ↔ 内部SKU(系统心脏)
CREATE TABLE IF NOT EXISTS shop_product (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    shop_id        BIGINT       NOT NULL COMMENT '店铺ID(shop.id)',
    platform_product_id VARCHAR(64) NOT NULL COMMENT '平台商品ID',
    platform_sku_id     VARCHAR(64) NULL COMMENT '平台SKU级商品ID(部分平台有)',
    product_id     BIGINT       NULL COMMENT '绑定的内部SPU,NULL=未绑定(先拉取后绑定)',
    listing_status VARCHAR(32)  NULL COMMENT '平台侧listing状态快照',
    last_sync_at   DATETIME     NULL COMMENT '最近同步时间',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_shop_platform_product (shop_id, platform_product_id),
    KEY idx_product (product_id)
) COMMENT '店铺商品(平台listing ↔ 内部SPU)';

CREATE TABLE IF NOT EXISTS shop_product_sku (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    shop_product_id BIGINT      NOT NULL COMMENT '店铺商品ID(shop_product.id)',
    seller_sku     VARCHAR(128) NOT NULL COMMENT '平台侧SKU标识(Amazon: seller-sku;国内: sku_id),自动匹配依据',
    sku_id         BIGINT       NULL COMMENT '内部SKU(product_sku.id),NULL=未绑定(进待匹配列表)',
    quantity       INT          NULL COMMENT '平台侧可售数量快照',
    price          DECIMAL(12,4) NULL COMMENT '平台侧售价快照',
    currency       CHAR(3)      NULL COMMENT '币种(ISO 4217,如CNY/USD)',
    match_status   TINYINT      NOT NULL DEFAULT 0 COMMENT '0待匹配(sku_id为NULL) 1商家编码自动 2人工',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_shop_seller_sku (shop_product_id, seller_sku),
    KEY idx_sku (sku_id)
) COMMENT 'SKU映射绑定(订单/库存/财务都以内部sku_id为准)';

-- ---------------- 订单 ----------------
CREATE TABLE IF NOT EXISTS shop_order (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    shop_id          BIGINT       NOT NULL COMMENT '店铺ID(shop.id)',
    platform         VARCHAR(32)  NOT NULL COMMENT 'PlatformType枚举名:TAOBAO/AMAZON/...',
    platform_order_id VARCHAR(64) NOT NULL COMMENT '平台订单号,幂等唯一键',
    order_status     VARCHAR(32)  NOT NULL COMMENT 'WAIT_PAY/WAIT_SHIP/SHIPPED/COMPLETED/CANCELLED/CLOSED',
    fulfillment_channel VARCHAR(32) NOT NULL DEFAULT 'SELF_FULFILL' COMMENT 'SELF_FULFILL/FBA/OVERSEAS_WAREHOUSE',
    order_time       DATETIME     NOT NULL COMMENT '下单时间(平台侧)',
    paid_time        DATETIME     NULL COMMENT '支付时间',
    buyer_note       VARCHAR(512) NULL COMMENT '买家留言',
    receiver_name    VARCHAR(64)  NULL COMMENT '收货人姓名',
    receiver_phone   VARCHAR(32)  NULL COMMENT '收货人电话',
    receiver_country VARCHAR(8)   NULL COMMENT '收货国家(ISO 3166,如CN/US)',
    receiver_state   VARCHAR(64)  NULL COMMENT '收货省/州',
    receiver_city    VARCHAR(64)  NULL COMMENT '收货城市',
    receiver_address VARCHAR(512) NULL COMMENT '收货详细地址',
    receiver_zip     VARCHAR(32)  NULL COMMENT '收货邮编',
    currency         CHAR(3)      NOT NULL COMMENT '币种(ISO 4217)',
    exchange_rate    DECIMAL(12,8) NOT NULL DEFAULT 1 COMMENT '下单日汇率快照(原币→本位币)',
    order_amount     DECIMAL(12,4) NOT NULL DEFAULT 0 COMMENT '订单总金额(原币)',
    shipping_fee     DECIMAL(12,4) NOT NULL DEFAULT 0 COMMENT '运费(原币)',
    discount_amount  DECIMAL(12,4) NOT NULL DEFAULT 0 COMMENT '优惠金额(原币)',
    raw_json         JSON         NULL COMMENT '平台原始报文,排查/补偿用',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_shop_platform_order (shop_id, platform_order_id),
    KEY idx_order_time (order_time),
    KEY idx_status (order_status)
) COMMENT '平台订单(幂等:uk_shop_platform_order)';

CREATE TABLE IF NOT EXISTS shop_order_item (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    order_id         BIGINT       NOT NULL COMMENT '订单ID(shop_order.id)',
    platform_order_item_id VARCHAR(64) NULL COMMENT '平台子订单/明细ID',
    shop_product_id  BIGINT       NULL COMMENT '店铺商品ID(shop_product.id)',
    shop_product_sku_id BIGINT    NULL COMMENT 'SKU映射ID(shop_product_sku.id)',
    sku_id           BIGINT       NULL COMMENT '落库时匹配到的内部SKU',
    platform_sku     VARCHAR(128) NULL COMMENT '平台侧SKU标识(seller_sku快照)',
    product_name     VARCHAR(255) NULL COMMENT '商品名称快照',
    quantity         INT          NOT NULL COMMENT '数量',
    unit_price       DECIMAL(12,4) NOT NULL DEFAULT 0 COMMENT '单价(原币)',
    item_amount      DECIMAL(12,4) NOT NULL DEFAULT 0 COMMENT '小计金额(原币)=单价×数量',
    currency         CHAR(3)      NOT NULL COMMENT '币种(ISO 4217)',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_order (order_id),
    KEY idx_sku (sku_id)
) COMMENT '订单明细';

-- 销量日统计(#6 销量数据面,2026-09-07 定稿 docs/03 §7.1):支付日×SKU 合计购买数量,已支付态口径;
-- erp-api SalesSnapshotJob 每日窗口 upsert(uk_sku_date 幂等,覆盖状态回传修正),erp-ai 经 SalesQueryApi 只读
CREATE TABLE IF NOT EXISTS order_sales_daily (
    id        BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    stat_date DATE    NOT NULL COMMENT '统计日期(支付日口径,paid_time 所在日)',
    sku_id    BIGINT  NOT NULL COMMENT '内部SKU ID(product_sku.id),未绑定 SKU 不统计',
    qty_sold  INT     NOT NULL DEFAULT 0 COMMENT '当日销量(购买数量合计,已支付态 WAIT_SHIP/SHIPPED/COMPLETED)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_sku_date (sku_id, stat_date),
    KEY idx_date (stat_date)
) COMMENT='订单销量日统计';
-- ---------------- 采购/发货/售后(二期,docs/03 §5 草案定稿 2026-09-03) ----------------
CREATE TABLE IF NOT EXISTS supplier (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    name        VARCHAR(128) NOT NULL COMMENT '供应商名称',
    contact     VARCHAR(64) NULL COMMENT '联系人',
    phone       VARCHAR(32) NULL COMMENT '联系电话',
    settle_type VARCHAR(32) NULL COMMENT '结算方式,走 sys_dict(预付/月结等)',
    remark      VARCHAR(255) NULL COMMENT '备注',
    status      TINYINT NOT NULL DEFAULT 1 COMMENT '1启用0禁用',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_name (name)
) COMMENT '供应商';

CREATE TABLE IF NOT EXISTS purchase_order (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    po_no        VARCHAR(64) NOT NULL COMMENT '采购单号,唯一',
    supplier_id  BIGINT NOT NULL COMMENT '供应商ID(supplier.id)',
    warehouse_id BIGINT NOT NULL COMMENT '收货仓ID(warehouse.id)',
    status       VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT草稿/AUDITED已审核/PARTIAL_RECEIVED部分入库/RECEIVED已入库/CLOSED已关闭(状态机草案,业务确认后调整)',
    total_amount DECIMAL(12,4) NOT NULL DEFAULT 0 COMMENT '采购总金额(本位币)',
    remark       VARCHAR(255) NULL COMMENT '备注',
    created_by   BIGINT NULL COMMENT '创建人(sys_user.id)',
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_po_no (po_no),
    KEY idx_supplier (supplier_id),
    KEY idx_status (status)
) COMMENT '采购单(审核/入库状态机 TODO(#10) 人工补)';

CREATE TABLE IF NOT EXISTS purchase_order_item (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    po_id          BIGINT NOT NULL COMMENT '采购单ID(purchase_order.id)',
    sku_id         BIGINT NOT NULL COMMENT 'SKU ID(product_sku.id)',
    quantity       INT NOT NULL COMMENT '采购数量',
    arrived_qty    INT NOT NULL DEFAULT 0 COMMENT '已入库数量(入库核销累加)',
    purchase_price DECIMAL(12,4) NOT NULL DEFAULT 0 COMMENT '采购单价',
    created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_po (po_id)
) COMMENT '采购单明细';

CREATE TABLE IF NOT EXISTS purchase_inbound (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    inbound_no   VARCHAR(64) NOT NULL COMMENT '入库单号,唯一',
    po_id        BIGINT NOT NULL COMMENT '采购单ID(purchase_order.id)',
    warehouse_id BIGINT NOT NULL COMMENT '入库仓ID(warehouse.id)',
    status       VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING待入库/RECEIVED已入库/CANCELLED已取消',
    remark       VARCHAR(255) NULL COMMENT '备注(数量差异说明等)',
    created_by   BIGINT NULL COMMENT '创建人(sys_user.id)',
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_inbound_no (inbound_no),
    KEY idx_po (po_id)
) COMMENT '采购入库单(入库必须经 InventoryService.change 同事务写 flow,flow_type=IN_PURCHASE)';

CREATE TABLE IF NOT EXISTS purchase_inbound_item (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    inbound_id  BIGINT NOT NULL COMMENT '入库单ID(purchase_inbound.id)',
    po_item_id  BIGINT NOT NULL COMMENT '采购单明细ID(purchase_order_item.id)',
    sku_id      BIGINT NOT NULL COMMENT 'SKU ID(product_sku.id,冗余自采购明细,便于流水对账)',
    inbound_qty INT NOT NULL COMMENT '本单入库数量',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_inbound (inbound_id)
) COMMENT '采购入库单明细(确认入库逐行经 InventoryService.change 写 flow,#10 激活补,2026-09-04 拍板)';

CREATE TABLE IF NOT EXISTS delivery_order (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    delivery_no       VARCHAR(64) NOT NULL COMMENT '发货单号,唯一',
    order_id          BIGINT NOT NULL COMMENT '平台订单ID(shop_order.id)',
    shop_id           BIGINT NOT NULL COMMENT '店铺ID(shop.id,服务端按订单回填)',
    warehouse_id      BIGINT NOT NULL DEFAULT 0 COMMENT '出库仓ID(warehouse.id,发货指定仓,ship 时从该仓扣库存;#11 激活加列 2026-09-04)',
    type              VARCHAR(32) NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL手工/WAYBILL电子面单/SUPPLIER供应商代发/FBA/OVERSEAS海外仓',
    status            VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING待发货/SHIPPED已发货/DELIVERED已签收/CANCELLED已取消',
    ship_by_time      DATETIME NULL COMMENT '承诺发货时限(平台侧快照,国内平台考核;#11 激活加列 2026-09-04)',
    logistics_company VARCHAR(64) NULL COMMENT '物流公司',
    tracking_no       VARCHAR(64) NULL COMMENT '运单号',
    waybill_url       VARCHAR(512) NULL COMMENT '电子面单文件地址',
    shipped_at        DATETIME NULL COMMENT '发货时间(ship 确认时回写)',
    created_by        BIGINT NULL COMMENT '创建人(sys_user.id,接 SecurityContext 随前端工程;#11 激活加列 2026-09-04)',
    created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_delivery_no (delivery_no),
    UNIQUE KEY uk_tracking_no (tracking_no),
    KEY idx_order (order_id),
    KEY idx_shop (shop_id),
    KEY idx_warehouse (warehouse_id)
) COMMENT '发货单(tracking_no 可多条 NULL,MySQL UNIQUE 不约束 NULL;FBA/海外仓履约订单不产生系统内发货单)';

CREATE TABLE IF NOT EXISTS delivery_order_item (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    delivery_id   BIGINT NOT NULL COMMENT '发货单ID(delivery_order.id)',
    order_item_id BIGINT NOT NULL COMMENT '订单明细ID(shop_order_item.id)',
    sku_id        BIGINT NOT NULL COMMENT 'SKU ID(product_sku.id,冗余自订单明细,便于流水对账;未绑定行不进发货单)',
    ship_qty      INT NOT NULL COMMENT '本单发货数量',
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_delivery (delivery_id),
    KEY idx_order_item (order_item_id)
) COMMENT '发货单明细(ship 确认逐行经 InventoryService.change 写 flow,flow_type=OUT_SHIP;发货进度事实源,#11 激活补,2026-09-04 拍板:支持多次部分发货与凭证留痕)';

CREATE TABLE IF NOT EXISTS aftersale_order (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    aftersale_no       VARCHAR(64) NOT NULL COMMENT '售后单号,唯一',
    shop_id            BIGINT NOT NULL COMMENT '店铺ID(shop.id)',
    platform_refund_id VARCHAR(64) NOT NULL COMMENT '平台退款/售后单ID,幂等键',
    order_id           BIGINT NOT NULL COMMENT '关联平台订单ID(shop_order.id)',
    warehouse_id       BIGINT NULL COMMENT '退货入库仓ID(warehouse.id,收退件时必填回填;#12 激活加列 2026-09-04)',
    type               VARCHAR(32) NOT NULL COMMENT 'REFUND_ONLY仅退款/RETURN_REFUND退货退款/EXCHANGE换货/RESEND补发',
    status             VARCHAR(32) NOT NULL COMMENT 'PENDING待处理/APPROVED已同意/RETURNING待收退件/RETURN_RECEIVED已收退件/REFUNDED已退款/COMPLETED已完成/REJECTED已拒绝/CANCELLED已取消(#12 状态机 2026-09-04 定版)',
    refund_amount      DECIMAL(12,4) NULL COMMENT '退款金额(原币)',
    currency           CHAR(3) NOT NULL COMMENT '币种(ISO 4217)',
    reason             VARCHAR(512) NULL COMMENT '售后原因',
    result             VARCHAR(512) NULL COMMENT '处理结果',
    created_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_shop_platform_refund (shop_id, platform_refund_id),
    UNIQUE KEY uk_aftersale_no (aftersale_no),
    KEY idx_order (order_id),
    KEY idx_status (status)
) COMMENT '售后单(平台售后同步,幂等:uk_shop_platform_refund)';

CREATE TABLE IF NOT EXISTS aftersale_return_item (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    aftersale_id  BIGINT NOT NULL COMMENT '售后单ID(aftersale_order.id)',
    order_item_id BIGINT NOT NULL COMMENT '订单明细ID(shop_order_item.id,SKU 归属锚点)',
    sku_id        BIGINT NOT NULL COMMENT '内部SKU ID(product_sku.id,服务端按订单行回填,不入参)',
    return_qty    INT    NOT NULL COMMENT '实收退货数量(正数,仓库验件人工录入,可≠平台申明)',
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_aftersale (aftersale_id),
    KEY idx_order_item (order_item_id)
) COMMENT '售后退货明细(receive-return 人工录入实收,同事务 IN_RETURN 动账凭证;#12 激活补,2026-09-04 拍板:仅 sku_id 已绑定行可退,归属/数量预校验防超退)';

-- ---------------- 仓库/库存 ----------------
CREATE TABLE IF NOT EXISTS warehouse (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    wh_name    VARCHAR(128) NOT NULL COMMENT '仓库名称',
    wh_type    VARCHAR(32)  NOT NULL DEFAULT 'SELF' COMMENT 'SELF自仓/FBA/OVERSEAS海外仓/VIRTUAL虚拟仓',
    country    VARCHAR(8)   NULL COMMENT '国家(ISO 3166)',
    address    VARCHAR(512) NULL COMMENT '仓库地址',
    status     TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用0禁用',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) COMMENT '仓库';

CREATE TABLE IF NOT EXISTS inventory (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    sku_id       BIGINT NOT NULL COMMENT 'SKU ID(product_sku.id)',
    warehouse_id BIGINT NOT NULL COMMENT '仓库ID(warehouse.id)',
    qty_on_hand  INT    NOT NULL DEFAULT 0 COMMENT '在库',
    qty_locked   INT    NOT NULL DEFAULT 0 COMMENT '占用(发货单占用未发货,建单占用/取消·删除·改单释放,#11)',
    qty_transit  INT    NOT NULL DEFAULT 0 COMMENT '在途(采购审核占用未入库,审核占/关闭释放,#10)',
    qty_available INT  NOT NULL DEFAULT 0 COMMENT '可用=在库-占用,由InventoryService同事务维护,禁止旁路update',
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_sku_wh (sku_id, warehouse_id)
) COMMENT '库存(只能经 inventory_flow 变动)';

CREATE TABLE IF NOT EXISTS inventory_flow (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    sku_id       BIGINT      NOT NULL COMMENT 'SKU ID(product_sku.id)',
    warehouse_id BIGINT      NOT NULL COMMENT '仓库ID(warehouse.id)',
    flow_type    VARCHAR(32) NOT NULL COMMENT 'IN_PURCHASE采购入库(核销在途)/OUT_SHIP销售出库(核销占用)/IN_RETURN售后退货入库/ADJUST人工调整/TRANSFER_OUT调拨出库/TRANSFER_IN调拨入库/IN_TRANSIT采购在途(审核占/关闭释放)/LOCK_SHIP发货单占用(建单占/取消释放)(#7 2026-09-06 八值)',
    quantity     INT         NOT NULL COMMENT '正负数',
    before_qty   INT         NOT NULL COMMENT '变更前可用库存',
    after_qty    INT         NOT NULL COMMENT '变更后可用库存',
    biz_type     VARCHAR(32) NULL COMMENT '关联业务类型',
    biz_id       BIGINT      NULL COMMENT '关联业务单据ID',
    remark       VARCHAR(255) NULL COMMENT '备注',
    created_by   BIGINT      NULL COMMENT '操作人(sys_user.id),系统动作为NULL',
    created_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_sku_time (sku_id, created_at)
) COMMENT '库存流水(与库存变更同事务写入)';

-- ---------------- AI 辅助(三期 2026-09-06 随 #6 AI 地基落地,docs/03 §7 定稿) ----------------
CREATE TABLE IF NOT EXISTS ai_suggestion (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    suggestion_type VARCHAR(32)  NOT NULL COMMENT '建议类型:REPLENISH补货/PRICING定价/ANOMALY异常/COPYWRITING文案(封闭词表,随AI服务扩容)',
    shop_id         BIGINT       NULL COMMENT '关联店铺ID(shop.id,跨店/全局建议为NULL)',
    sku_id          BIGINT       NULL COMMENT '关联内部SKU ID(product_sku.id,非SKU维度建议为NULL)',
    ref_type        VARCHAR(32)  NULL COMMENT '关联业务类型(如SHOP_ORDER/INVENTORY,对齐inventory_flow.biz_type风格)',
    ref_id          BIGINT       NULL COMMENT '关联业务单据ID',
    payload_json    JSON         NULL COMMENT '建议结构化负载(补货量/建议价等,展示与采纳回放用)',
    summary         VARCHAR(512) NOT NULL COMMENT '建议摘要(列表直显,LLM结论一句话)',
    risk_level      VARCHAR(8)   NOT NULL DEFAULT 'LOW' COMMENT '风险等级:LOW/MID/HIGH(HIGH须人工复核)',
    status          TINYINT      NOT NULL DEFAULT 0 COMMENT '确认状态:0待确认/1已采纳/2已忽略(人工确认后走业务接口,AI禁直接写业务表)',
    confirmed_by    BIGINT       NULL COMMENT '确认人(sys_user.id)',
    confirmed_at    DATETIME     NULL COMMENT '确认时间',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_type_status (suggestion_type, status),
    KEY idx_sku (sku_id)
) COMMENT 'AI建议表(AI产出一律落此表,人工确认后走正常业务接口)';

CREATE TABLE IF NOT EXISTS ai_chat_session (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    user_id    BIGINT       NOT NULL COMMENT '所属用户ID(sys_user.id,会话归属校验依据)',
    title      VARCHAR(128) NOT NULL COMMENT '会话标题(首条提问截断)',
    source     VARCHAR(16)  NOT NULL DEFAULT 'CHAT' COMMENT '会话来源:CHAT智能对话/AGENT智能体(四期 agent/),前端列表按来源隔离',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_user_updated (user_id, updated_at)
) COMMENT 'AI会话(多轮对话分组,个人助手口径仅本人可见)';

CREATE TABLE IF NOT EXISTS ai_chat_message (
    id                BIGINT     AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    session_id        BIGINT     NOT NULL COMMENT '会话ID(ai_chat_session.id)',
    role              VARCHAR(16) NOT NULL COMMENT '消息角色:USER用户/AI助手/TOOL工具调用',
    content           MEDIUMTEXT NOT NULL COMMENT '消息内容',
    tool_name         VARCHAR(64) NULL COMMENT '工具名(role=TOOL时记录,审计用)',
    prompt_tokens     INT        NULL COMMENT '输入token用量(模型未回传则NULL)',
    completion_tokens INT        NULL COMMENT '输出token用量(模型未回传则NULL)',
    created_at        DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_session_time (session_id, created_at)
) COMMENT 'AI会话消息(对话落库可审计)';

-- TODO(三期余量): settlement/settlement_detail/ad_report_daily/exchange_rate(docs/03 §6 草案)、
--                  inventory_snapshot_daily(docs/03 §4/§7 草案,随库存预警切片落地)
-- 表结构见 docs/03-数据库设计.md,随对应模块开发时建表
