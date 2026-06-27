import { Badge, Button, List, Popover, Spin, Typography } from 'antd'
import { BellOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { http } from '@/api/http'

interface Notification {
  id: number
  title: string
  content: string
  read: boolean
  createTime: string
}

function NotificationList() {
  const { data, isLoading } = useQuery<Notification[]>({
    queryKey: ['notifications', 'unread'],
    queryFn: () => http.get<Notification[]>('/notifications/unread').then((r) => r.data),
    refetchInterval: 60_000,
    retry: false,
  })

  if (isLoading) return <Spin style={{ padding: 24 }} />
  if (!data?.length) {
    return (
      <Typography.Text type="secondary" style={{ padding: '16px 24px', display: 'block' }}>
        暂无未读通知
      </Typography.Text>
    )
  }

  return (
    <List
      style={{ width: 320, maxHeight: 400, overflow: 'auto' }}
      dataSource={data}
      renderItem={(item) => (
        <List.Item key={item.id} style={{ padding: '10px 16px' }}>
          <List.Item.Meta
            title={<span style={{ fontSize: 13 }}>{item.title}</span>}
            description={
              <span style={{ fontSize: 12, color: 'var(--kf-muted-foreground)' }}>
                {item.content}
              </span>
            }
          />
        </List.Item>
      )}
    />
  )
}

export default function NotificationBell() {
  const { data } = useQuery<Notification[]>({
    queryKey: ['notifications', 'unread'],
    queryFn: () => http.get<Notification[]>('/notifications/unread').then((r) => r.data),
    refetchInterval: 60_000,
    retry: false,
  })

  const unreadCount = data?.length ?? 0

  return (
    <Popover content={<NotificationList />} trigger="click" placement="bottomRight" arrow={false}>
      <Badge count={unreadCount} size="small" offset={[-2, 2]}>
        <Button
          type="text"
          icon={<BellOutlined />}
          style={{ color: 'var(--kf-muted-foreground)' }}
        />
      </Badge>
    </Popover>
  )
}
