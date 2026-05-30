<template>
  <div>
    <el-card>
      <div class="toolbar">
        <el-input v-model="keyword" placeholder="Search by MRN or email" clearable style="width:260px" @clear="fetchData" @keyup.enter="fetchData" />
        <el-button type="primary" @click="openDialog()">Add Patient</el-button>
      </div>
      <el-table :data="tableData" border stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="mrn" label="MRN" width="130" />
        <el-table-column prop="name" label="Name" width="140" />
        <el-table-column prop="sexAtBirth" label="Sex" width="70" />
        <el-table-column prop="dateOfBirth" label="DOB" width="120" />
        <el-table-column prop="phoneMobile" label="Phone" width="140" />
        <el-table-column prop="email" label="Email" />
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

    <el-dialog v-model="dialogVisible" :title="editId ? 'Edit Patient' : 'Add Patient'" width="700px" @close="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="160px">
        <el-divider content-position="left">Identity</el-divider>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="MRN" prop="mrn">
              <el-input v-model="form.mrn" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="Name" prop="name">
              <el-input v-model="form.name" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="Date of Birth">
              <el-date-picker v-model="form.dateOfBirth" type="date" placeholder="Select date" value-format="YYYY-MM-DD" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="SSN">
              <el-input v-model="form.ssn" placeholder="xxx-xx-xxxx" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="Sex at Birth">
              <el-select v-model="form.sexAtBirth">
                <el-option value="M" label="Male" />
                <el-option value="F" label="Female" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="Gender Identity">
              <el-input v-model="form.genderIdentity" placeholder="Male/Female/Non-binary..." />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="Race">
              <el-select v-model="form.race">
                <el-option value="White" label="White" />
                <el-option value="Black or African American" label="Black or African American" />
                <el-option value="Asian" label="Asian" />
                <el-option value="American Indian or Alaska Native" label="American Indian or Alaska Native" />
                <el-option value="Native Hawaiian or Other Pacific Islander" label="Native Hawaiian or Other Pacific Islander" />
                <el-option value="Other" label="Other" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="Ethnicity">
              <el-select v-model="form.ethnicity">
                <el-option value="Hispanic or Latino" label="Hispanic or Latino" />
                <el-option value="Not Hispanic or Latino" label="Not Hispanic or Latino" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="Preferred Language">
              <el-select v-model="form.preferredLanguage">
                <el-option value="en" label="English" />
                <el-option value="es" label="Spanish" />
                <el-option value="zh" label="Chinese" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="Marital Status">
              <el-select v-model="form.maritalStatus">
                <el-option value="Single" label="Single" />
                <el-option value="Married" label="Married" />
                <el-option value="Divorced" label="Divorced" />
                <el-option value="Widowed" label="Widowed" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">Contact</el-divider>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="Phone (Mobile)">
              <el-input v-model="form.phoneMobile" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="Phone (Home)">
              <el-input v-model="form.phoneHome" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="Email">
          <el-input v-model="form.email" />
        </el-form-item>

        <el-divider content-position="left">Address</el-divider>
        <el-form-item label="Address Line 1">
          <el-input v-model="form.addressLine1" />
        </el-form-item>
        <el-form-item label="Address Line 2">
          <el-input v-model="form.addressLine2" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="City">
              <el-input v-model="form.city" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="State">
              <el-input v-model="form.state" maxlength="2" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="ZIP Code">
              <el-input v-model="form.zipCode" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">Emergency Contact</el-divider>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="Emergency Contact">
              <el-input v-model="form.emergencyContactName" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="Phone">
              <el-input v-model="form.emergencyContactPhone" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="Relation">
          <el-select v-model="form.emergencyContactRelation">
            <el-option value="Spouse" label="Spouse" />
            <el-option value="Parent" label="Parent" />
            <el-option value="Child" label="Child" />
            <el-option value="Sibling" label="Sibling" />
            <el-option value="Other" label="Other" />
          </el-select>
        </el-form-item>

        <el-divider content-position="left">Insurance</el-divider>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="Insurance Payer">
              <el-input v-model="form.insurancePayer" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="Member ID">
              <el-input v-model="form.insuranceMemberId" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="Group Number">
          <el-input v-model="form.insuranceGroupNumber" />
        </el-form-item>

        <el-divider content-position="left">Medical</el-divider>
        <el-form-item label="Primary Care Provider">
          <el-input v-model="form.primaryCareProvider" />
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
const form = reactive({
  name: '', mrn: '', ssn: '', dateOfBirth: null, sexAtBirth: '', genderIdentity: '',
  race: '', ethnicity: '', preferredLanguage: '', maritalStatus: '',
  phoneMobile: '', phoneHome: '', email: '',
  addressLine1: '', addressLine2: '', city: '', state: '', zipCode: '',
  emergencyContactName: '', emergencyContactPhone: '', emergencyContactRelation: '',
  insurancePayer: '', insuranceMemberId: '', insuranceGroupNumber: '',
  primaryCareProvider: '', medicalHistory: '', allergies: '', patientStatus: 'active'
})
const rules = {
  name: [{ required: true, message: 'Required', trigger: 'blur' }],
  mrn: [{ required: true, message: 'Required', trigger: 'blur' }]
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
      name: detail.name || '', mrn: detail.mrn || '', ssn: detail.ssnLast4 ? '' : detail.ssn || '',
      dateOfBirth: detail.dateOfBirth, sexAtBirth: detail.sexAtBirth || '',
      genderIdentity: detail.genderIdentity || '', race: detail.race || '',
      ethnicity: detail.ethnicity || '', preferredLanguage: detail.preferredLanguage || '',
      maritalStatus: detail.maritalStatus || '',
      phoneMobile: detail.phoneMobile || '', phoneHome: detail.phoneHome || '', email: detail.email || '',
      addressLine1: detail.addressLine1 || '', addressLine2: detail.addressLine2 || '',
      city: detail.city || '', state: detail.state || '', zipCode: detail.zipCode || '',
      emergencyContactName: detail.emergencyContactName || '',
      emergencyContactPhone: detail.emergencyContactPhone || '',
      emergencyContactRelation: detail.emergencyContactRelation || '',
      insurancePayer: detail.insurancePayer || '',
      insuranceMemberId: detail.insuranceMemberId || '',
      insuranceGroupNumber: detail.insuranceGroupNumber || '',
      primaryCareProvider: detail.primaryCareProvider || '',
      medicalHistory: detail.medicalHistory || '', allergies: detail.allergies || '',
      patientStatus: detail.patientStatus || 'active'
    })
  } else { editId.value = null; resetForm() }
  dialogVisible.value = true
}

function resetForm() {
  formRef.value?.resetFields()
  Object.assign(form, {
    name: '', mrn: '', ssn: '', dateOfBirth: null, sexAtBirth: '', genderIdentity: '',
    race: '', ethnicity: '', preferredLanguage: '', maritalStatus: '',
    phoneMobile: '', phoneHome: '', email: '',
    addressLine1: '', addressLine2: '', city: '', state: '', zipCode: '',
    emergencyContactName: '', emergencyContactPhone: '', emergencyContactRelation: '',
    insurancePayer: '', insuranceMemberId: '', insuranceGroupNumber: '',
    primaryCareProvider: '', medicalHistory: '', allergies: '', patientStatus: 'active'
  })
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
