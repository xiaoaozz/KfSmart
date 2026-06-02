import { fakePaginationRequest, request } from '../request';

/** Get flat list of org tags for table display (wraps in pagination format for useTable) */
export function fetchGetOrgTagList() {
  return fakePaginationRequest<Api.OrgTag.List>({ url: '/admin/org-tags' });
}

/** Get org tag tree for cascader display */
export function fetchGetOrgTagTree() {
  return request<Api.OrgTag.Item[]>({ url: '/admin/org-tags/tree' });
}
