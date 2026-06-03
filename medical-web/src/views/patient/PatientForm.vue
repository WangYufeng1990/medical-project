<template>
  <div class="patient-form">
    <el-card>
      <div class="toolbar">
        <el-input v-model="keyword" placeholder="Search by MRN" clearable style="width:260px"
          @clear="fetchData" @keyup.enter="fetchData" />
        <el-button type="primary" @click="openDialog(undefined)">Add Patient</el-button>
      </div>

      <!-- Patient Table with Masked Display -->
      <el-table :data="tableData" border stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="mrn" label="MRN" width="130" />
        <el-table-column label="Name" width="140">
          <template #default="{ row }">{{ row.name ?? '[DECRYPT_FAILED]' }}</template>
        </el-table-column>
        <el-table-column label="SSN" width="120">
          <template #default="{ row }">
            <span v-if="row.ssnLast4 === '[DECRYPT_FAILED]'"
              style="color:#909399;font-style:italic">Data Unavailable</span>
            <span v-else>{{ row.ssnLast4 ?? 'N/A' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="sexAtBirth" label="Sex" width="60" />
        <el-table-column prop="dateOfBirth" label="DOB" width="110" />
        <el-table-column label="Phone" width="140">
          <template #default="{ row }">
            <span v-if="row.phoneMobile === '[DECRYPT_FAILED]'" class="phi-unavailable">Data Unavailable</span>
            <span v-else>{{ maskPhone(row.phoneMobile) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="Email" min-width="160">
          <template #default="{ row }">
            <span v-if="row.email === '[DECRYPT_FAILED]'" class="phi-unavailable">Data Unavailable</span>
            <span v-else>{{ maskEmail(row.email) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="Actions" width="220" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openDialog(row)">Edit</el-button>
            <el-button size="small" type="success" @click="openCase(row.id)">FHIR Case</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row.id)">Delete</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="page" v-model:page-size="size" :total="total"
        :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next"
        @current-change="fetchData" @size-change="fetchData"
        style="margin-top:16px;justify-content:flex-end" />
    </el-card>

    <!-- CRUD Dialog -->
    <el-dialog v-model="dialogVisible"
      :title="editId ? 'Edit Patient' : 'Add Patient'" width="800px"
      @close="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="180px">
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
              <el-date-picker v-model="form.dateOfBirth" type="date"
                placeholder="Select date" value-format="YYYY-MM-DD" style="width:100%" />
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
              <el-select v-model="form.sexAtBirth" style="width:100%">
                <el-option value="M" label="Male" />
                <el-option value="F" label="Female" />
                <el-option value="U" label="Unknown" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="Gender Identity">
              <el-input v-model="form.genderIdentity"
                placeholder="Male / Female / Non-binary / Transgender" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="Race (OMB)">
              <el-select v-model="form.race" style="width:100%">
                <el-option value="White" label="White" />
                <el-option value="Black or African American" label="Black or African American" />
                <el-option value="Asian" label="Asian" />
                <el-option value="American Indian or Alaska Native"
                  label="American Indian or Alaska Native" />
                <el-option value="Native Hawaiian or Other Pacific Islander"
                  label="Native Hawaiian or Other Pacific Islander" />
                <el-option value="Other" label="Other" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="Ethnicity">
              <el-select v-model="form.ethnicity" style="width:100%">
                <el-option value="Hispanic or Latino" label="Hispanic or Latino" />
                <el-option value="Not Hispanic or Latino" label="Not Hispanic or Latino" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="Preferred Language">
              <el-select v-model="form.preferredLanguage" style="width:100%">
                <el-option value="en" label="English" />
                <el-option value="es" label="Spanish" />
                <el-option value="zh" label="Chinese" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="Marital Status">
              <el-select v-model="form.maritalStatus" style="width:100%">
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
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="Phone (Work)">
              <el-input v-model="form.phoneWork" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="Email">
              <el-input v-model="form.email" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">Structured Address</el-divider>
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
              <el-input v-model="form.state" maxlength="2" placeholder="IL" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="ZIP Code">
              <el-input v-model="form.zipCode" maxlength="10" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">Emergency Contact</el-divider>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="Contact Name">
              <el-input v-model="form.emergencyContactName" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="Contact Phone">
              <el-input v-model="form.emergencyContactPhone" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="Relation">
          <el-select v-model="form.emergencyContactRelation" style="width:100%">
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

        <el-divider content-position="left">Clinical</el-divider>
        <el-form-item label="Primary Care Provider">
          <el-input v-model="form.primaryCareProvider" />
        </el-form-item>
        <el-form-item label="Medical History">
          <el-input v-model="form.medicalHistory" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="Allergies">
          <el-input v-model="form.allergies" placeholder="Penicillin; Shellfish" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">Cancel</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">Save</el-button>
      </template>
    </el-dialog>

    <!-- FHIR Case Dialog -->
    <el-dialog v-model="caseVisible" title="FHIR R4 Patient Case" width="900px" top="5vh">
      <div v-if="caseLoading" v-loading="caseLoading" style="min-height:200px" />
      <div v-else-if="caseError" class="fhir-error">{{ caseError }}</div>
      <div v-else class="fhir-bundle">
        <el-descriptions v-if="fhirPatient" :column="2" border size="small" title="Patient">
          <el-descriptions-item label="MRN">{{ fhirPatient.mrn }}</el-descriptions-item>
          <el-descriptions-item label="Name">{{ fhirPatient.name }}</el-descriptions-item>
          <el-descriptions-item label="DOB">{{ fhirPatient.birthDate }}</el-descriptions-item>
          <el-descriptions-item label="Sex">{{ fhirPatient.gender }}</el-descriptions-item>
        </el-descriptions>
        <div v-if="fhirConditions.length" style="margin-top:16px">
          <h4>Conditions</h4>
          <el-tag v-for="c in fhirConditions" :key="c.id" type="warning" style="margin:4px">
            {{ c.code ?? 'Data Unavailable' }}
          </el-tag>
        </div>
        <div v-if="fhirAllergies.length" style="margin-top:16px">
          <h4>Allergies</h4>
          <el-tag v-for="a in fhirAllergies" :key="a.id" type="danger" style="margin:4px">
            {{ a.code ?? 'Data Unavailable' }}
          </el-tag>
        </div>
        <div v-if="fhirEncounters.length" style="margin-top:16px">
          <h4>Encounters ({{ fhirEncounters.length }})</h4>
          <el-table :data="fhirEncounters" size="small" max-height="300">
            <el-table-column prop="date" label="Date" width="160" />
            <el-table-column prop="type" label="Type" />
            <el-table-column prop="status" label="Status" width="100" />
          </el-table>
        </div>
        <div v-if="fhirMedications.length" style="margin-top:16px">
          <h4>Medication Requests ({{ fhirMedications.length }})</h4>
          <el-table :data="fhirMedications" size="small" max-height="300">
            <el-table-column prop="medication" label="Drug" />
            <el-table-column prop="dosage" label="Dosage" />
            <el-table-column prop="status" label="Status" width="100" />
          </el-table>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getPatientPage, getPatientById, createPatient, updatePatient, deletePatient,
  getPatientCase
} from '@/api/patient'

// ── types ──────────────────────────────────────────────
interface PatientForm {
  name: string; mrn: string; ssn: string; dateOfBirth: string | null
  sexAtBirth: string; genderIdentity: string
  race: string; ethnicity: string; preferredLanguage: string; maritalStatus: string
  phoneMobile: string; phoneHome: string; phoneWork: string; email: string
  addressLine1: string; addressLine2: string; city: string; state: string; zipCode: string
  emergencyContactName: string; emergencyContactPhone: string; emergencyContactRelation: string
  insurancePayer: string; insuranceMemberId: string; insuranceGroupNumber: string
  primaryCareProvider: string; medicalHistory: string; allergies: string
  patientStatus: string
}

interface FhirPatient { mrn: string; name: string; birthDate: string; gender: string }
interface FhirEntry { id: string; date?: string; type?: string; status?: string; medication?: string; dosage?: string; code?: string }

const DECRYPT_MARKER = '[DECRYPT_FAILED]'
const PHI_UNAVAILABLE = 'Data Unavailable (Compliance Protection)'

// ── state ──────────────────────────────────────────────
const loading = ref(false); const tableData = ref<any[]>([]); const total = ref(0)
const page = ref(1); const size = ref(10); const keyword = ref('')
const dialogVisible = ref(false); const editId = ref<number | null>(null)
const submitting = ref(false); const formRef = ref<any>(null)

const caseVisible = ref(false); const caseLoading = ref(false); const caseError = ref('')
const fhirPatient = ref<FhirPatient | null>(null)
const fhirConditions = ref<FhirEntry[]>([]); const fhirAllergies = ref<FhirEntry[]>([])
const fhirEncounters = ref<FhirEntry[]>([]); const fhirMedications = ref<FhirEntry[]>([])

const emptyForm = (): PatientForm => ({
  name: '', mrn: '', ssn: '', dateOfBirth: null, sexAtBirth: '', genderIdentity: '',
  race: '', ethnicity: '', preferredLanguage: '', maritalStatus: '',
  phoneMobile: '', phoneHome: '', phoneWork: '', email: '',
  addressLine1: '', addressLine2: '', city: '', state: '', zipCode: '',
  emergencyContactName: '', emergencyContactPhone: '', emergencyContactRelation: '',
  insurancePayer: '', insuranceMemberId: '', insuranceGroupNumber: '',
  primaryCareProvider: '', medicalHistory: '', allergies: '', patientStatus: 'active'
})
const form = reactive<PatientForm>(emptyForm())

const rules = {
  name: [{ required: true, message: 'Required', trigger: 'blur' }],
  mrn: [{ required: true, message: 'Required', trigger: 'blur' }]
}

// ── masking helpers ────────────────────────────────────
function isDecryptFailed(val: string | null | undefined): boolean {
  return val === DECRYPT_MARKER
}

function maskPhone(phone: string | null | undefined): string {
  if (!phone) return 'N/A'
  if (isDecryptFailed(phone)) return PHI_UNAVAILABLE
  if (phone.length <= 4) return '****'
  return '****' + phone.slice(-4)
}

function maskEmail(email: string | null | undefined): string {
  if (!email) return 'N/A'
  if (isDecryptFailed(email)) return PHI_UNAVAILABLE
  const at = email.indexOf('@')
  if (at <= 0) return '****'
  return email.charAt(0) + '***' + email.slice(at)
}

function maskSSN(ssn: string | null | undefined): string {
  if (!ssn) return 'N/A'
  if (isDecryptFailed(ssn)) return PHI_UNAVAILABLE
  const digits = ssn.replace(/[^0-9]/g, '')
  if (digits.length <= 4) return '***-**-' + digits
  return '***-**-' + digits.slice(-4)
}

function safeField(val: string | null | undefined): string {
  if (!val) return ''
  if (isDecryptFailed(val)) return PHI_UNAVAILABLE
  return val
}

// ── data fetching ──────────────────────────────────────
async function fetchData() {
  loading.value = true
  try {
    const res = await getPatientPage({ page: page.value, size: size.value, keyword: keyword.value })
    tableData.value = res.records; total.value = res.total
  } finally { loading.value = false }
}

async function openDialog(row?: any) {
  if (row) {
    editId.value = row.id
    const detail = await getPatientById(row.id)
    Object.assign(form, {
      name: safeField(detail.name), mrn: detail.mrn || '', ssn: '',
      dateOfBirth: detail.dateOfBirth, sexAtBirth: detail.sexAtBirth || '',
      genderIdentity: detail.genderIdentity || '', race: detail.race || '',
      ethnicity: detail.ethnicity || '', preferredLanguage: detail.preferredLanguage || '',
      maritalStatus: detail.maritalStatus || '',
      phoneMobile: safeField(detail.phoneMobile), phoneHome: safeField(detail.phoneHome),
      phoneWork: safeField(detail.phoneWork), email: safeField(detail.email),
      addressLine1: safeField(detail.addressLine1), addressLine2: safeField(detail.addressLine2),
      city: safeField(detail.city), state: safeField(detail.state),
      zipCode: safeField(detail.zipCode),
      emergencyContactName: safeField(detail.emergencyContactName),
      emergencyContactPhone: safeField(detail.emergencyContactPhone),
      emergencyContactRelation: detail.emergencyContactRelation || '',
      insurancePayer: safeField(detail.insurancePayer),
      insuranceMemberId: safeField(detail.insuranceMemberId),
      insuranceGroupNumber: safeField(detail.insuranceGroupNumber),
      primaryCareProvider: safeField(detail.primaryCareProvider),
      medicalHistory: safeField(detail.medicalHistory),
      allergies: safeField(detail.allergies),
      patientStatus: detail.patientStatus || 'active'
    })
  } else { editId.value = null; resetForm() }
  dialogVisible.value = true
}

function resetForm() {
  formRef.value?.resetFields()
  Object.assign(form, emptyForm())
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
  } catch (e: any) {
    ElMessage.error(e?.message ?? 'Save failed')
  } finally { submitting.value = false }
}

async function handleDelete(id: number) {
  await ElMessageBox.confirm('Delete this patient?', 'Confirm', { type: 'warning' })
  await deletePatient(id); ElMessage.success('Deleted'); fetchData()
}

// ── FHIR Bundle ────────────────────────────────────────
async function openCase(patientId: number) {
  caseVisible.value = true; caseLoading.value = true; caseError.value = ''
  fhirPatient.value = null
  fhirConditions.value = []; fhirAllergies.value = []
  fhirEncounters.value = []; fhirMedications.value = []
  try {
    const bundle = await getPatientCase(patientId)
    parseFhirBundle(bundle)
  } catch (e: any) {
    caseError.value = e?.message ?? 'Failed to load FHIR case data'
  } finally { caseLoading.value = false }
}

function parseFhirBundle(bundle: any) {
  if (!bundle || bundle.resourceType !== 'Bundle' || !Array.isArray(bundle.entry)) {
    caseError.value = 'Invalid FHIR Bundle response'; return
  }
  for (const entry of bundle.entry) {
    const r = entry.resource
    if (!r) continue
    try {
      switch (r.resourceType) {
        case 'Patient':
          fhirPatient.value = {
            mrn: r.identifier?.find((i: any) =>
              i.system === 'http://hl7.org/fhir/sid/us-mrn')?.value ?? 'N/A',
            name: r.name?.[0]?.family ?? 'N/A',
            birthDate: r.birthDate ?? 'N/A',
            gender: r.gender ?? 'N/A'
          }
          break
        case 'Condition':
          fhirConditions.value.push({
            id: r.id ?? '',
            code: r.code?.text ?? r.code?.coding?.[0]?.display ?? 'Unknown'
          })
          break
        case 'AllergyIntolerance':
          fhirAllergies.value.push({
            id: r.id ?? '',
            code: r.code?.text ?? r.code?.coding?.[0]?.display ?? 'Unknown'
          })
          break
        case 'Encounter':
          fhirEncounters.value.push({
            id: r.id ?? '',
            date: r.period?.start ?? 'N/A',
            type: r.type?.[0]?.text ?? 'N/A',
            status: r.status ?? 'N/A'
          })
          break
        case 'MedicationRequest':
          fhirMedications.value.push({
            id: r.id ?? '',
            medication: r.medicationCodeableConcept?.text ??
              r.medicationCodeableConcept?.coding?.[0]?.display ?? 'N/A',
            dosage: r.dosageInstruction?.[0]?.text ?? 'N/A',
            status: r.status ?? 'N/A'
          })
          break
      }
    } catch {
      // skip malformed entry — defensive against partial decrypt failures
    }
  }
}

onMounted(fetchData)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; margin-bottom: 16px; }
.phi-unavailable { color: #e6a23c; font-style: italic; cursor: help; }
.fhir-error { color: #f56c6c; padding: 24px; text-align: center; }
.fhir-bundle h4 { margin: 8px 0 4px; color: #303133; }
</style>
