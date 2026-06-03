<template>
  <el-card>
    <template #header><span>My Appointments</span></template>
    <el-table :data="tableData" v-loading="loading" stripe>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="doctorName" label="Doctor" />
      <el-table-column prop="patientName" label="Patient" />
      <el-table-column label="Appointment Time" width="180">
        <template #default="{ row }">{{ row.appointmentTime }}</template>
      </el-table-column>
      <el-table-column label="Status" width="120">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="Description" show-overflow-tooltip />
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

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { getMyAppointments } from '../../../api/patientPortal'

const tableData = ref([])
const loading = ref(false)
const total = ref(0)
const query = reactive({ page: 1, size: 10 })

function statusType(status) {
  const map = { 0: 'info', 1: 'success', 2: 'danger', 3: '' }
  return map[status] || 'info'
}

function statusText(status) {
  const map = { 0: 'Pending', 1: 'Confirmed', 2: 'Cancelled', 3: 'Completed' }
  return map[status] || 'Unknown'
}

async function fetchData() {
  loading.value = true
  try {
    const res = await getMyAppointments(query)
    tableData.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

onMounted(() => fetchData())
</script>
