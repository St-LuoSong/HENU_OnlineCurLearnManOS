<script setup lang="ts">
import {
  ChatDotRound,
  Collection,
  DataAnalysis,
  Folder,
  Key,
  Lock,
  Medal,
  Reading,
  School,
  SetUp,
  Tickets,
  User
} from '@element-plus/icons-vue'
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const activeMenu = computed(() => route.path)
</script>

<template>
  <div class="admin-shell">
    <aside class="admin-nav">
      <div class="admin-brand">
        <span class="brand-mark"><el-icon><Reading /></el-icon></span>
        <div class="admin-brand-copy">
          <strong>线上课程学习</strong>
          <span>管理控制台</span>
        </div>
      </div>
      <div class="admin-nav-title">平台管理</div>
      <el-menu :default-active="activeMenu" router class="admin-menu">
        <el-menu-item index="/admin/dashboard">
          <el-icon><DataAnalysis /></el-icon>
          <span>概览</span>
        </el-menu-item>
        <el-menu-item index="/admin/users">
          <el-icon><User /></el-icon>
          <span>用户管理</span>
        </el-menu-item>
        <el-menu-item index="/admin/password-requests">
          <el-icon><Lock /></el-icon>
          <span>安全审批</span>
        </el-menu-item>
        <el-menu-item index="/admin/llm">
          <el-icon><Key /></el-icon>
          <span>LLM 供应商</span>
        </el-menu-item>
        <el-menu-item index="/admin/models">
          <el-icon><SetUp /></el-icon>
          <span>模型与价格</span>
        </el-menu-item>
        <el-menu-item index="/admin/resources">
          <el-icon><Collection /></el-icon>
          <span>学习资源</span>
        </el-menu-item>
        <el-menu-item index="/admin/knowledge-points">
          <el-icon><Reading /></el-icon>
          <span>知识点维护</span>
        </el-menu-item>
        <el-menu-item index="/admin/points">
          <el-icon><Medal /></el-icon>
          <span>积分与 AI 异常</span>
        </el-menu-item>
        <el-menu-item index="/admin/audit-logs">
          <el-icon><Tickets /></el-icon>
          <span>审计日志</span>
        </el-menu-item>
        <el-menu-item index="/admin/forum">
          <el-icon><ChatDotRound /></el-icon>
          <span>内容治理</span>
        </el-menu-item>
        <el-menu-item index="/admin/cloud-classes">
          <el-icon><School /></el-icon>
          <span>云班级总览</span>
        </el-menu-item>
        <el-menu-item index="/admin/files">
          <el-icon><Folder /></el-icon>
          <span>文件治理</span>
        </el-menu-item>
      </el-menu>
    </aside>
    <section class="admin-content">
      <router-view />
    </section>
  </div>
</template>
