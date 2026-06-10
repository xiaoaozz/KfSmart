import { fakePaginationRequest, request } from '../request';

/** Get flat list of org tags for table display (wraps in pagination format for useTable) */
export function fetchGetOrgTagList(params?: Api.Common.CommonSearchParams) {
  return fakePaginationRequest<Api.OrgTag.List>({ url: '/admin/org-tags', params });
}

/** Get org tag tree for cascader display (for regular users) */
export function fetchGetOrgTagTree() {
  return request<Api.OrgTag.Item[]>({ url: '/users/org-tags/tree' });
}

/** Get org tag tree for admin management (admin only) */
export function fetchGetAdminOrgTagTree() {
  return request<Api.OrgTag.Item[]>({ url: '/admin/org-tags/tree' });
}
