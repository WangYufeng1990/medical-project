import { http, BlobDownload } from './request'

function saveBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  a.remove()
  setTimeout(() => URL.revokeObjectURL(url), 0)
}

// The columns, the PHI masking (phone/email/claim number), the CSV escaping and
// formula-injection guard, the export rate limit and the EXPORT_* audit entry
// all live in the backend streaming export — never rebuild the CSV here.
async function downloadCsv(path: string, fallbackName: string) {
  const { blob, filename } = await http.get<BlobDownload>(path, { responseType: 'blob' })
  // The server omits a BOM; Excel needs one to treat the file as UTF-8.
  const utf8 = new Blob(['\uFEFF', blob], { type: 'text/csv;charset=UTF-8' })
  saveBlob(utf8, filename ?? fallbackName)
}

export const downloadPatientsCsv = () => downloadCsv('/export/patients', 'patients.csv')

export const downloadBillsCsv = () => downloadCsv('/export/bills', 'bills.csv')
