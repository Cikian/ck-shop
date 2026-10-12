/**
 * 多店铺演示数据播种脚本（幂等，可重复执行）
 *
 * 给每个启用店铺分别灌入：分类树（2~3 级）、标签（全局共享）、商品（含多语言文案、
 * 图集、规格与 SKU 组合）、轮播图。图片全部使用**公开可访问**的真实图片 URL
 * （Unsplash CDN，已在 .probe/check-image-urls.mjs 中逐个核验过 200 + image/*）。
 *
 * 用法：
 *   $env:CK_TOKEN='<admin JWT>'
 *   $env:CK_BASE='http://127.0.0.1:9952/api'      # 默认 http://127.0.0.1:9951/api
 *   node .probe/seed-demo-data.mjs
 *   node .probe/seed-demo-data.mjs --dry-run      # 只看计划，不写库
 *
 * 幂等策略：分类按「店铺+名称」、商品按 SPU、标签按 code、轮播图按 desc 判重，已存在则跳过。
 */
const BASE = process.env.CK_BASE || 'http://127.0.0.1:9951/api';
const TOKEN = process.env.CK_TOKEN || '';
const DRY = process.argv.includes('--dry-run');

const img = (id, w = 900, h = 900) =>
  `https://images.unsplash.com/photo-${id}?auto=format&fit=crop&w=${w}&h=${h}&q=80`;

/** 轮播横幅：把方图地址改写成宽幅裁剪参数 */
const banner = (url) => url.replace(/w=\d+&h=\d+/, 'w=1600&h=600');

/**
 * 已核验可用的图片（.probe/check-image-urls.mjs 全绿）。
 * 这里直接存**完整 URL**：早先版本存的是纯 photo-id，结果商品图集/分类封面落库成了裸 id，
 * 前台自然显示不出图——播种脚本自己也要对"用真实图片 URL"负责。
 */
const RAW_IDS = {
  phone: ['1511707171634-5f897ff02aa9', '1592750475338-74b7b21085ab', '1580910051074-3eb694886505', '1520923642038-b4259acecbd7', '1512499617640-c74ae3a79d37', '1567581935884-3349723552ca'],
  headphone: ['1505740420928-5e560c06d30e', '1583394838336-acd977736f90', '1546435770-a3e426bf472b', '1484704849700-f032a568e944'],
  laptop: ['1517336714731-489689fd1ca8', '1496181133206-80ce9b88a853', '1541807084-5c52b6b3adef', '1587614382346-4ec70e388b28'],
  camera: ['1526170375885-4d8ecf77b99f', '1502920917128-1aa500764cbd', '1495707902641-75cac588d2e9'],
  watch: ['1523275335684-37898b6baf30', '1546868871-7041f2a55e12', '1524592094714-0f0654e20314'],
  speaker: ['1608043152269-423dbba4e7e1', '1545454675-3531b543be5d'],
  keyboard: ['1587829741301-dc798b83add3', '1527864550417-7fd91fc51a46'],
  tablet: ['1544244015-0df4b3ffc6b0', '1561154464-82e9adf32764'],
  tent: ['1504280390367-361c6d9f38f4', '1478131143081-80f7f84ca84d', '1537565266759-34bbc16be345', '1445308394109-4ec2920981b1'],
  backpack: ['1553062407-98eeb64c6a62', '1622260614153-03223fb72052'],
  shoes: ['1542291026-7eec264c27ff', '1600185365483-26d7a4cc7519', '1552346154-21d32810aba3', '1460353581641-37baddab0fa2'],
  jacket: ['1551028719-00167b16eac5', '1544923246-77307dd654cb', '1591047139829-d91aecb6caea'],
  gear: ['1510312305653-8ed496efae75', '1508873881324-c92a3fc536ba', '1602143407151-7111542de6e8'],
  bottle: ['1523362628745-0c100150b504'],
  hiking: ['1533240332313-0db49b459ad6', '1551632811-561732d1e306', '1454391304352-2bf4678b1a7a'],
};
const P = Object.fromEntries(Object.entries(RAW_IDS).map(([kind, ids]) => [kind, ids.map((id) => img(id))]));

const LANGS = ['zh', 'en', 'fr'];

/** 全局标签（标签表没有归属列，按设计是全局共享的） */
const TAGS = [
  { code: 'NEW', zh: '新品上市', en: 'New Arrival', fr: 'Nouveautés' },
  { code: 'HOT', zh: '热销爆款', en: 'Best Seller', fr: 'Meilleures ventes' },
  { code: 'SALE', zh: '限时特惠', en: 'On Sale', fr: 'En promotion' },
  { code: 'PICK', zh: '店主推荐', en: "Editor's Pick", fr: 'Sélection du shop' },
  { code: 'FAST', zh: '现货速发', en: 'In Stock', fr: 'En stock' },
  { code: 'TOP', zh: '品质优选', en: 'Premium Quality', fr: 'Qualité premium' },
];

