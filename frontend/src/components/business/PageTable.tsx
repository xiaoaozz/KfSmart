import { Table, type TableProps, type TableColumnType } from 'antd'
import type { Key } from 'react'

export type { TableColumnType }

interface PageTableProps<T extends object> extends Omit<TableProps<T>, 'pagination'> {
  total?: number
  current?: number
  pageSize?: number
  onPageChange?: (page: number, size: number) => void
}

export default function PageTable<T extends object>({
  total = 0,
  current = 1,
  pageSize = 20,
  onPageChange,
  rowKey,
  ...rest
}: PageTableProps<T>) {
  return (
    <Table<T>
      rowKey={(rowKey as ((record: T) => Key) | undefined) ?? 'id'}
      size="middle"
      pagination={
        total > 0
          ? {
              total,
              current,
              pageSize,
              showSizeChanger: true,
              showQuickJumper: true,
              showTotal: (n) => `共 ${n} 条`,
              onChange: onPageChange,
              pageSizeOptions: ['10', '20', '50', '100'],
            }
          : false
      }
      scroll={{ x: 'max-content' }}
      {...rest}
    />
  )
}
