<template>
  <div>
    <el-row :gutter="20">
      <el-col :span="6" v-for="card in cards" :key="card.label">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-label">{{ card.label }}</div>
          <div class="stat-value">{{ card.value }}</div>
          <el-icon :size="36" :color="card.color"><component :is="card.icon" /></el-icon>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getPatientPage } from '../../api/patient'
import { getAppointmentPage } from '../../api/appointment'
import { getBillPage } from '../../api/bill'

const cards = ref([
  { label: 'Total Patients', value: 0, icon: 'UserFilled', color: '#409EFF' },
  { label: 'Today Appointments', value: 0, icon: 'Calendar', color: '#67C23A' },
  { label: 'Pending Bills', value: 0, icon: 'Money', color: '#E6A23C' },
  { label: 'Prescriptions', value: 0, icon: 'Document', color: '#F56C6C' }
])

onMounted(async () => {
  try {
    const [patients, appointments, bills] = await Promise.all([
      getPatientPage({ page: 1, size: 1 }),
      getAppointmentPage({ page: 1, size: 1, status: 0 }),
      getBillPage({ page: 1, size: 1, claimStatus: 'PENDING' })
    ])
    cards.value[0].value = patients.total
    cards.value[1].value = appointments.total
    cards.value[2].value = bills.total
    cards.value[3].value = '-'
  } catch (e) { /* ignore */ }
})
</script>

<style scoped>
.stat-card { position: relative; overflow: hidden; }
.stat-label { font-size: 14px; color: #909399; }
.stat-value { font-size: 28px; font-weight: bold; margin: 10px 0; color: #303133; }
.stat-card :deep(.el-icon) { position: absolute; right: 20px; bottom: 20px; opacity: 0.3; }
</style>
