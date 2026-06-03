<template>
  <div>
    <el-card>
      <div class="toolbar">
        <div>
          <el-select v-model="statusFilter" placeholder="Status" clearable style="width:150px" @change="fetchData">
            <el-option :value="0" label="Pending" />
            <el-option :value="1" label="Confirmed" />
            <el-option :value="2" label="Cancelled" />
            <el-option :value="3" label="Completed" />
          </el-select>
        </div>
        <el-button type="primary" @click="openDialog()">Add Appointment</el-button>
      </div>
      <el-table :data="tableData" border stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="100" />
        <el-table-column prop="patientName" label="Patient" width="120" />
        <el-table-column prop="doctorName" label="Doctor" width="120" />
        <el-table-column prop="appointmentTime" label="Time" width="180">
          <template #default="{ row }">{{ new Date(row.appointmentTime).toLocaleString() }}</template>
        </el-table-column>
        <el-table-column prop="status" label="Status" width="100">
          <template #default="{ row }">
            <el-tag :type="['info','success','danger',''][row.status]" size="small">
              {{ ['Pending','Confirmed','Cancelled','Completed'][row.status] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="Description" />
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

    <el-dialog v-model="dialogVisible" :title="editId ? 'Edit Appointment' : 'Add Appointment'" width="500px" @close="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="Patient" prop="patientId">
          <el-input v-model="form.patientId" placeholder="Patient ID" type="number" />
        </el-form-item>
        <el-form-item label="Doctor" prop="doctorId">
          <el-input v-model="form.doctorId" placeholder="Doctor ID" type="number" />
        </el-form-item>
        <el-form-item label="Time" prop="appointmentTime">
          <el-date-picker v-model="form.appointmentTime" type="datetime" placeholder="Select time" value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%" />
        </el-form-item>
        <el-form-item label="Status">
          <el-select v-model="form.status">
            <el-option :value="0" label="Pending" />
            <el-option :value="1" label="Confirmed" />
            <el-option :value="2" label="Cancelled" />
            <el-option :value="3" label="Completed" />
          </el-select>
        </el-form-item>
        <el-form-item label="Description">
          <el-input v-model="form.description" />
        </el-form-item>
        <el-form-item label="Notes">
          <el-input v-model="form.notes" type="textarea" :rows="3" />
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
import { getAppointmentPage, getAppointmentById, createAppointment, updateAppointment, deleteAppointment } from '../../api/appointment'

const loading = ref(false), tableData = ref([]), total = ref(0), page = ref(1), size = ref(10), statusFilter = ref<any>(null)
const dialogVisible = ref(false), editId = ref<any>(null), submitting = ref(false), formRef = ref<any>(null)
const form = reactive({ patientId: null, doctorId: null, appointmentTime: '', description: '', notes: '', status: 0 })
const rules = {
  patientId: [{ required: true, message: 'Required', trigger: 'blur' }],
  doctorId: [{ required: true, message: 'Required', trigger: 'blur' }],
  appointmentTime: [{ required: true, message: 'Required', trigger: 'change' }]
}

async function fetchData() {
  loading.value = true
  try {
    const res = await getAppointmentPage({ page: page.value, size: size.value, status: statusFilter.value })
    tableData.value = res.records; total.value = res.total
  } finally { loading.value = false }
}

async function openDialog(row?: any) {
  if (row) {
    editId.value = row.id
    const detail = await getAppointmentById(row.id)
    Object.assign(form, {
      patientId: detail.patientId, doctorId: detail.doctorId,
      appointmentTime: detail.appointmentTime, description: detail.description || '',
      notes: detail.notes || '', status: detail.status
    })
  } else { editId.value = null; resetForm() }
  dialogVisible.value = true
}

function resetForm() {
  formRef.value?.resetFields()
  Object.assign(form, { patientId: null, doctorId: null, appointmentTime: '', description: '', notes: '', status: 0 })
  editId.value = null
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    editId.value ? await updateAppointment(editId.value, form) : await createAppointment(form)
    ElMessage.success(editId.value ? 'Updated' : 'Created')
    dialogVisible.value = false; fetchData()
  } finally { submitting.value = false }
}

async function handleDelete(id) {
  await ElMessageBox.confirm('Delete this appointment?', 'Confirm', { type: 'warning' })
  await deleteAppointment(id); ElMessage.success('Deleted'); fetchData()
}

onMounted(fetchData)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; margin-bottom: 16px; }
</style>
