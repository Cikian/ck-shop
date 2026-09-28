// server/api/products/[slug].get.ts
// [slug] 是占位符，这个文件对应 GET /api/products/任意字符串
// 比如访问 /api/products/classic-sneakers，slug 的值就是 "classic-sneakers"

interface Product {
  id: number
  slug: string
  name: string
  description: string
  price: number
  originalPrice?: number
  image: string
  images: string[]
  rating: number
  reviewCount: number
  isNew?: boolean
  isBestseller?: boolean
  discount?: number
  category: string
  stock: number
  specifications?: Record<string, string>
}

const MOCK_PRODUCTS: Record<string, Record<string, Product>> = {
  en: {
    'classic-canvas-sneakers': {
      id: 1,
      slug: 'classic-canvas-sneakers',
      name: 'Classic Canvas Sneakers',
      description:
        'Comfortable everyday sneakers made from premium durable canvas. Perfect for casual outings and daily wear. Features a cushioned insole for all-day comfort and a rubber outsole for excellent traction.',
      price: 59,
      originalPrice: 79,
      image: 'https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=600&h=600&fit=crop',
      images: [
        'https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=1000&h=1000&fit=crop',
        'https://images.unsplash.com/photo-1606107557195-0e29a4b5b4aa?w=1000&h=1000&fit=crop',
        'https://images.unsplash.com/photo-1595950653106-6c9ebd614d3a?w=1000&h=1000&fit=crop',
      ],
      rating: 4.5,
      reviewCount: 128,
      isNew: true,
      discount: 25,
      category: 'Shoes',
      stock: 42,
      specifications: {
        'Upper Material': 'Premium Canvas',
        'Sole Material': 'Rubber',
        'Closure Type': 'Lace-up',
        'Insole': 'Cushioned EVA',
        'Weight': '280g per shoe',
        'Care Instructions': 'Wipe clean with damp cloth',
      },
    },
    'premium-leather-backpack': {
      id: 2,
      slug: 'premium-leather-backpack',
      name: 'Premium Leather Backpack',
      description:
        'Handcrafted genuine leather backpack for daily commute and travel. Features multiple compartments, padded laptop sleeve, and adjustable shoulder straps. Ages beautifully with a rich patina over time.',
      price: 189,
      originalPrice: 249,
      image: 'https://images.unsplash.com/photo-1553062407-98eeb64c6a62?w=600&h=600&fit=crop',
      images: [
        'https://images.unsplash.com/photo-1553062407-98eeb64c6a62?w=1000&h=1000&fit=crop',
        'https://images.unsplash.com/photo-1548036328-c9fa89d128fa?w=1000&h=1000&fit=crop',
        'https://images.unsplash.com/photo-1581605405669-fcdf81165afa?w=1000&h=1000&fit=crop',
      ],
      rating: 4.8,
      reviewCount: 256,
      isBestseller: true,
      discount: 24,
      category: 'Bags',
      stock: 18,
      specifications: {
        'Material': 'Full Grain Leather',
        'Capacity': '25L',
        'Laptop Sleeve': '15.6"',
        'Weight': '1.2kg',
        'Dimensions': '30 x 45 x 18 cm',
        'Compartments': 'Main + Laptop + Front Pocket',
      },
    },
    'minimalist-wrist-watch': {
      id: 3,
      slug: 'minimalist-wrist-watch',
      name: 'Minimalist Wrist Watch',
      description:
        'Elegant minimalist design with Japanese quartz movement. Features a sapphire crystal glass face and genuine leather strap. Water resistant up to 50 meters.',
      price: 149,
      image: 'https://images.unsplash.com/photo-1523275335684-37898b6baf30?w=600&h=600&fit=crop',
      images: [
        'https://images.unsplash.com/photo-1523275335684-37898b6baf30?w=1000&h=1000&fit=crop',
        'https://images.unsplash.com/photo-1524592094714-0f0654e20314?w=1000&h=1000&fit=crop',
      ],
      rating: 4.7,
      reviewCount: 89,
      isNew: true,
      category: 'Accessories',
      stock: 35,
      specifications: {
        'Movement': 'Japanese Quartz',
        'Case Material': 'Stainless Steel',
        'Crystal': 'Sapphire Glass',
        'Strap': 'Genuine Leather',
        'Water Resistance': '50m (5ATM)',
        'Case Diameter': '40mm',
      },
    },
    'wireless-bluetooth-earbuds': {
      id: 4,
      slug: 'wireless-bluetooth-earbuds',
      name: 'Wireless Bluetooth Earbuds',
      description:
        'Premium sound quality with active noise cancellation. Features Bluetooth 5.3, 30-hour battery life with charging case, and IPX5 water resistance.',
      price: 129,
      originalPrice: 159,
      image: 'https://images.unsplash.com/photo-1590658268037-6bf12165a8df?w=600&h=600&fit=crop',
      images: [
        'https://images.unsplash.com/photo-1590658268037-6bf12165a8df?w=1000&h=1000&fit=crop',
        'https://images.unsplash.com/photo-1583394838336-acd977736f90?w=1000&h=1000&fit=crop',
      ],
      rating: 4.6,
      reviewCount: 342,
      isBestseller: true,
      discount: 19,
      category: 'Electronics',
      stock: 120,
      specifications: {
        'Bluetooth': 'Version 5.3',
        'Battery Life': '8h (earbuds) + 22h (case)',
        'ANC': 'Active Noise Cancellation',
        'Water Resistance': 'IPX5',
        'Drivers': '10mm Dynamic',
        'Codec': 'AAC / SBC / aptX',
      },
    },
    'ceramic-pour-over-coffee': {
      id: 5,
      slug: 'ceramic-pour-over-coffee',
      name: 'Ceramic Pour Over Coffee Set',
      description:
        'Hand-thrown ceramic dripper for the perfect cup of coffee. Includes dripper, ceramic server, and 100 paper filters. Microwave and dishwasher safe.',
      price: 45,
      image: 'https://images.unsplash.com/photo-1495474472287-4d71bcdd2085?w=600&h=600&fit=crop',
      images: [
        'https://images.unsplash.com/photo-1495474472287-4d71bcdd2085?w=1000&h=1000&fit=crop',
        'https://images.unsplash.com/photo-1461023058943-07fcbe16d735?w=1000&h=1000&fit=crop',
      ],
      rating: 4.9,
      reviewCount: 67,
      category: 'Home',
      stock: 88,
      specifications: {
        'Material': 'Stoneware Ceramic',
        'Capacity': '600ml Server',
        'Filter Size': 'V60 (01 size)',
        'Dishwasher Safe': 'Yes',
        'Microwave Safe': 'Yes',
        'Includes': 'Dripper + Server + 100 Filters',
      },
    },
    'sustainable-bamboo-sunglasses': {
      id: 6,
      slug: 'sustainable-bamboo-sunglasses',
      name: 'Sustainable Bamboo Sunglasses',
      description:
        'Eco-friendly bamboo frame with polarized UV400 lenses. Lightweight and comfortable, each pair comes with a bamboo case and microfiber cleaning cloth.',
      price: 79,
      originalPrice: 99,
      image: 'https://images.unsplash.com/photo-1572635196237-14b3f281503f?w=600&h=600&fit=crop',
      images: [
        'https://images.unsplash.com/photo-1572635196237-14b3f281503f?w=1000&h=1000&fit=crop',
        'https://images.unsplash.com/photo-1511499767150-a48a237f0083?w=1000&h=1000&fit=crop',
      ],
      rating: 4.4,
      reviewCount: 156,
      discount: 20,
      category: 'Accessories',
      stock: 64,
      specifications: {
        'Frame Material': 'Sustainable Bamboo',
        'Lens': 'Polarized UV400',
        'Lens Color': 'Dark Brown',
        'Weight': '18g',
        'Frame Width': '142mm',
        'Includes': 'Bamboo Case + Cleaning Cloth',
      },
    },
  },
  zh: {
    'classic-canvas-sneakers': {
      id: 1,
      slug: 'classic-canvas-sneakers',
      name: '经典帆布鞋',
      description:
        '采用优质耐磨帆布制作，舒适耐穿的日常休闲鞋。鞋垫柔软减震，适合长时间穿着，橡胶大底提供出色的抓地力。',
      price: 59,
      originalPrice: 79,
      image: 'https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=600&h=600&fit=crop',
      images: [
        'https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=1000&h=1000&fit=crop',
        'https://images.unsplash.com/photo-1606107557195-0e29a4b5b4aa?w=1000&h=1000&fit=crop',
        'https://images.unsplash.com/photo-1595950653106-6c9ebd614d3a?w=1000&h=1000&fit=crop',
      ],
      rating: 4.5,
      reviewCount: 128,
      isNew: true,
      discount: 25,
      category: '鞋履',
      stock: 42,
      specifications: {
        '鞋面材质': '优质帆布',
        '鞋底材质': '橡胶',
        '闭合方式': '系带',
        '鞋垫': 'EVA 缓震',
        '重量': '单只 280g',
        '保养说明': '湿布擦拭清洁',
      },
    },
    'premium-leather-backpack': {
      id: 2,
      slug: 'premium-leather-backpack',
      name: '真皮双肩包',
      description:
        '手工制作的头层牛皮背包，适合日常通勤和短途旅行。多隔层设计，带防震电脑仓，可调节肩带。随时间推移会形成独特的包浆。',
      price: 189,
      originalPrice: 249,
      image: 'https://images.unsplash.com/photo-1553062407-98eeb64c6a62?w=600&h=600&fit=crop',
      images: [
        'https://images.unsplash.com/photo-1553062407-98eeb64c6a62?w=1000&h=1000&fit=crop',
        'https://images.unsplash.com/photo-1548036328-c9fa89d128fa?w=1000&h=1000&fit=crop',
        'https://images.unsplash.com/photo-1581605405669-fcdf81165afa?w=1000&h=1000&fit=crop',
      ],
      rating: 4.8,
      reviewCount: 256,
      isBestseller: true,
      discount: 24,
      category: '箱包',
      stock: 18,
      specifications: {
        '材质': '头层牛皮',
        '容量': '25L',
        '电脑仓': '15.6 英寸',
        '重量': '1.2kg',
        '尺寸': '30 x 45 x 18 cm',
        '隔层': '主仓 + 电脑仓 + 前袋',
      },
    },
    'minimalist-wrist-watch': {
      id: 3,
      slug: 'minimalist-wrist-watch',
      name: '极简主义腕表',
      description:
        '优雅极简设计，搭载日本石英机芯。蓝宝石玻璃表镜，真皮表带，50 米生活防水。',
      price: 149,
      image: 'https://images.unsplash.com/photo-1523275335684-37898b6baf30?w=600&h=600&fit=crop',
      images: [
        'https://images.unsplash.com/photo-1523275335684-37898b6baf30?w=1000&h=1000&fit=crop',
        'https://images.unsplash.com/photo-1524592094714-0f0654e20314?w=1000&h=1000&fit=crop',
      ],
      rating: 4.7,
      reviewCount: 89,
      isNew: true,
      category: '配饰',
      stock: 35,
      specifications: {
        '机芯': '日本石英',
        '表壳材质': '不锈钢',
        '表镜': '蓝宝石玻璃',
        '表带': '真皮',
        '防水深度': '50米 (5ATM)',
        '表径': '40mm',
      },
    },
    'wireless-bluetooth-earbuds': {
      id: 4,
      slug: 'wireless-bluetooth-earbuds',
      name: '无线蓝牙耳机',
      description:
        '高品质音效，主动降噪技术。蓝牙 5.3，充电盒总续航 30 小时，IPX5 防水等级。',
      price: 129,
      originalPrice: 159,
      image: 'https://images.unsplash.com/photo-1590658268037-6bf12165a8df?w=600&h=600&fit=crop',
      images: [
        'https://images.unsplash.com/photo-1590658268037-6bf12165a8df?w=1000&h=1000&fit=crop',
        'https://images.unsplash.com/photo-1583394838336-acd977736f90?w=1000&h=1000&fit=crop',
      ],
      rating: 4.6,
      reviewCount: 342,
      isBestseller: true,
      discount: 19,
      category: '数码',
      stock: 120,
      specifications: {
        '蓝牙版本': '5.3',
        '续航时间': '8小时（耳机）+ 22小时（充电盒）',
        '降噪': '主动降噪 ANC',
        '防水等级': 'IPX5',
        '驱动单元': '10mm 动圈',
        '编码格式': 'AAC / SBC / aptX',
      },
    },
    'ceramic-pour-over-coffee': {
      id: 5,
      slug: 'ceramic-pour-over-coffee',
      name: '陶瓷手冲咖啡套装',
      description:
        '手工拉坯陶瓷滤杯，萃取完美一杯咖啡。套装包含滤杯、陶瓷分享壶和 100 张滤纸。可微波、可洗碗机。',
      price: 45,
      image: 'https://images.unsplash.com/photo-1495474472287-4d71bcdd2085?w=600&h=600&fit=crop',
      images: [
        'https://images.unsplash.com/photo-1495474472287-4d71bcdd2085?w=1000&h=1000&fit=crop',
        'https://images.unsplash.com/photo-1461023058943-07fcbe16d735?w=1000&h=1000&fit=crop',
      ],
      rating: 4.9,
      reviewCount: 67,
      category: '家居',
      stock: 88,
      specifications: {
        '材质': '炻器陶瓷',
        '容量': '600ml 分享壶',
        '滤纸规格': 'V60 (01号)',
        '洗碗机可用': '是',
        '微波炉可用': '是',
        '套装包含': '滤杯 + 分享壶 + 100张滤纸',
      },
    },
    'sustainable-bamboo-sunglasses': {
      id: 6,
      slug: 'sustainable-bamboo-sunglasses',
      name: '环保竹制太阳镜',
      description:
        '环保竹制镜框，偏光 UV400 镜片。超轻舒适，每副都附赠竹制镜盒和超细纤维擦镜布。',
      price: 79,
      originalPrice: 99,
      image: 'https://images.unsplash.com/photo-1572635196237-14b3f281503f?w=600&h=600&fit=crop',
      images: [
        'https://images.unsplash.com/photo-1572635196237-14b3f281503f?w=1000&h=1000&fit=crop',
        'https://images.unsplash.com/photo-1511499767150-a48a237f0083?w=1000&h=1000&fit=crop',
      ],
      rating: 4.4,
      reviewCount: 156,
      discount: 20,
      category: '配饰',
      stock: 64,
      specifications: {
        '镜框材质': '环保楠竹',
        '镜片': '偏光 UV400',
        '镜片颜色': '深棕色',
        '重量': '18g',
        '镜框宽度': '142mm',
        '套装包含': '竹制镜盒 + 擦镜布',
      },
    },
  },
}

export default defineEventHandler(async (event) => {
  // getRouterParam 专门用来取 [slug] 这种动态路径参数
  const slug = getRouterParam(event, 'slug') as string
  const query = getQuery(event)
  const lang = (query.lang as string) === 'zh' ? 'zh' : 'en'

  // ============================================
  // 【接入真实 Java 后端时替换成】
  //
  // const product = await $fetch(`http://localhost:8080/api/products/${slug}`, {
  //   query: { lang },
  // })
  // ============================================

  const product = MOCK_PRODUCTS[lang][slug]

  if (!product) {
    // createError 会自动生成正确的 HTTP 状态码返回给前端
    throw createError({ statusCode: 404, statusMessage: 'Product not found' })
  }

  return product
})
