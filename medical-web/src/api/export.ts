import request from './request'

function csv(v: any) {
  if (v == null) return ''
  const s = String(v)
  if (s.includes(',') || s.includes('"') || s.includes('\n')) return `"${s.replace(/"/g, '""')}"`
  return s
}

function download(filename: string, header: string, rows: string[]) {
  const bom = '﻿'
  const blob = new Blob([bom + header + '\n' + rows.join('\n')], { type: 'text/csv;charset=UTF-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a'); a.href = url; a.download = filename; a.click()
  URL.revokeObjectURL(url)
}

export const downloadPatientsCsv = async () => {
  const res = await request.get('/patients', { params: { page: 1, size: 9999 } })
  const patients = res.records ?? []
  const header = 'MRN,Name,DOB,Sex,Gender,Race,Ethnicity,Language,Phone,Email,Address,City,State,ZIP,Insurance,MedicalHistory,Allergies,Created'
  const rows = patients.map((p: any) =>
    [p.mrn, p.name, p.dateOfBirth ?? '', p.sexAtBirth, p.genderIdentity,
      p.race, p.ethnicity, p.preferredLanguage,
      p.phoneMobile, p.email,
      p.addressLine1, p.city, p.state, p.zipCode,
      p.insurancePayer, p.medicalHistory, p.allergies,
      p.createTime?.slice(0, 10) ?? ''].map(csv).join(','))
  download('patients.csv', header, rows)
}

export const downloadBillsCsv = async () => {
  const res = await request.get('/bills', { params: { page: 1, size: 9999 } })
  const bills = res.records ?? []
  const header = 'ID,PatientID,Type,Status,TotalCharge,InsAdj,InsPay,PatientResp,PatientPaid,Copay,CPT,ICD10,POS,Payer,Claim#,FilingDate,PayTime,Method,Created'
  const rows = bills.map((b: any) =>
    [b.id, b.patientId, b.billType, b.claimStatus,
      b.totalCharge, b.insuranceAdjustment, b.insurancePayment, b.patientResponsibility,
      b.patientPaidAmount, b.copayAmount,
      b.cptCodes, b.icd10Codes, b.placeOfServiceCode, b.insurancePayerName,
      b.insuranceClaimNumber ? '****' + String(b.insuranceClaimNumber).slice(-4) : '',
      b.claimFilingDate,
      b.payTime?.slice(0, 19) ?? '', b.paymentMethod,
      b.createTime?.slice(0, 10) ?? ''].map(csv).join(','))
  download('bills.csv', header, rows)
}
