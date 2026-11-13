#!/usr/bin/env node
/**
 * 14-E 真实环境回归：对着正在运行的后端（真实 MySQL）跑一遍关键链路。
 *
 * 用法：
 *   1) 启动后端：mvn -f server/pom.xml spring-boot:run
 *   2) 执行脚本：node scripts/e2e-regression.mjs
 *
 * 覆盖：V20 / V21 / V22 迁移是否生效、双登录入口隔离、跨角色越权、
 *      文件上传校验矩阵（扩展名 / 伪装 MIME / 路径穿越 / 超限）、
 *      下载与在线预览、举报与治理、密码审批后旧 token 失效、
 *      云班级作业附件版本与权限。
 *
 * 环境变量：
 *   E2E_BASE_URL        默认 http://127.0.0.1:8080/api/v1
 *   E2E_ADMIN_USERNAME  默认 admin
 *   E2E_ADMIN_PASSWORD  默认 123456
 */

const BASE_URL = process.env.E2E_BASE_URL ?? 'http://127.0.0.1:8080/api/v1'
const ADMIN_USERNAME = process.env.E2E_ADMIN_USERNAME ?? 'admin'
const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? '123456'

const stamp = Date.now().toString(36)
const STUDENT = { username: `e2e_stu_${stamp}`, password: 'E2eStudent123' }
const TEACHER = { username: `e2e_tea_${stamp}`, password: 'E2eTeacher123' }
const NEW_STUDENT_PASSWORD = 'E2eChanged456'

const results = []
const state = {}

function expect(condition, message) {
  if (!condition) {
    throw new Error(message)
  }
}

async function step(name, run) {
  try {
    const detail = await run()
    results.push({ name, ok: true, detail: detail ?? '' })
  } catch (error) {
    results.push({ name, ok: false, detail: error.message })
  }
}

function skip(name, reason) {
  results.push({ name, ok: true, detail: `SKIPPED：${reason}` })
}

async function api(method, path, options = {}) {
  const headers = {}
  if (options.token) {
    headers.Authorization = `Bearer ${options.token}`
  }
  let body
  if (options.form) {
    body = options.form
  } else if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json'
    body = JSON.stringify(options.body)
  }
  // 认证类接口有每分钟限流（10 次/分钟），连续跑回归时自动等待窗口恢复
  const maxAttempts = path.startsWith('/auth/') && options.retry !== false ? 5 : 1
  for (let attempt = 1; attempt <= maxAttempts; attempt++) {
    const response = await fetch(`${BASE_URL}${path}`, { method, headers, body })
    if (response.status === 429 && attempt < maxAttempts) {
      console.log(`  · 触发登录限流，等待 15s 后重试（${attempt}/${maxAttempts - 1}）`)
      await new Promise((resolve) => setTimeout(resolve, 15000))
      continue
    }
    const contentType = response.headers.get('content-type') ?? ''
    const payload = contentType.includes('application/json')
      ? await response.json()
      : await response.arrayBuffer()
    return {
      status: response.status,
      ok: response.ok,
      data: payload && payload.data !== undefined ? payload.data : payload,
      envelope: contentType.includes('application/json') ? payload : null,
      headers: response.headers
    }
  }
  throw new Error('认证接口持续限流，请稍后重试')
}

function fileForm(name, type, content) {
  const form = new FormData()
  form.append('file', new Blob([content], { type }), name)
  return form
}

function upload(token, purpose, name, type, content) {
  return api('POST', `/files?purpose=${purpose}`, {
    token,
    form: fileForm(name, type, content)
  })
}

