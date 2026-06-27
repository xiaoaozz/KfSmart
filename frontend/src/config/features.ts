/**
 * Feature flags — control progressive rollout of major features.
 * All flags default to stable production behaviour.
 * To enable a flag locally, set VITE_FF_<NAME>=true in .env.local.
 */
const flag = (key: string, defaultValue = false): boolean => {
  const env = import.meta.env[`VITE_FF_${key}`]
  if (env === undefined) return defaultValue
  return env === 'true' || env === true
}

export const features = {
  /** Dify-style visual workflow editor (Phase 4) */
  workflowEditor: flag('WORKFLOW_EDITOR'),

  /** AI-powered document chat widget */
  docChat: flag('DOC_CHAT', true),

  /** Knowledge graph visualisation */
  kgVisualization: flag('KG_VISUALIZATION'),

  /** Unified search with AI summarisation */
  searchAI: flag('SEARCH_AI', true),

  /** Multi-tenant org management UI */
  orgManagement: flag('ORG_MANAGEMENT', true),

  /** Beta features: show experimental UI elements  */
  betaUI: flag('BETA_UI'),
} as const

export type FeatureKey = keyof typeof features
