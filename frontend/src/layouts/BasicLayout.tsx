import { useEffect, useState, type ReactElement } from 'react'
import { Outlet, useNavigate, useLocation } from 'react-router-dom'
import { Layout, Menu, Avatar, Dropdown, Drawer, Button, Breadcrumb } from 'antd'
import {
  DashboardOutlined,
  DatabaseOutlined,
  FileTextOutlined,
  MessageOutlined,
  RobotOutlined,
  ApartmentOutlined,
  BookOutlined,
  UserOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  LogoutOutlined,
  ThunderboltOutlined,
  ApiOutlined,
  KeyOutlined,
  SafetyCertificateOutlined,
  DashboardFilled,
} from '@ant-design/icons'
import { AnimatePresence, motion } from 'framer-motion'
import { ThemeSwitch } from '@/components/base'
import NotificationBell from '@/components/layout/NotificationBell'
import { useAuthStore } from '@/stores/auth'
import { useLayoutStore } from '@/stores/layout'
import { useCurrentUser } from '@/hooks/usePermission'
import { authApi } from '@/api/auth'
import { features } from '@/config/features'
import styles from './BasicLayout.module.css'

const { Header, Sider, Content } = Layout

const NAV_ITEMS = [
  { key: '/dashboard', icon: <DashboardOutlined />, label: '仪表盘' },
  { key: '/chat', icon: <MessageOutlined />, label: 'AI 对话' },
  { key: '/knowledge-bases', icon: <DatabaseOutlined />, label: '知识库' },
  { key: '/documents', icon: <FileTextOutlined />, label: '文档管理' },
  features.orgManagement && { key: '/agents', icon: <RobotOutlined />, label: 'Agent 管理' },
  features.workflowEditor && {
    key: '/workflows',
    icon: <ApartmentOutlined />,
    label: '工作流',
  },
  {
    key: 'skill-group',
    icon: <BookOutlined />,
    label: '技能库',
    children: [
      { key: '/skills', icon: <ThunderboltOutlined />, label: '技能' },
      { key: '/prompts', icon: <FileTextOutlined />, label: 'Prompt' },
      { key: '/mcp-tools', icon: <ApiOutlined />, label: 'MCP 工具' },
      { key: '/models', icon: <RobotOutlined />, label: '模型' },
    ],
  },
  { key: '/profile', icon: <UserOutlined />, label: '个人中心' },
].filter(Boolean) as Array<{
  key: string
  icon: ReactElement
  label: string
  children?: Array<{ key: string; icon: ReactElement; label: string }>
}>

const ADMIN_ITEMS = [
  { key: '/admin/users', icon: <UserOutlined />, label: '用户管理' },
  { key: '/admin/roles', icon: <SafetyCertificateOutlined />, label: '角色权限' },
  { key: '/admin/orgs', icon: <ApartmentOutlined />, label: '组织标签' },
  { key: '/admin/api-keys', icon: <KeyOutlined />, label: 'API Key' },
  { key: '/admin/system', icon: <DashboardFilled />, label: '系统状态' },
  { key: '/admin/activity-logs', icon: <FileTextOutlined />, label: '操作日志' },
]

function SideMenu({ collapsed }: { collapsed: boolean }) {
  const navigate = useNavigate()
  const location = useLocation()
  const { data: user } = useCurrentUser()

  const allLeafKeys = NAV_ITEMS.flatMap((i) =>
    i.children ? i.children.map((c) => c.key) : [i.key],
  )
  const selectedKey =
    allLeafKeys.find((k) => location.pathname.startsWith(k)) ??
    ADMIN_ITEMS.find((i) => location.pathname.startsWith(i.key))?.key ??
    NAV_ITEMS.find((i) => !i.children && location.pathname.startsWith(i.key))?.key ??
    '/dashboard'

  const items = [
    ...NAV_ITEMS,
    ...(user?.role?.toUpperCase() === 'ADMIN'
      ? [{ type: 'divider' as const }, ...ADMIN_ITEMS]
      : []),
  ]

  return (
    <Menu
      mode="inline"
      selectedKeys={[selectedKey]}
      inlineCollapsed={collapsed}
      style={{ border: 'none', flex: 1, overflow: 'auto' }}
      onClick={({ key }) => navigate(key)}
      items={items}
    />
  )
}

function Logo({ collapsed }: { collapsed: boolean }) {
  const navigate = useNavigate()
  return (
    <div className={styles.logo} onClick={() => navigate('/dashboard')}>
      <span className={styles.logoIcon}>K</span>
      {!collapsed && <span className={styles.logoText}>KfSmart</span>}
    </div>
  )
}

