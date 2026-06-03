<template>
  <div>
    <h3>Welcome, {{ patientStore.patientInfo.name }}</h3>
    <el-row :gutter="20" style="margin-top: 20px">
      <el-col :span="8">
        <el-card shadow="hover" @click="$router.push('/patient/appointments')" class="stat-card">
          <el-statistic title="Appointments" :value="appointmentCount" />
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover" @click="$router.push('/patient/prescriptions')" class="stat-card">
          <el-statistic title="Prescriptions" :value="prescriptionCount" />
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover" @click="$router.push('/patient/bills')" class="stat-card">
          <el-statistic title="Bills" :value="billCount" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { usePatientStore } from '../../../stores/patient'
import { getMyAppointments } from '../../../api/patientPortal'
import { getMyPrescriptions } from '../../../api/patientPortal'
import { getMyBills } from '../../../api/patientPortal'

const patientStore = usePatientStore()
const appointmentCount = ref(0)
const prescriptionCount = ref(0)
const billCount = ref(0)

onMounted(async () => {
  try {
    const [appts, prescs, bills] = await Promise.all([
      getMyAppointments({ page: 1, size: 1 }),
      getMyPrescriptions({ page: 1, size: 1 }),
      getMyBills({ page: 1, size: 1 })
    ])
    appointmentCount.value = appts.total
    prescriptionCount.value = prescs.total
    billCount.value = bills.total
  } catch (e) { /* ignore */ }
})
</script>

<style scoped>
h3 { margin: 0; }
.stat-card { cursor: pointer; text-align: center; }
</style>
