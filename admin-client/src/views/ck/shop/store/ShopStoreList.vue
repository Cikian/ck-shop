<template>
  <div class="shop-store">
    <a-card :bordered="false">
      <!-- 查询区 -->
      <div class="shop-store__search">
        <a-input v-model:value="query.code" placeholder="店铺编码" allow-clear style="width: 180px" @press-enter="reload" />
        <a-input v-model:value="query.name" placeholder="店铺名称" allow-clear style="width: 180px" @press-enter="reload" />
        <a-select v-model:value="query.status" placeholder="状态" allow-clear style="width: 120px" :options="statusOptions" />
        <a-button type="primary" preIcon="ant-design:search-outlined" @click="reload">查询</a-button>
        <a-button @click="resetQuery">重置</a-button>
        <span class="shop-store__spacer"></span>
        <a-button type="primary" v-auth="'shop-store:exportXls'" preIcon="ant-design:export-outlined" @click="onExportXls">导出</a-button>
        <j-upload-button type="primary" v-auth="'shop-store:importExcel'" preIcon="ant-design:import-outlined" @click="onImportXls">
          导入
        </j-upload-button>
        <a-button type="primary" v-auth="'shop-store:add'" preIcon="ant-design:plus-outlined" @click="openAdd">新增店铺</a-button>
      </div>

      <a-table
        size="middle"
        rowKey="id"
        bordered
        :columns="columns"
        :data-source="records"
        :loading="loading"
        :pagination="pagination"
        :row-selection="{ selectedRowKeys: selectedKeys, onChange: (keys) => (selectedKeys = keys) }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'code'">
            <a-tag color="blue">{{ record.code }}</a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'domain'">
            <span v-if="record.domain">{{ record.domain }}</span>
            <span v-else class="shop-store__muted">未配置（后端将依赖 X-Store-Code）</span>
          </template>
          <template v-else-if="column.dataIndex === 'departId'">
            <span v-if="departNameMap[record.departId]">{{ departNameMap[record.departId] }}</span>
            <a-tooltip v-else-if="record.departId" title="该部门可能已被删除，请重新绑定或解绑">
              <span class="shop-store__warn">未知部门（{{ record.departId }}）</span>
            </a-tooltip>
            <span v-else class="shop-store__muted">未绑定</span>
          </template>
          <template v-else-if="column.dataIndex === 'status'">
            <a-badge :status="record.status === 'Y' ? 'success' : 'default'" :text="record.status === 'Y' ? '启用' : '停用'" />
          </template>
          <template v-else-if="column.dataIndex === 'action'">
            <a-space>
              <a v-auth="'shop-store:edit'" @click="openEdit(record)">编辑</a>
              <!-- v-auth 放在 popconfirm 上：没有删除权限时整个控件（含空的确认框）都不渲染 -->
              <a-popconfirm v-auth="'shop-store:delete'" title="确认删除该店铺？" ok-text="删除" cancel-text="取消" @confirm="removeOne(record)">
                <a class="shop-store__danger">删除</a>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <!-- 新增 / 编辑 -->
    <a-modal v-model:open="modalOpen" :title="form.id ? '编辑店铺' : '新增店铺'" :confirm-loading="saving" @ok="submit">
      <a-form ref="formRef" :model="form" :rules="rules" :label-col="{ span: 5 }" :wrapper-col="{ span: 18 }">
        <a-form-item label="店铺编码" name="code">
          <a-input v-model:value="form.code" placeholder="如 digital / food / outdoor，只允许字母数字下划线中划线" :disabled="!!form.id" />
          <span class="shop-store__hint">该编码要写进前台站点的 STORE_CODE 配置，创建后不可修改</span>
        </a-form-item>
        <a-form-item label="店铺名称" name="name">
          <a-input v-model:value="form.name" placeholder="如 数码专卖店" />
        </a-form-item>
        <a-form-item label="绑定部门" name="departId">
          <!--
            清除（allow-clear / 清空选择）会把 departId 置为 ''：这是"显式解绑"，
            与"不传该字段 = 不改动"是两种语义，后端据此判断是否解绑。
            '' 用 :value 归一成 undefined，避免下拉框把空串当已选值显示。
          -->
          <a-select
            :value="form.departId || undefined"
            placeholder="选择店铺归属的部门，可留空稍后再绑"
            allow-clear
            show-search
            option-filter-prop="label"
            :options="departOptions"
            @change="handleDepartChange"
          />
          <span class="shop-store__hint">店铺与部门 1:1：商品、分类的归属都按部门隔离</span>
          <span class="shop-store__hint">解绑（清除）前需先把店铺状态改为「停用」，启用中的店铺必须绑定部门</span>
        </a-form-item>
        <a-form-item label="前台域名" name="domain">
          <a-input v-model:value="form.domain" placeholder="如 digital.example.com，可留空" />
          <span class="shop-store__hint">填了之后，未携带 X-Store-Code 的请求可按域名自动归属到本店</span>
        </a-form-item>
        <a-form-item label="状态" name="status">
          <a-radio-group v-model:value="form.status">
            <a-radio value="Y">启用</a-radio>
            <a-radio value="N">停用</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item label="排序" name="sortOrder">
          <a-input-number v-model:value="form.sortOrder" :min="0" :precision="0" style="width: 100%" />
        </a-form-item>
        <a-form-item label="简介" name="description">
          <a-textarea v-model:value="form.description" :rows="2" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script lang="ts" setup>
  import { computed, onMounted, reactive, ref } from 'vue';
  import { useMessage } from '/@/hooks/web/useMessage';
  import { useMethods } from '/@/hooks/system/useMethods';
  import { queryTreeList } from '/@/api/common/api';
  import { add, deleteOne, edit, getExportUrl, getImportUrl, list, queryById } from './ShopStore.api';

  const { createMessage } = useMessage();
  const { handleExportXls, handleImportXls } = useMethods();

  const loading = ref(false);
  const saving = ref(false);
  const records = ref<any[]>([]);
  const selectedKeys = ref<string[]>([]);
  const modalOpen = ref(false);
  const formRef = ref();

  const query = reactive<Record<string, any>>({ code: '', name: '', status: undefined });
  const pageNo = ref(1);
  const pageSize = ref(10);
  const total = ref(0);

  const statusOptions = [
    { label: '启用', value: 'Y' },
    { label: '停用', value: 'N' },
  ];

  const columns = [
    { title: '店铺编码', dataIndex: 'code', width: 150 },
    { title: '店铺名称', dataIndex: 'name', width: 180 },
    { title: '绑定部门', dataIndex: 'departId', width: 200 },
    { title: '前台域名', dataIndex: 'domain', ellipsis: true },
    { title: '状态', dataIndex: 'status', width: 100 },
    { title: '排序', dataIndex: 'sortOrder', width: 80 },
    { title: '操作', dataIndex: 'action', width: 130, align: 'center' },
  ];

  /** 可绑定的部门下拉（value = sys_depart.id） */
  const departOptions = ref<any[]>([]);
  /** departId -> 部门名，列表行渲染用 */
  const departNameMap = ref<Record<string, string>>({});

  /**
   * 部门树拍平。
   * 只保留 orgType === '2'（部门）的节点：公司/岗位节点不参与店铺绑定。
   * orgType 是字符串（接口返回 '2'），用 String() 兜底数值型。
   */
  function flattenDeparts(nodes: any[], out: any[] = []): any[] {
    (nodes || []).forEach((node) => {
      if (!node) return;
      if (String(node.orgType) === '2') {
        out.push(node);
      }
      if (Array.isArray(node.children) && node.children.length) {
        flattenDeparts(node.children, out);
      }
    });
    return out;
  }

  /** 加载部门树：同时供表单下拉与列表「绑定部门」列显示名字 */
  async function loadDeparts() {
    try {
      const tree = await queryTreeList();
      departOptions.value = flattenDeparts(tree || []).map((node: any) => ({
        label: node.departName || node.title || node.id,
        value: node.id,
      }));
      const map: Record<string, string> = {};
      departOptions.value.forEach((item: any) => {
        map[item.value] = item.label;
      });
      departNameMap.value = map;
    } catch (e) {
      console.warn('部门树加载失败', e);
    }
  }

  const form = reactive<Record<string, any>>({
    id: '',
    code: '',
    name: '',
    departId: undefined,
    domain: '',
    status: 'Y',
    sortOrder: 0,
    description: '',
  });

  const rules = {
    code: [
      { required: true, message: '请输入店铺编码' },
      { pattern: /^[A-Za-z0-9_-]{1,50}$/, message: '只允许字母、数字、下划线和中划线' },
    ],
    name: [{ required: true, message: '请输入店铺名称' }],
  };

  // 必须是 computed：直接传 ref 对象给 a-table，AntD 拿到的是 Ref 而不是数值
  const pagination = computed(() => ({
    current: pageNo.value,
    pageSize: pageSize.value,
    total: total.value,
    showSizeChanger: true,
    pageSizeOptions: ['10', '20', '50'],
    showTotal: (t: number, range: [number, number]) => `第 ${range[0]}-${range[1]} 条 / 共 ${t} 个店铺`,
    onChange: (page: number, size: number) => {
      pageNo.value = size !== pageSize.value ? 1 : page;
      pageSize.value = size;
      load();
    },
  }));

  async function load() {
    loading.value = true;
    try {
      const res = await list({
        pageNo: pageNo.value,
        pageSize: pageSize.value,
        code: query.code || undefined,
        name: query.name || undefined,
        status: query.status || undefined,
      });
      records.value = res?.records || [];
      total.value = res?.total || 0;
      selectedKeys.value = [];
    } catch (e: any) {
      createMessage.error(e?.message || '店铺列表加载失败');
    } finally {
      loading.value = false;
    }
  }

  function reload() {
    pageNo.value = 1;
    load();
  }

  function resetQuery() {
    query.code = '';
    query.name = '';
    query.status = undefined;
    reload();
  }

  function resetForm(record?: any) {
    Object.assign(form, {
      id: record?.id || '',
      code: record?.code || '',
      name: record?.name || '',
      // 未提交语义一律用 undefined（不传该字段 = 后端不改动）；
      // 只有用户在表单里手动清除时才会变成 ''（= 显式解绑）
      departId: record?.departId || undefined,
      domain: record?.domain || '',
      status: record?.status || 'Y',
      sortOrder: record?.sortOrder ?? 0,
      description: record?.description || '',
    });
  }

  /** 绑定部门变化：清除时用 '' 表示显式解绑，而不是 undefined（不传 = 不改） */
  function handleDepartChange(value: any) {
    form.departId = value === undefined || value === null || value === '' ? '' : value;
  }

  /** 导出当前筛选条件下的店铺 */
  function onExportXls() {
    handleExportXls('店铺', getExportUrl, {
      code: query.code || undefined,
      name: query.name || undefined,
      status: query.status || undefined,
    });
  }

  /** 导入：JUploadButton 会把上传选项（含 file）传进来，后端逐行走校验 */
  function onImportXls(file: any) {
    handleImportXls(file, getImportUrl, () => load());
  }

  function openAdd() {
    resetForm();
    modalOpen.value = true;
    formRef.value?.clearValidate?.();
  }

  async function openEdit(record: any) {
    modalOpen.value = true;
    try {
      // 拉一次详情，避免列表未返回的字段被清空
      const detail = await queryById({ id: record.id });
      resetForm(detail || record);
    } catch {
      resetForm(record);
    }
    formRef.value?.clearValidate?.();
  }

  async function submit() {
    try {
      await formRef.value?.validate?.();
    } catch {
      return;
    }
    saving.value = true;
    try {
      if (form.id) {
        await edit({ ...form });
      } else {
        await add({ ...form });
      }
      createMessage.success('保存成功');
      modalOpen.value = false;
      load();
    } catch (e: any) {
      createMessage.error(e?.message || '保存失败');
    } finally {
      saving.value = false;
    }
  }

  function removeOne(record: any) {
    deleteOne({ id: record.id }, () => {
      createMessage.success('删除成功');
      load();
    });
  }

  onMounted(() => {
    load();
    // 部门树只用于下拉与列表显示名字，加载失败不阻塞店铺列表
    loadDeparts();
  });
</script>

<style lang="less" scoped>
  .shop-store {
    &__search {
      display: flex;
      align-items: center;
      gap: 8px;
      flex-wrap: wrap;
      margin-bottom: 16px;
    }

    &__spacer {
      flex: 1;
    }

    &__muted {
      color: rgba(0, 0, 0, 0.45);
      font-size: 12px;
    }

    &__warn {
      color: #d46b08;
      font-size: 12px;
    }

    &__hint {
      display: block;
      margin-top: 4px;
      color: rgba(0, 0, 0, 0.45);
      font-size: 12px;
      line-height: 1.6;
    }

    &__danger {
      color: #ff4d4f;
    }
  }
</style>
