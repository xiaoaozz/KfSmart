/**
 * Namespace Api
 *
 * All backend api type
 */
declare namespace Api {
  namespace Common {
    /** common params of paginating */
    interface PaginatingCommonParams {
      /** current page number */
      page?: number;
      number: number;
      /** page size */
      size?: number;
      /** total count */
      totalElements: number;
    }

    /** common params of paginating query list data */
    interface PaginatingQueryRecord<T = any> extends PaginatingCommonParams {
      data: T[];
      content: T[];
    }

    /** common search params of table */
    type CommonSearchParams = Pick<Common.PaginatingCommonParams, 'page' | 'size'>;
  }

  /**
   * namespace Auth
   *
   * backend api module: "auth"
   */
  namespace Auth {
    interface LoginToken {
      token: string;
      refreshToken: string;
    }

    interface UserInfo {
      id: number;
      username: string;
      role: 'USER' | 'ADMIN';
      orgTags: string[];
      primaryOrg: string;
    }
  }

  /**
   * namespace Route
   *
   * backend api module: "route"
   */
  namespace Route {
    type ElegantConstRoute = import('@elegant-router/types').ElegantConstRoute;

    interface MenuRoute extends ElegantConstRoute {
      id: string;
    }

    interface UserRoute {
      routes: MenuRoute[];
      home: import('@elegant-router/types').LastLevelRouteKey;
    }
  }

  namespace OrgTag {
    interface Item {
      tagId: string;
      name: string;
      description: string;
      parentTag: string | null;
      children?: Item[];
    }

    type List = Common.PaginatingQueryRecord<Item>;

    type Details = Pick<Item, 'tagId' | 'name' | 'description'>;
    type Mine = {
      orgTags: string[];
      primaryOrg: string;
      orgTagDetails: Details[];
    };
  }

  namespace User {
    type SearchParams = CommonType.RecordNullable<
      Common.CommonSearchParams & {
        keyword: string;
        orgTag: string;
        status: number;
      }
    >;

    type Item = {
      userId: string;
      username: string;
      email: string;
      status: number;
      orgTags: Pick<OrgTag.Item, 'tagId' | 'name'>[];
      primaryOrg: string;
      createTime: string;
      lastLoginTime: string;
    };

    type List = Common.PaginatingQueryRecord<Item>;
  }

  namespace KnowledgeBase {
    interface SearchParams {
      userId: string;
      query: string;
      topK: number;
    }

    interface SearchResult {
      fileMd5: string;
      chunkId: number;
      textContent: string;
      score: number;
      fileName: string;
    }

    interface UploadState {
      tasks: UploadTask[];
      activeUploads: Set<string>; // 当前正在上传的任务ID
    }

    interface Form {
      kbId: string | null;
      orgTag: string | null;
      orgTagName: string | null;
      isPublic: boolean;
      fileList: import('naive-ui').UploadFileInfo[];
    }

    interface UploadTask {
      file: File;
      chunk: Blob | null;
      fileMd5: string;
      chunkIndex: number;
      totalSize: number;
      fileName: string;
      kbId: string | null;
      orgTag: string | null;
      orgTagName?: string | null;
      public: boolean;
      isPublic: boolean;
      uploadedChunks: number[];
      progress: number;
      status: UploadStatus;
      createdAt?: string;
      mergedAt?: string;
      requestIds?: string[]; // 请求ID，用于取消上传
    }

    /** 知识库信息（独立于组织标签） */
    interface KnowledgeBaseInfo {
      id: number;
      kbId: string;
      name: string;
      description: string;
      orgTag: string | null;
      isPublic: boolean;
      icon: string;
      fileCount: number;
      totalSize: number;
      chunkCount: number;
      status: string;
      createdBy: string | null;
      createdAt: string;
      updatedAt: string;
    }

    /** 知识库统计概览 */
    interface KnowledgeBaseStats {
      knowledgeBaseCount: number;
      documentCount: number;
      totalSize: number;
      chunkCount: number;
      knowledgeBases: KnowledgeBaseInfo[];
      refreshedAt?: string;
    }

    /** 知识库筛选选项 */
    interface KnowledgeBaseFilterOptions {
      orgTags: string[];
      creators: string[];
      icons: string[];
      publicOptions: { label: string; value: boolean }[];
      timeRangeOptions: { label: string; value: string }[];
      fileTypes: string[];
    }

    type List = Common.PaginatingQueryRecord<UploadTask>;

    type Merge = Pick<UploadTask, 'fileMd5' | 'fileName'>;

    interface Progress {
      uploaded: number[];
      progress: number;
      totalChunks: number;
    }

    interface Result {
      objectUrl: string;
      fileSize: number;
    }
  }

  namespace Chat {
    interface Input {
      message: string;
      conversationId?: string;
    }

    interface Output {
      chunk: string;
    }

    interface Conversation {
      conversationId: string;
    }

    interface Message {
      role: 'user' | 'assistant';
      content: string;
      status?: 'pending' | 'loading' | 'finished' | 'error';
      timestamp?: string;
    }

    interface Session {
      id: string;
      title: string;
      lastMessage: string;
      lastRole: string;
      time: string;
      messageCount: number;
    }

    interface Token {
      cmdToken: string;
    }
  }

  namespace Document {
    interface DownloadResponse {
      fileName: string;
      downloadUrl: string;
      fileSize: number;
      fileMd5?: string;
    }

    interface ReferenceMd5Response {
      fileMd5: string;
      referenceNumber: number;
    }
  }

  namespace System {
    interface Stats {
      totalUsers: number;
      totalFiles: number;
      totalDocuments: number;
      totalConversations: number;
      totalOrgTags: number;
      todayUploads: number;
      todayConversations: number;
    }

    interface Status {
      cpu_usage: string;
      memory_usage: string;
      disk_usage: string;
      active_users: number;
      total_documents: number;
      total_conversations: number;
    }

    interface UserActivity {
      username: string;
      action: string;
      timestamp: string;
      ip_address: string;
    }
  }
}
