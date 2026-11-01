/**
 * 本地记账服务（Supabase 兼容子集）
 *
 * 背景：当前网络在 TLS 握手阶段阻断 *.supabase.co，App 无法访问云端。
 * 该服务在电脑上实现 App 实际用到的 GoTrue(Auth) 与 PostgREST 接口，
 * 数据保存在 server/data.json，模拟器通过 adb reverse 访问 127.0.0.1:8787。
 *
 * 仅用于本地开发联调，未实现完整 RLS/权限体系，请勿直接暴露到公网。
 */

import http from 'node:http'
import crypto from 'node:crypto'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const DATA_FILE = path.join(__dirname, 'data.json')
const PORT = Number(process.env.PORT || 8787)

/** 内置分类：与 supabase/schema.sql 保持一致 */
const DEFAULT_CATEGORIES = [
  { name: '餐饮', icon_key: 'restaurant', type: 0 },
  { name: '交通', icon_key: 'transport', type: 0 },
  { name: '购物', icon_key: 'shopping', type: 0 },
  { name: '住房', icon_key: 'housing', type: 0 },
  { name: '工资', icon_key: 'salary', type: 1 },
  { name: '红包', icon_key: 'gift', type: 1 },
  { name: '兼职', icon_key: 'parttime', type: 1 },
]

// ---------------------------------------------------------------- 数据存储

function loadDb() {
  if (fs.existsSync(DATA_FILE)) {
    try {
      return JSON.parse(fs.readFileSync(DATA_FILE, 'utf8'))
    } catch (e) {
      console.error('data.json 解析失败，将重新初始化：', e.message)
    }
  }
  return {
    secret: crypto.randomBytes(32).toString('hex'),
    users: [],
    categories: [],
    records: [],
    month_budget: [],
  }
}

const db = loadDb()

function saveDb() {
  const tmp = `${DATA_FILE}.tmp`
  fs.writeFileSync(tmp, JSON.stringify(db, null, 2))
  fs.renameSync(tmp, DATA_FILE)
}

function nowIso() {
  return new Date().toISOString()
}

// ---------------------------------------------------------------- 令牌签名

function b64url(input) {
  return Buffer.from(input).toString('base64url')
}

function signToken(payload) {
  const header = b64url(JSON.stringify({ alg: 'HS256', typ: 'JWT' }))
  const body = b64url(JSON.stringify(payload))
  const sig = crypto.createHmac('sha256', db.secret).update(`${header}.${body}`).digest('base64url')
  return `${header}.${body}.${sig}`
}

/** 校验令牌，返回负载；失败返回 null */
function verifyToken(token) {
  if (!token || token.split('.').length !== 3) return null
  const [header, body, sig] = token.split('.')
  const expected = crypto.createHmac('sha256', db.secret).update(`${header}.${body}`).digest('base64url')
  if (sig !== expected) return null
  try {
    const payload = JSON.parse(Buffer.from(body, 'base64url').toString('utf8'))
    if (payload.exp && payload.exp * 1000 < Date.now()) return null
    return payload
  } catch {
    return null
  }
}

function issueSession(user) {
  const now = Math.floor(Date.now() / 1000)
  const accessToken = signToken({
    sub: user.id,
    email: user.email,
    role: 'authenticated',
    type: 'access',
    iat: now,
    exp: now + 7 * 24 * 3600,
  })
  const refreshToken = signToken({
    sub: user.id,
    email: user.email,
    type: 'refresh',
    iat: now,
    exp: now + 30 * 24 * 3600,
  })
  return {
    access_token: accessToken,
    token_type: 'bearer',
    expires_in: 7 * 24 * 3600,
    refresh_token: refreshToken,
    user: toUserInfo(user),
  }
}

function toUserInfo(user) {
  return {
    id: user.id,
    aud: 'authenticated',
    role: 'authenticated',
    email: user.email,
    email_confirmed_at: user.createdAt,
    confirmed_at: user.createdAt,
    created_at: user.createdAt,
    updated_at: user.updatedAt,
    last_sign_in_at: nowIso(),
    app_metadata: { provider: 'email', providers: ['email'] },
    user_metadata: {},
    identities: [],
  }
}

// ---------------------------------------------------------------- 密码处理

function hashPassword(password, salt) {
  return crypto.scryptSync(password, salt, 64).toString('hex')
}

