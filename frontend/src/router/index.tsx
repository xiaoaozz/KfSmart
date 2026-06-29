import { lazy, Suspense } from 'react'
import { createBrowserRouter, Navigate, Outlet, useLocation } from 'react-router-dom'
import { Spin } from 'antd'
import { useAuthStore } from '@/stores/auth'
import { usePermission } from '@/hooks/usePermission'
import BasicLayout from '@/layouts/BasicLayout'
import AuthLayout from '@/layouts/AuthLayout'
import ErrorBoundary from '@/components/business/ErrorBoundary'

// ------------------------------------------------------------------ lazy pages
const LoginPage = lazy(() => import('@/pages/auth/LoginPage'))
const RegisterPage = lazy(() => import('@/pages/auth/RegisterPage'))
const DashboardPage = lazy(() => import('@/pages/dashboard/DashboardPage'))
const KbListPage = lazy(() => import('@/pages/knowledge-base/KbListPage'))
const KbDetailPage = lazy(() => import('@/pages/knowledge-base/KbDetailPage'))
const DocumentListPage = lazy(() => import('@/pages/document/DocumentListPage'))
const ChatPage = lazy(() => import('@/pages/chat/ChatPage'))
const AgentListPage = lazy(() => import('@/pages/agent/AgentListPage'))
const AgentEditorPage = lazy(() => import('@/pages/agent/AgentEditorPage'))
const AgentExecutionPage = lazy(() => import('@/pages/agent/AgentExecutionPage'))
const WorkflowListPage = lazy(() => import('@/pages/workflow/WorkflowListPage'))
const WorkflowEditorPage = lazy(() => import('@/pages/workflow/WorkflowEditorPage'))
const WorkflowExecutionPage = lazy(() => import('@/pages/workflow/WorkflowExecutionPage'))
const SkillListPage = lazy(() => import('@/pages/skill/SkillListPage'))
const SkillEditorPage = lazy(() => import('@/pages/skill/SkillEditorPage'))
const PromptListPage = lazy(() => import('@/pages/skill/PromptListPage'))
const McpToolPage = lazy(() => import('@/pages/skill/McpToolPage'))
const ModelConfigPage = lazy(() => import('@/pages/skill/ModelConfigPage'))
const ProfilePage = lazy(() => import('@/pages/profile/ProfilePage'))
const ExplorePage = lazy(() => import('@/pages/explore/ExplorePage'))
const ExploreChatPage = lazy(() => import('@/pages/explore/ExploreChatPage'))
// Admin
const AdminUsersPage = lazy(() => import('@/pages/admin/UserManagePage'))
const AdminRolesPage = lazy(() => import('@/pages/admin/RoleManagePage'))
const AdminOrgPage = lazy(() => import('@/pages/admin/OrgTagPage'))
const AdminSystemPage = lazy(() => import('@/pages/admin/SystemStatusPage'))
const AdminApiKeyPage = lazy(() => import('@/pages/admin/ApiKeyPage'))
const AdminActivityLogPage = lazy(() => import('@/pages/admin/ActivityLogPage'))
// Error
const Page403 = lazy(() => import('@/pages/error/403Page'))
const Page404 = lazy(() => import('@/pages/error/404Page'))
const Page500 = lazy(() => import('@/pages/error/500Page'))

// ------------------------------------------------------------------ guards
function PageLoader() {
  return (
    <div style={{ display: 'flex', justifyContent: 'center', padding: 80 }}>
      <Spin size="large" />
    </div>
  )
}

function RequireAuth() {
  const token = useAuthStore((s) => s.token)
  const location = useLocation()
  if (!token) return <Navigate to="/login" state={{ from: location }} replace />
  return <Outlet />
}

function RequireAdmin() {
  const { isAdmin, isAuthenticated } = usePermission()
  if (!isAuthenticated) return <Navigate to="/login" replace />
  if (!isAdmin) return <Navigate to="/403" replace />
  return <Outlet />
}

function RedirectIfAuthed() {
  const token = useAuthStore((s) => s.token)
  if (token) return <Navigate to="/dashboard" replace />
  return <Outlet />
}

function SuspenseWrap({ children }: { children: React.ReactNode }) {
  return (
    <ErrorBoundary>
      <Suspense fallback={<PageLoader />}>{children}</Suspense>
    </ErrorBoundary>
  )
}

// ------------------------------------------------------------------ router
export const router = createBrowserRouter([
  // Auth routes
  {
    element: (
      <SuspenseWrap>
        <RedirectIfAuthed />
      </SuspenseWrap>
    ),
    children: [
      {
        element: <AuthLayout />,
        children: [
          { path: '/login', element: <LoginPage /> },
          { path: '/register', element: <RegisterPage /> },
        ],
      },
    ],
  },

  // Error pages
  {
    path: '/403',
    element: (
      <SuspenseWrap>
        <Page403 />
      </SuspenseWrap>
    ),
  },
  {
    path: '/500',
    element: (
      <SuspenseWrap>
        <Page500 />
      </SuspenseWrap>
    ),
  },

  // Protected routes
  {
    element: (
      <SuspenseWrap>
        <RequireAuth />
      </SuspenseWrap>
    ),
    children: [
      {
        element: <BasicLayout />,
        children: [
          { index: true, element: <Navigate to="/dashboard" replace /> },
          { path: '/dashboard', element: <DashboardPage /> },
          { path: '/chat', element: <ChatPage /> },
          { path: '/chat/:sessionId', element: <ChatPage /> },
          { path: '/knowledge-bases', element: <KbListPage /> },
          { path: '/knowledge-bases/:kbId', element: <KbDetailPage /> },
          { path: '/documents', element: <DocumentListPage /> },
          { path: '/agents', element: <AgentListPage /> },
          { path: '/agents/new', element: <AgentEditorPage /> },
          { path: '/agents/:id/edit', element: <AgentEditorPage /> },
          { path: '/agents/:id/executions', element: <AgentExecutionPage /> },
          { path: '/workflows', element: <WorkflowListPage /> },
          { path: '/workflows/:id/edit', element: <WorkflowEditorPage /> },
          { path: '/workflows/:id/executions', element: <WorkflowExecutionPage /> },
          { path: '/skills', element: <SkillListPage /> },
          { path: '/skills/:id/edit', element: <SkillEditorPage /> },
          { path: '/prompts', element: <PromptListPage /> },
          { path: '/mcp-tools', element: <McpToolPage /> },
          { path: '/models', element: <ModelConfigPage /> },
          { path: '/profile', element: <ProfilePage /> },
          { path: '/explore', element: <ExplorePage /> },
          { path: '/explore/:type/:id', element: <ExploreChatPage /> },

          // Admin sub-tree
          {
            element: <RequireAdmin />,
            children: [
              { path: '/admin/users', element: <AdminUsersPage /> },
              { path: '/admin/roles', element: <AdminRolesPage /> },
              { path: '/admin/orgs', element: <AdminOrgPage /> },
              { path: '/admin/system', element: <AdminSystemPage /> },
              { path: '/admin/api-keys', element: <AdminApiKeyPage /> },
              { path: '/admin/activity-logs', element: <AdminActivityLogPage /> },
            ],
          },
        ],
      },
    ],
  },

  // Catch-all
  {
    path: '*',
    element: (
      <SuspenseWrap>
        <Page404 />
      </SuspenseWrap>
    ),
  },
])
