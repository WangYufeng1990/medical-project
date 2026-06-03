<template>
  <el-card>
    <template #header><span>My Prescriptions</span></template>
    <el-table :data="tableData" v-loading="loading" stripe @row-click="showDetail" row-style="cursor: pointer">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="doctorName" label="Doctor" />
      <el-table-column prop="patientName" label="Patient" />
      <el-table-column prop="diagnosis" label="Diagnosis" show-overflow-tooltip />
      <el-table-column prop="prescriptionDate" label="Date" width="120" />
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

    <el-dialog v-model="dialogVisible" title="Prescription Detail" width="700px">
      <template v-if="selected">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="Doctor">{{ selected.doctorName }}</el-descriptions-item>
          <el-descriptions-item label="Patient">{{ selected.patientName }}</el-descriptions-item>
          <el-descriptions-item label="Diagnosis" :span="2">{{ selected.diagnosis }}</el-descriptions-item>
          <el-descriptions-item label="Date">{{ selected.prescriptionDate }}</el-descriptions-item>
        </el-descriptions>
        <h4 style="margin-top: 16px">Medications</h4>
        <el-table :data="selected.items" size="small" stripe>
          <el-table-column prop="drugName" label="Drug" />
          <el-table-column prop="specification" label="Spec" />
          <el-table-column prop="dosage" label="Dosage" />
          <el-table-column prop="frequency" label="Frequency" />
          <el-table-column prop="duration" label="Days" />
          <el-table-column prop="quantity" label="Qty" />
        </el-table>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { getMyPrescriptions } from '../../../api/patientPortal'

const tableData = ref([])
const loading = ref(false)
const total = ref(0)
const query = reactive({ page: 1, size: 10 })
const dialogVisible = ref(false)
const selected = ref<any>(null)

async function fetchData() {
  loading.value = true
  try {
    const res = await getMyPrescriptions(query)
    tableData.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function showDetail(row) {
  selected.value = row
  dialogVisible.value = true
}

onMounted(() => fetchData())
</script>
