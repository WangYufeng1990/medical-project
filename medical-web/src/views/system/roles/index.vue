<template>
  <div>
    <el-card>
      <div class="toolbar">
        <el-input v-model="keyword" placeholder="Search" clearable style="width:260px" @clear="fetchData" @keyup.enter="fetchData" />
        <el-button type="primary" @click="openDialog()">Add Role</el-button>
      </div>
      <el-table :data="tableData" border stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="100" />
        <el-table-column prop="roleName" label="Name" width="150" />
        <el-table-column prop="roleCode" label="Code" width="150" />
        <el-table-column prop="description" label="Description" />
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
      <el-pagination v-model:current-page="page" v-model:page-size="size" :total="total"
        :page-sizes="[10,20,50]" layout="total, sizes, prev, pager, next"
        @current-change="fetchData" @size-change="fetchData"
        style="margin-top:16px; justify-content: flex-end" />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editId ? 'Edit Role' : 'Add Role'" width="500px" @close="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="Name" prop="roleName">
          <el-input v-model="form.roleName" />
        </el-form-item>
        <el-form-item label="Code" prop="roleCode">
          <el-input v-model="form.roleCode" :disabled="!!editId" />
        </el-form-item>
        <el-form-item label="Description">
          <el-input v-model="form.description" type="textarea" />
        </el-form-item>
        <el-form-item label="Status">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
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
import { getRolePage, createRole, updateRole, deleteRole } from '../../../api/role'

const loading = ref(false), tableData = ref([]), total = ref(0), page = ref(1), size = ref(10), keyword = ref('')
const dialogVisible = ref(false), editId = ref<any>(null), submitting = ref(false), formRef = ref<any>(null)
const form = reactive({ roleName: '', roleCode: '', description: '', status: 1 })
const rules = {
  roleName: [{ required: true, message: 'Required', trigger: 'blur' }],
  roleCode: [{ required: true, message: 'Required', trigger: 'blur' }]
}

async function fetchData() {
  loading.value = true
  try {
    const res = await getRolePage({ page: page.value, size: size.value, keyword: keyword.value })
    tableData.value = res.records; total.value = res.total
  } finally { loading.value = false }
}

function openDialog(row?: any) {
  if (row) { editId.value = row.id; Object.assign(form, { roleName: row.roleName, roleCode: row.roleCode, description: row.description || '', status: row.status || 1 }) }
  else { editId.value = null; resetForm() }
  dialogVisible.value = true
}

function resetForm() { formRef.value?.resetFields(); Object.assign(form, { roleName: '', roleCode: '', description: '', status: 1 }); editId.value = null }

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    editId.value ? await updateRole(editId.value, form) : await createRole(form)
    ElMessage.success(editId.value ? 'Updated' : 'Created')
    dialogVisible.value = false; fetchData()
  } finally { submitting.value = false }
}

async function handleDelete(id) {
  await ElMessageBox.confirm('Delete this role?', 'Confirm', { type: 'warning' })
  await deleteRole(id); ElMessage.success('Deleted'); fetchData()
}

onMounted(fetchData)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; margin-bottom: 16px; }
</style>
