# Vue 3 前端技术需求文档 (Frontend Technical Requirements)

## 1. 项目概述

本项目是一个集成了 AI 聊天、文档解析与交互、MCP 工具管理（大模型工具调用配置）和会话管理的人工智能 Web 应用程序。后端提供了基于 RESTful 和 SSE (Server-Sent Events) 的接口，现需开发配套的现代化、规范、美观的前端工程。

## 2. 技术栈选型

- **核心框架**: Vue 3 (Composition API, `<script setup>`)
- **构建工具**: Vite
- **类型系统**: TypeScript (强制严格模式)
- **状态管理**: Pinia
- **路由管理**: Vue Router 4
- **UI 组件库**: Element Plus / Ant Design Vue / Naive UI (推荐使用 Naive UI，风格更现代)
- **CSS 预处理器**: Tailwind CSS (用于快速、规范的原子化样式设计)
- **网络请求**: Axios (常规接口) + 原生 `EventSource` 或 `@microsoft/fetch-event-source` (用于流式对话 SSE)
- **Markdown 渲染**: `markdown-it` / `highlight.js` (渲染 AI 返回的 Markdown 及代码高亮)
- **代码规范**: ESLint + Prettier

## 3. 页面与路由规划

前端系统主要分为两个大模块：**用户端（C端）** 和 **管理端（Admin）**。

### 3.1 路由结构
```text
/                   -> 布局视图 (Layout)
├── /chat           -> AI 聊天主界面（包含历史会话侧边栏、聊天区域）
├── /document       -> 知识库/文档上传与解析界面
└── /admin          -> 管理后台
    └── /admin/mcp  -> MCP 工具配置管理界面
```

### 3.2 页面设计说明

#### 1. AI 聊天主界面 (`/chat`)
- **左侧边栏 (Sidebar)**:
  - 会话列表：展示历史会话列表，支持点击切换会话，支持删除会话。
  - 底部操作：新建会话按钮。
- **顶部导航 (Header)**:
  - 模型切换下拉框：动态加载支持的 AI 模型（如 GPT-4, Claude 等），默认选中后端返回的默认模型。
- **中间内容区 (Chat Area)**:
  - 消息展示区：滚动显示用户输入和 AI 回复。AI 回复需支持 Markdown 实时流式渲染、代码高亮。
  - 交互设计：打字机效果呈现，思考过程可视化。
- **底部输入区 (Input Area)**:
  - 多行文本输入框，支持 `Ctrl+Enter` 快捷发送。
  - 支持快捷文件上传入口（可选拓展）。

#### 2. 文档交互页面 (`/document`)
- **上传区域**: 支持拖拽和点击上传文件（支持 pdf, docx, txt, csv 等多格式）。
- **知识列表**: 展示已上传的文档列表，支持下载原文档。

#### 3. MCP 工具管理界面 (`/admin/mcp`)
- **CRUD 表格**: 列表展示所有 MCP (Model Context Protocol) 插件/工具配置。
- **操作列**:
  - 创建、编辑、删除工具。
  - **重载工具 (Reload)** 按钮，以及顶部显示“当前已加载客户端数量”。

## 4. API 对接规范

后端统一返回结构为 `ApiResponse<T>`，所有网络请求需要统一拦截器处理错误。

### 4.1 会话管理 (Session API)
- `GET /api/sessions`: 获取侧边栏会话列表。
- `GET /api/session/{chatId}/info`: 获取指定会话详细信息。
- `GET /api/session/{chatId}/messages?limit=40`: 分页/限制获取历史聊天记录。
- `DELETE /api/session/{chatId}`: 删除会话。

### 4.2 聊天对话 (Chat API) - **核心难点**
- 接口: `GET /api/dchat?prompt={...}&chatId={...}&modelCode={...}`
- **类型**: `text/event-stream` (SSE 流式响应)
- **开发要点**: 
  - 不能使用普通的 axios request，需要使用 `EventSource` 监听数据流，或者 `fetch` 结合 `ReadableStream` 从而实现流式文字的逐字打出。
  - 响应的数据需拼接到当前消息中，触发 Markdown 重新渲染。

### 4.3 AI 模型 (Model API)
- `GET /api/ai-models`: 获取模型列表供下拉框使用。
- `GET /api/ai-models/default`: 获取默认模型。

### 4.4 文档上传与查询 (Document & Search API)
- `POST /api/upload`: 上传文件（使用 `FormData`，key 为 `file`）。
- `GET /api/download/{chatId}`: 附件下载。
- `GET /api/search/fuzzy?query=&chatId=&topK=5`: 模糊检索。

### 4.5 MCP 配置管理 (Admin API)
- `GET /api/admin/mcp`: 获取列表。
- `POST /api/admin/mcp`: 新增配置。
- `PUT /api/admin/mcp/{mcpId}`: 更新。
- `DELETE /api/admin/mcp/{mcpId}`: 删除。
- `POST /api/admin/mcp/{mcpId}/reload`: 通知后端重载配置。
- `GET /api/admin/mcp/stats/loaded`: 仪表盘统计。

