import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { getPatientPage } from '../../api/patient'
import { getObservations, getObservationTrend, getLoincCatalog } from '../../api/observation'
import { LoincEntry } from '../../types/entities'
import LabResultsTable, { LAB_PAGE_SIZE, LabFlagLegend } from './LabResultsTable'

export default function LabResults() {
  const [searchParams, setSearchParams] = useSearchParams()
  const loincParam = searchParams.get('loinc') || ''
  const [selectedPatientId, setSelectedPatientId] = useState<number | null>(null)
  const [page, setPage] = useState(1)

  const { data: patients } = useQuery({
    queryKey: ['patients', 'all'],
    queryFn: () => getPatientPage({ page: 1, size: 200 }).then(r => r.records ?? []),
  })

  const { data: catalog } = useQuery({
    queryKey: ['loinc', 'catalog'],
    queryFn: getLoincCatalog,
  })

  // Table mode: server-side pagination. Trend mode: full history of one test.
  const { data: pageData, isLoading: pageLoading } = useQuery({
    queryKey: ['observations', 'list', { patientId: selectedPatientId, page }],
    queryFn: () => getObservations(selectedPatientId!, { page, size: LAB_PAGE_SIZE }),
    enabled: selectedPatientId != null && !loincParam,
  })
  const { data: trendData, isLoading: trendLoading } = useQuery({
    queryKey: ['observations', 'trend', { patientId: selectedPatientId, loinc: loincParam }],
    queryFn: () => getObservationTrend(selectedPatientId!, loincParam),
    enabled: selectedPatientId != null && !!loincParam,
  })
  const isLoading = loincParam ? trendLoading : pageLoading
  const allList = loincParam ? (trendData ?? []) : (pageData?.records ?? [])
  const total = pageData?.total ?? 0


  const title = loincParam
    ? `Lab Results — ${allList[0]?.loincDisplay ?? loincParam} Trend`
    : 'Lab Results'

  const handleLoincChange = (code: string) => {
    setSearchParams(code ? { loinc: code } : {})
    setPage(1)
  }

  return (
    <div>
      <h2 style={{ marginBottom: 20 }}>{title}</h2>

      <div style={{ marginBottom: 16, display: 'flex', gap: 12, alignItems: 'center', flexWrap: 'wrap' }}>
        <label style={{ fontSize: 13, color: '#606266' }}>Patient:</label>
        <select value={selectedPatientId ?? ''} onChange={e => {
          const v = Number(e.target.value)
          setSelectedPatientId(v)
          setSearchParams({})
          setPage(1)
        }} style={{ padding: '6px 10px', border: '1px solid #dcdfe6', borderRadius: 4, fontSize: 13 }}>
          <option value="">-- Select Patient --</option>
          {(patients ?? []).map(p => <option key={p.id} value={p.id}>{p.name} (MRN: {p.mrn})</option>)}
        </select>

        {selectedPatientId != null && (
          <>
            <label style={{ fontSize: 13, color: '#606266', marginLeft: 8 }}>Filter by test:</label>
            <select value={loincParam} onChange={e => handleLoincChange(e.target.value)}
              style={{ padding: '6px 10px', border: '1px solid #dcdfe6', borderRadius: 4, fontSize: 13 }}>
              <option value="">All tests</option>
              {(catalog ?? []).map((c: LoincEntry) => <option key={c.loincCode} value={c.loincCode}>{c.display}</option>)}
            </select>
          </>
        )}
        <LabFlagLegend />
      </div>

      <LabResultsTable
        observations={allList}
        isLoading={isLoading}
        trendMode={!!loincParam}
        trendLabel={loincParam}
        page={page}
        total={total}
        onPageChange={setPage}
        emptyMessage={selectedPatientId ? 'No lab results found for this patient.' : ''}
      />
    </div>
  )
}