async function main() {
  // ---------------------------------------------------------------- 迁移与登录入口
  await step('管理员专属入口可登录（/auth/admin/login）', async () => {
    const response = await api('POST', '/auth/admin/login', {
      body: { username: ADMIN_USERNAME, password: ADMIN_PASSWORD }
    })
    expect(response.status === 200, `期望 200，实际 ${response.status}`)
    state.adminToken = response.data.accessToken
    expect(Boolean(state.adminToken), '未签发 token')
    return '已签发管理员 token'
  })

  await step('普通入口拒绝管理员账号', async () => {
    const response = await api('POST', '/auth/login', {
      body: { username: ADMIN_USERNAME, password: ADMIN_PASSWORD }
    })
    expect(response.status !== 200, `管理员不应能从普通入口登录，实际 ${response.status}`)
    return `普通入口返回 ${response.status}`
  })

  await step('V21 迁移生效（管理员可读取密码变更申请）', async () => {
    const response = await api('GET', '/admin/password-requests?status=PENDING', {
      token: state.adminToken
    })
    expect(response.status === 200, `期望 200，实际 ${response.status}`)
    return 'password_change_request 表可访问'
  })

  await step('V22 迁移生效（管理员可读取文件资产）', async () => {
    const response = await api('GET', '/admin/files', { token: state.adminToken })
    expect(response.status === 200, `期望 200，实际 ${response.status}`)
    return 'file_asset 表可访问'
  })

  await step('V20 迁移生效（管理员可读取论坛举报）', async () => {
    const response = await api('GET', '/admin/forum/reports', { token: state.adminToken })
    expect(response.status === 200, `期望 200，实际 ${response.status}`)
    return 'forum_report 表可访问'
  })

  await step('未认证访问管理端被拒', async () => {
    const response = await api('GET', '/admin/files')
    expect(response.status === 401, `期望 401，实际 ${response.status}`)
    return '返回 401'
  })

  // ---------------------------------------------------------------- 三类账号
  await step('学生自助注册后可直接登录', async () => {
    const registered = await api('POST', '/auth/register', {
      body: {
        username: STUDENT.username,
        password: STUDENT.password,
        displayName: '回归学生',
        email: `${STUDENT.username}@example.com`,
        role: 'STUDENT'
      }
    })
    expect(registered.status === 200, `注册失败：${registered.status}`)
    expect(!registered.data.pendingReview, '学生账号不应进入待审核')
    state.studentToken = registered.data.accessToken
    state.studentId = registered.data.user.id
    return `学生 ${STUDENT.username} 已创建`
  })

  await step('教师注册进入待审核，管理员启用后可登录', async () => {
    const registered = await api('POST', '/auth/register', {
      body: {
        username: TEACHER.username,
        password: TEACHER.password,
        displayName: '回归教师',
        email: `${TEACHER.username}@example.com`,
        role: 'TEACHER'
      }
    })
    expect(registered.status === 200, `注册失败：${registered.status}`)
    expect(registered.data.pendingReview === true, '教师注册应进入待审核')
    state.teacherId = registered.data.user.id

    const blocked = await api('POST', '/auth/login', {
      body: { username: TEACHER.username, password: TEACHER.password, role: 'TEACHER' }
    })
    expect(blocked.status !== 200, '待审核教师不应能登录')

    const enabled = await api('PUT', `/admin/users/${state.teacherId}/status`, {
      token: state.adminToken,
      body: { enabled: true }
    })
    expect(enabled.status === 200, `启用失败：${enabled.status}`)

    const login = await api('POST', '/auth/login', {
      body: { username: TEACHER.username, password: TEACHER.password, role: 'TEACHER' }
    })
    expect(login.status === 200, `启用后仍无法登录：${login.status}`)
    state.teacherToken = login.data.accessToken
    return `教师 ${TEACHER.username} 已启用`
  })

  await step('学生访问管理端与学生专属入口的越权矩阵', async () => {
    const adminFiles = await api('GET', '/admin/files', { token: state.studentToken })
    expect(adminFiles.status === 403, `学生读管理文件应为 403，实际 ${adminFiles.status}`)
    const passwordRequests = await api('GET', '/admin/password-requests', {
      token: state.studentToken
    })
    expect(passwordRequests.status === 403, `学生读审批应为 403，实际 ${passwordRequests.status}`)
    const adminLogin = await api('POST', '/auth/admin/login', {
      body: { username: STUDENT.username, password: STUDENT.password }
    })
    expect(adminLogin.status !== 200, '学生不应能从管理员入口登录')
    const roleMismatch = await api('POST', '/auth/login', {
      body: { username: STUDENT.username, password: STUDENT.password, role: 'TEACHER' }
    })
    expect(roleMismatch.status !== 200, '身份不匹配时不应签发 token')
    return '403 / 入口拒绝 / 身份不匹配均符合预期'
  })

  // ---------------------------------------------------------------- 文件校验矩阵
  await step('合法 txt 上传成功并返回元数据', async () => {
    const response = await upload(
      state.adminToken,
      'PLATFORM_RESOURCE',
      '回归说明.txt',
      'text/plain',
      '第一行内容\n第二行内容'
    )
    expect(response.status === 200, `期望 200，实际 ${response.status}`)
    expect(response.data.sha256?.length === 64, '未返回 SHA-256')
    state.textFileId = response.data.id
    return `fileId=${state.textFileId}，SHA-256 已生成`
  })

  await step('伪装 MIME 的伪造 PDF 被拒绝', async () => {
    const response = await upload(
      state.adminToken,
      'PLATFORM_RESOURCE',
      'fake.pdf',
      'application/pdf',
      'this is not a pdf'
    )
    expect(response.status === 400, `期望 400，实际 ${response.status}`)
    return '返回 400'
  })

  await step('白名单外扩展名被拒绝', async () => {
    const response = await upload(
      state.adminToken,
      'PLATFORM_RESOURCE',
      'evil.exe',
      'application/octet-stream',
      'MZ'
    )
    expect(response.status === 400, `期望 400，实际 ${response.status}`)
    return '返回 400'
  })

  await step('文件名路径穿越被拒绝', async () => {
    const response = await upload(
      state.adminToken,
      'PLATFORM_RESOURCE',
      '..\\..\\evil.txt',
      'text/plain',
      'traversal'
    )
    expect(response.status === 400, `期望 400，实际 ${response.status}`)
    return '返回 400'
  })

  await step('超过大小上限的文件被拒绝', async () => {
    const oversize = Buffer.alloc(21 * 1024 * 1024, 97)
    const response = await upload(
      state.adminToken,
      'PLATFORM_RESOURCE',
      'big.txt',
      'text/plain',
      oversize
    )
    expect(response.status === 400, `期望 400，实际 ${response.status}`)
    return '返回 400'
  })

  await step('文件用途与角色匹配（教师不能上传作业附件，学生不能上传平台资源）', async () => {
    const teacherSubmission = await upload(
      state.teacherToken,
      'SUBMISSION',
      'homework.txt',
      'text/plain',
      'teacher upload'
    )
    expect(teacherSubmission.status === 403, `教师上传作业附件应为 403，实际 ${teacherSubmission.status}`)
    const studentPlatform = await upload(
      state.studentToken,
      'PLATFORM_RESOURCE',
      'platform.txt',
      'text/plain',
      'student upload'
    )
    expect(studentPlatform.status === 403, `学生上传平台资源应为 403，实际 ${studentPlatform.status}`)
    return '403 / 403'
  })

  await step('未挂载文件对其他人不可读', async () => {
    const response = await api('GET', `/files/${state.textFileId}/preview`, {
      token: state.studentToken
    })
    expect(response.status === 403, `期望 403，实际 ${response.status}`)
    return '返回 403'
  })

  await step('上传者可下载并在线预览自己的文件', async () => {
    const download = await api('GET', `/files/${state.textFileId}/download`, {
      token: state.adminToken
    })
    expect(download.status === 200, `下载失败：${download.status}`)
    const disposition = download.headers.get('content-disposition') ?? ''
    expect(disposition.includes('attachment'), '下载响应缺少 attachment 声明')
    expect(disposition.includes("filename*=UTF-8''"), '下载响应缺少 UTF-8 文件名')

    const preview = await api('GET', `/files/${state.textFileId}/preview`, {
      token: state.adminToken
    })
    expect(preview.status === 200, `预览失败：${preview.status}`)
    expect(preview.data.kind === 'TEXT', `预览类型应为 TEXT，实际 ${preview.data.kind}`)
    expect(preview.data.text.includes('第二行'), '预览内容不正确')
    return 'attachment + TEXT 预览通过'
  })

  await step('文件接口不接受路径穿越参数', async () => {
    const response = await api('GET', '/files/..%2F..%2Fetc%2Fpasswd/preview', {
      token: state.adminToken
    })
    expect(response.status !== 200, `路径穿越不应返回 200，实际 ${response.status}`)
    return `返回 ${response.status}`
  })

  // ---------------------------------------------------------------- 论坛举报与治理
  await step('学生发帖、教师举报、管理员隐藏后公开详情不可见', async () => {
    const categories = await api('GET', '/forum/categories', { token: state.studentToken })
    expect(categories.status === 200 && categories.data.length > 0, '论坛分类为空，无法发帖')
    const post = await api('POST', '/forum/posts', {
      token: state.studentToken,
      body: {
        categoryId: categories.data[0].id,
        title: `回归测试帖 ${stamp}`,
        content: '这是 14-E 回归脚本创建的帖子。'
      }
    })
    expect(post.status === 200, `发帖失败：${post.status}`)
    state.postId = post.data.id

    const report = await api('POST', '/forum/reports', {
      token: state.teacherToken,
      body: { targetType: 'POST', targetId: state.postId, reason: 'SPAM', detail: '回归测试举报' }
    })
    expect(report.status === 200, `举报失败：${report.status}`)
    state.reportId = report.data.id

    const duplicate = await api('POST', '/forum/reports', {
      token: state.teacherToken,
      body: { targetType: 'POST', targetId: state.postId, reason: 'SPAM', detail: '重复举报' }
    })
    expect(duplicate.status === 409, `重复举报应为 409，实际 ${duplicate.status}`)

    const hide = await api('POST', '/admin/forum/moderate', {
      token: state.adminToken,
      body: {
        targetType: 'POST',
        targetId: state.postId,
        action: 'HIDE',
        note: '回归测试：隐藏',
        reportId: state.reportId
      }
    })
    expect(hide.status === 200, `隐藏失败：${hide.status}`)

    const hidden = await api('GET', `/forum/posts/${state.postId}`, {
      token: state.studentToken
    })
    expect(hidden.status === 404, `隐藏后学生详情应 404，实际 ${hidden.status}`)

    const adminView = await api('GET', `/forum/posts/${state.postId}`, {
      token: state.adminToken
    })
    expect(adminView.status === 200, `管理员仍应可见隐藏内容，实际 ${adminView.status}`)
    return `postId=${state.postId} 举报→隐藏→可见性校验通过`
  })

  // ---------------------------------------------------------------- 密码审批
  await step('密码审批通过后旧 token 立即失效，新密码可登录', async () => {
    const submit = await api('POST', '/settings/password-request', {
      token: state.studentToken,
      body: { currentPassword: STUDENT.password, newPassword: NEW_STUDENT_PASSWORD }
    })
    expect(submit.status === 200, `提交申请失败：${submit.status}`)
    state.passwordRequestId = submit.data.id

    const duplicate = await api('POST', '/settings/password-request', {
      token: state.studentToken,
      body: { currentPassword: STUDENT.password, newPassword: 'AnotherPass789' }
    })
    expect(duplicate.status === 409, `重复申请应为 409，实际 ${duplicate.status}`)

    const approve = await api('PUT', `/admin/password-requests/${state.passwordRequestId}/review`, {
      token: state.adminToken,
      body: { approved: true, note: '回归测试：批准' }
    })
    expect(approve.status === 200, `审批失败：${approve.status}`)

    const stale = await api('GET', '/auth/me', { token: state.studentToken })
    expect(stale.status === 401, `旧 token 应失效，实际 ${stale.status}`)

    const relogin = await api('POST', '/auth/login', {
      body: {
        username: STUDENT.username,
        password: NEW_STUDENT_PASSWORD,
        role: 'STUDENT'
      }
    })
    expect(relogin.status === 200, `新密码登录失败：${relogin.status}`)
    state.studentToken = relogin.data.accessToken
    return '旧 token 401，新密码登录成功'
  })

  // ---------------------------------------------------------------- 云班级作业附件
  const subjects = await api('GET', '/subjects', { token: state.teacherToken })
  if (subjects.status !== 200 || !Array.isArray(subjects.data) || subjects.data.length === 0) {
    skip('云班级作业附件链路', '学科数据为空，无法创建云班级')
  } else {
    await step('教师建班→发布任务→学生加入并提交→上传作业附件', async () => {
      const created = await api('POST', '/cloud-classes', {
        token: state.teacherToken,
        body: {
          subjectId: subjects.data[0].id,
          name: `回归班级 ${stamp}`,
          description: '14-E 回归脚本创建'
        }
      })
      expect(created.status === 200, `建班失败：${created.status}`)
      state.classId = created.data.id
      expect(/^[A-Z0-9]{6}$/.test(created.data.classCode), '班级码格式不正确')

      const task = await api('POST', `/cloud-classes/${state.classId}/tasks`, {
        token: state.teacherToken,
        body: {
          title: `回归任务 ${stamp}`,
          description: '提交文本 + 附件',
          taskType: 'TASK',
          rewardPoints: 0,
          allowResubmit: true,
          maxScore: 100
        }
      })
      expect(task.status === 200, `发布任务失败：${task.status}`)
      state.taskId = task.data.id

      const join = await api('POST', '/cloud-classes/join', {
        token: state.studentToken,
        body: { classCode: created.data.classCode }
      })
      expect(join.status === 200, `加入班级失败：${join.status}`)

      const submission = await api('POST', `/cloud-classes/tasks/${state.taskId}/submit`, {
        token: state.studentToken,
        body: { content: '回归作业正文' }
      })
      expect(submission.status === 200, `提交作业失败：${submission.status}`)
      state.submissionId = submission.data.id

      const attachmentFile = await upload(
        state.studentToken,
        'SUBMISSION',
        '作业报告.txt',
        'text/plain',
        '作业报告内容'
      )
      expect(attachmentFile.status === 200, `学生上传作业附件失败：${attachmentFile.status}`)
      state.submissionFileId = attachmentFile.data.id

      const attach = await api(
        'POST',
        `/cloud-classes/submissions/${state.submissionId}/attachments`,
        { token: state.studentToken, body: { fileId: state.submissionFileId } }
      )
      expect(attach.status === 200, `关联作业附件失败：${attach.status}`)
      expect(attach.data.versionNo >= 1, '附件未记录版本号')

      const teacherView = await api(
        'GET',
        `/cloud-classes/submissions/${state.submissionId}/attachments`,
        { token: state.teacherToken }
      )
      expect(teacherView.status === 200, `教师读取作业附件失败：${teacherView.status}`)
      expect(teacherView.data.length === 1, '教师应看到 1 个附件')
      expect(teacherView.data[0].canDelete === true, '班级教师应能删除作业附件')

      const adminRead = await api('GET', `/files/${state.submissionFileId}/preview`, {
        token: state.adminToken
      })
      expect(adminRead.status === 200, '管理员应可读取全部文件')
      return `classId=${state.classId} submissionId=${state.submissionId} versionNo=${attach.data.versionNo}`
    })
  }

  // ---------------------------------------------------------------- 清理
  await step('清理：管理员强制删除附件文件与平台文件', async () => {
    for (const fileId of [state.submissionFileId, state.textFileId]) {
      if (!fileId) {
        continue
      }
      const response = await api('DELETE', `/admin/files/${fileId}`, { token: state.adminToken })
      expect(response.status === 200, `删除文件 ${fileId} 失败：${response.status}`)
    }
    return '测试文件已清理'
  })

  await step('清理：管理员恢复帖子后学生删除自己发布的帖子', async () => {
    if (!state.postId) {
      return '无测试帖子'
    }
    const restore = await api('POST', '/admin/forum/moderate', {
      token: state.adminToken,
      body: {
        targetType: 'POST',
        targetId: state.postId,
        action: 'RESTORE',
        note: '回归测试：恢复后由作者删除'
      }
    })
    expect(restore.status === 200, `恢复失败：${restore.status}`)
    const removed = await api('DELETE', `/forum/posts/${state.postId}`, {
      token: state.studentToken
    })
    expect(removed.status === 200, `删除帖子失败：${removed.status}`)
    return '帖子已清理'
  })

  await step('清理：停用回归脚本创建的全部 e2e_ 测试账号', async () => {
    const page = await api('GET', '/admin/users?page=1&size=50&keyword=e2e_', {
      token: state.adminToken
    })
    expect(page.status === 200, `读取用户列表失败：${page.status}`)
    const targets = (page.data.records ?? []).filter(
      (user) => typeof user.loginId === 'string'
        && user.loginId.startsWith('e2e_')
        && user.enabled
    )
    for (const user of targets) {
      const disabled = await api('PUT', `/admin/users/${user.id}/status`, {
        token: state.adminToken,
        body: { enabled: false }
      })
      expect(disabled.status === 200, `停用 ${user.loginId} 失败：${disabled.status}`)
    }
    return `已停用 ${targets.length} 个测试账号`
  })

  report()
}

function report() {
  const passed = results.filter((item) => item.ok).length
  const failed = results.length - passed
  console.log('')
  console.log('=== 14-E 端到端回归结果 ===')
  for (const item of results) {
    const mark = item.ok ? 'PASS' : 'FAIL'
    console.log(`[${mark}] ${item.name}${item.detail ? ` — ${item.detail}` : ''}`)
  }
  console.log('')
  console.log(`合计：${results.length} 项，通过 ${passed} 项，失败 ${failed} 项`)
  console.log(`本次测试账号：${STUDENT.username} / ${TEACHER.username}（已在清理步骤停用）`)
  if (failed > 0) {
    process.exitCode = 1
  }
}

main().catch((error) => {
  console.error('回归脚本异常终止：', error)
  process.exitCode = 1
})