function createUser(email, password) {
  const salt = crypto.randomBytes(16).toString('hex')
  const createdAt = nowIso()
  const user = {
    id: crypto.randomUUID(),
    email: email.toLowerCase(),
    salt,
    passwordHash: hashPassword(password, salt),
    createdAt,
    updatedAt: createdAt,
  }
  db.users.push(user)
  // 注册即写入内置分类，等价于 schema.sql 中的触发器
  for (const c of DEFAULT_CATEGORIES) {
    db.categories.push({
      id: crypto.randomUUID(),
      user_id: user.id,
      name: c.name,
      icon_key: c.icon_key,
      type: c.type,
      is_default: true,
      created_at: createdAt,
    })
  }
  saveDb()
  return user
}

// ---------------------------------------------------------------- 请求工具

function json(res, status, body) {
  const text = body === undefined ? '' : JSON.stringify(body)
  // 关闭长连接：adb reverse 场景下连接复用容易出现 EOF，交由客户端每次新建连接
  res.shouldKeepAlive = false
  res.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': Buffer.byteLength(text),
    Connection: 'close',
  })
  res.end(text)
}

function empty(res, status = 204) {
  res.shouldKeepAlive = false
  res.writeHead(status, { Connection: 'close' })
  res.end()
}

function apiError(res, status, message, code = 'bad_request', details = null) {
  json(res, status, { code: String(code), message, details, hint: null })
}

function readBody(req) {
  return new Promise((resolve) => {
    const chunks = []
    req.on('data', (c) => chunks.push(c))
    req.on('end', () => {
      const raw = Buffer.concat(chunks).toString('utf8')
      if (!raw) return resolve({})
      try {
        resolve(JSON.parse(raw))
      } catch {
        resolve({})
      }
    })
  })
}

/** 从 Authorization 头解析当前用户，未登录返回 null */
function currentUser(req) {
  const auth = req.headers['authorization'] || ''
  const token = auth.startsWith('Bearer ') ? auth.slice(7) : ''
  const payload = verifyToken(token)
  if (!payload || payload.type !== 'access') return null
  return db.users.find((u) => u.id === payload.sub) || null
}

// ---------------------------------------------------------------- PostgREST 查询

const TABLE_SCHEMAS = {
  categories: {
    columns: ['id', 'user_id', 'name', 'icon_key', 'type', 'is_default', 'created_at'],
  },
  records: {
    columns: ['id', 'user_id', 'type', 'amount', 'category_id', 'record_date', 'record_time', 'remark', 'created_at', 'updated_at'],
  },
  month_budget: {
    columns: ['id', 'user_id', 'year', 'month', 'budget_amount', 'created_at', 'updated_at'],
  },
}

function compareValues(a, b) {
  if (a === b) return 0
  if (a === null || a === undefined) return 1
  if (b === null || b === undefined) return -1
  if (typeof a === 'number' && typeof b === 'number') return a - b
  // 时间类字符串先按时间比较，失败再按字典序
  const ta = Date.parse(a)
  const tb = Date.parse(b)
  if (!Number.isNaN(ta) && !Number.isNaN(tb)) return ta - tb
  return String(a).localeCompare(String(b))
}

function applyFilters(rows, params, userId) {
  let out = rows.filter((r) => r.user_id === userId)
  for (const [key, raw] of params) {
    if (['select', 'order', 'limit', 'offset', 'on_conflict'].includes(key)) continue
    if (key.includes('.')) continue
    const dot = raw.indexOf('.')
    if (dot < 0) continue
    const op = raw.slice(0, dot)
    const value = raw.slice(dot + 1)
    out = out.filter((row) => {
      const actual = row[key]
      switch (op) {
        case 'eq': return String(actual) === value
        case 'neq': return String(actual) !== value
        case 'gt': return compareValues(actual, value) > 0
        case 'gte': return compareValues(actual, value) >= 0
        case 'lt': return compareValues(actual, value) < 0
        case 'lte': return compareValues(actual, value) <= 0
        case 'in': {
          const list = value.replace(/^\(|\)$/g, '').split(',').map((v) => v.replace(/^"|"$/g, ''))
          return list.includes(String(actual))
        }
        default: return true
      }
    })
  }
  return out
}

