<template>
  <div>
    <el-card>
      <div class="toolbar">
        <el-input v-model="keyword" placeholder="Search by name or phone" clearable style="width:260px" @clear="fetchData" @keyup.enter="fetchData" />
        <el-button type="primary" @click="openDialog()">Add Patient</el-button>
      </div>
      <el-table :data="tableData" border stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="100" />
        <el-table-column prop="name" label="Name" width="120" />
        <el-table-column prop="gender" label="Gender" width="80">
          <template #default="{ row }">{{ ['?','Male','Female'][row.gender] || '?' }}</template>
        </el-table-column>
        <el-table-column prop="age" label="Age" width="80" />
        <el-table-column prop="phone" label="Phone" width="140" />
        <el-table-column prop="idCard" label="ID Card" width="200" />
        <el-table-column prop="address" label="Address" />
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

    <el-dialog v-model="dialogVisible" :title="editId ? 'Edit Patient' : 'Add Patient'" width="600px" @close="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="130px">
        <el-form-item label="Name" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="Gender" prop="gender">
          <el-select v-model="form.gender">
            <el-option :value="1" label="Male" />
            <el-option :value="2" label="Female" />
          </el-select>
        </el-form-item>
        <el-form-item label="Age" prop="age">
          <el-input-number v-model="form.age" :min="0" :max="200" />
        </el-form-item>
        <el-form-item label="ID Card">
          <el-input v-model="form.idCard" />
        </el-form-item>
        <el-form-item label="Phone">
          <el-input v-model="form.phone" />
        </el-form-item>
        <el-form-item label="Address">
          <el-input v-model="form.address" />
        </el-form-item>
        <el-form-item label="Medical History">
          <el-input v-model="form.medicalHistory" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="Allergies">
          <el-input v-model="form.allergies" />
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
import { getPatientPage, getPatientById, createPatient, updatePatient, deletePatient } from '../../api/patient'

const loading = ref(false), tableData = ref([]), total = ref(0), page = ref(1), size = ref(10), keyword = ref('')
const dialogVisible = ref(false), editId = ref(null), submitting = ref(false), formRef = ref(null)
const form = reactive({ name: '', gender: 1, age: 0, idCard: '', phone: '', address: '', medicalHistory: '', allergies: '' })
const rules = {
  name: [{ required: true, message: 'Required', trigger: 'blur' }],
  gender: [{ required: true, message: 'Required', trigger: 'change' }],
  age: [{ required: true, message: 'Required', trigger: 'blur' }]
}

async function fetchData() {
  loading.value = true
  try {
    const res = await getPatientPage({ page: page.value, size: size.value, keyword: keyword.value })
    tableData.value = res.records; total.value = res.total
  } finally { loading.value = false }
}

async function openDialog(row) {
  if (row) {
    editId.value = row.id
    const detail = await getPatientById(row.id)
    Object.assign(form, {
      name: detail.name, gender: detail.gender, age: detail.age,
      idCard: detail.idCard || '', phone: detail.phone || '',
      address: detail.address || '', medicalHistory: detail.medicalHistory || '', allergies: detail.allergies || ''
    })
  } else { editId.value = null; resetForm() }
  dialogVisible.value = true
}

function resetForm() {
  formRef.value?.resetFields()
  Object.assign(form, { name: '', gender: 1, age: 0, idCard: '', phone: '', address: '', medicalHistory: '', allergies: '' })
  editId.value = null
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    editId.value ? await updatePatient(editId.value, form) : await createPatient(form)
    ElMessage.success(editId.value ? 'Updated' : 'Created')
    dialogVisible.value = false; fetchData()
  } finally { submitting.value = false }
}

async function handleDelete(id) {
  await ElMessageBox.confirm('Delete this patient?', 'Confirm', { type: 'warning' })
  await deletePatient(id); ElMessage.success('Deleted'); fetchData()
}

onMounted(fetchData)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; margin-bottom: 16px; }
</style>