## 5. 组件与状态设计规范 (Pinia)

- **`useChatStore`**:
  - `sessions`: 当前的会话列表。
  - `activeSessionId`: 当前高亮的会话 ID。
  - `messages`: 当前会话的消息列表。
  - `isGenerating`: 标识 AI 是否正在回复，用于控制按钮禁用态及加载动画。
- **`useModelStore`**:
  - `models`: 可用模型列表。
  - `currentModel`: 用户当前选中的模型。

## 6. UI/UX 体验与规范要求

1. **响应式设计 (Responsive)**: 兼容 PC 和平板（Pad）展示，聊天界面在窄屏下自动隐藏左侧侧边栏，改为抽屉(Drawer)呼出。
2. **优雅的错误处理**: 请求失败或断网时，不应白屏，而应通过全局 Message/Notification 组件提示，比如“会话生成失败，请重试”。
3. **动画效果**: 消息的出现使用流畅的缓动动画 (`transition: all 0.3s ease`)；切换不同会话时有平滑过渡。
4. **加载状态**: 上传文件时需要有进度条或 Loading 遮罩；SSE 流请求未返回第一个字符时，显示“AI 正在思考...”的骨架屏或闪烁点。

## 7. 目录结构约定

```text
src/
├── api/          # 接口封装 (分模块：chat.ts, session.ts, mcp.ts等)
├── assets/       # 静态资源 (图片、全局 CSS 等)
├── components/   # 公共组件 (MessageBubble, MarkdownView 等)
├── composables/  # Vue 3 Hooks (如 useSSE.ts, useMarkdown.ts)
├── layouts/      # 布局组件 (MainLayout.vue, AdminLayout.vue)
├── router/       # 路由配置
├── store/        # Pinia 状态管理
├── utils/        # 工具函数 (request.ts, format.ts)
├── views/        # 页面视图 (Chat/index.vue, Admin/McpList.vue)
├── App.vue
└── main.ts
```

按照此技术需求文档进行开发，能够确保前端代码质量高、逻辑清晰、组件复用率高，且视觉效果满足现代化 SaaS 应用的标准。

---

## 8. V2 扩展：Agent 策略对话（智能体对话模式）

> 此功能是 V1（简单单轮 AI 聊天）的增强扩展。后端新增了 3 种 Agent 执行策略：Fixed / Auto / Flow，用户可以选择不同的 Agent 来获得不同的对话体验。

### 8.1 新增/变更的路由

```text
/                   -> 布局视图 (Layout)
├── /chat           -> AI 聊天主界面（原对话方式，不作改动）
├── /agent          -> Agent 智能体对话界面（新增）
├── /document       -> 知识库/文档上传与解析界面
└── /admin          -> 管理后台
    ├── /admin/mcp  -> MCP 工具配置管理界面
    ├── /admin/agents -> Agent 智能体列表管理（新增）
    └── /admin/agents/:agentId/flow -> Agent 流程步骤配置（新增）
```

### 8.2 新增 API 对接

#### 8.2.1 Agent 执行（核心 SSE 接口）

```
POST /api/agent/execute
Content-Type: application/json
Response-Type: text/event-stream (SSE)
```

**请求参数：**
```json
{
  "aiAgentId": "agent001",       // 智能体ID（必填）
  "message": "用户的问题或任务",  // 用户输入（必填）
  "sessionId": "xxx",            // 会话ID（可选，不传后端自动生成）
  "maxStep": 4                   // 最大执行步数（可选，默认3-4步）
}
```

**SSE 响应格式：**（不同于普通聊天，每条 data 都有明确语义）

```
// --- Fixed 策略示例：2步链 ---
data: 步骤1/2 **默认AI助手**\n\n[AI回复内容]

data: 步骤2/2 **结果优化器**\n\n[润色后的内容]

data: [最终汇总结果]

data: [DONE / 执行完成]

// --- Auto 策略示例：4步链 ---
data: ## 🔍 任务分析结果\n\n[分析内容]

data: ## ⚡ 执行结果\n\n[执行内容]

data: ## ✅ 质量检查\n\n[监督内容]

data: ## 📋 最终结果\n\n[汇总内容]

data: Agent 任务执行完成 ✅

// --- Flow 策略示例：4步链 ---
data: ## 🔧 MCP 工具能力分析\n\n[分析内容]

data: ## 📋 执行计划\n\n[计划内容]

data: ## ⚙️ 执行记录\n\n[执行记录]

data: ## 📊 最终汇总\n\n[汇总内容]

data: Flow Agent 流程执行完成 ✅
```

**开发要点：**
- 同样使用 `@microsoft/fetch-event-source` 或原生 `EventSource` 消费 SSE。
- 但 **不要** 像普通聊天那样逐字追加到同一个消息气泡——每条 `data` 可能是一个独立的步骤，应展示为独立的卡片/区块。
- 需要根据内容前缀（如 `步骤1/2`、`## 🔍`）或内置关键词（`步骤`、`分析结果`、`质量检查`）来判断当前步骤的**类型**，从而决定展示样式。
- 流结束后（收到 complete / [DONE]），恢复输入框可用。

