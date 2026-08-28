export interface FrameworkConfig {
  apiBaseUrl: string;
}

export function createFrameworkConfig(overrides: Partial<FrameworkConfig> = {}): FrameworkConfig {
  return {
    apiBaseUrl: "/api",
    ...overrides,
  };
}

export async function fetchGreeting(config: FrameworkConfig, name?: string): Promise<string> {
  const url = new URL(`${config.apiBaseUrl}/hello`, window.location.origin);
  if (name) url.searchParams.set("name", name);
  const res = await fetch(url);
  if (!res.ok) throw new Error(`framework request failed: ${res.status}`);
  return res.text();
}
