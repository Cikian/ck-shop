<template>
  <a-spin :spinning="loading">
    <div class="sku-expand">
      <a-empty v-if="!loading && !skus.length" description="该商品暂未配置规格组合" :image="simpleImage" />
      <a-table
        v-else
        size="small"
        rowKey="id"
        :pagination="false"
        :columns="skuColumns"
        :data-source="skus"
        :scroll="{ x: 'max-content' }"
        :row-class-name="rowClassName"
      >
        <template #bodyCell="{ column, text, record: sku }">
          <template v-if="column.dataIndex === 'combinationName'">
            <span>{{ sku.combinationName || '-' }}</span>
          </template>
          <template v-else-if="column.dataIndex === 'imageUrl'">
            <img v-if="text" :src="text" class="sku-thumb" alt="SKU配图" loading="lazy" />
            <span v-else class="sku-muted">-</span>
          </template>
          <template v-else-if="column.dataIndex === 'effectivePrice'">
            <span>{{ sku.effectivePrice != null ? `¥${Number(sku.effectivePrice).toFixed(2)}` : '-' }}</span>
            <span v-if="sku.price == null" class="sku-muted">（回退基准价）</span>
          </template>
          <template v-else-if="column.dataIndex === 'stock'">
            <span>{{ sku.stock ?? 0 }}</span>
          </template>
          <template v-else-if="column.dataIndex === 'saleable'">
            <a-tag v-if="sku.saleable === 'N'" color="default">不发售</a-tag>
            <a-tag v-else-if="Number(sku.stock ?? 0) <= 0" color="red">已售罄</a-tag>
            <a-tag v-else color="green">可售</a-tag>
          </template>
          <template v-else>{{ text ?? '-' }}</template>
        </template>
      </a-table>
    </div>
  </a-spin>
</template>

<script lang="ts" setup>
  import { onMounted, ref, watch } from 'vue';
  import { Empty } from 'ant-design-vue';
  import { querySkuList } from '../ShopProduct.api';
  import { useMessage } from '/@/hooks/web/useMessage';

  /**
   * 列表展开行：自加载该商品的 SKU 规格组合，避免在父组件模板里 await 异步数据
   */
  const props = defineProps<{ productId: string }>();

  const simpleImage = Empty.PRESENTED_IMAGE_SIMPLE;
  const { createMessage } = useMessage();

  const skus = ref<any[]>([]);
  const loading = ref<boolean>(true);

  const skuColumns = [
    { title: '规格组合', dataIndex: 'combinationName', key: 'combinationName', width: 280 },
    { title: 'SKU编码', dataIndex: 'skuCode', key: 'skuCode', width: 190 },
    { title: 'SKU配图', dataIndex: 'imageUrl', key: 'imageUrl', width: 90 },
    { title: '售价', dataIndex: 'effectivePrice', key: 'effectivePrice', width: 160 },
    { title: '库存', dataIndex: 'stock', key: 'stock', width: 90 },
    { title: '售卖状态', dataIndex: 'saleable', key: 'saleable', width: 110 },
  ];

  function rowClassName(sku: any) {
    return sku.saleable === 'N' ? 'sku-row-unsaleable' : '';
  }

  async function load() {
    loading.value = true;
    try {
      const res = await querySkuList({ id: props.productId });
      skus.value = Array.isArray(res) ? res : res?.records || [];
    } catch (e) {
      skus.value = [];
      createMessage.warning('SKU 加载失败');
    } finally {
      loading.value = false;
    }
  }

  onMounted(load);

  // 同一实例被复用到另一个商品时重新加载
  watch(
    () => props.productId,
    (val, old) => {
      if (val && val !== old) {
        load();
      }
    }
  );
</script>

<style lang="less" scoped>
  .sku-expand {
    padding: 4px 8px 8px 48px;

    .sku-thumb {
      width: 34px;
      height: 34px;
      object-fit: cover;
      border-radius: 4px;
      border: 1px solid #f0f0f0;
    }

    .sku-muted {
      color: rgba(0, 0, 0, 0.45);
      font-size: 12px;
    }

    :deep(.sku-row-unsaleable) {
      background-color: #fafafa;
      color: rgba(0, 0, 0, 0.45);
    }
  }
</style>