const STORES = [
  {
    code: 'default',
    orgCode: 'A06A01',
    label: '数码部',
    categories: [
      {
        name: '智能手机', en: 'Smartphones', fr: 'Téléphones', img: P.phone[0],
        children: [
          { name: '旗舰手机', en: 'Flagship Phones', fr: 'Téléphones haut de gamme', img: P.phone[1] },
          { name: '手机配件', en: 'Phone Accessories', fr: 'Accessoires téléphone', img: P.phone[2] },
        ],
      },
      {
        name: '电脑办公', en: 'Computers & Office', fr: 'Ordinateurs & Bureau', img: P.laptop[0],
        children: [
          { name: '笔记本电脑', en: 'Laptops', fr: 'Ordinateurs portables', img: P.laptop[1] },
          { name: '键鼠外设', en: 'Keyboard & Mouse', fr: 'Claviers & souris', img: P.keyboard[0] },
        ],
      },
      {
        name: '影音数码', en: 'Audio & Imaging', fr: 'Audio & Image', img: P.headphone[0],
        children: [
          { name: '耳机音箱', en: 'Headphones & Speakers', fr: 'Casques & enceintes', img: P.headphone[1] },
          { name: '智能穿戴', en: 'Smart Wearables', fr: 'Objets connectés', img: P.watch[0] },
          { name: '摄影摄像', en: 'Cameras', fr: 'Appareils photo', img: P.camera[0] },
        ],
      },
    ],
    products: [
      { spu: 'DEMO-DIGI-001', cat: '旗舰手机', price: 4299, unit: '台', rec: 'Y', tags: ['NEW', 'HOT'], imgs: [P.phone[0], P.phone[3], P.phone[4]],
        zh: ['极光 X1 5G 智能手机', '6.7 英寸 120Hz 高刷屏 · 5000mAh 长续航', '<p>极光 X1 搭载 6.7 英寸 120Hz 高刷直屏与 5000mAh 大电池，支持 66W 快充。</p><ul><li>骁龙旗舰平台</li><li>5000 万像素主摄</li><li>IP68 防尘防水</li></ul>'],
        en: ['Aurora X1 5G Smartphone', '6.7" 120Hz display · 5000mAh battery', '<p>Aurora X1 features a 6.7-inch 120Hz display, a 5000mAh battery and 66W fast charging.</p>'],
        fr: ['Smartphone Aurora X1 5G', 'Écran 6,7" 120Hz · batterie 5000mAh', "<p>L'Aurora X1 dispose d'un écran 6,7 pouces 120 Hz et d'une batterie de 5000 mAh.</p>"],
        specs: [{ name: '颜色', values: ['星夜黑', '晨光银'] }, { name: '存储', values: ['8GB+128GB', '12GB+256GB'] }], base: 4299 },
      { spu: 'DEMO-DIGI-002', cat: '旗舰手机', price: 1999, unit: '台', rec: 'N', tags: ['HOT'], imgs: [P.phone[1], P.phone[5]],
        zh: ['极光 Note 5G 手机', '大电池长续航 · 千元档优选', '<p>6000mAh 超大电池，支持 33W 快充。</p>'],
        en: ['Aurora Note 5G', 'Big battery, great value', '<p>6000mAh battery with 33W fast charging.</p>'],
        fr: ['Aurora Note 5G', 'Grande autonomie, petit prix', '<p>Batterie 6000 mAh, charge rapide 33 W.</p>'],
        specs: [{ name: '颜色', values: ['幻夜黑', '海盐蓝'] }], base: 1999 },
      { spu: 'DEMO-DIGI-003', cat: '手机配件', price: 129, unit: '个', rec: 'N', tags: ['FAST'], imgs: [P.phone[2]],
        zh: ['65W 氮化镓快充充电器', '单口 65W · 兼容手机与笔记本', '<p>氮化镓材质，体积更小，支持 PD/QC 多协议。</p>'],
        en: ['65W GaN Fast Charger', '65W single port for phone & laptop', '<p>GaN technology, PD/QC multi-protocol support.</p>'],
        fr: ['Chargeur rapide GaN 65W', '65W pour téléphone et ordinateur', '<p>Technologie GaN, compatible PD/QC.</p>'],
        specs: [{ name: '接口', values: ['Type-C 单口', 'Type-C + USB-A'] }], base: 129 },
      { spu: 'DEMO-DIGI-004', cat: '手机配件', price: 199, unit: '个', rec: 'N', tags: ['NEW'], imgs: [P.tablet[0]],
        zh: ['磁吸无线充电宝 10000mAh', '磁吸即贴即充 · 可上飞机', '<p>10000mAh 容量，支持 15W 磁吸无线快充。</p>'],
        en: ['Magnetic Wireless Power Bank 10000mAh', 'Snap and charge, flight safe', '<p>10000mAh with 15W magnetic wireless charging.</p>'],
        fr: ['Batterie externe magnétique 10000mAh', 'Fixation magnétique, 15W', '<p>10000 mAh, charge sans fil magnétique 15 W.</p>'],
        specs: [{ name: '颜色', values: ['皓月白', '曜石黑'] }], base: 199 },
      { spu: 'DEMO-DIGI-005', cat: '笔记本电脑', price: 5499, unit: '台', rec: 'Y', tags: ['PICK', 'NEW'], imgs: [P.laptop[0], P.laptop[2], P.laptop[3]],
        zh: ['轻薄商务笔记本 Air 14', '1.29kg 轻至 · 2.8K 高色域屏', '<p>14 英寸 2.8K 90Hz 屏，整机 1.29kg，续航 12 小时。</p>'],
        en: ['Air 14 Ultrabook', 'From 1.29kg · 2.8K display', '<p>14-inch 2.8K 90Hz display, 1.29kg, 12-hour battery.</p>'],
        fr: ['Ultraportable Air 14', 'Dès 1,29 kg · écran 2,8K', '<p>Écran 14 pouces 2,8K 90 Hz, 1,29 kg.</p>'],
        specs: [{ name: '内存/硬盘', values: ['16GB+512GB', '32GB+1TB'] }, { name: '颜色', values: ['深空灰', '星光银'] }], base: 5499 },
      { spu: 'DEMO-DIGI-006', cat: '笔记本电脑', price: 8999, unit: '台', rec: 'N', tags: ['TOP'], imgs: [P.laptop[1], P.laptop[2]],
        zh: ['高性能创作本 Pro 16', '独显直通 · 4K 触控屏', '<p>16 英寸 4K 触控屏，独立显卡，面向设计与视频剪辑。</p>'],
        en: ['Creator Pro 16', 'Discrete GPU · 4K touch display', '<p>16-inch 4K touchscreen with discrete graphics.</p>'],
        fr: ['Créateur Pro 16', 'GPU dédié · écran 4K tactile', '<p>Écran tactile 4K 16 pouces, carte graphique dédiée.</p>'],
        specs: [{ name: '配置', values: ['i7/16GB/1TB', 'i9/32GB/2TB'] }], base: 8999 },
      { spu: 'DEMO-DIGI-007', cat: '键鼠外设', price: 399, unit: '个', rec: 'N', tags: ['HOT'], imgs: [P.keyboard[0], P.keyboard[1]],
        zh: ['无线机械键盘 87 键', '三模连接 · 热插拔轴体', '<p>支持蓝牙/2.4G/有线三模，Gasket 结构，热插拔轴座。</p>'],
        en: ['Wireless Mechanical Keyboard 87-key', 'Tri-mode · hot-swappable', '<p>Bluetooth / 2.4G / wired, gasket mount, hot-swappable sockets.</p>'],
        fr: ['Clavier mécanique sans fil 87 touches', 'Tri-mode · hot-swap', '<p>Bluetooth / 2,4G / filaire, montage gasket.</p>'],
        specs: [{ name: '轴体', values: ['线性红轴', '段落茶轴'] }], base: 399 },
      { spu: 'DEMO-DIGI-008', cat: '键鼠外设', price: 199, unit: '个', rec: 'N', tags: ['FAST'], imgs: [P.keyboard[1]],
        zh: ['人体工学无线鼠标', '静音微动 · 4000DPI', '<p>垂直人体工学设计，静音按键，2.4G 无线连接。</p>'],
        en: ['Ergonomic Wireless Mouse', 'Silent switches · 4000DPI', '<p>Vertical ergonomic design with silent switches.</p>'],
        fr: ['Souris ergonomique sans fil', 'Silencieuse · 4000 DPI', '<p>Design ergonomique vertical, clics silencieux.</p>'],
        specs: [{ name: '颜色', values: ['哑光黑', '珍珠白'] }], base: 199 },
      { spu: 'DEMO-DIGI-009', cat: '耳机音箱', price: 899, unit: '副', rec: 'Y', tags: ['HOT', 'PICK'], imgs: [P.headphone[0], P.headphone[2], P.headphone[3]],
        zh: ['主动降噪头戴式耳机', '40dB 降噪 · 60 小时续航', '<p>混合主动降噪，40mm 动圈单元，佩戴舒适。</p>'],
        en: ['Active Noise Cancelling Headphones', '40dB ANC · 60h battery', '<p>Hybrid ANC with 40mm drivers.</p>'],
        fr: ['Casque à réduction de bruit active', '40 dB · 60 h d’autonomie', '<p>Réduction de bruit hybride, transducteurs 40 mm.</p>'],
        specs: [{ name: '颜色', values: ['曜石黑', '云雾白', '雾霾蓝'] }], base: 899 },
      { spu: 'DEMO-DIGI-010', cat: '耳机音箱', price: 499, unit: '个', rec: 'N', tags: ['NEW'], imgs: [P.speaker[0], P.speaker[1]],
        zh: ['便携蓝牙音箱', 'IPX7 防水 · 20 小时播放', '<p>双单元立体声，支持 TWS 串联与 IPX7 防水。</p>'],
        en: ['Portable Bluetooth Speaker', 'IPX7 waterproof · 20h playtime', '<p>Dual drivers, TWS pairing, IPX7 waterproof.</p>'],
        fr: ['Enceinte Bluetooth portable', 'Étanche IPX7 · 20 h', '<p>Double haut-parleur, appairage TWS, IPX7.</p>'],
        specs: [{ name: '颜色', values: ['炭灰', '军绿', '珊瑚橙'] }], base: 499 },
      { spu: 'DEMO-DIGI-011', cat: '智能穿戴', price: 1299, unit: '块', rec: 'N', tags: ['NEW', 'TOP'], imgs: [P.watch[0], P.watch[1], P.watch[2]],
        zh: ['智能运动手表 S2', '血氧心率 · 14 天续航', '<p>1.43 英寸 AMOLED 屏，100+ 运动模式，5ATM 防水。</p>'],
        en: ['Smart Sports Watch S2', 'SpO2 & HR · 14-day battery', '<p>1.43-inch AMOLED, 100+ sport modes, 5ATM.</p>'],
        fr: ['Montre connectée S2', 'SpO2 & cardio · 14 jours', '<p>Écran AMOLED 1,43 pouce, 100+ modes sportifs, 5ATM.</p>'],
        specs: [{ name: '表带', values: ['硅胶黑', '尼龙绿', '真皮棕'] }], base: 1299 },
      { spu: 'DEMO-DIGI-012', cat: '摄影摄像', price: 6499, unit: '台', rec: 'N', tags: ['TOP'], imgs: [P.camera[0], P.camera[1], P.camera[2]],
        zh: ['微单相机 24MP 套机', '五轴防抖 · 4K60 视频', '<p>2420 万像素 APS-C 传感器，机身五轴防抖，支持 4K60 录制。</p>'],
        en: ['Mirrorless Camera 24MP Kit', '5-axis IBIS · 4K60 video', '<p>24.2MP APS-C sensor, 5-axis IBIS, 4K60 recording.</p>'],
        fr: ['Appareil photo hybride 24 Mpx', 'Stabilisation 5 axes · 4K60', '<p>Capteur APS-C 24,2 Mpx, stabilisation 5 axes.</p>'],
        specs: [{ name: '套装', values: ['单机身', '18-55mm 套机'] }], base: 6499 },
    ],
    slides: [
      { desc: '数码新品季', sort: 1, img: banner(P.laptop[0]), target: 'https://www.cikian.cn',
        zh: ['数码新品季', '笔记本 / 手机 / 耳机 全场上新'],
        en: ['New Tech Season', 'Laptops, phones and audio — new arrivals'],
        fr: ['Nouveautés high-tech', 'Ordinateurs, téléphones et audio'] },
      { desc: '智能穿戴专场', sort: 2, img: banner(P.watch[0]),
        zh: ['智能穿戴专场', '运动手表 低至 8 折'],
        en: ['Wearables Week', 'Smart watches up to 20% off'],
        fr: ['Semaine connectée', "Montres jusqu'à -20%"] },
    ],
  },
  {
    code: 'outdoor',
    orgCode: 'A06A02',
    label: '户外部',
    categories: [
      {
        name: '户外服装', en: 'Outdoor Clothing', fr: 'Vêtements outdoor', img: P.jacket[0],
        children: [
          { name: '冲锋衣', en: 'Hardshell Jackets', fr: 'Vestes imperméables', img: P.jacket[1] },
          { name: '速干衣裤', en: 'Quick-dry Wear', fr: 'Vêtements respirants', img: P.jacket[2] },
        ],
      },
      {
        name: '露营装备', en: 'Camping Gear', fr: 'Équipement de camping', img: P.tent[0],
        children: [
          { name: '帐篷', en: 'Tents', fr: 'Tentes', img: P.tent[1] },
          { name: '睡袋', en: 'Sleeping Bags', fr: 'Sacs de couchage', img: P.tent[2] },
        ],
      },
      {
        name: '背包鞋靴', en: 'Packs & Footwear', fr: 'Sacs & chaussures', img: P.backpack[0],
        children: [
          { name: '登山包', en: 'Hiking Backpacks', fr: 'Sacs à dos', img: P.backpack[1] },
          { name: '徒步鞋', en: 'Hiking Shoes', fr: 'Chaussures de randonnée', img: P.shoes[0] },
        ],
      },
      {
        name: '户外工具', en: 'Outdoor Tools', fr: 'Outils outdoor', img: P.gear[0],
        children: [
          { name: '登山杖', en: 'Trekking Poles', fr: 'Bâtons de randonnée', img: P.hiking[0] },
          { name: '保温水具', en: 'Insulated Bottles', fr: 'Bouteilles isothermes', img: P.bottle[0] },
        ],
      },
    ],
    products: [
      { spu: 'DEMO-OUT-001', cat: '冲锋衣', price: 899, unit: '件', rec: 'Y', tags: ['HOT', 'PICK'], imgs: [P.jacket[0], P.jacket[1], P.jacket[2]],
        zh: ['三合一冲锋衣', '防水 10000mm · 可拆卸抓绒内胆', '<p>三层复合面料，防水透气指数 10000/5000，内胆可拆单穿。</p>'],
        en: ['3-in-1 Hardshell Jacket', '10,000mm waterproof · removable fleece', '<p>Three-layer fabric, 10,000/5,000 waterproof-breathable, removable liner.</p>'],
        fr: ['Veste 3-en-1 imperméable', '10 000 mm · doublure polaire amovible', '<p>Tissu 3 couches, 10 000/5 000, doublure amovible.</p>'],
        specs: [{ name: '尺码', values: ['S', 'M', 'L', 'XL'] }, { name: '颜色', values: ['藏青', '橙红'] }], base: 899 },
      { spu: 'DEMO-OUT-002', cat: '冲锋衣', price: 299, unit: '件', rec: 'N', tags: ['FAST'], imgs: [P.jacket[1], P.jacket[2]],
        zh: ['轻量防晒皮肤衣', 'UPF50+ · 仅 120g', '<p>超轻透气，UPF50+ 防晒，可收纳进口袋。</p>'],
        en: ['Ultralight Sun Jacket', 'UPF50+ · only 120g', '<p>Ultralight and breathable, packs into its own pocket.</p>'],
        fr: ['Veste anti-UV ultralégère', 'UPF50+ · 120 g', '<p>Ultralégère, UPF50+, se range dans sa poche.</p>'],
        specs: [{ name: '尺码', values: ['M', 'L', 'XL'] }], base: 299 },
      { spu: 'DEMO-OUT-003', cat: '速干衣裤', price: 159, unit: '件', rec: 'N', tags: ['FAST'], imgs: [P.jacket[2]],
        zh: ['速干短袖 T 恤', '吸湿排汗 · 抗菌防臭', '<p>聚酯纤维速干面料，30 分钟速干，抗菌防臭处理。</p>'],
        en: ['Quick-dry T-Shirt', 'Moisture wicking · anti-odour', '<p>Quick-dry polyester, dries in 30 minutes.</p>'],
        fr: ['T-shirt respirant', 'Évacuation de l’humidité', '<p>Polyester séchage rapide, traitement anti-odeurs.</p>'],
        specs: [{ name: '尺码', values: ['S', 'M', 'L', 'XL'] }, { name: '颜色', values: ['石墨灰', '军绿'] }], base: 159 },
      { spu: 'DEMO-OUT-004', cat: '速干衣裤', price: 259, unit: '条', rec: 'N', tags: ['NEW'], imgs: [P.hiking[0]],
        zh: ['弹力速干长裤', '四面弹 · 防泼水', '<p>四面弹力面料，表面防泼水，膝部立体剪裁。</p>'],
        en: ['Stretch Quick-dry Pants', '4-way stretch · water repellent', '<p>Four-way stretch fabric with DWR finish.</p>'],
        fr: ['Pantalon respirant extensible', '4 sens · déperlant', '<p>Tissu extensible 4 sens, traitement déperlant.</p>'],
        specs: [{ name: '尺码', values: ['28', '30', '32', '34'] }], base: 259 },
      { spu: 'DEMO-OUT-005', cat: '帐篷', price: 699, unit: '顶', rec: 'Y', tags: ['HOT'], imgs: [P.tent[0], P.tent[1], P.tent[3]],
        zh: ['双人自动速开帐篷', '3 秒速开 · 防暴雨 2000mm', '<p>液压自动杆，3 秒搭建，防水 2000mm，双门双窗通风。</p>'],
        en: ['2-Person Pop-up Tent', '3-second setup · 2000mm', '<p>Hydraulic poles, sets up in 3 seconds, 2000mm waterproof.</p>'],
        fr: ['Tente 2 places pop-up', 'Montage 3 s · 2000 mm', '<p>Arceaux hydrauliques, montage en 3 secondes.</p>'],
        specs: [{ name: '规格', values: ['2 人', '3-4 人'] }, { name: '颜色', values: ['湖蓝', '沙棕'] }], base: 699 },
      { spu: 'DEMO-OUT-006', cat: '帐篷', price: 1299, unit: '顶', rec: 'N', tags: ['TOP'], imgs: [P.tent[1], P.tent[2]],
        zh: ['超轻单兵帐篷 1.4kg', '铝合金杆 · 双层防凝露', '<p>1.4kg 超轻，20D 硅胶面料，双层结构防凝露。</p>'],
        en: ['Ultralight Solo Tent 1.4kg', 'Alloy poles · double wall', '<p>1.4kg, 20D silnylon, double-wall anti-condensation.</p>'],
        fr: ['Tente solo ultralégère 1,4 kg', 'Arceaux alu · double paroi', '<p>1,4 kg, tissu 20D, double paroi.</p>'],
        specs: [{ name: '规格', values: ['1 人', '1.5 人'] }], base: 1299 },
      { spu: 'DEMO-OUT-007', cat: '睡袋', price: 399, unit: '个', rec: 'N', tags: ['NEW'], imgs: [P.tent[3], P.gear[2]],
        zh: ['户外露营睡袋 -5℃', '中空棉 · 可拼接', '<p>适用 -5℃ 环境，中空棉填充，左右可拼接成双人睡袋。</p>'],
        en: ['Camping Sleeping Bag -5°C', 'Hollow fibre · zip-together', '<p>Comfort rating -5°C, hollow fibre fill, zip together two bags.</p>'],
        fr: ['Sac de couchage -5 °C', 'Fibre creuse · assemblable', '<p>Confort -5 °C, fibre creuse, assembable.</p>'],
        specs: [{ name: '规格', values: ['信封式', '木乃伊式'] }], base: 399 },
      { spu: 'DEMO-OUT-008', cat: '登山包', price: 599, unit: '个', rec: 'Y', tags: ['PICK'], imgs: [P.backpack[0], P.backpack[1], P.gear[0]],
        zh: ['60L 大容量登山包', '背负系统 · 防雨罩', '<p>60L 容量，铝合金背负支撑，附防雨罩，适合 3-5 天徒步。</p>'],
        en: ['60L Hiking Backpack', 'Suspension system · rain cover', '<p>60L with aluminium suspension and rain cover.</p>'],
        fr: ['Sac à dos 60 L', 'Suspension · housse de pluie', '<p>60 L, suspension aluminium, housse de pluie incluse.</p>'],
        specs: [{ name: '颜色', values: ['深灰', '丛林绿'] }, { name: '容量', values: ['50L', '60L'] }], base: 599 },
      { spu: 'DEMO-OUT-009', cat: '登山包', price: 329, unit: '个', rec: 'N', tags: ['FAST'], imgs: [P.backpack[1]],
        zh: ['28L 轻量日爬包', '透气背负 · 水袋仓', '<p>28L 日行容量，透气背板，内置水袋仓与雨罩。</p>'],
        en: ['28L Daypack', 'Ventilated back · hydration sleeve', '<p>28L daypack with ventilated back panel and hydration sleeve.</p>'],
        fr: ['Sac daypack 28 L', 'Dos ventilé · poche à eau', '<p>28 L, dos ventilé, poche à eau intégrée.</p>'],
        specs: [{ name: '颜色', values: ['炭黑', '雾蓝', '砖红'] }], base: 329 },
      { spu: 'DEMO-OUT-010', cat: '徒步鞋', price: 799, unit: '双', rec: 'Y', tags: ['HOT', 'TOP'], imgs: [P.shoes[0], P.shoes[1], P.shoes[2]],
        zh: ['中帮防水徒步鞋', 'GTX 防水 · 防滑大底', '<p>防水透气膜，中帮护踝，Vibram 级防滑大底。</p>'],
        en: ['Mid Waterproof Hiking Boots', 'Waterproof membrane · grippy outsole', '<p>Waterproof breathable membrane, mid-cut ankle support.</p>'],
        fr: ['Chaussures de randonnée mid', 'Membrane imperméable · semelle grip', '<p>Membrane imperméable, tige mid, semelle adhérente.</p>'],
        specs: [{ name: '尺码', values: ['39', '40', '41', '42', '43'] }, { name: '颜色', values: ['棕褐', '灰绿'] }], base: 799 },
      { spu: 'DEMO-OUT-011', cat: '徒步鞋', price: 699, unit: '双', rec: 'N', tags: ['NEW'], imgs: [P.shoes[3], P.shoes[1]],
        zh: ['轻量越野跑鞋', '单只 260g · 抓地缓震', '<p>单只 260g，落差 6mm，适合越野跑与轻装徒步。</p>'],
        en: ['Lightweight Trail Runner', '260g · grip & cushioning', '<p>260g per shoe, 6mm drop, built for trails.</p>'],
        fr: ['Chaussure de trail légère', '260 g · adhérence', '<p>260 g par chaussure, drop 6 mm.</p>'],
        specs: [{ name: '尺码', values: ['40', '41', '42', '43', '44'] }], base: 699 },
      { spu: 'DEMO-OUT-012', cat: '登山杖', price: 259, unit: '对', rec: 'N', tags: ['FAST'], imgs: [P.hiking[1], P.hiking[2]],
        zh: ['便携折叠登山杖', '五节折叠 · 外锁快调', '<p>五节折叠设计，收纳仅 36cm，7075 铝合金杖身。</p>'],
        en: ['Folding Trekking Poles', '5-section · flick-lock', '<p>Folds to 36cm, 7075 aluminium shafts.</p>'],
        fr: ['Bâtons de randonnée pliables', '5 sections · verrouillage', '<p>Pliage à 36 cm, aluminium 7075.</p>'],
        specs: [{ name: '规格', values: ['单支', '一对'] }], base: 259 },
      { spu: 'DEMO-OUT-013', cat: '保温水具', price: 189, unit: '个', rec: 'N', tags: ['TOP'], imgs: [P.bottle[0], P.gear[2]],
        zh: ['316 不锈钢保温杯 750ml', '24 小时保温 · 广口易清洗', '<p>316 食品级不锈钢，保温 24 小时，广口设计易清洗。</p>'],
        en: ['316 Stainless Bottle 750ml', '24h insulation · wide mouth', '<p>Food-grade 316 stainless steel, 24-hour insulation.</p>'],
        fr: ['Bouteille inox 316 750 ml', 'Isotherme 24 h', '<p>Inox alimentaire 316, isolation 24 h.</p>'],
        specs: [{ name: '容量', values: ['500ml', '750ml'] }, { name: '颜色', values: ['银灰', '哑光黑'] }], base: 189 },
    ],
    slides: [
      { desc: '户外露营季', sort: 1, img: banner(P.tent[0]), target: 'https://www.cikian.cn',
        zh: ['户外露营季', '帐篷 / 睡袋 满 599 减 60'],
        en: ['Camping Season', 'Tents & sleeping bags on sale'],
        fr: ['Saison camping', 'Tentes et sacs de couchage'] },
      { desc: '徒步鞋靴专场', sort: 2, img: banner(P.shoes[0]),
        zh: ['徒步鞋靴专场', '防水徒步鞋 新品首发'],
        en: ['Footwear Week', 'Waterproof hiking boots — new'],
        fr: ['Semaine chaussures', 'Nouvelles chaussures imperméables'] },
    ],
  },
];

