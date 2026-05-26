<template>
  <div>
    <el-card>
      <div class="toolbar">
        <span></span>
        <el-button type="primary" @click="openDialog()">Add Menu</el-button>
      </div>
      <el-table :data="tableData" border stripe row-key="id" default-expand-all v-loading="loading">
        <el-table-column prop="menuName" label="Name" width="200" />
        <el-table-column prop="icon" label="Icon" width="100" />
        <el-table-column prop="path" label="Path" width="180" />
        <el-table-column prop="component" label="Component" width="200" />
        <el-table-column prop="type" label="Type" width="100">
          <template #default="{ row }">
            <el-tag :type="row.type === 'DIRECTORY' ? '' : row.type === 'MENU' ? 'success' : 'info'" size="small">{{ row.type }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="permission" label="Permission" width="180" />
        <el-table-column prop="sort" label="Sort" width="60" />
        <el-table-column label="Actions" width="180" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openDialog(row)">Edit</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row.id)">Delete</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editId ? 'Edit Menu' : 'Add Menu'" width="550px" @close="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="Type" prop="type">
          <el-select v-model="form.type" @change="onTypeChange">
            <el-option value="DIRECTORY" label="Directory" />
            <el-option value="MENU" label="Menu" />
            <el-option value="BUTTON" label="Button" />
          </el-select>
        </el-form-item>
        <el-form-item label="Parent" prop="parentId">
          <el-tree-select v-model="form.parentId" :data="treeOptions" :props="{ label: 'menuName', value: 'id', children: 'children' }"
            check-strictly clearable placeholder="Top level" style="width:100%" />
        </el-form-item>
        <el-form-item label="Name" prop="menuName">
          <el-input v-model="form.menuName" />
        </el-form-item>
        <el-form-item label="Path" v-if="form.type !== 'BUTTON'">
          <el-input v-model="form.path" />
        </el-form-item>
        <el-form-item label="Component" v-if="form.type === 'MENU'">
          <el-input v-model="form.component" />
        </el-form-item>
        <el-form-item label="Icon" v-if="form.type !== 'BUTTON'">
          <el-input v-model="form.icon" />
        </el-form-item>
        <el-form-item label="Permission" v-if="form.type !== 'DIRECTORY'">
          <el-input v-model="form.permission" />
        </el-form-item>
        <el-form-item label="Sort">
          <el-input-number v-model="form.sort" :min="0" />
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

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMenuTree, getAllMenus, createMenu, updateMenu, deleteMenu } from '../../../api/menu'

const loading = ref(false), tableData = ref([]), treeOptions = ref([])
const dialogVisible = ref(false), editId = ref(null), submitting = ref(false), formRef = ref(null)
const form = reactive({ parentId: null, menuName: '', path: '', component: '', icon: '', type: 'MENU', permission: '', sort: 0, status: 1 })
const rules = {
  menuName: [{ required: true, message: 'Required', trigger: 'blur' }],
  type: [{ required: true, message: 'Required', trigger: 'change' }]
}

async function fetchData() {
  loading.value = true
  try {
    const [tree, all] = await Promise.all([getMenuTree(), getAllMenus()])
    tableData.value = tree
    treeOptions.value = all
  } finally { loading.value = false }
}

function openDialog(row) {
  if (row) {
    editId.value = row.id
    Object.assign(form, {
      parentId: row.parentId || null, menuName: row.menuName, path: row.path || '',
      component: row.component || '', icon: row.icon || '', type: row.type,
      permission: row.permission || '', sort: row.sort || 0, status: row.status
    })
  } else { editId.value = null; resetForm() }
  dialogVisible.value = true
}

function onTypeChange(val) {
  if (val === 'DIRECTORY') form.permission = ''
  if (val === 'BUTTON') { form.path = ''; form.component = ''; form.icon = '' }
}

function resetForm() {
  formRef.value?.resetFields()
  Object.assign(form, { parentId: null, menuName: '', path: '', component: '', icon: '', type: 'MENU', permission: '', sort: 0, status: 1 })
  editId.value = null
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    editId.value ? await updateMenu(editId.value, form) : await createMenu(form)
    ElMessage.success(editId.value ? 'Updated' : 'Created')
    dialogVisible.value = false; fetchData()
  } finally { submitting.value = false }
}

async function handleDelete(id) {
  await ElMessageBox.confirm('Delete this menu?', 'Confirm', { type: 'warning' })
  await deleteMenu(id); ElMessage.success('Deleted'); fetchData()
}

onMounted(fetchData)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; margin-bottom: 16px; }
</style>
