// 统一测试入口：显式绑定项目内置 JDK 21 与本仓库 .m2 仓库，避免终端里残留的 Java 8 / 全局仓库权限问题。
// 用法：
//   node scripts/run-tests.mjs                 后端全量 + 前端 vitest + 前端 build
//   node scripts/run-tests.mjs -b              仅后端全量
//   node scripts/run-tests.mjs -f              仅前端 vitest
//   node scripts/run-tests.mjs -t ChatSseMvcTimingTest   仅后端指定测试类
import { spawnSync } from 'node:child_process'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const javaHome = process.env.ONLINE_CUR_LEARN_MAN_OS_JDK || 'C:\\Program Files\\Java\\jdk-21.0.12.1'
const env = {
  ...process.env,
  JAVA_HOME: javaHome,
  PATH: javaHome + '\\bin;' + process.env.PATH
}

function run(label, command, args) {
  process.stdout.write('\n### ' + label + '\n')
  const result = spawnSync(command, args, { stdio: 'inherit', env, cwd: root, shell: true })
  if (result.status !== 0) {
    process.stdout.write('### ' + label + ' FAILED (exit ' + result.status + ')\n')
    process.exit(result.status ?? 1)
  }
}

const flags = process.argv.slice(2)
const only = flags.indexOf('-t')
const backendOnly = flags.includes('-b')
const frontendOnly = flags.includes('-f')
const repoFlag = ['--batch-mode', '-Dmaven.repo.local=.m2/repository']

if (!frontendOnly) {
  const target = only >= 0 ? flags[only + 1] : null
  run('mvn test ' + (target ?? '(all)'), 'mvn', [
    '-f', 'server/pom.xml', ...repoFlag,
    ...(target ? ['-Dtest=' + target, '-DfailIfNoSpecifiedTests=false'] : []),
    'test'
  ])
}

if (!backendOnly) {
  run('vitest', 'npm', ['--prefix', 'web', 'run', 'test'])
  run('vue-tsc + vite build', 'npm', ['--prefix', 'web', 'run', 'build'])
}


