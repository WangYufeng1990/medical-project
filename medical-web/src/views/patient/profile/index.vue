<template>
  <el-card>
    <template #header><span>My Profile</span></template>
    <el-descriptions :column="2" border v-if="profile" v-loading="loading">
      <el-descriptions-item label="Name">{{ profile.name }}</el-descriptions-item>
      <el-descriptions-item label="Username">{{ profile.username || '-' }}</el-descriptions-item>
      <el-descriptions-item label="Gender">{{ profile.gender === 1 ? 'Male' : profile.gender === 2 ? 'Female' : 'Unknown' }}</el-descriptions-item>
      <el-descriptions-item label="Age">{{ profile.age || '-' }}</el-descriptions-item>
      <el-descriptions-item label="Phone">{{ profile.phone || '-' }}</el-descriptions-item>
      <el-descriptions-item label="Address">{{ profile.address || '-' }}</el-descriptions-item>
      <el-descriptions-item label="Medical History" :span="2">{{ profile.medicalHistory || '-' }}</el-descriptions-item>
      <el-descriptions-item label="Allergies" :span="2">{{ profile.allergies || '-' }}</el-descriptions-item>
    </el-descriptions>
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getMyProfile } from '../../../api/patientPortal'

const profile = ref(null)
const loading = ref(false)

onMounted(async () => {
  loading.value = true
  try {
    profile.value = await getMyProfile()
  } finally {
    loading.value = false
  }
})
</script>