/* ------------------------------- HTTP 封装 ------------------------------- */
async function call(method, path, { query = {}, body } = {}) {
  const qs = new URLSearchParams(query).toString();
  const res = await fetch(`${BASE}${path}${qs ? `?${qs}` : ''}`, {
    method,
    headers: { 'Content-Type': 'application/json', 'X-Access-Token': TOKEN },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  let json;
  try { json = JSON.parse(text); } catch { json = { raw: text.slice(0, 300) }; }
  return { status: res.status, json };
}
const ok = (r) => r.json?.success === true;
const msg = (r) => r.json?.message || JSON.stringify(r.json).slice(0, 200);

const stats = { categories: 0, products: 0, tags: 0, slides: 0, skipped: 0 };

/* ------------------------------- 1. 标签 ------------------------------- */
async function seedTags() {
  console.log('\n=== 标签（全局共享）===');
  const all = await call('GET', '/shop/tag/all');
  const existing = new Map((Array.isArray(all.json?.result) ? all.json.result : []).map((t) => [t.code, t]));
  for (const tag of TAGS) {
    if (existing.has(tag.code)) {
      console.log(`  跳过 ${tag.code}（已存在）`);
      stats.skipped++;
      continue;
    }
    if (DRY) { console.log(`  [dry] 新建标签 ${tag.code}`); continue; }
    const r = await call('POST', '/shop/tag/add', {
      body: {
        code: tag.code,
        isActive: 'Y',
        translations: LANGS.map((l) => ({ languageCode: l, name: tag[l] })),
      },
    });
    console.log(`  ${ok(r) ? 'OK  ' : 'FAIL'} 标签 ${tag.code} ${ok(r) ? '' : msg(r)}`);
    if (ok(r)) stats.tags++;
  }
  const after = await call('GET', '/shop/tag/all');
  return new Map((Array.isArray(after.json?.result) ? after.json.result : []).map((t) => [t.code, t.id]));
}

/* ------------------------------- 2. 分类 ------------------------------- */
async function seedCategories(store) {
  console.log(`\n=== [${store.code}] 分类 ===`);
  const list = await call('GET', '/shop/category/list', { query: { pageNo: 1, pageSize: 500, sysOrgCode: store.orgCode } });
  const records = list.json?.result?.records || [];
  const findId = (name, parentId) =>
    records.find((c) => c.name === name && (c.parentId || '0') === (parentId || '0'))?.id;

  const ids = {};
  for (const top of store.categories) {
    let topId = findId(top.name, '0');
    if (!topId) {
      if (DRY) { console.log(`  [dry] 新建一级分类 ${top.name}`); }
      else {
        const r = await call('POST', '/shop/category/add', {
          body: {
            parentId: '0', sortOrder: store.categories.indexOf(top) + 1, isShow: 'Y',
            name: top.name, iconUrl: top.img, coverUrl: top.img, sysOrgCode: store.orgCode,
            translations: LANGS.map((l) => ({ languageCode: l, name: l === 'zh' ? top.name : top[l], coverUrl: top.img })),
          },
        });
        if (!ok(r)) { console.log(`  FAIL 一级分类 ${top.name} ${msg(r)}`); continue; }
        stats.categories++;
        const again = await call('GET', '/shop/category/list', { query: { pageNo: 1, pageSize: 500, sysOrgCode: store.orgCode } });
        topId = (again.json?.result?.records || []).find((c) => c.name === top.name && (c.parentId || '0') === '0')?.id;
        console.log(`  OK   一级分类 ${top.name} → ${topId}`);
      }
    } else {
      console.log(`  跳过 一级分类 ${top.name}（已存在）`);
      stats.skipped++;
    }
    ids[top.name] = topId;
    for (const child of top.children || []) {
      const existId = findId(child.name, topId);
      if (existId) { console.log(`  跳过 二级分类 ${child.name}（已存在）`); stats.skipped++; ids[child.name] = existId; continue; }
      if (DRY) { console.log(`  [dry] 新建二级分类 ${child.name}`); continue; }
      const r = await call('POST', '/shop/category/add', {
        body: {
          parentId: topId, sortOrder: (top.children.indexOf(child) + 1), isShow: 'Y',
          name: child.name, iconUrl: child.img, coverUrl: child.img, sysOrgCode: store.orgCode,
          translations: LANGS.map((l) => ({ languageCode: l, name: l === 'zh' ? child.name : child[l], coverUrl: child.img })),
        },
      });
      if (!ok(r)) { console.log(`  FAIL 二级分类 ${child.name} ${msg(r)}`); continue; }
      stats.categories++;
      const after = await call('GET', '/shop/category/list', { query: { pageNo: 1, pageSize: 500, sysOrgCode: store.orgCode } });
      ids[child.name] = (after.json?.result?.records || []).find((c) => c.name === child.name && c.parentId === topId)?.id;
      console.log(`  OK   二级分类 ${child.name} → ${ids[child.name]}`);
    }
  }
  return ids;
}

/* ------------------------------- 3. 商品 ------------------------------- */
function buildProduct(store, p, catId, tagIdByCode, order) {
  const images = p.imgs.map((url, i) => ({ url, sortOrder: i + 1, isMain: i === 0 ? 'Y' : 'N' }));
  const specs = (p.specs || []).map((s, si) => ({
    name: s.name,
    sortOrder: si + 1,
    isRequired: 'Y',
    values: s.values.map((v, vi) => ({ value: v, sortOrder: vi + 1, tempId: `t${si}_${vi}` })),
  }));
  // 笛卡尔积生成 SKU 组合
  const combinations = [];
  if (specs.length) {
    let rows = [[]];
    specs.forEach((spec, si) => {
      const next = [];
      rows.forEach((row) => spec.values.forEach((v) => next.push([...row, { si, tempId: v.tempId, label: v.value }])));
      rows = next;
    });
    rows.forEach((row, i) => {
      const price = Number((p.base * (1 + (i % 3) * 0.06)).toFixed(2));
      combinations.push({
        skuCode: `${p.spu}-${String(i + 1).padStart(2, '0')}`,
        combinationKey: row.map((r) => r.tempId).join('-'),
        combinationName: row.map((r) => r.label).join(' / '),
        price,
        stock: 20 + ((i * 7) % 60),
        saleable: 'Y',
        specValueRefs: row.map((r) => r.tempId),
      });
    });
  } else {
    combinations.push({ skuCode: `${p.spu}-01`, combinationKey: p.spu, combinationName: '默认', price: p.base, stock: 50, saleable: 'Y', specValueRefs: [] });
  }
  const main = images[0]?.url;
  return {
    spuCode: p.spu,
    categoryId: catId,
    price: p.base,
    unit: p.unit,
    status: 'Y',
    isRecommend: p.rec || 'N',
    // 显式排序：前台与管理端都按 sort_order 升序，不填就会退化成"数据库返回顺序"，演示数据看着像乱序
    sortOrder: order,
    mainImage: main,
    sysOrgCode: store.orgCode,
    tagIds: (p.tags || []).map((c) => tagIdByCode.get(c)).filter(Boolean),
    images,
    specs,
    combinations,
    translations: LANGS.map((l) => ({
      languageCode: l,
      title: p[l][0],
      subTitle: p[l][1],
      description: p[l][2],
      seoTitle: p[l][0],
      seoKeywords: `${p[l][0]},${store.label}`,
      coverUrl: main,
    })),
  };
}

async function seedProducts(store, catIds, tagIdByCode) {
  console.log(`\n=== [${store.code}] 商品 ===`);
  for (let i = 0; i < store.products.length; i++) {
    const p = store.products[i];
    const list = await call('GET', '/shop/product/list', { query: { pageNo: 1, pageSize: 1, spuCode: p.spu } });
    if ((list.json?.result?.records || []).length > 0) {
      console.log(`  跳过 ${p.spu}（已存在）`);
      stats.skipped++;
      continue;
    }
    const catId = catIds[p.cat];
    if (!catId) { console.log(`  FAIL ${p.spu}：找不到分类「${p.cat}」`); continue; }
    if (DRY) { console.log(`  [dry] 新建商品 ${p.spu}（分类 ${p.cat}，${p.imgs.length} 图，${p.specs?.length || 0} 规格）`); continue; }
    const body = buildProduct(store, p, catId, tagIdByCode, i + 1);
    const r = await call('POST', '/shop/product/add', { body });
    if (!ok(r)) { console.log(`  FAIL ${p.spu} ${msg(r)}`); continue; }
    stats.products++;
    const id = typeof r.json.result === 'string' && r.json.result.length > 8 ? r.json.result : '?';
    console.log(`  OK   ${p.spu}  ${p.zh[0]}  (${body.combinations.length} SKU, ${body.images.length} 图) id=${id}`);
  }
}

/* ------------------------------- 4. 轮播图 ------------------------------- */
async function seedSlides(store) {
  console.log(`\n=== [${store.code}] 轮播图 ===`);
  const existing = await call('GET', '/home/slide/all', { query: { sysOrgCode: store.orgCode } });
  const list = Array.isArray(existing.json?.result) ? existing.json.result : [];
  const descs = new Set(list.map((s) => s.desc));
  // 新轮播图排在**已有轮播图之后**：直接写 1、2 会和原有那张撞号，前台顺序看着像随机
  const baseSort = list.reduce((max, s) => Math.max(max, Number(s.sort) || 0), 0);
  for (let i = 0; i < store.slides.length; i++) {
    const s = store.slides[i];
    if (descs.has(s.desc)) { console.log(`  跳过 ${s.desc}（已存在）`); stats.skipped++; continue; }
    if (DRY) { console.log(`  [dry] 新建轮播图 ${s.desc} (sort=${baseSort + i + 1})`); continue; }
    const body = { desc: s.desc, sort: baseSort + i + 1, enabled: 'Y', goods: '', sysOrgCode: store.orgCode, targetUrl: s.target || '' };
    for (const l of LANGS) body[l] = { [`title_${l}`]: s[l][0], [`pic_url_${l}`]: s.img };
    const r = await call('POST', '/home/slide', { body });
    console.log(`  ${ok(r) ? 'OK  ' : 'FAIL'} 轮播图 ${s.desc} sort=${baseSort + i + 1} ${ok(r) ? `id=${r.json.result}` : msg(r)}`);
    if (ok(r)) { stats.slides++; descs.add(s.desc); }
  }
}

/* ------------------------------- 主流程 ------------------------------- */
async function main() {
  console.log(`BASE = ${BASE}${DRY ? '   [DRY-RUN 不写库]' : ''}`);
  if (!TOKEN && !DRY) { console.error('缺少 CK_TOKEN'); process.exit(2); }
  const ping = await call('GET', '/shop/store/all');
  if (!ok(ping)) { console.error(`后端不可用或 token 失效：${msg(ping)}`); process.exit(2); }

  const tagIdByCode = await seedTags();
  console.log(`  标签 id 映射：${[...tagIdByCode.keys()].join(', ') || '(空)'}`);

  for (const store of STORES) {
    const catIds = await seedCategories(store);
    await seedProducts(store, catIds, tagIdByCode);
    await seedSlides(store);
  }

  console.log(`\n===== 完成：新增 分类 ${stats.categories} / 商品 ${stats.products} / 标签 ${stats.tags} / 轮播图 ${stats.slides}，跳过已存在 ${stats.skipped} =====`);
}

main().catch((e) => { console.error('异常：', e); process.exit(1); });