export default function BasicLayout() {
  const { collapsed, setSiderCollapsed, toggleSider } = {
    collapsed: useLayoutStore((s) => s.siderCollapsed),
    setSiderCollapsed: useLayoutStore((s) => s.setSiderCollapsed),
    toggleSider: useLayoutStore((s) => s.toggleSider),
  }
  const clearTokens = useAuthStore((s) => s.clearTokens)
  const navigate = useNavigate()
  const location = useLocation()
  const { data: user } = useCurrentUser()

  // Lazy initializer reads matchMedia once — no synchronous setState in effect
  const [isMobile, setIsMobile] = useState(() => window.matchMedia('(max-width: 768px)').matches)
  const [drawerOpen, setDrawerOpen] = useState(false)

  useEffect(() => {
    const mq = window.matchMedia('(max-width: 768px)')
    const handler = (e: MediaQueryListEvent) => setIsMobile(e.matches)
    mq.addEventListener('change', handler)
    return () => mq.removeEventListener('change', handler)
  }, [])

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setDrawerOpen(false)
  }, [location.pathname])

  const handleLogout = async () => {
    try {
      await authApi.logout()
    } catch {
      // 即使后端调用失败也清除本地登录态
    }
    clearTokens()
    navigate('/login', { replace: true })
  }

  const userMenu = {
    items: [
      {
        key: 'profile',
        icon: <UserOutlined />,
        label: '个人中心',
        onClick: () => navigate('/profile'),
      },
      { type: 'divider' as const },
      {
        key: 'logout',
        icon: <LogoutOutlined />,
        label: '退出登录',
        danger: true,
        onClick: handleLogout,
      },
    ],
  }

  const sider = (
    <div className={styles.sider}>
      <Logo collapsed={isMobile ? false : collapsed} />
      <SideMenu collapsed={isMobile ? false : collapsed} />
      {!isMobile && (
        <div className={styles.siderFooter}>
          <Avatar
            size={collapsed ? 32 : 36}
            src={user?.avatar}
            style={{ background: 'var(--kf-accent)', cursor: 'pointer' }}
          >
            {user?.username?.[0]?.toUpperCase()}
          </Avatar>
          {!collapsed && (
            <div className={styles.userInfo}>
              <span className={styles.userName}>{user?.username}</span>
              <span className={styles.userRole}>
                {user?.role?.toUpperCase() === 'ADMIN' ? '管理员' : '用户'}
              </span>
            </div>
          )}
        </div>
      )}
    </div>
  )

  return (
    <Layout style={{ minHeight: '100vh', background: 'var(--kf-bg)' }}>
      {isMobile ? (
        <Drawer
          open={drawerOpen}
          onClose={() => setDrawerOpen(false)}
          placement="left"
          width={240}
          styles={{ body: { padding: 0 } }}
          closable={false}
        >
          {sider}
        </Drawer>
      ) : (
        <Sider
          width={240}
          collapsedWidth={64}
          collapsed={collapsed}
          onCollapse={setSiderCollapsed}
          style={{
            background: 'var(--kf-sidebar-bg)',
            borderRight: '1px solid var(--kf-sidebar-border)',
            overflow: 'hidden',
          }}
        >
          {sider}
        </Sider>
      )}

      <Layout>
        <Header className={styles.header}>
          <Button
            type="text"
            icon={
              isMobile ? (
                <MenuUnfoldOutlined />
              ) : collapsed ? (
                <MenuUnfoldOutlined />
              ) : (
                <MenuFoldOutlined />
              )
            }
            onClick={isMobile ? () => setDrawerOpen(true) : toggleSider}
            style={{ color: 'var(--kf-muted-foreground)' }}
          />

          <Breadcrumb
            className={styles.breadcrumb}
            items={[
              { title: 'KfSmart' },
              { title: NAV_ITEMS.find((i) => location.pathname.startsWith(i.key))?.label ?? '' },
            ]}
          />

          <div className={styles.headerRight}>
            <NotificationBell />
            <ThemeSwitch />
            <Dropdown menu={userMenu} placement="bottomRight" arrow>
              <Avatar
                size={32}
                src={user?.avatar}
                style={{ background: 'var(--kf-accent)', cursor: 'pointer' }}
              >
                {user?.username?.[0]?.toUpperCase()}
              </Avatar>
            </Dropdown>
          </div>
        </Header>

        <Content className={styles.content}>
          <AnimatePresence mode="wait">
            <motion.div
              key={location.pathname}
              initial={{ opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -8 }}
              transition={{ duration: 0.18, ease: 'easeOut' }}
              style={{ height: '100%' }}
            >
              <Outlet />
            </motion.div>
          </AnimatePresence>
        </Content>
      </Layout>
    </Layout>
  )
}
