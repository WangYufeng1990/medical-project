import request from './request'

export const downloadPatientsCsv = () =>
  request.get('/export/patients', { responseType: 'blob' }).then(res => {
    const url = URL.createObjectURL(new Blob([res.data]))
    const a = document.createElement('a'); a.href = url; a.download = 'patients.csv'; a.click()
    URL.revokeObjectURL(url)
  })

export const downloadBillsCsv = () =>
  request.get('/export/bills', { responseType: 'blob' }).then(res => {
    const url = URL.createObjectURL(new Blob([res.data]))
    const a = document.createElement('a'); a.href = url; a.download = 'bills.csv'; a.click()
    URL.revokeObjectURL(url)
  })