function applyOrder(rows, orderParam) {
  if (!orderParam) return rows
  const rules = orderParam.split(',').map((part) => {
    const [column, dir, ...rest] = part.split('.')
    return { column, desc: dir === 'desc', nullsFirst: rest.includes('nullsfirst') }
  })
  return rows.slice().sort((a, b) => {
    for (const r of rules) {
      const av = a[r.column]
      const bv = b[r.column]
      if (av === null || av === undefined || bv === null || bv === undefined) {
        if (av === bv) continue
        const nullFirst = r.nullsFirst
        return (av === null || av === undefined) ? (nullFirst ? -1 : 1) : (nullFirst ? 1 : -1)
      }
      const cmp = compareValues(av, bv)
      if (cmp !== 0) return r.desc ? -cmp : cmp
    }
    return 0
  })
}

function coerceRow(table, input, userId) {
  const row = {}
  const schema = TABLE_SCHEMAS[table].columns
  for (const col of schema) {
    if (col === 'id' || col === 'user_id' || col === 'created_at' || col === 'updated_at') continue
    if (input[col] !== undefined) row[col] = input[col]
  }
  // 金额统一存为数字，避免字符串参与运算
  if (table === 'records' && row.amount !== undefined) row.amount = Number(row.amount)
  if (table === 'month_budget' && row.budget_amount !== undefined) row.budget_amount = Number(row.budget_amount)
  if (table === 'categories') {
    row.icon_key = row.icon_key ?? 'other'
    row.type = row.type ?? 0
    row.is_default = row.is_default ?? false
  }
  if (table === 'records') {
    row.remark = row.remark ?? null
    row.amount = row.amount ?? 0
  }
  row.id = crypto.randomUUID()
  row.user_id = userId
  row.created_at = nowIso()
  row.updated_at = row.created_at
  return row
}

function normalizeRows(table, body) {
  if (Array.isArray(body)) return body
  if (body && typeof body === 'object') return [body]
  return []
}

function parseParams(url) {
  return [...url.searchParams.entries()]
}

function handleRest(req, res, table, url) {
  const user = currentUser(req)
  if (!user) return apiError(res, 401, '登录状态已失效，请重新登录', '401')

  const schema = TABLE_SCHEMAS[table]
  if (!schema) return apiError(res, 404, `未知数据表：${table}`, '404')

  const params = parseParams(url)
  const prefer = String(req.headers['prefer'] || '')
  const wantRows = prefer.includes('return=representation')
  const rows = db[table]

  if (req.method === 'GET' || req.method === 'HEAD') {
    const filtered = applyFilters(rows, params, user.id)
    const orderParam = url.searchParams.get('order')
    const ordered = applyOrder(filtered, orderParam)
    const offset = Number(url.searchParams.get('offset') || 0)
    const limitRaw = url.searchParams.get('limit')
    const limited = limitRaw ? ordered.slice(offset, offset + Number(limitRaw)) : ordered.slice(offset)
    return json(res, 200, limited)
  }

  if (req.method === 'POST') {
    return readBody(req).then((body) => {
      const conflictCols = (url.searchParams.get('on_conflict') || '').split(',').filter(Boolean)
      const inputs = normalizeRows(table, body)
      const result = []
      for (const input of inputs) {
        let existing = null
        if (conflictCols.length) {
          existing = rows.find((r) => r.user_id === user.id && conflictCols.every((c) => String(r[c]) === String(input[c])))
        }
        if (existing) {
          // 冲突时更新（upsert 语义），仅覆盖请求中出现的字段
          for (const [key, value] of Object.entries(input)) {
            if (key === 'user_id' || key === 'id') continue
            existing[key] = table === 'month_budget' && key === 'budget_amount' ? Number(value) : value
          }
          existing.updated_at = nowIso()
          result.push(existing)
        } else {
          const row = coerceRow(table, input, user.id)
          rows.push(row)
          result.push(row)
        }
      }
      saveDb()
      if (wantRows) return json(res, 201, result)
      return empty(res, 201)
    })
  }

  if (req.method === 'PATCH') {
    return readBody(req).then((body) => {
      const filtered = applyFilters(rows, params, user.id)
      for (const row of filtered) {
        for (const [key, value] of Object.entries(body)) {
          if (['id', 'user_id', 'created_at'].includes(key)) continue
          row[key] = table === 'records' && key === 'amount' ? Number(value) : value
        }
        row.updated_at = nowIso()
      }
      saveDb()
      if (wantRows) return json(res, 200, filtered)
      return empty(res, 204)
    })
  }

  if (req.method === 'DELETE') {
    if (table === 'categories') {
      const filtered = applyFilters(rows, params, user.id)
      const defaultRow = filtered.find((r) => r.is_default)
      if (defaultRow) return apiError(res, 400, '内置分类不可删除', '42501')
      // 等价于外键约束：分类被流水引用时禁止删除
      const inUse = filtered.find((c) => db.records.some((r) => r.category_id === c.id))
      if (inUse) return apiError(res, 409, '该分类下已有账单，无法删除', '23503')
      const ids = new Set(filtered.map((r) => r.id))
      db.categories = db.categories.filter((r) => !ids.has(r.id))
      saveDb()
      return empty(res, 204)
    }
    const filtered = applyFilters(rows, params, user.id)
    const ids = new Set(filtered.map((r) => r.id))
    db[table] = rows.filter((r) => !ids.has(r.id))
    saveDb()
    return empty(res, 204)
  }

  return apiError(res, 405, '不支持的方法', '405')
}

