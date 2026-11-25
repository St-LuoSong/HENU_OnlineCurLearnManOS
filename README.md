# OnlineCurLearnManOS

线上课程学习管理系统，面向学生、教师和管理员的课程项目，使用 Java 21、Spring Boot、Vue 3 和 MySQL 8。

## 功能

- 学习首页：学习交互统计、近期成绩、已加入课程、成长目标、签到和日历状态。
- AI 助手：按科目或自定义主题问答，增量显示 Markdown、公式和代码，支持停止生成及回答收藏。
- 套题练习：生成题目、保存作答、确认交卷、成绩解析及历史回顾。
- 知识锦囊：收藏回答快照、科目筛选、关键词检索及每页八条分页。
- 云班级：教师创建班级与发布任务，学生凭班级码加入、提交作业，教师批改及查看统计。
- 成长规划：学习周期、方向、目标、计划、行动和周复盘；AI 草案经本人确认后采纳。
- 学习论坛：分类讨论、回复、排行、教师公示及个人消息。
- 个人中心：资料、安全、积分流水、模型偏好与通知设置。
- 平台管理：用户与安全审批、资源、模型与价格、内容治理和审计。

## 目录

```text
OnlineCurLearnManOS/
├── server/          Spring Boot 后端
├── web/             Vue 前端
├── scripts/         启动及维护脚本
├── .devcontainer/   开发容器配置
└── docx/            课程需求文档
```

课程材料：[软件需求规格说明书](docx/软件需求规格说明书.docx)、[需求构思及描述](docx/需求构思及描述.docx)，[软件设计规格说明书](docx/软件设计规格说明书.docx)。

## 开发环境

- Java 21
- Maven 3.9 及以上
- Node.js 20 及以上
- MySQL 8

## 配置

创建数据库：

```sql
CREATE DATABASE onlinecurlearnmanos
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;
```

复制 `server/.env.example` 为 `server/.env`，填写数据库连接、`JWT_SECRET` 和 `APP_ENCRYPTION_KEY`。两项密钥均应使用至少 32 个字符的独立随机值。实际环境文件不提交到仓库。

`DB_NAME` 默认为 `onlinecurlearnmanos`。接入已有数据库时，通过 `DB_NAME` 指定实际数据库名称；无需修改已存在的数据或迁移记录。

管理员首次初始化可临时启用 `APP_BOOTSTRAP_ADMIN_ENABLED=true` 并设置 `APP_BOOTSTRAP_ADMIN_PASSWORD`，初始化完成后关闭。模型供应商凭据通过管理员页面录入，不写入代码和示例配置。

## 启动

在项目根目录启动后端：

```text
scripts\run-backend.cmd
```

Windows 脚本使用其中指定的 JDK 路径，运行前应与本机安装位置核对。也可以在正确配置 Java 21 后运行：

```bash
mvn -f server/pom.xml spring-boot:run
```

另开终端启动前端：

```bash
npm --prefix web install
npm --prefix web run dev
```

前端默认地址为 `http://127.0.0.1:5173`，后端为 `http://127.0.0.1:8080`。前端将 `/api` 请求代理到后端；后端健康检查为 `/api/v1/health`，接口说明位于 `/swagger-ui.html`。

## 账号与业务规则

学生、教师和管理员按角色及业务对象归属访问功能。自助注册不能获得管理员权限，管理员通过授权初始化建立。

普通模式、专家模式和练习换题遵循当前服务端配置；专家次数和积分余额分别检查。签到奖励、扣减、失败恢复和学习历史均应以服务端返回结果为准。

## 项目标识

项目名称为 `OnlineCurLearnManOS`；后端 Java 包为 `com.onlinecurlearnmanos`，启动类为 `OnlineCurLearnManOSApplication`，构建标识分别为 `onlinecurlearnmanos-server` 和 `onlinecurlearnmanos-web`。

## 许可证

MIT，详见 [LICENSE](LICENSE)。
