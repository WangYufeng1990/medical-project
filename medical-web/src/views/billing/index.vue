<template>
  <div>
    <el-card>
      <div class="toolbar">
        <div>
          <el-select v-model="statusFilter" placeholder="Status" clearable style="width:150px" @change="fetchData">
            <el-option value="DRAFT" label="Draft" />
            <el-option value="SUBMITTED" label="Submitted" />
            <el-option value="PENDING" label="Pending" />
            <el-option value="PAID" label="Paid" />
            <el-option value="DENIED" label="Denied" />
          </el-select>
        </div>
        <el-button type="primary" @click="openDialog()">Add Bill</el-button>
      </div>
      <el-table :data="tableData" border stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="100" />
        <el-table-column prop="patientName" label="Patient" width="120" />
        <el-table-column prop="totalCharge" label="Amount" width="120">
          <template #default="{ row }">${{ row.totalCharge }}</template>
        </el-table-column>
        <el-table-column prop="patientPaidAmount" label="Paid" width="120">
          <template #default="{ row }">${{ row.patientPaidAmount }}</template>
        </el-table-column>
        <el-table-column prop="claimStatus" label="Status" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.claimStatus)" size="small">
              {{ row.claimStatus }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="payTime" label="Pay Time" width="180">
          <template #default="{ row }">{{ row.payTime ? new Date(row.payTime).toLocaleString() : '-' }}</template>
        </el-table-column>
        <el-table-column label="Actions" width="260" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.claimStatus === 'SUBMITTED' || row.claimStatus === 'DRAFT'" size="small" type="success" @click="openPayDialog(row)">Pay</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row.id)">Delete</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="page" v-model:page-size="size" :total="total"
        :page-sizes="[10,20,50]" layout="total, sizes, prev, pager, next"
        @current-change="fetchData" @size-change="fetchData"
        style="margin-top:16px; justify-content: flex-end" />
    </el-card>

    <!-- Add Bill Dialog -->
    <el-dialog v-model="dialogVisible" title="Add Bill" width="500px" @close="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="130px">
        <el-form-item label="Patient" prop="patientId">
          <el-input v-model="form.patientId" placeholder="Patient ID" type="number" />
        </el-form-item>
        <el-form-item label="Prescription ID">
          <el-input v-model="form.prescriptionId" placeholder="Optional" type="number" />
        </el-form-item>
        <el-form-item label="Total Charge" prop="totalCharge">
          <el-input-number v-model="form.totalCharge" :min="0.01" :precision="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">Cancel</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">Save</el-button>
      </template>
    </el-dialog>

    <!-- Pay Bill Dialog -->
    <el-dialog v-model="payDialogVisible" title="Pay Bill" width="400px">
      <el-form ref="payFormRef" :model="payForm" :rules="payRules" label-width="140px">
        <el-form-item label="Payment Amount" prop="paymentAmount">
          <el-input-number v-model="payForm.paymentAmount" :min="0.01" :precision="2" />
        </el-form-item>
        <el-form-item label="Payment Method" prop="paymentMethod">
          <el-select v-model="payForm.paymentMethod">
            <el-option value="CREDIT_CARD" label="Credit Card" />
            <el-option value="DEBIT_CARD" label="Debit Card" />
            <el-option value="CASH" label="Cash" />
            <el-option value="CHECK" label="Check" />
            <el-option value="HSA" label="HSA" />
            <el-option value="INSURANCE" label="Insurance" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="payDialogVisible = false">Cancel</el-button>
        <el-button type="primary" @click="handlePaySubmit" :loading="paying">Pay</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getBillPage, createBill, payBill, deleteBill } from '../../api/bill'

const loading = ref(false), tableData = ref([]), total = ref(0), page = ref(1), size = ref(10), statusFilter = ref<any>(null)
const dialogVisible = ref(false), submitting = ref(false), formRef = ref<any>(null)
const payDialogVisible = ref(false), paying = ref(false), payFormRef = ref<any>(null), payingBillId = ref<any>(null)
const form = reactive({ patientId: null, prescriptionId: null, totalCharge: 0 })
const payForm = reactive({ paymentAmount: 0, paymentMethod: 'CREDIT_CARD' })
const rules = {
  patientId: [{ required: true, message: 'Required', trigger: 'blur' }],
  totalCharge: [{ required: true, message: 'Required', trigger: 'blur' }]
}
const payRules = {
  paymentAmount: [{ required: true, message: 'Required', trigger: 'blur' }],
  paymentMethod: [{ required: true, message: 'Required', trigger: 'change' }]
}

function statusTagType(status) {
  const map = { DRAFT: 'info', SUBMITTED: 'warning', PENDING: 'warning', PAID: 'success', DENIED: 'danger' }
  return map[status] || 'info'
}

async function fetchData() {
  loading.value = true
  try {
    const res = await getBillPage({ page: page.value, size: size.value, claimStatus: statusFilter.value })
    tableData.value = res.records; total.value = res.total
  } finally { loading.value = false }
}

function openDialog() { resetForm(); dialogVisible.value = true }
function resetForm() { formRef.value?.resetFields(); Object.assign(form, { patientId: null, prescriptionId: null, totalCharge: 0 }) }

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

function openPayDialog(row) {
  payingBillId.value = row.id
  payForm.paymentAmount = row.balanceDue || 0
  payForm.paymentMethod = 'CREDIT_CARD'
  payDialogVisible.value = true
}

async function handlePaySubmit() {
  const valid = await payFormRef.value?.validate().catch(() => false)
  if (!valid) return
  paying.value = true
  try {
    await payBill(payingBillId.value, payForm)
    ElMessage.success('Payment completed')
    payDialogVisible.value = false; fetchData()
  } finally { paying.value = false }
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