#### 8.2.2 Agent 列表

```
GET /api/agent/list
Response: ApiResponse<List<AiAgent>>
```

```json
// 返回数据示例
[
  {
    "agentId": "agent001",
    "agentName": "简单对话体（Fixed）",
    "description": "固定客户端链式执行，适用于有明确步骤的任务",
    "channel": "agent",
    "strategy": "fixedAgentExecuteStrategy",
    "status": 1
  },
  {
    "agentId": "agent002",
    "agentName": "智能分析体（Auto）",
    "description": "自动分析→执行→检查→总结，全自动完成复杂任务",
    "channel": "agent",
    "strategy": "autoAgentExecuteStrategy",
    "status": 1
  },
  {
    "agentId": "agent003",
    "agentName": "流程编排体（Flow）",
    "description": "按步骤分析→规划→解析→执行，适合多工具协作",
    "channel": "agent",
    "strategy": "flowAgentExecuteStrategy",
    "status": 1
  }
]
```

#### 8.2.3 Agent 管理（Admin CRUD）

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/admin/agents` | 获取所有 Agent 列表（含禁用） |
| `POST` | `/api/admin/agents` | 创建 Agent |
| `PUT` | `/api/admin/agents/{agentId}` | 更新 Agent |
| `DELETE` | `/api/admin/agents/{agentId}` | 删除 Agent |
| `GET` | `/api/admin/agents/{agentId}/flow` | 获取 Agent 的流程步骤列表 |
| `POST` | `/api/admin/agents/{agentId}/flow` | 新增/批量替换步骤 |
| `PUT` | `/api/admin/agents/{agentId}/flow/{flowId}` | 更新某个步骤 |
| `DELETE` | `/api/admin/agents/{agentId}/flow/{flowId}` | 删除某个步骤 |

> **注意**：上述 Admin 接口为后续规划，当前后端暂未全部实现。第一版优先使用 SSE Agent 执行接口。

### 8.3 新增 Pinia Store

#### `useAgentStore`

```typescript
// store/agent.ts
export const useAgentStore = defineStore('agent', () => {
  const agents = ref<AiAgent[]>([])         // 可用 Agent 列表
  const currentAgentId = ref<string | null>(null)  // 当前选中的 Agent
  const steps = ref<AgentStepResult[]>([])  // 当前对话的步骤结果列表
  const isExecuting = ref(false)            // 是否正在执行

  // actions
  const fetchAgents = async () => { /* GET /api/agent/list */ }
  const selectAgent = (agentId: string) => { currentAgentId.value = agentId }
  const executeAgent = async (message: string) => { /* POST /api/agent/execute + SSE */ }
  const stopExecution = () => { /* 中断 SSE */ }
  const clearSteps = () => { steps.value = [] }
})
```

**`steps` 的数据结构：**
```typescript
interface AgentStepResult {
  id: string               // 唯一标识
  stepIndex: number        // 步骤序号（1-based）
  stepName: string         // 步骤名称（如"默认AI助手"、"任务分析器"）
  type: 'analysis' | 'execution' | 'supervision' | 'summary' | 'complete' | 'error'
  content: string          // Markdown 内容
  isStreaming?: boolean    // 是否正在接收 SSE 内容
  isLast?: boolean         // 是否是最后一步
}
```

### 8.4 新增页面设计

#### 8.4.1 Agent 聊天主界面 (`/agent`)

**与普通聊天的区别：**
| 维度 | 普通聊天 `/chat` | Agent 聊天 `/agent` |
|:----|:---------------|:-----------------|
| 请求方式 | `GET /api/dchat` SSE | `POST /api/agent/execute` SSE |
| 回复结构 | 一段连续文本 | 多步骤，每步独立区块 |
| 输入框上方 | 模型选择器 | Agent 选择下拉框 + 模型选择器 |
| 消息展示 | 单一气泡 | 步骤卡片列表（步骤1→步骤2→...） |
| 用户消息 | 普通气泡 | 普通气泡（同左侧） |

**页面布局（从上到下）：**

```
┌─────────────────────────────────────────────┐
│  [Sidebar]                           [Header] │
│                                          │
│           ┌─ Agent选择下拉框 ─┐             │
│           │ agent001 简单对话体 ▼ │         │
│           └──────────────────┘             │
│                                            │
│   用户消息气泡                              │
│   ┌────────────────────────┐               │
│   │ 帮我写一篇Spring AI文章  │               │
│   └────────────────────────┘               │
│                                            │
│   步骤卡片 1/2  ┌──────────────┐           │
│   🔍 步骤名称   │ AI 回复内容  │           │
│   "默认AI助手"  │ (Markdown)   │           │
│                └──────────────┘           │
│                                            │
│   步骤卡片 2/2  ┌──────────────┐           │
│   ⚡ 步骤名称   │ 润色后的内容 │           │
│   "结果优化器"  │ (Markdown)   │           │
│                └──────────────┘           │
│                                            │
│   ┌──────────────────────────────────┐     │
│   │ 输入消息...            [发送]     │     │
│   └──────────────────────────────────┘     │
└─────────────────────────────────────────────┘
```

**步骤卡片的设计要点：**
- **步骤编号 + 步骤名称**：左上角显示 `步骤 1/2` 和步骤名称
- **类型图标**：根据 `type` 显示不同 emoji
  - `analysis` → 🔍（分析）
  - `execution` → ⚡（执行）
  - `supervision` → ✅（监督）
  - `summary` → 📋（总结）
  - `error` → ❌（错误）
- **背景色**：分析步骤使用浅蓝色、执行步骤使用浅绿色、监督使用浅黄色、总结使用浅紫色
- **Markdown 渲染**：卡片内容使用 `MarkdownView` 组件渲染
- **流式加载**：当 SSE 正在推流当前步骤时，卡片显示加载动画/骨架屏，完成后内容稳定显示
- **展开/收起**：长步骤卡片默认展开，用户可手动折叠

**Agent 选择下拉框的交互：**
- 默认选中列表中的第一个 Agent
- 选择后右侧展示简要描述（在输入框上方显示）
- 切换 Agent 时清空当前步骤列表

#### 8.4.2 Admin Agent 管理 (`/admin/agents`)

**表格列：**
| 列 | 说明 |
|:--|------|
| Agent ID | `agentId` |
| 名称 | `agentName` |
| 策略 | `strategy`（显示为标签：fixed=蓝色, auto=紫色, flow=绿色） |
| 渠道 | `channel` |
| 状态 | 启用/禁用 switch |
| 操作 | 编辑、删除、**流程配置**（跳转到 Flow 配置页） |
| 描述 | `description` |

**新增/编辑弹窗：**
- Agent ID（新建时不可重复）
- 名称
- 渠道（下拉：agent / chat_stream）
- 策略（下拉：fixedAgentExecuteStrategy / autoAgentExecuteStrategy / flowAgentExecuteStrategy）
- 状态（switch）
- 描述（textarea）

#### 8.4.3 Admin Flow 步骤配置 (`/admin/agents/:agentId/flow`)

**用途：** 为 Fixed / Flow 策略配置具体的执行步骤顺序。

**布局：**

```
┌────────────────────────────────────────────┐
│ Agent名称: 简单对话体（Fixed）              │
│ 策略: fixedAgentExecuteStrategy  [返回]     │
├────────────────────────────────────────────┤
│                                            │
│  步骤列表（拖拽排序）                        │
│  ┌────────────────────────────────────┐    │
│  │ ① 默认AI助手  [编辑] [删除] [↕拖拽] │    │
│  │   提示词: 你是一个友好的AI助手...   │    │
│  ├────────────────────────────────────┤    │
│  │ ② 结果优化器  [编辑] [删除] [↕拖拽] │    │
│  │   提示词: 你是一个专业的文本优化...  │    │
│  └────────────────────────────────────┘    │
│                                            │
│  [+ 新增步骤]                               │
└────────────────────────────────────────────┘
```

**每个步骤的表单字段：**
| 字段 | 说明 |
|:----|------|
| Client ID | 客户端标识（字符串） |
| 名称 | 步骤显示名称，如"默认AI助手" |
| 类型 | 步骤类型标识 |
| 顺序 | 整数，可拖拽调整 |
| 提示词 | `stepPrompt` —— 该步骤的 System Prompt（textarea，支持多行） |

### 8.5 新增/修改的组件

| 组件名 | 类型 | 说明 |
|:------|:---:|------|
| `components/agent/AgentSelector.vue` | **新增** | Agent 选择下拉框（带描述） |
| `components/agent/StepCard.vue` | **新增** | 单个步骤卡片（类型图标 + 步骤名 + Markdown 内容） |
| `components/agent/StepCardList.vue` | **新增** | 步骤卡片列表（按顺序排列，自动滚动到底部） |
| `components/agent/StepConfigEditor.vue` | **新增** | 流程步骤编辑器（表单 + 顺序调整） |
| `components/chat/ChatInput.vue` | **修改** | 增加 `mode` prop：`'chat'` / `'agent'`，agent 模式禁用文件上传 |
| `layouts/MainLayout.vue` | **修改** | 侧边栏增加 "Agent 对话" 导航入口 |

### 8.6 目录新增

```text
src/
├── api/
│   └── agent.ts                  # Agent 相关的 API 封装（新增）
├── store/
│   └── agent.ts                  # useAgentStore（新增）
├── components/
│   ├── agent/
│   │   ├── AgentSelector.vue     # Agent 选择下拉框（新增）
│   │   ├── StepCard.vue          # 步骤卡片（新增）
│   │   ├── StepCardList.vue      # 步骤卡片列表（新增）
│   │   └── StepConfigEditor.vue  # 流程步骤编辑器（新增）
│   └── chat/
│       └── ChatInput.vue         # 修改：增加 agent mode
└── views/
    ├── Agent/
    │   └── index.vue             # /agent 页面（新增）
    └── Admin/
        ├── McpList.vue
        ├── AgentList.vue         # /admin/agents 页面（新增）
        └── AgentFlowConfig.vue   # /admin/agents/:id/flow（新增）
