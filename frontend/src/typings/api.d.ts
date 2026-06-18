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
      total?: number;
      totalPages?: number;
      hasNext?: boolean;
      nextCursor?: string | null;
    }

    /** common params of paginating query list data */
    interface PaginatingQueryRecord<T = any> extends PaginatingCommonParams {
      data: T[];
      content: T[];
      records?: T[];
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
      avatar?: string | null;
      avatarVersion?: number;
      /** RBAC 权限编码列表（如 ['kb:read', 'kb:write', 'chat:use']） */
      permissions: string[];
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

  /** RBAC 角色与权限类型 */
  namespace Rbac {
    interface Permission {
      permCode: string;
      permName: string;
    }

    interface Role {
      id: number;
      roleCode: string;
      roleName: string;
      description: string;
      isSystem: boolean;
      permissions?: Permission[];
    }
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

    interface UsageTrendItem {
      date: string;
      label: string;
      questions: number;
    }

    interface TopKnowledgeBase {
      kbId: string;
      name: string;
      count: number;
    }

    interface FeatureUsage {
      label: string;
      count: number;
      value: number;
      color: string;
    }

    interface UsageStats {
      totalConversations: number;
      todayConversations: number;
      totalDocuments: number;
      todayUploads: number;
      knowledgeBaseCount: number;
      weekActiveDays: number;
      totalStorage: number;
      favoriteCount: number;
      usageTrends: UsageTrendItem[];
      topKnowledgeBases: TopKnowledgeBase[];
      featureUsage: FeatureUsage[];
      rangeDays: number;
    }
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
      errorMessage?: string;
      timestamp?: string;
    }

    interface Session {
      id: string;
      title: string;
      lastMessage: string;
      lastRole: string;
      time: string;
      messageCount: number;
      createdAt?: string;
      updatedAt?: string;
      keywords?: string[];
      searchText?: string;
      isPinned?: boolean;
      pinnedAt?: string;
    }

    interface DeleteSessionResult {
      deletedConversationId: string;
      currentConversationId: string;
      remainingCount: number;
    }

    interface PinSessionResult {
      conversationId: string;
      isPinned: boolean;
      pinnedAt: string;
    }

    interface Token {
      cmdToken: string;
    }

    /** 单条检索结果 */
    interface SearchResultItem {
      referenceNumber: number;
      fileName: string;
      fileMd5: string;
      score: number;
      chunkId: number;
      snippet: string;
      /** 完整文本内容（可直接用于弹窗展示，不再发网络请求时使用） */
      fullContent: string;
    }

    /** chunk 上下文中的一条 */
    interface ChunkContextItem {
      chunkId: number;
      textContent: string;
      isCurrent: boolean;
    }

    /** WebSocket 推送的检索结果消息 */
    interface SearchResultsMessage {
      type: 'search_results';
      results: SearchResultItem[];
      totalCount: number;
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
      todayQuestions: number;
      knowledgeHitRate: number;
      averageResponseTimeMs: number;
      usageTrends: UsageTrendItem[];
      popularQuestions: PopularQuestion[];
    }

    interface UsageTrendItem {
      date: string;
      label: string;
      questions: number;
    }

    interface PopularQuestion {
      rank: number;
      question: string;
      count: number;
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

  /** 登录记录 */
  namespace LoginRecord {
    interface Item {
      id: number;
      username: string;
      loginTime: string;
      ipAddress: string;
      deviceInfo: string;
      location: string;
      status: 'SUCCESS' | 'FAILED';
      failReason: string | null;
    }

    interface PaginatedResult {
      content: Item[];
      totalElements: number;
      totalPages: number;
      size: number;
      number: number;
    }

    interface Statistics {
      totalLogins: number;
      successLogins: number;
      failedLogins: number;
      recentRecords: Item[];
    }
  }

  namespace AgentCenter {
    interface Workflow {
      id: number;
      workflowId: string;
      name: string;
      description: string;
      type: string;
      status: string;
      ownerName: string;
      tags: string;
      callCount: number;
      successCount: number;
      failureCount: number;
      avgDurationMs: number;
      successRate: number;
      installCount: number;
      permissionScope: string;
      knowledgeBases: string;
      promptRefs: string;
      mcpTools: string;
      models: string;
      nodesJson: string;
      edgesJson: string;
      systemPrompt: string;
      avatarEmoji: string;
      temperature: number;
      topP: number;
      maxTokens: number;
      memoryTypes: string;
      createdAt: string;
      updatedAt: string;
      publishedAt: string | null;
    }

    interface WorkflowStats {
      agentCount: number;
      runCount: number;
      successRate: number;
      avgDurationMs: number;
    }

    interface PromptTemplate {
      id: number;
      templateId: string;
      name: string;
      description: string;
      category: string;
      version: string;
      systemContent: string;
      content: string;
      variables: string;
      tags: string;
      status: string;
      createdAt: string;
      updatedAt: string;
    }

    interface PromptHistory {
      id: number;
      templateId: string;
      version: string;
      name: string;
      description: string;
      category: string;
      systemContent: string;
      content: string;
      variables: string;
      tags: string;
      status: string;
      snapshotBy: string;
      changeDescription: string;
      snapshotAt: string;
    }

    interface McpTool {
      id: number;
      toolId: string;
      name: string;
      type: string;
      status: string;
      endpoint: string;
      authType: string;
      apiKeyMasked: string;
      apiKey?: string;
      description: string;
      callCount: number;
      updatedAt: string;
    }

    interface DebugResult {
      trace: { name: string; durationMs: number; status: string }[];
      variables: Record<string, any>;
      tokens: {
        promptTokens: number;
        completionTokens: number;
        totalTokens: number;
        cost: number;
      };
      output: {
        answer: string;
        documents: any[];
      };
      durationMs: number;
    }

    interface MarketplaceItem {
      workflowId: string;
      name: string;
      category: string;
      description: string;
      installCount: number;
      tags: string;
    }

    interface RunAnalysis extends WorkflowStats {
      failureRate: number;
      hotAgents: { name: string; callCount: number }[];
      cost: {
        tokenUsage: number;
        modelCost: number;
        toolCost: number;
      };
    }

    interface ModelConfig {
      id: number;
      name: string;
      provider: string;
      apiUrl: string;
      apiKey: string;
      modelName: string;
      active: boolean;
      authType: string;
      temperature: number;
      maxTokens: number;
      topP: number;
      remark: string;
      status: string;
      scene: string;
      updatedAt: string;
    }

    interface WorkflowVersion {
      id: number;
      versionId: string;
      workflowId: string;
      versionNumber: number;
      name: string;
      description: string;
      status: string;
      changeDescription: string;
      snapshotBy: string;
      isActive: boolean;
      snapshotAt: string;
    }

    interface WorkflowExecutionLog {
      id: number;
      executionId: string;
      workflowId: string;
      triggerType: string;
      status: string;
      startedBy: string;
      startedAt: string;
      completedAt: string | null;
      durationMs: number;
      totalTokens: number;
      cost: number;
      errorMessage: string | null;
    }
  }
}
