import type { PaginationProps } from 'naive-ui';

import { transformRecordToOption } from '@/utils/common';

export const yesOrNoRecord: Record<CommonType.YesOrNo, App.I18n.I18nKey> = {
  Y: 'common.yesOrNo.yes',
  N: 'common.yesOrNo.no'
};

export const yesOrNoOptions = transformRecordToOption(yesOrNoRecord);

export const enableStatusOptions = [
  { label: '启用', value: 1 },
  { label: '禁用', value: 0 }
];

export const PAGINATION_PAGE_SIZES = [5, 10, 20, 50] as const;

export const DEFAULT_PAGE_SIZE = 10;

export const PAGINATION_PAGE_SIZE_OPTIONS: PaginationProps['pageSizes'] = PAGINATION_PAGE_SIZES.map(size => ({
  label: `${size}/页`,
  value: size
}));

export const chunkSize = 5 * 1024 * 1024;

export const uploadAccept = '.pdf,.doc,.docx,.txt,.rtf,.md,.xls,.xlsx,.ppt,.pptx,.odt,.ods,.odp,.html,.htm,.xml,.json,.csv,.epub,.pages,.numbers,.keynote';
