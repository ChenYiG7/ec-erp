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
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, perm_key, path, component, icon, sort) VALUES
(1, 0, '系统管理', 1, NULL, '/system', NULL, 'setting', 1),
(2, 1, '用户管理', 2, 'system:user:list', '/system/users', 'system/user/index', 'user', 1),
(3, 1, '角色管理', 2, 'system:role:list', '/system/roles', 'system/role/index', 'peoples', 2),
(4, 1, '菜单管理', 2, 'system:menu:list', '/system/menus', 'system/menu/index', 'tree', 3),
(5, 1, '店铺管理', 2, 'shop:list', '/shop', 'shop/index', 'shopping', 4),
(6, 0, '商品中心', 1, NULL, '/goods', NULL, 'list', 2),
(7, 6, '商品管理', 2, 'goods:list', '/goods/list', 'goods/index', 'component', 1),
(8, 0, '订单中心', 1, NULL, '/order', NULL, 'order', 3),
(9, 8, '订单管理', 2, 'order:list', '/order/list', 'order/index', 'documentation', 1);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES
(1,1),(1,2),(1,3),(1,4),(1,5),(1,6),(1,7),(1,8),(1,9);

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

-- 站内通知(TODO#14,2026-09-04:系统写入表,告警由任务扇出写入,对外只读查询 + 用户已读状态接口)
CREATE TABLE IF NOT EXISTS sys_notification (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    user_id     BIGINT       NOT NULL COMMENT '接收用户ID(sys_user.id),写侧扇出到全部启用用户',
    title       VARCHAR(128) NOT NULL COMMENT '通知标题',
    content     VARCHAR(1024) NULL COMMENT '通知内容(写侧截断)',
    notify_type VARCHAR(32)  NOT NULL COMMENT '通知类型:PULL_FAIL=拉单连续失败告警',
    biz_type    VARCHAR(32)  NULL COMMENT '关联业务类型:SHOP等',
    biz_id      BIGINT       NULL COMMENT '关联业务ID(如店铺ID)',
    read_status TINYINT      NOT NULL DEFAULT 0 COMMENT '已读状态:0未读 1已读',
    read_at     DATETIME     NULL COMMENT '已读时间',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_user_read (user_id, read_status),
    KEY idx_created (created_at)
) COMMENT '站内通知(系统告警扇出+用户已读状态)';

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
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
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
    qty_locked   INT    NOT NULL DEFAULT 0 COMMENT '占用(已分配未发货)',
    qty_transit  INT    NOT NULL DEFAULT 0 COMMENT '在途(采购未入库)',
    qty_available INT  NOT NULL DEFAULT 0 COMMENT '可用=在库-占用,由InventoryService同事务维护,禁止旁路update',
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_sku_wh (sku_id, warehouse_id)
) COMMENT '库存(只能经 inventory_flow 变动)';

CREATE TABLE IF NOT EXISTS inventory_flow (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    sku_id       BIGINT      NOT NULL COMMENT 'SKU ID(product_sku.id)',
    warehouse_id BIGINT      NOT NULL COMMENT '仓库ID(warehouse.id)',
    flow_type    VARCHAR(32) NOT NULL COMMENT 'IN_PURCHASE/OUT_SHIP/IN_RETURN/ADJUST/TRANSFER_OUT/TRANSFER_IN',
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

-- TODO(三期): settlement/settlement_detail/ad_report_daily/exchange_rate、AI 辅助表(ai_suggestion/ai_chat_session/ai_chat_message)、
--              inventory_snapshot_daily(见 docs/03 §6/§7 草案,字段随功能细化后再落地)
-- 表结构见 docs/03-数据库设计.md,随对应模块开发时建表
