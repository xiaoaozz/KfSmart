import { useEffect, useState, type ReactElement } from 'react'
import { Outlet, useNavigate, useLocation } from 'react-router-dom'
import { Layout, Menu, Dropdown, Drawer, Button, Breadcrumb } from 'antd'
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
import { useTranslation } from 'react-i18next'
import { ThemeSwitch, LanguageSwitch } from '@/components/base'
import NotificationBell from '@/components/layout/NotificationBell'
import UserAvatar from '@/components/UserAvatar'
import { useAuthStore } from '@/stores/auth'
import { useLayoutStore } from '@/stores/layout'
import { useCurrentUser } from '@/hooks/usePermission'
import { authApi } from '@/api/auth'
import { features } from '@/config/features'
import styles from './BasicLayout.module.css'

const { Header, Sider, Content } = Layout

function useNavItems() {
  const { t } = useTranslation()

  const navItems = [
    { key: '/dashboard', icon: <DashboardOutlined />, label: t('nav.dashboard') },
    { key: '/chat', icon: <MessageOutlined />, label: t('nav.chat') },
    { key: '/knowledge-bases', icon: <DatabaseOutlined />, label: t('nav.knowledgeBase') },
    { key: '/documents', icon: <FileTextOutlined />, label: t('nav.documents') },
    features.orgManagement && { key: '/agents', icon: <RobotOutlined />, label: t('nav.agents') },
    features.workflowEditor && {
      key: '/workflows',
      icon: <ApartmentOutlined />,
      label: t('nav.workflows'),
    },
    {
      key: 'skill-group',
      icon: <BookOutlined />,
      label: t('nav.skillGroup'),
      children: [
        { key: '/skills', icon: <ThunderboltOutlined />, label: t('nav.skills') },
        { key: '/prompts', icon: <FileTextOutlined />, label: t('nav.prompts') },
        { key: '/mcp-tools', icon: <ApiOutlined />, label: t('nav.mcpTools') },
        { key: '/models', icon: <RobotOutlined />, label: t('nav.models') },
      ],
    },
    { key: '/profile', icon: <UserOutlined />, label: t('nav.profile') },
  ].filter(Boolean) as Array<{
    key: string
    icon: ReactElement
    label: string
    children?: Array<{ key: string; icon: ReactElement; label: string }>
  }>

  const adminItems = [
    { key: '/admin/users', icon: <UserOutlined />, label: t('nav.admin.users') },
    { key: '/admin/roles', icon: <SafetyCertificateOutlined />, label: t('nav.admin.roles') },
    { key: '/admin/orgs', icon: <ApartmentOutlined />, label: t('nav.admin.orgs') },
    { key: '/admin/api-keys', icon: <KeyOutlined />, label: t('nav.admin.apiKeys') },
    { key: '/admin/system', icon: <DashboardFilled />, label: t('nav.admin.system') },
    { key: '/admin/activity-logs', icon: <FileTextOutlined />, label: t('nav.admin.activityLogs') },
  ]

  return { navItems, adminItems }
}

function SideMenu({ collapsed }: { collapsed: boolean }) {
  const navigate = useNavigate()
  const location = useLocation()
  const { data: user } = useCurrentUser()
  const { navItems, adminItems } = useNavItems()

  const allLeafKeys = navItems.flatMap((i) => (i.children ? i.children.map((c) => c.key) : [i.key]))
  const selectedKey =
    allLeafKeys.find((k) => location.pathname.startsWith(k)) ??
    adminItems.find((i) => location.pathname.startsWith(i.key))?.key ??
    navItems.find((i) => !i.children && location.pathname.startsWith(i.key))?.key ??
    '/dashboard'

  const items = [
    ...navItems,
    ...(user?.role?.toUpperCase() === 'ADMIN' ? [{ type: 'divider' as const }, ...adminItems] : []),
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
  const { t } = useTranslation()
  const { navItems } = useNavItems()

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
        label: t('user.profile'),
        onClick: () => navigate('/profile'),
      },
      { type: 'divider' as const },
      {
        key: 'logout',
        icon: <LogoutOutlined />,
        label: t('user.logout'),
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
          <UserAvatar
            size={collapsed ? 32 : 36}
            avatar={user?.avatar}
            username={user?.username}
            style={{ cursor: 'pointer' }}
          />
          {!collapsed && (
            <div className={styles.userInfo}>
              <span className={styles.userName}>{user?.username}</span>
              <span className={styles.userRole}>
                {user?.role?.toUpperCase() === 'ADMIN' ? t('user.roleAdmin') : t('user.roleUser')}
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
              { title: navItems.find((i) => location.pathname.startsWith(i.key))?.label ?? '' },
            ]}
          />

          <div className={styles.headerRight}>
            <NotificationBell />
            <LanguageSwitch />
            <ThemeSwitch />
            <Dropdown menu={userMenu} placement="bottomRight" arrow>
              <UserAvatar
                size={32}
                avatar={user?.avatar}
                username={user?.username}
                style={{ cursor: 'pointer' }}
              />
            </Dropdown>
          </div>
        </Header>

        <Content className={styles.content}>
          <AnimatePresence mode="wait">
            <motion.div
              // Normalize the chat route so switching conversations
              // (/chat/A → /chat/B) keeps the same key — otherwise the page
              // fade transition fires on every switch, causing the "flash".
              key={
                location.pathname === '/chat' || location.pathname.startsWith('/chat/')
                  ? '/chat'
                  : location.pathname
              }
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
