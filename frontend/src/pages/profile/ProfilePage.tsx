import { useState } from 'react'
import { Menu } from 'antd'
import {
  UserOutlined,
  LockOutlined,
  BellOutlined,
  StarOutlined,
  HistoryOutlined,
  SafetyOutlined,
} from '@ant-design/icons'
import { motion, AnimatePresence } from 'framer-motion'
import BasicInfoSection from './sections/BasicInfoSection'
import PasswordSection from './sections/PasswordSection'
import NotificationSection from './sections/NotificationSection'
import FavoritesSection from './sections/FavoritesSection'
import ActivitySection from './sections/ActivitySection'
import LoginRecordSection from './sections/LoginRecordSection'
import styles from './ProfilePage.module.css'

const MENU_ITEMS = [
  { key: 'basic', icon: <UserOutlined />, label: '基本资料' },
  { key: 'password', icon: <LockOutlined />, label: '修改密码' },
  { key: 'notifications', icon: <BellOutlined />, label: '通知设置' },
  { key: 'favorites', icon: <StarOutlined />, label: '我的收藏' },
  { key: 'activity', icon: <HistoryOutlined />, label: '操作记录' },
  { key: 'login', icon: <SafetyOutlined />, label: '登录记录' },
]

const SECTIONS: Record<string, React.ReactNode> = {
  basic: <BasicInfoSection />,
  password: <PasswordSection />,
  notifications: <NotificationSection />,
  favorites: <FavoritesSection />,
  activity: <ActivitySection />,
  login: <LoginRecordSection />,
}

export default function ProfilePage() {
  const [activeKey, setActiveKey] = useState('basic')

  return (
    <div className={styles.root}>
      <div className={styles.sidebar}>
        <div className={styles.sidebarTitle}>个人中心</div>
        <Menu
          mode="inline"
          selectedKeys={[activeKey]}
          onClick={({ key }) => setActiveKey(key)}
          items={MENU_ITEMS}
          style={{ border: 'none', background: 'transparent' }}
        />
      </div>
      <div className={styles.content}>
        <AnimatePresence mode="wait">
          <motion.div
            key={activeKey}
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -6 }}
            transition={{ duration: 0.18 }}
          >
            {SECTIONS[activeKey]}
          </motion.div>
        </AnimatePresence>
      </div>
    </div>
  )
}
