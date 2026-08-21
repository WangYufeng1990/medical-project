import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { http } from '../../../api/patientRequest'
import { PageResult } from '../../../types/common'
import { PrescriptionVO, RefillRequestVO } from '../../../types/entities'
import { PAGE_SIZE } from '../../../utils/labels'
import { useConfirm } from '../../../utils/ConfirmDialog'
import styles from '../../shared.module.css'

export default function PatientPrescriptions() {
  const [page, setPage] = useState(1)
  const queryClient = useQueryClient()
  const { confirm } = useConfirm()

  const onError = (err: Error) => alert(err?.message || 'Operation failed')

  const { data: pageData } = useQuery({
    queryKey: ['me', 'prescriptions', 'list', { page, size: PAGE_SIZE }],
    queryFn: () => http.get<PageResult<PrescriptionVO>>(`/patient/me/prescriptions?page=${page}&size=${PAGE_SIZE}`),
  })
  const data = pageData?.records ?? []
  const total = pageData?.total ?? 0

  const { data: refillRequests } = useQuery({
    queryKey: ['me', 'refill-requests'],
    queryFn: () => http.get<RefillRequestVO[]>('/patient/me/refill-requests'),
  })

  const refillMutation = useMutation({
    mutationFn: (data: { prescriptionId: number; reason?: string }) =>
      http.post('/patient/me/refill-requests', data),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['me', 'refill-requests'] }),
    onError,
  })

  const requestedIds = new Set((refillRequests ?? []).map(r => r.prescriptionId))
  const getRequestStatus = (prescriptionId: number) =>
    (refillRequests ?? []).find(r => r.prescriptionId === prescriptionId)?.status

  return (<div>
    <h2 style={{ marginBottom: 20 }}>My Prescriptions</h2>
    <table className={styles.table}><thead><tr><th>Date</th><th>Doctor</th><th>Diagnosis</th><th>ICD-10</th><th>Status</th><th></th></tr></thead>
      <tbody>{data.map(r => {
        const reqStatus = getRequestStatus(r.id)
        return (<tr key={r.id}><td>{r.prescriptionDate}</td><td>{r.doctorName}</td><td>{r.diagnosis}</td><td>{r.icd10Codes}</td>
          <td style={{ color: r.rxStatus === 'active' ? '#67C23A' : '#909399', fontWeight: 600 }}>{r.rxStatus}</td>
          <td>
            {r.rxStatus === 'active' && !requestedIds.has(r.id) && (
              <button className={styles.btnSm} disabled={refillMutation.isPending} onClick={async () => {
                if (await confirm('Request a refill for this prescription?')) refillMutation.mutate({ prescriptionId: r.id, reason: 'Refill requested by patient' })
              }}>Request Refill</button>
            )}
            {reqStatus && <span style={{ fontSize: 11, color: reqStatus === 'APPROVED' ? '#67C23A' : reqStatus === 'DENIED' ? '#F56C6C' : '#E6A23C', fontWeight: 600 }}>{reqStatus}</span>}
          </td>
        </tr>)
      })}</tbody></table>
    <div className={styles.pagination}><button disabled={page<=1} onClick={()=>setPage(p=>p-1)}>Prev</button><span>Page {page}</span><button disabled={page*PAGE_SIZE>=total} onClick={()=>setPage(p=>p+1)}>Next</button></div>
  </div>)
}