```

### 8.7 API 封装参考（`api/agent.ts`）

```typescript
import request from '@/utils/request'
import type { AgentStepResult } from '@/types'

/** 获取可用 Agent 列表 */
export const getAgentList = (): Promise<AiAgent[]> =>
  request.get('/agent/list')

/** Agent 执行（SSE） —— 此接口不走 axios，使用 fetchEventSource */
export const executeAgent = (
  params: { aiAgentId: string; message: string; sessionId?: string; maxStep?: number },
  onMessage: (data: string) => void,
  onDone: () => void,
  onError?: (err: unknown) => void
) => {
  /* 使用 @microsoft/fetch-event-source 的 fetchEventSource
     发送 POST 请求到 /api/agent/execute
     每次 onmessage 解析 event.data，传给 onMessage 回调
     前端根据内容特征判断步骤类型 */
}
```

### 8.8 前端步骤类型判断逻辑

由于后端 SSE 的 `data` 内容是纯文本，前端需要**按内容特征**来判断当前数据类型：

```typescript
function parseStepType(data: string): 'analysis' | 'execution' | 'supervision' | 'summary' | 'complete' | 'error' | 'unknown' {
  if (data.includes('执行完成 ✅') || data.includes('[DONE]')) return 'complete'
  if (data.startsWith('❌') || data.startsWith('error')) return 'error'
  if (data.includes('🔍') || data.includes('分析结果') || data.includes('任务分析')) return 'analysis'
  if (data.includes('⚡') || data.includes('执行结果') || data.includes('执行记录')) return 'execution'
  if (data.includes('✅') || data.includes('质量检查') || data.includes('质量监督')) return 'supervision'
  if (data.includes('📋') || data.includes('最终结果') || data.includes('最终汇总')) return 'summary'
  // Fixed 策略：data 以"步骤X/Y"开头
  if (/^步骤\d+\/\d+/.test(data)) return 'execution'
  return 'summary'
}
```

### 8.9 Agent 对话状态管理流程

```
用户选择 Agent → 获取 Agent 列表
用户输入消息 → POST /api/agent/execute (SSE)
              ↓
        isExecuting = true
              ↓
        SSE onMessage:
          data: "步骤1/2 **名称**\n\n内容"
          → 解析 type = 'execution', stepName = '名称'
          → push 新 StepCard 到 steps[]
          → 或追加到最后一个 steps[last].content
              ↓
        SSE onDone / onComplete:
          → 最后一个步骤标记 isLast = true
          → isExecuting = false
