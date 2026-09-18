import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { http } from '../../../api/patientRequest'
import { PageResult } from '../../../types/common'
import { ObservationVO, LoincEntry } from '../../../types/entities'
import { readPatientInfo } from '../../../utils/auth'
import LabResultsTable, { LAB_PAGE_SIZE, LabFlagLegend } from '../../lab/LabResultsTable'

export default function PatientLab() {
  const info = readPatientInfo()
  const [loincFilter, setLoincFilter] = useState('')
  const [page, setPage] = useState(1)

  const { data: catalog } = useQuery({
    queryKey: ['loinc', 'catalog'],
    queryFn: () => http.get<LoincEntry[]>('/loinc/catalog'),
  })

  // Table mode: server-side pagination. Trend mode: full history of one test.
  const { data: pageData, isLoading: pageLoading } = useQuery({
    queryKey: ['me', 'lab', 'list', { page }],
    queryFn: () => http.get<PageResult<ObservationVO>>('/patient/me/observations', { params: { page, size: LAB_PAGE_SIZE } }),
    enabled: !loincFilter,
  })
  const { data: trendData, isLoading: trendLoading } = useQuery({
    queryKey: ['me', 'lab', 'trend', { loinc: loincFilter }],
    queryFn: () => http.get<ObservationVO[]>('/patient/me/observations/trend', { params: { loinc: loincFilter } }),
    enabled: !!loincFilter,
  })
  const isLoading = loincFilter ? trendLoading : pageLoading
  const allObservations = (loincFilter ? trendData : pageData?.records) ?? []
  const total = pageData?.total ?? 0

  const handleLoincChange = (code: string) => {
    setLoincFilter(code)
    setPage(1)
  }

  return (
    <div>
      <h2 style={{ marginBottom: 20 }}>{info.name || 'Patient'} — Lab Results</h2>

      <div style={{ marginBottom: 16, display: 'flex', gap: 12, alignItems: 'center' }}>
        <label style={{ fontSize: 13, color: '#606266' }}>Filter by test:</label>
        <select value={loincFilter} onChange={e => handleLoincChange(e.target.value)}
          style={{ padding: '6px 10px', border: '1px solid #dcdfe6', borderRadius: 4, fontSize: 13 }}>
          <option value="">All tests</option>
          {(catalog ?? []).map(c => <option key={c.loincCode} value={c.loincCode}>{c.display}</option>)}
        </select>
        <LabFlagLegend />
      </div>

      <LabResultsTable
        observations={allObservations}
        isLoading={isLoading}
        trendMode={!!loincFilter}
        trendLabel={loincFilter}
        page={page}
        total={total}
        onPageChange={setPage}
        emptyMessage="No lab results found."
      />
    </div>
  )
}
