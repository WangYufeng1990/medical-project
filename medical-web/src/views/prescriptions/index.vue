<template>
  <div>
    <el-card>
      <div class="toolbar">
        <span></span>
        <el-button type="primary" @click="openDialog()">Add Prescription</el-button>
      </div>
      <el-table :data="tableData" border stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="100" />
        <el-table-column prop="patientName" label="Patient" width="120" />
        <el-table-column prop="doctorName" label="Doctor" width="120" />
        <el-table-column prop="diagnosis" label="Diagnosis" />
        <el-table-column prop="prescriptionDate" label="Date" width="120" />
        <el-table-column label="Actions" width="180" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="viewDetail(row)">Detail</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row.id)">Delete</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="page" v-model:page-size="size" :total="total"
        :page-sizes="[10,20,50]" layout="total, sizes, prev, pager, next"
        @current-change="fetchData" @size-change="fetchData"
        style="margin-top:16px; justify-content: flex-end" />
    </el-card>

    <el-dialog v-model="dialogVisible" title="Add Prescription" width="700px" @close="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="Patient" prop="patientId">
          <el-input v-model="form.patientId" placeholder="Patient ID" type="number" />
        </el-form-item>
        <el-form-item label="Doctor" prop="doctorId">
          <el-input v-model="form.doctorId" placeholder="Doctor ID" type="number" />
        </el-form-item>
        <el-form-item label="Date" prop="prescriptionDate">
          <el-date-picker v-model="form.prescriptionDate" type="date" placeholder="Select date" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item label="Diagnosis" prop="diagnosis">
          <el-input v-model="form.diagnosis" type="textarea" :rows="2" />
        </el-form-item>
        <el-divider>Prescription Items</el-divider>
        <div v-for="(item, idx) in form.items" :key="idx" class="item-row">
          <el-input v-model="item.drugName" placeholder="Drug name" style="width:140px" />
          <el-input v-model="item.specification" placeholder="Spec" style="width:100px" />
          <el-input v-model="item.dosage" placeholder="Dosage" style="width:100px" />
          <el-input v-model="item.frequency" placeholder="Frequency" style="width:100px" />
          <el-input-number v-model="item.duration" :min="1" placeholder="Days" style="width:80px" />
          <el-input-number v-model="item.quantity" :min="1" style="width:80px" />
          <el-input-number v-model="item.unitPrice" :min="0" :precision="2" style="width:120px" />
          <el-button type="danger" :icon="Delete" circle size="small" @click="form.items.splice(idx, 1)" />
        </div>
        <el-button type="primary" size="small" @click="form.items.push({ drugName: '', specification: '', dosage: '', frequency: '', duration: 7, quantity: 1, unitPrice: 0 })" style="margin-top:10px">
          Add Item
        </el-button>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">Cancel</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">Save</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailVisible" title="Prescription Detail" width="600px">
      <template v-if="detail">
        <p><strong>Patient:</strong> {{ detail.patientName }}</p>
        <p><strong>Doctor:</strong> {{ detail.doctorName }}</p>
        <p><strong>Date:</strong> {{ detail.prescriptionDate }}</p>
        <p><strong>Diagnosis:</strong> {{ detail.diagnosis }}</p>
        <el-table :data="detail.items" border size="small" style="margin-top:12px">
          <el-table-column prop="drugName" label="Drug" />
          <el-table-column prop="specification" label="Spec" />
          <el-table-column prop="dosage" label="Dosage" />
          <el-table-column prop="frequency" label="Frequency" />
          <el-table-column prop="duration" label="Days" />
          <el-table-column prop="quantity" label="Qty" />
          <el-table-column prop="unitPrice" label="Unit Price" />
        </el-table>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getPrescriptionPage, getPrescriptionById, createPrescription, deletePrescription } from '../../api/prescription'

const loading = ref(false), tableData = ref([]), total = ref(0), page = ref(1), size = ref(10)
const dialogVisible = ref(false), submitting = ref(false), formRef = ref<any>(null)
const detailVisible = ref(false), detail = ref<any>(null)
const form = reactive({
  patientId: null, doctorId: null, prescriptionDate: '', diagnosis: '',
  items: [{ drugName: '', specification: '', dosage: '', frequency: '', duration: 7, quantity: 1, unitPrice: 0 }]
})
const rules = {
  patientId: [{ required: true, message: 'Required', trigger: 'blur' }],
  doctorId: [{ required: true, message: 'Required', trigger: 'blur' }],
  diagnosis: [{ required: true, message: 'Required', trigger: 'blur' }]
}

async function fetchData() {
  loading.value = true
  try {
    const res = await getPrescriptionPage({ page: page.value, size: size.value })
    tableData.value = res.records; total.value = res.total
  } finally { loading.value = false }
}

function openDialog() {
  resetForm(); dialogVisible.value = true
}

function resetForm() {
  formRef.value?.resetFields()
  Object.assign(form, {
    patientId: null, doctorId: null, prescriptionDate: '', diagnosis: '',
    items: [{ drugName: '', specification: '', dosage: '', frequency: '', duration: 7, quantity: 1, unitPrice: 0 }]
  })
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    await createPrescription(form)
    ElMessage.success('Created')
    dialogVisible.value = false; fetchData()
  } finally { submitting.value = false }
}

async function viewDetail(row) {
  detail.value = await getPrescriptionById(row.id)
  detailVisible.value = true
}

async function handleDelete(id) {
  await ElMessageBox.confirm('Delete this prescription?', 'Confirm', { type: 'warning' })
  await deletePrescription(id); ElMessage.success('Deleted'); fetchData()
}

onMounted(fetchData)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; margin-bottom: 16px; }
.item-row { display: flex; gap: 8px; align-items: center; margin-bottom: 8px; flex-wrap: wrap; }
</style>