```

### 8.10 新增类型定义

```typescript
// types/index.ts 追加

/** Agent 智能体 */
interface AiAgent {
  agentId: string
  agentName: string
  description: string
  channel: string
  strategy: string
  status: number
}

/** Agent 执行步骤 */
interface AgentFlowStep {
  id?: number
  agentId: string
  clientId: string
  clientName: string
  clientType: string
  sequence: number
  stepPrompt: string
}

/** 前端展示的步骤结果卡片 */
interface AgentStepResult {
  id: string
  stepIndex: number
  stepName: string
  type: 'analysis' | 'execution' | 'supervision' | 'summary' | 'complete' | 'error'
  content: string
  isStreaming?: boolean
  isLast?: boolean
}
```

按照此技术需求文档进行开发，能够确保前端代码质量高、逻辑清晰、组件复用率高，且视觉效果满足现代化 SaaS 应用的标准。

---

## 8. V2 扩展：Agent 策略对话（智能体对话模式）

> 此功能是 V1（简单单轮 AI 聊天）的增强扩展。后端新增了 3 种 Agent 执行策略：Fixed / Auto / Flow，用户可以选择不同的 Agent 来获得不同的对话体验。

### 8.1 新增/变更的路由

```text
/                   -> 布局视图 (Layout)
├── /chat           -> AI 聊天主界面（原对话方式，不作改动）
├── /agent          -> Agent 智能体对话界面（新增）
├── /document       -> 知识库/文档上传与解析界面
└── /admin          -> 管理后台
    ├── /admin/mcp  -> MCP 工具配置管理界面
    ├── /admin/agents -> Agent 智能体列表管理（新增）
    └── /admin/agents/:agentId/flow -> Agent 流程步骤配置（新增）
```

### 8.2 新增 API 对接

#### 8.2.1 Agent 执行（核心 SSE 接口）

```
POST /api/agent/execute
Content-Type: application/json
Response-Type: text/event-stream (SSE)
```

**请求参数：**
```json
{
  "aiAgentId": "agent001",       // 智能体ID（必填）
  "message": "用户的问题或任务",  // 用户输入（必填）
  "sessionId": "xxx",            // 会话ID（可选，不传后端自动生成）
  "maxStep": 4                   // 最大执行步数（可选，默认3-4步）
}
```

**SSE 响应格式：**（不同于普通聊天，每条 data 都有明确语义）

```
// --- Fixed 策略示例：2步链 ---
data: 步骤1/2 **默认AI助手**\n\n[AI回复内容]

