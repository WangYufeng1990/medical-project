<template>
  <el-card>
    <template #header><span>My Bills</span></template>
    <el-table :data="tableData" v-loading="loading" stripe>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="patientName" label="Patient" />
      <el-table-column label="Amount" width="120">
        <template #default="{ row }">${{ row.amount?.toFixed(2) }}</template>
      </el-table-column>
      <el-table-column label="Paid" width="120">
        <template #default="{ row }">${{ row.paidAmount?.toFixed(2) }}</template>
      </el-table-column>
      <el-table-column label="Status" width="120">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="Pay Time" width="180">
        <template #default="{ row }">{{ row.payTime || '-' }}</template>
      </el-table-column>
      <el-table-column label="Created" width="180">
        <template #default="{ row }">{{ row.createTime }}</template>
      </el-table-column>
    </el-table>
    <div style="margin-top: 16px; text-align: right">
      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @size-change="fetchData"
        @current-change="fetchData"
      />
    </div>
  </el-card>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getMyBills } from '../../../api/patientPortal'

const tableData = ref([])
const loading = ref(false)
const total = ref(0)
const query = reactive({ page: 1, size: 10 })

function statusType(status) {
  const map = { 0: 'warning', 1: 'success', 2: 'danger' }
  return map[status] || 'info'
}

function statusText(status) {
  const map = { 0: 'Unpaid', 1: 'Paid', 2: 'Cancelled' }
  return map[status] || 'Unknown'
}

async function fetchData() {
  loading.value = true
  try {
    const res = await getMyBills(query)
    tableData.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

onMounted(() => fetchData())
</script>
