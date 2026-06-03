<template>
  <div>
    <el-card>
      <div class="toolbar">
        <el-input v-model="keyword" placeholder="Search by username or name" clearable style="width:260px" @clear="fetchData" @keyup.enter="fetchData" />
        <el-button type="primary" @click="openDialog()">Add User</el-button>
      </div>

      <el-table :data="tableData" border stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="100" />
        <el-table-column prop="username" label="Username" width="120" />
        <el-table-column prop="realName" label="Name" width="120" />
        <el-table-column prop="phone" label="Phone" width="140" />
        <el-table-column prop="email" label="Email" />
        <el-table-column prop="gender" label="Gender" width="80">
          <template #default="{ row }">{{ ['?','Male','Female'][row.gender] || '?' }}</template>
        </el-table-column>
        <el-table-column prop="status" label="Status" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? 'Active' : 'Disabled' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Actions" width="180" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openDialog(row)">Edit</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row.id)">Delete</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="page" v-model:page-size="size"
        :total="total" :page-sizes="[10,20,50]"
        layout="total, sizes, prev, pager, next"
        @current-change="fetchData" @size-change="fetchData"
        style="margin-top:16px; justify-content: flex-end"
      />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editId ? 'Edit User' : 'Add User'" width="500px" @close="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="Username" prop="username">
          <el-input v-model="form.username" :disabled="!!editId" />
        </el-form-item>
        <el-form-item label="Password" :prop="editId ? '' : 'password'">
          <el-input v-model="form.password" type="password" show-password :placeholder="editId ? 'Leave blank to keep' : 'Enter password'" />
        </el-form-item>
        <el-form-item label="Real Name" prop="realName">
          <el-input v-model="form.realName" />
        </el-form-item>
        <el-form-item label="Phone">
          <el-input v-model="form.phone" />
        </el-form-item>
        <el-form-item label="Email">
          <el-input v-model="form.email" />
        </el-form-item>
        <el-form-item label="Gender">
          <el-select v-model="form.gender">
            <el-option :value="0" label="Unknown" />
            <el-option :value="1" label="Male" />
            <el-option :value="2" label="Female" />
          </el-select>
        </el-form-item>
        <el-form-item label="Status">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="Active" inactive-text="Disabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">Cancel</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">Save</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getUserPage, getUserById, createUser, updateUser, deleteUser } from '../../../api/user'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const keyword = ref('')

const dialogVisible = ref(false)
const editId = ref<any>(null)
const submitting = ref(false)
const formRef = ref<any>(null)
const form = reactive({
  username: '', password: '', realName: '', phone: '', email: '', gender: 0, status: 1
})

const rules = {
  username: [{ required: true, message: 'Username is required', trigger: 'blur' }],
  password: [{ required: true, message: 'Password is required', trigger: 'blur' }]
}

async function fetchData() {
  loading.value = true
  try {
    const res = await getUserPage({ page: page.value, size: size.value, keyword: keyword.value })
    tableData.value = res.records
    total.value = res.total
  } finally { loading.value = false }
}

async function openDialog(row?: any) {
  if (row) {
    editId.value = row.id
    const detail = await getUserById(row.id)
    Object.assign(form, { username: detail.username, realName: detail.realName || '', phone: detail.phone || '', email: detail.email || '', gender: detail.gender || 0, status: detail.status || 1, password: '' })
  } else {
    editId.value = null
    resetForm()
  }
  dialogVisible.value = true
}

function resetForm() {
  formRef.value?.resetFields()
  Object.assign(form, { username: '', password: '', realName: '', phone: '', email: '', gender: 0, status: 1 })
  editId.value = null
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (editId.value) {
      await updateUser(editId.value, form)
      ElMessage.success('Updated')
    } else {
      await createUser(form)
      ElMessage.success('Created')
    }
    dialogVisible.value = false
    fetchData()
  } finally { submitting.value = false }
}

async function handleDelete(id) {
  await ElMessageBox.confirm('Delete this user?', 'Confirm', { type: 'warning' })
  await deleteUser(id)
  ElMessage.success('Deleted')
  fetchData()
}

onMounted(fetchData)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; margin-bottom: 16px; }
</style>
