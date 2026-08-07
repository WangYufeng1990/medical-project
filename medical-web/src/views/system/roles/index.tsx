import { useState, FormEvent } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { getRolePage, createRole, updateRole, deleteRole } from '../../../api/role'
import { RoleForm, RoleVO } from '../../../types/entities'
import { PAGE_SIZE } from '../../../utils/labels'
import { useConfirm } from '../../../utils/ConfirmDialog'
import styles from '../../shared.module.css'
const ef: RoleForm = { roleName: '', roleCode: '', description: '', status: 1 }
const ROLE_FIELDS = ['roleName', 'roleCode', 'description'] as const

export default function Roles() {
  const queryClient = useQueryClient()
  const { confirm } = useConfirm()
  const [page, setPage] = useState(1)
  const [showForm, setShowForm] = useState(false)
  const [editId, setEditId] = useState<number | null>(null)
  const [form, setForm] = useState<RoleForm>({ ...ef })

  const { data: pageData, isLoading } = useQuery({
    queryKey: ['roles', 'list', { page, size: PAGE_SIZE }],
    queryFn: () => getRolePage({ page, size: PAGE_SIZE }),
  })
  const data = pageData?.records ?? []
  const total = pageData?.total ?? 0
  const onError = (err: Error) => alert(err?.message || 'Operation failed')

  const saveMutation = useMutation({
    mutationFn: (params: { id?: number; data: RoleForm }) =>
      params.id != null ? updateRole(params.id, params.data) : createRole(params.data),
    onSuccess: () => { setShowForm(false); queryClient.invalidateQueries({ queryKey: ['roles'] }) },
    onError,
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => deleteRole(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['roles'] }),
    onError,
  })

  const openForm = (row?: RoleVO) => { if (row) { setEditId(row.id); setForm({ ...ef, ...row, status: row.status ?? 1 } as RoleForm) } else { setEditId(null); setForm({ ...ef }) }; setShowForm(true) }

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault()
    saveMutation.mutate(editId != null ? { id: editId, data: form } : { data: form })
  }

  if (isLoading) return <p style={{ color: '#909399' }}>Loading...</p>
  return (<div>
    <h2>Roles</h2>
    <button className={styles.btnPrimary} onClick={() => openForm()}>+ Add</button>
    <table className={styles.table}><thead><tr><th>ID</th><th>Name</th><th>Code</th><th></th></tr></thead>
      <tbody>{data.map(r => (<tr key={r.id} className={styles.clickableRow} onClick={() => openForm(r)}><td>{r.id}</td><td>{r.roleName}</td><td>{r.roleCode}</td>
        <td onClick={e => e.stopPropagation()}><button className={styles.btnSm} onClick={() => openForm(r)}>Edit</button>
          <button className={styles.btnSmDanger} onClick={async () => { if (await confirm('Delete?')) deleteMutation.mutate(r.id) }}>Del</button></td></tr>))}
        {data.length === 0 && <tr><td colSpan={4} style={{ textAlign: 'center', color: '#909399', padding: 20 }}>No roles found</td></tr>}
        </tbody></table>
    <div className={styles.pagination}><button disabled={page<=1} onClick={()=>setPage(p=>p-1)}>Prev</button><span>Page {page}</span><button disabled={page*PAGE_SIZE>=total} onClick={()=>setPage(p=>p+1)}>Next</button></div>
    {showForm && <div className={styles.modalOverlay} onClick={() => setShowForm(false)}><div className={styles.modal} onClick={e => e.stopPropagation()}><h3>{editId ? 'Edit' : 'Add'} Role</h3>
      <form onSubmit={handleSubmit}>
        {ROLE_FIELDS.map(f => (<div key={f} className={styles.formGroup} style={{marginBottom:8}}><label>{f}</label><input value={form[f] ?? ''} onChange={e=>setForm(prev => ({...prev,[f]:e.target.value}) as RoleForm)} /></div>))}
        <div className={styles.formActions}><button type="button" className={styles.btnSm} onClick={()=>setShowForm(false)}>Cancel</button><button type="submit" className={styles.btnPrimary}>Save</button></div></form></div></div>}
  </div>)
}
