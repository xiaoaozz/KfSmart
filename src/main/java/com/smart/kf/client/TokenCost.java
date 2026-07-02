package com.smart.kf.client;

/**
 * Token 计费兜底公式（消除 ReActEngine / ModelClient / AgentContext 三处重复）。
 *
 * <p>仅在模型未返回实际费用（modelCost &lt;= 0）时作为估算使用。
 * 单位与历史实现保持一致：每千 token，prompt 0.001、completion 0.002。
 * 后续可演进为按 {@code ApiKeyConfig} 的差异化定价表（见 remediation-progress）。
 */
public final class TokenCost {

    private TokenCost() {
    }

    public static double estimate(int promptTokens, int completionTokens) {
        return (promptTokens * 0.001 + completionTokens * 0.002) / 1000.0;
    }
}
