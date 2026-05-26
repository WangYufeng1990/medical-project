<template>
  <div class="login-wrapper">
    <div class="login-card">
      <h2>Patient Portal</h2>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="handleLogin">
        <el-form-item label="Username" prop="username">
          <el-input v-model="form.username" placeholder="Enter username" />
        </el-form-item>
        <el-form-item label="Password" prop="password">
          <el-input v-model="form.password" type="password" placeholder="Enter password" show-password />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="handleLogin" style="width:100%">
            Login
          </el-button>
        </el-form-item>
        <el-form-item>
          <el-button @click="$router.push('/login')" style="width:100%">
            Admin Login
          </el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { usePatientStore } from '../../stores/patient'
import { ElMessage } from 'element-plus'

const router = useRouter()
const patientStore = usePatientStore()
const formRef = ref(null)
const loading = ref(false)
const form = reactive({ username: '', password: '' })

const rules = {
  username: [{ required: true, message: 'Username is required', trigger: 'blur' }],
  password: [{ required: true, message: 'Password is required', trigger: 'blur' }]
}

async function handleLogin() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await patientStore.login(form)
    ElMessage.success('Login successful')
    router.push('/patient/dashboard')
  } catch (e) {
    ElMessage.error('Invalid username or password')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-wrapper {
  height: 100vh; display: flex; align-items: center; justify-content: center;
  background: linear-gradient(135deg, #43e97b 0%, #38f9d7 100%);
}
.login-card {
  width: 400px; padding: 40px; background: #fff; border-radius: 8px; box-shadow: 0 4px 20px rgba(0,0,0,0.15);
}
.login-card h2 { text-align: center; margin-bottom: 30px; color: #303133; }
</style>