data: 步骤2/2 **结果优化器**\n\n[润色后的内容]

data: [最终汇总结果]

data: [DONE / 执行完成]

// --- Auto 策略示例：4步链 ---
data: ## 🔍 任务分析结果\n\n[分析内容]

data: ## ⚡ 执行结果\n\n[执行内容]

data: ## ✅ 质量检查\n\n[监督内容]

data: ## 📋 最终结果\n\n[汇总内容]

data: Agent 任务执行完成 ✅

// --- Flow 策略示例：4步链 ---
data: ## 🔧 MCP 工具能力分析\n\n[分析内容]

data: ## 📋 执行计划\n\n[计划内容]

data: ## ⚙️ 执行记录\n\n[执行记录]

data: ## 📊 最终汇总\n\n[汇总内容]

data: Flow Agent 流程执行完成 ✅
```

**开发要点：**
- 同样使用 `@microsoft/fetch-event-source` 或原生 `EventSource` 消费 SSE。
- 但 **不要** 像普通聊天那样逐字追加到同一个消息气泡——每条 `data` 可能是一个独立的步骤，应展示为独立的卡片/区块。
- 需要根据内容前缀（如 `步骤1/2`、`## 🔍`）或内置关键词（`步骤`、`分析结果`、`质量检查`）来判断当前步骤的**类型**，从而决定展示样式。
- 流结束后（收到 complete / [DONE]），恢复输入框可用。

#### 8.2.2 Agent 列表

```
GET /api/agent/list
Response: ApiResponse<List<AiAgent>>
```

```json
// 返回数据示例
[
  {
    "agentId": "agent001",
    "agentName": "简单对话体（Fixed）",
    "description": "固定客户端链式执行，适用于有明确步骤的任务",
    "channel": "agent",
    "strategy": "fixedAgentExecuteStrategy",
    "status": 1
  },
  {
    "agentId": "agent002",
    "agentName": "智能分析体（Auto）",
    "description": "自动分析→执行→检查→总结，全自动完成复杂任务",
    "channel": "agent",
    "strategy": "autoAgentExecuteStrategy",
    "status": 1
  },
  {
    "agentId": "agent003",
    "agentName": "流程编排体（Flow）",
    "description": "按步骤分析→规划→解析→执行，适合多工具协作",
    "channel": "agent",
    "strategy": "flowAgentExecuteStrategy",
    "status": 1
  }
]
```

