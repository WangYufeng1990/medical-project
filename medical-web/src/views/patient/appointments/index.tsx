import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import patientRequest from '../../../api/patientRequest'
import { useConfirm } from '../../../utils/ConfirmDialog'
import { APPOINTMENT_STATUS, PAGE_SIZE, APPOINTMENT_STATUS_COLOR } from '../../../utils/labels'
import styles from '../../shared.module.css'

export default function PatientAppointments() {
  const { confirm } = useConfirm()
  const queryClient = useQueryClient()
  const [page, setPage] = useState(1)

  const { data: pageData } = useQuery({
    queryKey: ['me', 'appointments', 'list', { page, size: PAGE_SIZE }],
    queryFn: () => patientRequest.get(`/patient/me/appointments?page=${page}&size=${PAGE_SIZE}`).then(r => r),
  })
  const data = pageData?.records ?? []
  const total = pageData?.total ?? 0

  const cancelMutation = useMutation({
    mutationFn: (id: number) => patientRequest.put(`/patient/me/appointments/${id}/cancel`),
    onSuccess: () => {
      setPage(1)
      queryClient.invalidateQueries({ queryKey: ['me', 'appointments'] })
    },
  })

  const canCancel = (s: number) => s !== 2 && s !== 3 && s !== 4

  const handleCancel = async (id: number) => {
    if (await confirm('Cancel this appointment?')) cancelMutation.mutate(id)
  }

  return (<div>
    <h2 style={{ marginBottom: 20 }}>My Appointments</h2>
    <table className={styles.table}>
      <thead><tr><th>Date</th><th>Doctor</th><th>Duration</th><th>Visit Type</th><th>Department</th><th>Status</th><th></th></tr></thead>
      <tbody>{data.map(r => (
        <tr key={r.id}>
          <td>{r.appointmentTime}</td><td>{r.doctorName}</td><td>{r.duration}m</td><td>{r.visitType}</td><td>{r.department}</td>
          <td><span style={{ color: APPOINTMENT_STATUS_COLOR[r.status] ?? '#909399', fontWeight: 600 }}>{APPOINTMENT_STATUS[r.status] ?? r.status}</span></td>
          <td>
            {canCancel(r.status) && (
              <button className={styles.btnSmDanger} disabled={cancelMutation.isPending && cancelMutation.variables === r.id}
                onClick={() => handleCancel(r.id)}>
                {cancelMutation.isPending && cancelMutation.variables === r.id ? '...' : 'Cancel'}
              </button>
            )}
          </td>
        </tr>
      ))}</tbody>
    </table>
    <div className={styles.pagination}><button disabled={page<=1} onClick={()=>setPage(p=>p-1)}>Prev</button><span>Page {page}</span><button disabled={page*PAGE_SIZE>=total} onClick={()=>setPage(p=>p+1)}>Next</button></div>
  </div>)
}
