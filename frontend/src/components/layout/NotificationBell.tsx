import { Badge, Button, List, Popover, Spin, Typography, App, Empty } from 'antd'
import { BellOutlined, CheckOutlined } from '@ant-design/icons'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { http } from '@/api/http'

interface Notification {
  id: number
  actionType: string
  resourceName?: string
  message: string
  read: boolean
  createdAt: string
}

function useUnreadCount() {
  return useQuery<{ unreadCount: number }>({
    queryKey: ['notifications', 'unread-count'],
    queryFn: () =>
      http.get<{ unreadCount: number }>('/notifications/unread-count').then((r) => r.data),
    refetchInterval: 60_000,
    retry: false,
  })
}

function NotificationList() {
  const qc = useQueryClient()
  const { message } = App.useApp()

  const { data, isLoading } = useQuery<Notification[]>({
    queryKey: ['notifications'],
    queryFn: () => http.get<Notification[]>('/notifications').then((r) => r.data),
    retry: false,
  })

  const readOne = useMutation({
    mutationFn: (id: number) => http.put(`/notifications/${id}/read`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['notifications'] })
      qc.invalidateQueries({ queryKey: ['notifications', 'unread-count'] })
    },
  })

  const readAll = useMutation({
    mutationFn: () => http.put('/notifications/read-all'),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['notifications'] })
      qc.invalidateQueries({ queryKey: ['notifications', 'unread-count'] })
      message.success('已全部标记为已读')
    },
  })

  if (isLoading) return <Spin style={{ padding: 24 }} />
  if (!data?.length) {
    return (
      <Empty
        image={Empty.PRESENTED_IMAGE_SIMPLE}
        description="暂无通知"
        style={{ padding: '16px 0' }}
      />
    )
  }

  const hasUnread = data.some((n) => !n.read)

  return (
    <div style={{ width: 340 }}>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          padding: '8px 16px',
          borderBottom: '1px solid var(--kf-border)',
        }}
      >
        <Typography.Text strong style={{ fontSize: 13 }}>
          通知
        </Typography.Text>
        {hasUnread && (
          <Button
            type="link"
            size="small"
            icon={<CheckOutlined />}
            loading={readAll.isPending}
            onClick={() => readAll.mutate()}
            style={{ padding: 0, fontSize: 12 }}
          >
            全部已读
          </Button>
        )}
      </div>
      <List
        style={{ maxHeight: 360, overflow: 'auto' }}
        dataSource={data}
        renderItem={(item) => (
          <List.Item
            key={item.id}
            style={{
              padding: '10px 16px',
              background: item.read ? 'transparent' : 'var(--kf-accent-bg, rgba(0,0,0,0.02))',
              cursor: item.read ? 'default' : 'pointer',
            }}
            onClick={() => {
              if (!item.read) readOne.mutate(item.id)
            }}
          >
            <List.Item.Meta
              title={
                <span style={{ fontSize: 13 }}>
                  {!item.read && <Badge status="processing" style={{ marginRight: 6 }} />}
                  {item.resourceName || item.actionType}
                </span>
              }
              description={
                <span style={{ fontSize: 12, color: 'var(--kf-muted-foreground)' }}>
                  {item.message}
                  {item.createdAt && (
                    <span style={{ marginLeft: 6 }}>
                      {new Date(item.createdAt).toLocaleString()}
                    </span>
                  )}
                </span>
              }
            />
          </List.Item>
        )}
      />
    </div>
  )
}

export default function NotificationBell() {
  const { data } = useUnreadCount()
  const unreadCount = data?.unreadCount ?? 0

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
