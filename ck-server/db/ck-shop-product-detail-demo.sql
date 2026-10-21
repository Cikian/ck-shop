-- =============================================================================
-- 商品详情（详情模块 + 规格参数）演示数据
--
-- 用途：给开发库里的两件演示商品铺一套完整详情节，便于在管理端 / 店铺端
--       直接看到 8 种模块、KV 参数、多语言与语言回退的真实效果。
--
-- 适用对象（按 spu_code 定位，不写死商品主键）：
--   DEMO-DIGI-001  极光 X1 5G 智能手机     → 数码部店铺（store code = default）
--   DEMO-OUT-001   三合一冲锋衣           → 户外专卖店（store code = outdoor）
--
-- 幂等：只清理并重建这两个商品的 **zh / en** 详情数据（其它语言如 fr 由运营自行维护，脚本不碰）；
--       不修改商品主表/规格/SKU/图集等任何既有数据。
-- 注意：本脚本是**演示数据**，生产环境不要执行。
-- =============================================================================

SET @p_digi := (SELECT id FROM ck_shop_product WHERE spu_code = 'DEMO-DIGI-001' AND del_flag = 0 LIMIT 1);
SET @p_out  := (SELECT id FROM ck_shop_product WHERE spu_code = 'DEMO-OUT-001'  AND del_flag = 0 LIMIT 1);

DELETE FROM ck_shop_product_detail_block
WHERE product_id IN (@p_digi, @p_out) AND language_code IN ('zh', 'en');
DELETE FROM ck_shop_product_param
WHERE product_id IN (@p_digi, @p_out) AND language_code IN ('zh', 'en');

-- -----------------------------------------------------------------------------
-- 1) 极光 X1 5G 智能手机（zh 7 个模块 + en 3 个模块；zh 8 条参数 + en 5 条）
-- -----------------------------------------------------------------------------
INSERT INTO ck_shop_product_detail_block
  (id, product_id, language_code, block_type, content, sort_order, create_by, create_time, update_by, update_time)
VALUES
('ddemo-x1-zh-01', @p_digi, 'zh', 'TITLE',
 '{"text":"极光 X1 · 产品亮点","sub":"6.7 英寸 120Hz 高刷直屏 · 5000mAh 长续航"}', 0, 'demo', NOW(), 'demo', NOW()),
('ddemo-x1-zh-02', @p_digi, 'zh', 'IMAGE',
 '{"url":"https://images.unsplash.com/photo-1511707171634-5f897ff02aa9?auto=format&fit=crop&w=900&h=900&q=80","alt":"极光 X1 正面"}', 1, 'demo', NOW(), 'demo', NOW()),
('ddemo-x1-zh-03', @p_digi, 'zh', 'IMAGE_GRID',
 '{"columns":2,"images":[{"url":"https://images.unsplash.com/photo-1520923642038-b4259acecbd7?auto=format&fit=crop&w=900&h=900&q=80","alt":"侧面"},{"url":"https://images.unsplash.com/photo-1512499617640-c74ae3a79d37?auto=format&fit=crop&w=900&h=900&q=80","alt":"背面"}]}', 2, 'demo', NOW(), 'demo', NOW()),
('ddemo-x1-zh-04', @p_digi, 'zh', 'IMAGE_TEXT',
 '{"url":"https://images.unsplash.com/photo-1512499617640-c74ae3a79d37?auto=format&fit=crop&w=900&h=900&q=80","title":"一体成型中框","text":"航空级铝合金中框，边缘过渡更顺，长时间握持也不硌手。","layout":"right","alt":"中框细节"}', 3, 'demo', NOW(), 'demo', NOW()),
('ddemo-x1-zh-05', @p_digi, 'zh', 'RICH_TEXT',
 '{"html":"<h3>性能与体验</h3><p>搭载骁龙旗舰平台，配合 120Hz 高刷直屏，日常滑动与游戏都更跟手。</p><ul><li>5000 万像素主摄</li><li>66W 有线快充</li><li>IP68 防尘防水</li></ul><p><a href=''https://www.cikian.cn'' target=''_blank'' rel=''noopener noreferrer''>了解品牌故事</a></p>"}', 4, 'demo', NOW(), 'demo', NOW()),
