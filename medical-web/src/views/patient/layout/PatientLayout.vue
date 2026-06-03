<template>
  <el-container class="layout">
    <el-aside width="220px" class="aside">
      <div class="logo" @click="$router.push('/patient/dashboard')">
        <el-icon :size="24"><Plus /></el-icon>
        <span class="logo-text">Patient Portal</span>
      </div>
      <el-menu
        :default-active="route.path"
        router
        background-color="#304156"
        text-color="#bfcbd9"
        active-text-color="#409EFF"
      >
        <el-menu-item index="/patient/dashboard">
          <el-icon><Odometer /></el-icon>
          <span>Dashboard</span>
        </el-menu-item>
        <el-menu-item index="/patient/profile">
          <el-icon><UserFilled /></el-icon>
          <span>My Profile</span>
        </el-menu-item>
        <el-menu-item index="/patient/appointments">
          <el-icon><Calendar /></el-icon>
          <span>My Appointments</span>
        </el-menu-item>
        <el-menu-item index="/patient/prescriptions">
          <el-icon><Document /></el-icon>
          <span>My Prescriptions</span>
        </el-menu-item>
        <el-menu-item index="/patient/bills">
          <el-icon><Money /></el-icon>
          <span>My Bills</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="header-left">
          <span class="page-title">Patient Portal</span>
        </div>
        <div class="header-right">
          <span class="user-name">
            <el-icon><UserFilled /></el-icon>
            {{ patientStore.patientInfo.name || patientStore.patientInfo.username }}
          </span>
          <el-button text type="danger" @click="handleLogout">Logout</el-button>
        </div>
      </el-header>

      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { useRouter, useRoute } from 'vue-router'
import { usePatientStore } from '../../../stores/patient'
import { ElMessageBox } from 'element-plus'

const router = useRouter()
const route = useRoute()
const patientStore = usePatientStore()

function handleLogout() {
  ElMessageBox.confirm('Are you sure you want to logout?', 'Confirm', {
    type: 'warning'
  }).then(() => {
    patientStore.logout()
    router.push('/patient/login')
  }).catch(() => {})
}
</script>

<style scoped>
.layout { height: 100vh; }
.aside { background-color: #304156; overflow: hidden; }
.logo { height: 60px; display: flex; align-items: center; justify-content: center; color: #fff; cursor: pointer; gap: 8px; }
.logo-text { font-size: 16px; font-weight: bold; white-space: nowrap; }
.aside :deep(.el-menu) { border-right: none; }
.header { background: #fff; display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid #e6e6e6; padding: 0 20px; height: 60px; }
.header-left { display: flex; align-items: center; gap: 12px; }
.page-title { font-size: 16px; font-weight: 500; }
.header-right { display: flex; align-items: center; gap: 16px; }
.user-name { display: flex; align-items: center; gap: 4px; color: #333; }
.el-main { background: #f0f2f5; padding: 20px; }
</style>
