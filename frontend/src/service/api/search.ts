import { request } from '../request';

/** 获取 chunk 上下文 */
export function fetchGetChunkContext(params: {
  fileMd5: string;
  chunkId: number;
  contextSize?: number;
}) {
  return request<Api.Chat.ChunkContextItem[]>({ url: '/search/chunk-context', params });
}