('ddemo-x1-zh-06', @p_digi, 'zh', 'DIVIDER', '{}', 5, 'demo', NOW(), 'demo', NOW()),
('ddemo-x1-zh-07', @p_digi, 'zh', 'TEXT',
 '{"title":"包装清单","text":"手机 ×1\\n充电器 ×1\\n数据线 ×1\\n保护壳 ×1"}', 6, 'demo', NOW(), 'demo', NOW()),
('ddemo-x1-en-01', @p_digi, 'en', 'TITLE',
 '{"text":"Aurora X1 · Highlights","sub":"6.7-inch 120Hz display · 5000mAh battery"}', 0, 'demo', NOW(), 'demo', NOW()),
('ddemo-x1-en-02', @p_digi, 'en', 'IMAGE',
 '{"url":"https://images.unsplash.com/photo-1511707171634-5f897ff02aa9?auto=format&fit=crop&w=900&h=900&q=80","alt":"Aurora X1 front"}', 1, 'demo', NOW(), 'demo', NOW()),
('ddemo-x1-en-03', @p_digi, 'en', 'TEXT',
 '{"title":"What is in the box","text":"Phone x1\\nCharger x1\\nUSB-C cable x1\\nProtective case x1"}', 2, 'demo', NOW(), 'demo', NOW());

INSERT INTO ck_shop_product_param
  (id, product_id, language_code, param_name, param_value, sort_order, create_by, create_time, update_by, update_time)
VALUES
('ddemo-x1-pz-01', @p_digi, 'zh', '屏幕尺寸', '6.7 英寸', 0, 'demo', NOW(), 'demo', NOW()),
('ddemo-x1-pz-02', @p_digi, 'zh', '屏幕刷新率', '120Hz', 1, 'demo', NOW(), 'demo', NOW()),
('ddemo-x1-pz-03', @p_digi, 'zh', '电池容量', '5000mAh', 2, 'demo', NOW(), 'demo', NOW()),
('ddemo-x1-pz-04', @p_digi, 'zh', '有线快充', '66W', 3, 'demo', NOW(), 'demo', NOW()),
('ddemo-x1-pz-05', @p_digi, 'zh', '处理器', '骁龙旗舰平台', 4, 'demo', NOW(), 'demo', NOW()),
('ddemo-x1-pz-06', @p_digi, 'zh', '主摄', '5000 万像素', 5, 'demo', NOW(), 'demo', NOW()),
('ddemo-x1-pz-07', @p_digi, 'zh', '防护等级', 'IP68', 6, 'demo', NOW(), 'demo', NOW()),
('ddemo-x1-pz-08', @p_digi, 'zh', '整机重量', '199g', 7, 'demo', NOW(), 'demo', NOW()),
('ddemo-x1-pe-01', @p_digi, 'en', 'Screen', '6.7 inch', 0, 'demo', NOW(), 'demo', NOW()),
('ddemo-x1-pe-02', @p_digi, 'en', 'Refresh rate', '120Hz', 1, 'demo', NOW(), 'demo', NOW()),
('ddemo-x1-pe-03', @p_digi, 'en', 'Battery', '5000mAh', 2, 'demo', NOW(), 'demo', NOW()),
('ddemo-x1-pe-04', @p_digi, 'en', 'Charging', '66W wired', 3, 'demo', NOW(), 'demo', NOW()),
('ddemo-x1-pe-05', @p_digi, 'en', 'Weight', '199g', 4, 'demo', NOW(), 'demo', NOW());

-- -----------------------------------------------------------------------------
-- 2) 三合一冲锋衣（zh 7 个模块 + en 3 个模块；zh 8 条参数 + en 5 条）
-- -----------------------------------------------------------------------------
INSERT INTO ck_shop_product_detail_block
  (id, product_id, language_code, block_type, content, sort_order, create_by, create_time, update_by, update_time)
