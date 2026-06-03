<template>
  <div>
    <el-card>
      <el-tabs>
        <el-tab-pane label="Edit Profile">
          <el-form ref="profileFormRef" :model="profileForm" :rules="profileRules" label-width="120px" style="max-width:500px">
            <el-form-item label="Username">
              <el-input :value="profile.username" disabled />
            </el-form-item>
            <el-form-item label="Real Name" prop="realName">
              <el-input v-model="profileForm.realName" />
            </el-form-item>
            <el-form-item label="Phone">
              <el-input v-model="profileForm.phone" />
            </el-form-item>
            <el-form-item label="Email">
              <el-input v-model="profileForm.email" />
            </el-form-item>
            <el-form-item label="Gender">
              <el-select v-model="profileForm.gender">
                <el-option :value="0" label="Unknown" />
                <el-option :value="1" label="Male" />
                <el-option :value="2" label="Female" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleUpdateProfile" :loading="saving">Save</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="Change Password">
          <el-form ref="passwordFormRef" :model="passwordForm" :rules="passwordRules" label-width="140px" style="max-width:450px">
            <el-form-item label="Old Password" prop="oldPassword">
              <el-input v-model="passwordForm.oldPassword" type="password" show-password />
            </el-form-item>
            <el-form-item label="New Password" prop="newPassword">
              <el-input v-model="passwordForm.newPassword" type="password" show-password />
            </el-form-item>
            <el-form-item label="Confirm Password" prop="confirmPassword">
              <el-input v-model="passwordForm.confirmPassword" type="password" show-password />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleChangePassword" :loading="savingPwd">Update Password</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getProfile, updateProfile, changePassword } from '../../api/profile'

const profile = ref({})
const saving = ref(false)
const savingPwd = ref(false)
const profileFormRef = ref<any>(null)
const passwordFormRef = ref<any>(null)

const profileForm = reactive({ realName: '', phone: '', email: '', gender: 0 })
const profileRules = {
  realName: [{ required: true, message: 'Required', trigger: 'blur' }]
}

const passwordForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
const validateConfirm = (rule, value, callback) => {
  if (value !== passwordForm.newPassword) {
    callback(new Error('Passwords do not match'))
  } else {
    callback()
  }
}
const passwordRules = {
  oldPassword: [{ required: true, message: 'Required', trigger: 'blur' }],
  newPassword: [{ required: true, min: 6, message: 'At least 6 characters', trigger: 'blur' }],
  confirmPassword: [{ required: true, validator: validateConfirm, trigger: 'blur' }]
}

onMounted(async () => {
  const data = await getProfile()
  profile.value = data
  Object.assign(profileForm, {
    realName: data.realName || '',
    phone: data.phone || '',
    email: data.email || '',
    gender: data.gender || 0
  })
})

async function handleUpdateProfile() {
  const valid = await profileFormRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    await updateProfile(profileForm)
    ElMessage.success('Profile updated')
  } finally { saving.value = false }
}

async function handleChangePassword() {
  const valid = await passwordFormRef.value?.validate().catch(() => false)
  if (!valid) return
  savingPwd.value = true
  try {
    await changePassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword
    })
    ElMessage.success('Password changed')
    passwordFormRef.value?.resetFields()
  } finally { savingPwd.value = false }
}
</script>
