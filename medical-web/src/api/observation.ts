import request from './request'

export const getObservations = (patientId: number, loinc: string) =>
  request.get(`/patients/${patientId}/observations`, { params: { loinc } })

export const getLoincCatalog = () => request.get('/loinc/catalog')

export const getLoincPanel = (parentCode: string) => request.get(`/loinc/panel/${parentCode}`)
