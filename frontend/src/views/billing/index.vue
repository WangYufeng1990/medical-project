<template>
  <div>
    <el-card>
      <div class="toolbar">
        <div>
          <el-select v-model="statusFilter" placeholder="Status" clearable style="width:150px" @change="fetchData">
            <el-option :value="0" label="Unpaid" />
            <el-option :value="1" label="Paid" />
            <el-option :value="2" label="Refunded" />
          </el-select>
        </div>
        <el-button type="primary" @click="openDialog()">Add Bill</el-button>
      </div>
      <el-table :data="tableData" border stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="100" />
        <el-table-column prop="patientName" label="Patient" width="120" />
        <el-table-column prop="amount" label="Amount" width="120">
          <template #default="{ row }">${{ row.amount }}</template>
        </el-table-column>
        <el-table-column prop="paidAmount" label="Paid" width="120">
          <template #default="{ row }">${{ row.paidAmount }}</template>
        </el-table-column>
        <el-table-column prop="status" label="Status" width="100">
          <template #default="{ row }">
            <el-tag :type="['warning','success','danger'][row.status]" size="small">
              {{ ['Unpaid','Paid','Refunded'][row.status] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="payTime" label="Pay Time" width="180">
          <template #default="{ row }">{{ row.payTime ? new Date(row.payTime).toLocaleString() : '-' }}</template>
        </el-table-column>
        <el-table-column label="Actions" width="220" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 0" size="small" type="success" @click="handlePay(row.id)">Pay</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row.id)">Delete</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="page" v-model:page-size="size" :total="total"
        :page-sizes="[10,20,50]" layout="total, sizes, prev, pager, next"
        @current-change="fetchData" @size-change="fetchData"
        style="margin-top:16px; justify-content: flex-end" />
    </el-card>

    <el-dialog v-model="dialogVisible" title="Add Bill" width="500px" @close="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="130px">
        <el-form-item label="Patient" prop="patientId">
          <el-input v-model="form.patientId" placeholder="Patient ID" type="number" />
        </el-form-item>
        <el-form-item label="Prescription ID">
          <el-input v-model="form.prescriptionId" placeholder="Optional" type="number" />
        </el-form-item>
        <el-form-item label="Amount" prop="amount">
          <el-input-number v-model="form.amount" :min="0.01" :precision="2" />
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
import { getBillPage, createBill, payBill, deleteBill } from '../../api/bill'

const loading = ref(false), tableData = ref([]), total = ref(0), page = ref(1), size = ref(10), statusFilter = ref(null)
const dialogVisible = ref(false), submitting = ref(false), formRef = ref(null)
const form = reactive({ patientId: null, prescriptionId: null, amount: 0 })
const rules = {
  patientId: [{ required: true, message: 'Required', trigger: 'blur' }],
  amount: [{ required: true, message: 'Required', trigger: 'blur' }]
}

async function fetchData() {
  loading.value = true
  try {
    const res = await getBillPage({ page: page.value, size: size.value, status: statusFilter.value })
    tableData.value = res.records; total.value = res.total
  } finally { loading.value = false }
}

function openDialog() { resetForm(); dialogVisible.value = true }
function resetForm() { formRef.value?.resetFields(); Object.assign(form, { patientId: null, prescriptionId: null, amount: 0 }) }

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    await createBill(form)
    ElMessage.success('Created')
    dialogVisible.value = false; fetchData()
  } finally { submitting.value = false }
}

async function handlePay(id) {
  await ElMessageBox.confirm('Confirm payment?', 'Confirm', { type: 'warning' })
  await payBill(id)
  ElMessage.success('Payment completed')
  fetchData()
}

async function handleDelete(id) {
  await ElMessageBox.confirm('Delete this bill?', 'Confirm', { type: 'warning' })
  await deleteBill(id); ElMessage.success('Deleted'); fetchData()
}

onMounted(fetchData)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; margin-bottom: 16px; }
</style>