VALUES
('ddemo-jc-zh-01', @p_out, 'zh', 'TITLE',
 '{"text":"三合一冲锋衣 · 全天候户外","sub":"防水 10000mm · 可拆卸抓绒内胆"}', 0, 'demo', NOW(), 'demo', NOW()),
('ddemo-jc-zh-02', @p_out, 'zh', 'IMAGE',
 '{"url":"https://images.unsplash.com/photo-1551028719-00167b16eac5?auto=format&fit=crop&w=900&h=900&q=80","alt":"三合一冲锋衣正面"}', 1, 'demo', NOW(), 'demo', NOW()),
('ddemo-jc-zh-03', @p_out, 'zh', 'IMAGE_GRID',
 '{"columns":2,"images":[{"url":"https://images.unsplash.com/photo-1544923246-77307dd654cb?auto=format&fit=crop&w=900&h=900&q=80","alt":"细节一"},{"url":"https://images.unsplash.com/photo-1591047139829-d91aecb6caea?auto=format&fit=crop&w=900&h=900&q=80","alt":"细节二"}]}', 2, 'demo', NOW(), 'demo', NOW()),
('ddemo-jc-zh-04', @p_out, 'zh', 'IMAGE_TEXT',
 '{"url":"https://images.unsplash.com/photo-1591047139829-d91aecb6caea?auto=format&fit=crop&w=900&h=900&q=80","title":"可拆卸抓绒内胆","text":"内胆与外套独立穿着：深秋单穿外套，严寒时组合上身，一件覆盖三种温度区间。","layout":"right","alt":"抓绒内胆"}', 3, 'demo', NOW(), 'demo', NOW()),
('ddemo-jc-zh-05', @p_out, 'zh', 'RICH_TEXT',
 '{"html":"<h3>防水与透气</h3><p>外层采用三层复合面料，防水指数 10000mm，透气指数 8000g/㎡/24h，雨雪与高强度徒步都能应对。</p><ul><li>全压胶工艺，缝线不渗水</li><li>腋下透气拉链，快速排热</li><li>可调立体帽，兼容头盔</li></ul><p><a href=''https://www.cikian.cn'' target=''_blank'' rel=''noopener noreferrer''>了解品牌故事</a></p>"}', 4, 'demo', NOW(), 'demo', NOW()),
('ddemo-jc-zh-06', @p_out, 'zh', 'DIVIDER', '{}', 5, 'demo', NOW(), 'demo', NOW()),
('ddemo-jc-zh-07', @p_out, 'zh', 'TEXT',
 '{"title":"尺码与保养","text":"身高 170-180cm 建议 M/L；袖口与下摆均可调节。\\n建议 30℃ 以下手洗，禁用柔顺剂，避免破坏防水涂层。"}', 6, 'demo', NOW(), 'demo', NOW()),
('ddemo-jc-en-01', @p_out, 'en', 'TITLE',
 '{"text":"3-in-1 Shell Jacket","sub":"10,000mm waterproof · removable fleece liner"}', 0, 'demo', NOW(), 'demo', NOW()),
('ddemo-jc-en-02', @p_out, 'en', 'IMAGE',
 '{"url":"https://images.unsplash.com/photo-1551028719-00167b16eac5?auto=format&fit=crop&w=900&h=900&q=80","alt":"3-in-1 jacket front"}', 1, 'demo', NOW(), 'demo', NOW()),
('ddemo-jc-en-03', @p_out, 'en', 'TEXT',
 '{"title":"Sizing & care","text":"Recommended M/L for 170-180cm.\\nHand wash below 30°C; never use fabric softener."}', 2, 'demo', NOW(), 'demo', NOW());

INSERT INTO ck_shop_product_param
  (id, product_id, language_code, param_name, param_value, sort_order, create_by, create_time, update_by, update_time)