#### 8.2.3 Agent 管理（Admin CRUD）

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/admin/agents` | 获取所有 Agent 列表（含禁用） |
| `POST` | `/api/admin/agents` | 创建 Agent |
| `PUT` | `/api/admin/agents/{agentId}` | 更新 Agent |
| `DELETE` | `/api/admin/agents/{agentId}` | 删除 Agent |
| `GET` | `/api/admin/agents/{agentId}/flow` | 获取 Agent 的流程步骤列表 |
| `POST` | `/api/admin/agents/{agentId}/flow` | 新增/批量替换步骤 |
| `PUT` | `/api/admin/agents/{agentId}/flow/{flowId}` | 更新某个步骤 |
| `DELETE` | `/api/admin/agents/{agentId}/flow/{flowId}` | 删除某个步骤 |

> **注意**：上述 Admin 接口为后续规划，当前后端暂未全部实现。第一版优先使用 SSE Agent 执行接口。

### 8.3 新增 Pinia Store

#### `useAgentStore`

```typescript
// store/agent.ts
export const useAgentStore = defineStore('agent', () => {
  const agents = ref<AiAgent[]>([])         // 可用 Agent 列表
  const currentAgentId = ref<string | null>(null)  // 当前选中的 Agent
  const steps = ref<AgentStepResult[]>([])  // 当前对话的步骤结果列表
  const isExecuting = ref(false)            // 是否正在执行

  // actions
  const fetchAgents = async () => { /* GET /api/agent/list */ }
  const selectAgent = (agentId: string) => { currentAgentId.value = agentId }
  const executeAgent = async (message: string) => { /* POST /api/agent/execute + SSE */ }
  const stopExecution = () => { /* 中断 SSE */ }
  const clearSteps = () => { steps.value = [] }
})
```

**`steps` 的数据结构：**
```typescript
interface AgentStepResult {
  id: string               // 唯一标识
  stepIndex: number        // 步骤序号（1-based）
  stepName: string         // 步骤名称（如"默认AI助手"、"任务分析器"）
  type: 'analysis' | 'execution' | 'supervision' | 'summary' | 'complete' | 'error'
  content: string          // Markdown 内容
  isStreaming?: boolean    // 是否正在接收 SSE 内容
  isLast?: boolean         // 是否是最后一步
}
```

### 8.4 新增页面设计

#### 8.4.1 Agent 聊天主界面 (`/agent`)

**与普通聊天的区别：**
| 维度 | 普通聊天 `/chat` | Agent 聊天 `/agent` |
|:----|:---------------|:-----------------|
| 请求方式 | `GET /api/dchat` SSE | `POST /api/agent/execute` SSE |
| 回复结构 | 一段连续文本 | 多步骤，每步独立区块 |
| 输入框上方 | 模型选择器 | Agent 选择下拉框 + 模型选择器 |
| 消息展示 | 单一气泡 | 步骤卡片列表（步骤1→步骤2→...） |
| 用户消息 | 普通气泡 | 普通气泡（同左侧） |

**页面布局（从上到下）：**

```
┌─────────────────────────────────────────────┐
│  [Sidebar]                           [Header] │
│                                          │
│           ┌─ Agent选择下拉框 ─┐             │
│           │ agent001 简单对话体 ▼ │         │
│           └──────────────────┘             │
│                                            │
│   用户消息气泡                              │
│   ┌────────────────────────┐               │
│   │ 帮我写一篇Spring AI文章  │               │
│   └────────────────────────┘               │
│                                            │
│   步骤卡片 1/2  ┌──────────────┐           │
│   🔍 步骤名称   │ AI 回复内容  │           │
│   "默认AI助手"  │ (Markdown)   │           │
│                └──────────────┘           │
│                                            │
│   步骤卡片 2/2  ┌──────────────┐           │
│   ⚡ 步骤名称   │ 润色后的内容 │           │
│   "结果优化器"  │ (Markdown)   │           │
│                └──────────────┘           │
│                                            │
│   ┌──────────────────────────────────┐     │
│   │ 输入消息...            [发送]     │     │
│   └──────────────────────────────────┘     │
└─────────────────────────────────────────────┘
```

**步骤卡片的设计要点：**
- **步骤编号 + 步骤名称**：左上角显示 `步骤 1/2` 和步骤名称
- **类型图标**：根据 `type` 显示不同 emoji
  - `analysis` → 🔍（分析）
  - `execution` → ⚡（执行）
  - `supervision` → ✅（监督）
  - `summary` → 📋（总结）
  - `error` → ❌（错误）
- **背景色**：分析步骤使用浅蓝色、执行步骤使用浅绿色、监督使用浅黄色、总结使用浅紫色
- **Markdown 渲染**：卡片内容使用 `MarkdownView` 组件渲染
- **流式加载**：当 SSE 正在推流当前步骤时，卡片显示加载动画/骨架屏，完成后内容稳定显示
- **展开/收起**：长步骤卡片默认展开，用户可手动折叠

**Agent 选择下拉框的交互：**
- 默认选中列表中的第一个 Agent
- 选择后右侧展示简要描述（在输入框上方显示）
- 切换 Agent 时清空当前步骤列表

#### 8.4.2 Admin Agent 管理 (`/admin/agents`)

**表格列：**
| 列 | 说明 |
|:--|------|
| Agent ID | `agentId` |
| 名称 | `agentName` |
| 策略 | `strategy`（显示为标签：fixed=蓝色, auto=紫色, flow=绿色） |
| 渠道 | `channel` |
| 状态 | 启用/禁用 switch |
| 操作 | 编辑、删除、**流程配置**（跳转到 Flow 配置页） |
| 描述 | `description` |

**新增/编辑弹窗：**
- Agent ID（新建时不可重复）
- 名称
- 渠道（下拉：agent / chat_stream）
- 策略（下拉：fixedAgentExecuteStrategy / autoAgentExecuteStrategy / flowAgentExecuteStrategy）
- 状态（switch）
- 描述（textarea）

#### 8.4.3 Admin Flow 步骤配置 (`/admin/agents/:agentId/flow`)

**用途：** 为 Fixed / Flow 策略配置具体的执行步骤顺序。

**布局：**

```
┌────────────────────────────────────────────┐
│ Agent名称: 简单对话体（Fixed）              │
│ 策略: fixedAgentExecuteStrategy  [返回]     │
├────────────────────────────────────────────┤
│                                            │
│  步骤列表（拖拽排序）                        │
│  ┌────────────────────────────────────┐    │
│  │ ① 默认AI助手  [编辑] [删除] [↕拖拽] │    │
│  │   提示词: 你是一个友好的AI助手...   │    │
│  ├────────────────────────────────────┤    │
│  │ ② 结果优化器  [编辑] [删除] [↕拖拽] │    │
│  │   提示词: 你是一个专业的文本优化...  │    │
│  └────────────────────────────────────┘    │
│                                            │
│  [+ 新增步骤]                               │
└────────────────────────────────────────────┘
```

**每个步骤的表单字段：**
| 字段 | 说明 |
|:----|------|
| Client ID | 客户端标识（字符串） |
| 名称 | 步骤显示名称，如"默认AI助手" |
| 类型 | 步骤类型标识 |
| 顺序 | 整数，可拖拽调整 |
| 提示词 | `stepPrompt` —— 该步骤的 System Prompt（textarea，支持多行） |

### 8.5 新增/修改的组件

| 组件名 | 类型 | 说明 |
|:------|:---:|------|
| `components/agent/AgentSelector.vue` | **新增** | Agent 选择下拉框（带描述） |
| `components/agent/StepCard.vue` | **新增** | 单个步骤卡片（类型图标 + 步骤名 + Markdown 内容） |
| `components/agent/StepCardList.vue` | **新增** | 步骤卡片列表（按顺序排列，自动滚动到底部） |
| `components/agent/StepConfigEditor.vue` | **新增** | 流程步骤编辑器（表单 + 顺序调整） |
| `components/chat/ChatInput.vue` | **修改** | 增加 `mode` prop：`'chat'` / `'agent'`，agent 模式禁用文件上传 |
| `layouts/MainLayout.vue` | **修改** | 侧边栏增加 "Agent 对话" 导航入口 |

### 8.6 目录新增

```text
src/
├── api/
│   └── agent.ts                  # Agent 相关的 API 封装（新增）
├── store/
│   └── agent.ts                  # useAgentStore（新增）
├── components/
│   ├── agent/
│   │   ├── AgentSelector.vue     # Agent 选择下拉框（新增）
│   │   ├── StepCard.vue          # 步骤卡片（新增）
│   │   ├── StepCardList.vue      # 步骤卡片列表（新增）
│   │   └── StepConfigEditor.vue  # 流程步骤编辑器（新增）
│   └── chat/
│       └── ChatInput.vue         # 修改：增加 agent mode
└── views/
    ├── Agent/
    │   └── index.vue             # /agent 页面（新增）
    └── Admin/
        ├── McpList.vue
        ├── AgentList.vue         # /admin/agents 页面（新增）
        └── AgentFlowConfig.vue   # /admin/agents/:id/flow（新增）
