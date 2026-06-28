/**
 * Converts bare citation markers like `#1` into Markdown links `[#1](#cite-1)`.
 * Uses a negative lookbehind to leave `##heading` and `word#1` patterns untouched.
 */
export function injectCitationLinks(content: string): string {
  return content.replace(/(?<![#\w])#(\d+)/g, '[#$1](#cite-$1)')
}