VALUES
('ddemo-jc-pz-01', @p_out, 'zh', '面料', '三层复合面料', 0, 'demo', NOW(), 'demo', NOW()),
('ddemo-jc-pz-02', @p_out, 'zh', '防水指数', '10000mm', 1, 'demo', NOW(), 'demo', NOW()),
('ddemo-jc-pz-03', @p_out, 'zh', '透气指数', '8000g/㎡/24h', 2, 'demo', NOW(), 'demo', NOW()),
('ddemo-jc-pz-04', @p_out, 'zh', '内胆', '可拆卸抓绒', 3, 'demo', NOW(), 'demo', NOW()),
('ddemo-jc-pz-05', @p_out, 'zh', '帽子', '可调立体帽', 4, 'demo', NOW(), 'demo', NOW()),
('ddemo-jc-pz-06', @p_out, 'zh', '口袋', '5 个', 5, 'demo', NOW(), 'demo', NOW()),
('ddemo-jc-pz-07', @p_out, 'zh', '适用季节', '秋冬', 6, 'demo', NOW(), 'demo', NOW()),
('ddemo-jc-pz-08', @p_out, 'zh', '重量', '780g', 7, 'demo', NOW(), 'demo', NOW()),
('ddemo-jc-pe-01', @p_out, 'en', 'Fabric', '3-layer composite', 0, 'demo', NOW(), 'demo', NOW()),
('ddemo-jc-pe-02', @p_out, 'en', 'Waterproof', '10,000mm', 1, 'demo', NOW(), 'demo', NOW()),
('ddemo-jc-pe-03', @p_out, 'en', 'Breathability', '8000g/m²/24h', 2, 'demo', NOW(), 'demo', NOW()),
('ddemo-jc-pe-04', @p_out, 'en', 'Liner', 'Removable fleece', 3, 'demo', NOW(), 'demo', NOW()),
('ddemo-jc-pe-05', @p_out, 'en', 'Weight', '780g', 4, 'demo', NOW(), 'demo', NOW());

-- -----------------------------------------------------------------------------
-- 3) 演示用的模块间距（代码默认是 0；这里给文字类模块补可读留白）
--    图片 / 宫格保持 0 —— 方便直观看到「默认无留白」的效果
-- -----------------------------------------------------------------------------
UPDATE ck_shop_product_detail_block SET margin_top = 32, margin_bottom = 12
WHERE product_id IN (@p_digi, @p_out) AND language_code IN ('zh', 'en') AND block_type = 'TITLE';
UPDATE ck_shop_product_detail_block SET margin_top = 24, margin_bottom = 24
WHERE product_id IN (@p_digi, @p_out) AND language_code IN ('zh', 'en') AND block_type = 'IMAGE_TEXT';
UPDATE ck_shop_product_detail_block SET margin_top = 24, margin_bottom = 8
WHERE product_id IN (@p_digi, @p_out) AND language_code IN ('zh', 'en') AND block_type = 'RICH_TEXT';
UPDATE ck_shop_product_detail_block SET margin_top = 32, margin_bottom = 32
WHERE product_id IN (@p_digi, @p_out) AND language_code IN ('zh', 'en') AND block_type = 'DIVIDER';
UPDATE ck_shop_product_detail_block SET margin_top = 16, margin_bottom = 32
WHERE product_id IN (@p_digi, @p_out) AND language_code IN ('zh', 'en') AND block_type = 'TEXT';

-- -----------------------------------------------------------------------------
-- 校验
-- -----------------------------------------------------------------------------
SELECT p.spu_code, b.language_code, COUNT(*) AS blocks
FROM ck_shop_product_detail_block b JOIN ck_shop_product p ON p.id = b.product_id
WHERE p.spu_code IN ('DEMO-DIGI-001', 'DEMO-OUT-001') AND b.product_id IS NOT NULL
GROUP BY p.spu_code, b.language_code;

SELECT p.spu_code, COUNT(*) AS invalid_json_blocks
FROM ck_shop_product_detail_block b JOIN ck_shop_product p ON p.id = b.product_id
WHERE p.spu_code IN ('DEMO-DIGI-001', 'DEMO-OUT-001') AND JSON_VALID(b.content) = 0
GROUP BY p.spu_code;
