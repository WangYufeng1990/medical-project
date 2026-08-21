import { useState, FormEvent } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { getUserPage, getUserById, createUser, updateUser, deleteUser, unlockUser } from '../../../api/user'
import { SysUserForm, SysUserVO } from '../../../types/entities'
import { PAGE_SIZE } from '../../../utils/labels'
import { tokenStore } from '../../../utils/auth'
import { useConfirm } from '../../../utils/ConfirmDialog'
import styles from '../../shared.module.css'
const emptyForm: SysUserForm = { username: '', password: '', realName: '', phone: '', email: '', gender: 1, status: 1, npi: '', stateLicenseNumber: '', licenseState: '', deaNumber: '', taxonomyCode: '', credentials: '', specialty: '' }
const USER_FIELDS = ['username','password','realName','phone','email','npi','stateLicenseNumber','licenseState','deaNumber','taxonomyCode','credentials','specialty'] as const

export default function Users() {
  const queryClient = useQueryClient()
  const { confirm } = useConfirm()
  const currentUserId = Number(tokenStore.get('userId') || '0')
  const [page, setPage] = useState(1)
  const [showForm, setShowForm] = useState(false)
  const [editId, setEditId] = useState<number | null>(null)
  const [form, setForm] = useState<SysUserForm>({ ...emptyForm })

  const { data: pageData, isLoading } = useQuery({
    queryKey: ['users', 'list', { page, size: PAGE_SIZE }],
    queryFn: () => getUserPage({ page, size: PAGE_SIZE }),
  })
  const data = pageData?.records ?? []
  const total = pageData?.total ?? 0

  const onError = (err: Error) => alert(err?.message || 'Operation failed')

  const saveMutation = useMutation({
    mutationFn: (params: { id?: number; data: SysUserForm }) =>
      params.id != null ? updateUser(params.id, params.data) : createUser(params.data),
    onSuccess: () => { setShowForm(false); queryClient.invalidateQueries({ queryKey: ['users'] }) },
    onError,
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => deleteUser(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['users'] }),
    onError,
  })

  const unlockMutation = useMutation({
    mutationFn: (id: number) => unlockUser(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['users'] }),
    onError,
  })

  const openForm = async (row?: SysUserVO) => {
    if (row) { setEditId(row.id); const d = await getUserById(row.id); setForm({ ...emptyForm, ...d, password: '' } as SysUserForm) }
    else { setEditId(null); setForm({ ...emptyForm }) }
    setShowForm(true)
  }

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault()
    saveMutation.mutate(editId != null ? { id: editId, data: form } : { data: form })
  }

  if (isLoading) return <p style={{ color: '#909399' }}>Loading...</p>

  return (<div>
    <h2 style={{ marginBottom: 20 }}>Users</h2>
    <button className={styles.btnPrimary} onClick={() => openForm()} style={{ marginBottom: 16 }}>+ Add User</button>
    <table className={styles.table}><thead><tr><th>ID</th><th>Username</th><th>Name</th><th>NPI</th><th>Last Login</th><th>Status</th><th></th></tr></thead>
      <tbody>{data.map(r => (<tr key={r.id} className={styles.clickableRow} onClick={() => openForm(r)}><td>{r.id}</td><td>{r.username}</td><td>{r.realName}</td><td>{r.npi}</td>
        <td style={{ fontSize: 12, color: '#909399' }}>{r.lastLoginTime ? r.lastLoginTime.substring(0, 16).replace('T', ' ') : 'Never'}</td>
        <td>{r.lockedUntil && new Date(r.lockedUntil) > new Date()
          ? <span style={{ color: '#F56C6C', fontWeight: 600, fontSize: 12 }}>🔒 Locked</span>
          : <span style={{ color: '#67C23A', fontSize: 12 }}>Active</span>}</td>
        <td onClick={e => e.stopPropagation()}>
          {r.lockedUntil && new Date(r.lockedUntil) > new Date() &&
            <button className={styles.btnSm} style={{ color: '#67C23A' }}
              onClick={async () => { if (await confirm('Unlock this account?')) unlockMutation.mutate(r.id) }}>Unlock</button>}
          <button className={styles.btnSm} onClick={() => openForm(r)}>Edit</button>
          {r.id !== currentUserId && <button className={styles.btnSmDanger} onClick={async () => { if (await confirm('Delete?')) deleteMutation.mutate(r.id) }}>Del</button>}</td></tr>))}
        {data.length === 0 && <tr><td colSpan={7} style={{ textAlign: 'center', color: '#909399', padding: 20 }}>No users found</td></tr>}
        </tbody></table>
    <div className={styles.pagination}><button disabled={page<=1} onClick={()=>setPage(p=>p-1)}>Prev</button><span>Page {page}</span><button disabled={page*PAGE_SIZE>=total} onClick={()=>setPage(p=>p+1)}>Next</button></div>

    {showForm && <div className={styles.modalOverlay} onClick={() => setShowForm(false)}><div className={styles.modal} onClick={e => e.stopPropagation()}><h3>{editId ? 'Edit' : 'Add'} User</h3>
      <form onSubmit={handleSubmit} className={styles.formGrid}>
        {USER_FIELDS.map(f => (
          <div key={f} className={styles.formGroup}><label>{f}</label><input type={f==='password'?'password':'text'} value={form[f] ?? ''} onChange={e => setForm(prev => ({...prev,[f]:e.target.value}) as SysUserForm)} /></div>))}
        <div className={styles.formActions}><button type="button" className={styles.btnSm} onClick={() => setShowForm(false)}>Cancel</button><button type="submit" className={styles.btnPrimary} disabled={saveMutation.isPending}>Save</button></div></form></div></div>}
  </div>)
}
