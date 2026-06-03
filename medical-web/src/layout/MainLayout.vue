<template>
  <el-container class="layout">
    <el-aside :width="isCollapse ? '64px' : '220px'" class="aside">
      <div class="logo" @click="router.push('/dashboard')">
        <el-icon :size="24"><Plus /></el-icon>
        <span v-show="!isCollapse" class="logo-text">Medical MS</span>
      </div>
      <el-menu
        :default-active="route.path"
        :collapse="isCollapse"
        @select="handleSelect"
        router
        background-color="#304156"
        text-color="#bfcbd9"
        active-text-color="#409EFF"
      >
        <el-menu-item index="/dashboard">
          <el-icon><Odometer /></el-icon>
          <span>Dashboard</span>
        </el-menu-item>

        <el-sub-menu index="system" v-if="userStore.hasRole('ADMIN')">
          <template #title>
            <el-icon><Setting /></el-icon>
            <span>System</span>
          </template>
          <el-menu-item index="/system/users">Users</el-menu-item>
          <el-menu-item index="/system/roles">Roles</el-menu-item>
          <el-menu-item index="/system/menus">Menus</el-menu-item>
        </el-sub-menu>

        <el-menu-item index="/patients">
          <el-icon><UserFilled /></el-icon>
          <span>Patients</span>
        </el-menu-item>

        <el-menu-item index="/appointments">
          <el-icon><Calendar /></el-icon>
          <span>Appointments</span>
        </el-menu-item>

        <el-menu-item index="/prescriptions">
          <el-icon><Document /></el-icon>
          <span>Prescriptions</span>
        </el-menu-item>

        <el-menu-item index="/billing">
          <el-icon><Money /></el-icon>
          <span>Billing</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="header-left">
          <el-icon class="collapse-btn" @click="isCollapse = !isCollapse" :size="22">
            <Fold v-if="!isCollapse" /><Expand v-else />
          </el-icon>
          <span class="page-title">{{ route.matched.at(-1)?.name || '' }}</span>
        </div>
        <div class="header-right">
          <el-dropdown @command="handleUserCommand">
            <span class="user-dropdown">
              <el-icon><UserFilled /></el-icon>
              {{ userStore.userInfo.realName || userStore.userInfo.username }}
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">User Center</el-dropdown-item>
                <el-dropdown-item command="logout" divided>Logout</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '../stores/user'
import { ElMessageBox } from 'element-plus'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const isCollapse = ref(false)

function handleSelect(index) {
  router.push(index)
}

function handleUserCommand(command) {
  if (command === 'profile') {
    router.push('/profile')
  } else if (command === 'logout') {
    ElMessageBox.confirm('Are you sure you want to logout?', 'Confirm', {
      type: 'warning'
    }).then(() => {
      userStore.logout()
      router.push('/login')
    }).catch(() => {})
  }
}
</script>

<style scoped>
.layout { height: 100vh; }
.aside { background-color: #304156; overflow: hidden; transition: width 0.3s; }
.logo { height: 60px; display: flex; align-items: center; justify-content: center; color: #fff; cursor: pointer; gap: 8px; }
.logo-text { font-size: 16px; font-weight: bold; white-space: nowrap; }
.aside :deep(.el-menu) { border-right: none; }
.header { background: #fff; display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid #e6e6e6; padding: 0 20px; height: 60px; }
.header-left { display: flex; align-items: center; gap: 12px; }
.collapse-btn { cursor: pointer; }
.page-title { font-size: 16px; font-weight: 500; }
.header-right { display: flex; align-items: center; gap: 12px; }
.user-dropdown { display: flex; align-items: center; gap: 4px; cursor: pointer; color: #333; }
.el-main { background: #f0f2f5; padding: 20px; }
</style>
