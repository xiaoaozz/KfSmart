import { request } from '../request';

type PageParams = {
  page?: number;
  size?: number;
  cursor?: string;
};

export function fetchSkills(params?: { keyword?: string; category?: string; status?: string } & PageParams) {
  return request<Api.AgentCenter.PaginatingSkillRecord>({
    url: '/skills',
    params
  });
}

export function fetchSkillStats() {
  return request<Api.AgentCenter.SkillStats>({ url: '/skills/stats' });
}

export function fetchSkillDetail(skillId: string) {
  return request<Api.AgentCenter.Skill>({ url: `/skills/${skillId}` });
}

export function fetchSaveSkill(data: Partial<Api.AgentCenter.Skill>) {
  const method = data.skillId ? 'PUT' : 'POST';
  const url = data.skillId ? `/skills/${data.skillId}` : '/skills';
  return request<Api.AgentCenter.Skill>({ url, method, data });
}

export function fetchPublishSkill(skillId: string) {
  return request<Api.AgentCenter.Skill>({ url: `/skills/${skillId}/publish`, method: 'POST' });
}

export function fetchToggleSkillStatus(skillId: string) {
  return request<Api.AgentCenter.Skill>({ url: `/skills/${skillId}/toggle-status`, method: 'PUT' });
}

export function fetchDeleteSkill(skillId: string) {
  return request({ url: `/skills/${skillId}`, method: 'DELETE' });
}

export function fetchSkillHistories(skillId: string) {
  return request<Api.AgentCenter.SkillHistory[]>({ url: `/skills/${skillId}/histories` });
}

export function fetchRollbackSkill(skillId: string, snapshotId: number) {
  return request<Api.AgentCenter.Skill>({ url: `/skills/${skillId}/rollback/${snapshotId}`, method: 'POST' });
}

export function fetchTestSkill(skillId: string, data: Record<string, any>) {
  return request<Api.AgentCenter.SkillTestResult>({ url: `/skills/${skillId}/test`, method: 'POST', data });
}

export function fetchSkillUsages(skillId: string) {
  return request<Api.AgentCenter.SkillUsage[]>({ url: `/skills/${skillId}/usages` });
}