```

### 8.7 API 封装参考（`api/agent.ts`）

```typescript
import request from '@/utils/request'
import type { AgentStepResult } from '@/types'

/** 获取可用 Agent 列表 */
export const getAgentList = (): Promise<AiAgent[]> =>
  request.get('/agent/list')

/** Agent 执行（SSE） —— 此接口不走 axios，使用 fetchEventSource */
export const executeAgent = (
  params: { aiAgentId: string; message: string; sessionId?: string; maxStep?: number },
  onMessage: (data: string) => void,
  onDone: () => void,
  onError?: (err: unknown) => void
) => {
  /* 使用 @microsoft/fetch-event-source 的 fetchEventSource
     发送 POST 请求到 /api/agent/execute
     每次 onmessage 解析 event.data，传给 onMessage 回调
     前端根据内容特征判断步骤类型 */
}
```

### 8.8 前端步骤类型判断逻辑

由于后端 SSE 的 `data` 内容是纯文本，前端需要**按内容特征**来判断当前数据类型：

```typescript
function parseStepType(data: string): 'analysis' | 'execution' | 'supervision' | 'summary' | 'complete' | 'error' | 'unknown' {
  if (data.includes('执行完成 ✅') || data.includes('[DONE]')) return 'complete'
  if (data.startsWith('❌') || data.startsWith('error')) return 'error'
  if (data.includes('🔍') || data.includes('分析结果') || data.includes('任务分析')) return 'analysis'
  if (data.includes('⚡') || data.includes('执行结果') || data.includes('执行记录')) return 'execution'
  if (data.includes('✅') || data.includes('质量检查') || data.includes('质量监督')) return 'supervision'
  if (data.includes('📋') || data.includes('最终结果') || data.includes('最终汇总')) return 'summary'
  // Fixed 策略：data 以"步骤X/Y"开头
  if (/^步骤\d+\/\d+/.test(data)) return 'execution'
  return 'summary'
}
```

### 8.9 Agent 对话状态管理流程

```
用户选择 Agent → 获取 Agent 列表
用户输入消息 → POST /api/agent/execute (SSE)
              ↓
        isExecuting = true
              ↓
        SSE onMessage:
          data: "步骤1/2 **名称**\n\n内容"
          → 解析 type = 'execution', stepName = '名称'
          → push 新 StepCard 到 steps[]
          → 或追加到最后一个 steps[last].content
              ↓
        SSE onDone / onComplete:
          → 最后一个步骤标记 isLast = true
          → isExecuting = false
```

### 8.10 新增类型定义

```typescript
// types/index.ts 追加

/** Agent 智能体 */
interface AiAgent {
  agentId: string
  agentName: string
  description: string
  channel: string
  strategy: string
  status: number
}

/** Agent 执行步骤 */
interface AgentFlowStep {
  id?: number
  agentId: string
  clientId: string
  clientName: string
  clientType: string
  sequence: number
  stepPrompt: string
}

/** 前端展示的步骤结果卡片 */
interface AgentStepResult {
  id: string
  stepIndex: number
  stepName: string
  type: 'analysis' | 'execution' | 'supervision' | 'summary' | 'complete' | 'error'
  content: string
  isStreaming?: boolean
  isLast?: boolean
}
```

按照此技术需求文档进行开发，能够确保前端代码质量高、逻辑清晰、组件复用率高，且视觉效果满足现代化 SaaS 应用的标准。