// ---------------------------------------------------------------- Auth 接口

async function handleAuth(req, res, pathname, url) {
  if (pathname === '/auth/v1/signup' && req.method === 'POST') {
    const body = await readBody(req)
    const email = String(body.email || '').trim().toLowerCase()
    const password = String(body.password || '')
    if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) {
      return apiError(res, 400, '请输入正确的邮箱地址', 'invalid_email')
    }
    if (password.length < 6) {
      return apiError(res, 400, '密码至少 6 位', 'weak_password')
    }
    if (db.users.some((u) => u.email === email)) {
      return apiError(res, 400, '该邮箱已注册，请直接登录', 'user_already_exists')
    }
    const user = createUser(email, password)
    return json(res, 200, issueSession(user))
  }

  if (pathname === '/auth/v1/token' && req.method === 'POST') {
    const body = await readBody(req)
    const grant = url.searchParams.get('grant_type')
    if (grant === 'password') {
      const email = String(body.email || '').trim().toLowerCase()
      const password = String(body.password || '')
      const user = db.users.find((u) => u.email === email)
      if (!user || hashPassword(password, user.salt) !== user.passwordHash) {
        return apiError(res, 400, '邮箱或密码错误', 'invalid_credentials')
      }
      user.updatedAt = nowIso()
      saveDb()
      return json(res, 200, issueSession(user))
    }
    if (grant === 'refresh_token') {
      const payload = verifyToken(String(body.refresh_token || ''))
      if (!payload || payload.type !== 'refresh') {
        return apiError(res, 400, '登录已过期，请重新登录', 'invalid_refresh_token')
      }
      const user = db.users.find((u) => u.id === payload.sub)
      if (!user) return apiError(res, 400, '账号不存在', 'user_not_found')
      return json(res, 200, issueSession(user))
    }
    return apiError(res, 400, '不支持的授权类型', 'unsupported_grant_type')
  }

  if (pathname === '/auth/v1/user' && req.method === 'GET') {
    const user = currentUser(req)
    if (!user) return apiError(res, 401, '登录状态已失效，请重新登录', '401')
    return json(res, 200, toUserInfo(user))
  }

  if (pathname === '/auth/v1/logout' && req.method === 'POST') {
    return empty(res, 204)
  }

  return apiError(res, 404, '接口不存在', '404')
}

// ---------------------------------------------------------------- 路由入口

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, `http://${req.headers.host || '127.0.0.1'}`)
  const pathname = url.pathname.replace(/\/+$/, '')

  // 简单访问日志，便于排查 App 请求（同时落盘，计划任务运行时也能查看）
  res.on('finish', () => {
    const line = `${new Date().toISOString()} ${req.method} ${url.pathname}${url.search} -> ${res.statusCode}\n`
    console.log(line.trim())
    try {
      fs.appendFileSync(path.join(__dirname, 'requests.log'), line)
    } catch {
      // 日志写入失败不影响接口
    }
  })

  try {
    if (pathname === '/health') {
      return json(res, 200, { status: 'ok', time: nowIso() })
    }
    if (pathname.startsWith('/auth/v1/')) {
      return await handleAuth(req, res, pathname, url)
    }
    if (pathname.startsWith('/rest/v1/')) {
      const table = pathname.slice('/rest/v1/'.length)
      return handleRest(req, res, table, url)
    }
    return apiError(res, 404, '接口不存在', '404')
  } catch (e) {
    console.error('请求处理失败：', e)
    return apiError(res, 500, '服务内部错误', '500')
  }
})

server.listen(PORT, '0.0.0.0', () => {
  console.log(`本地记账服务已启动：http://127.0.0.1:${PORT}`)
  console.log(`数据文件：${DATA_FILE}`)
  console.log('模拟器请先执行：adb reverse tcp:8787 tcp:8787')
})
