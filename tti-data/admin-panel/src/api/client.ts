import type { Dataset, DatasetSummary, SchemaDefinition, UploadHistoryEntry, UploadResult } from "./types";

// Boş dize = GÖRELİ yol. Panel ve API üretimde aynı origin'den servis ediliyor
// (https://data.<DOMAIN>; Caddy /api, /admin, /docs yollarını backend'e, gerisini bu panele
// yönlendiriyor). Böylece imaja gömülü bir sunucu adresi kalmıyor ve CORS gerekmiyor.
// Geliştirmede Vite'in dev proxy'si aynı yolları localhost:8000'e taşıyor (vite.config.ts).
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "";

export class ApiError extends Error {
  status: number;

  constructor(message: string, status: number) {
    super(message);
    this.name = "ApiError";
    this.status = status;
  }
}

async function request<T>(path: string, options: RequestInit = {}, token?: string | null): Promise<T> {
  const headers = new Headers(options.headers);
  if (token) headers.set("Authorization", `Bearer ${token}`);

  const response = await fetch(`${API_BASE_URL}${path}`, { ...options, headers });

  if (!response.ok) {
    const body = await response.json().catch(() => null);
    const message = body?.detail ?? `İstek başarısız oldu (${response.status})`;
    throw new ApiError(typeof message === "string" ? message : JSON.stringify(message), response.status);
  }

  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

export async function login(username: string, password: string): Promise<string> {
  const result = await request<{ access_token: string }>("/admin/login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username, password }),
  });
  return result.access_token;
}

export function listDatasets(token: string): Promise<DatasetSummary[]> {
  return request<DatasetSummary[]>("/admin/datasets", {}, token);
}

export function createDataset(
  token: string,
  name: string,
  description: string | null,
  schemaDefinition: SchemaDefinition,
): Promise<Dataset> {
  return request<Dataset>(
    "/admin/datasets",
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ name, description, schema_definition: schemaDefinition }),
    },
    token,
  );
}

export function deleteDataset(token: string, datasetName: string): Promise<void> {
  return request<void>(`/admin/datasets/${encodeURIComponent(datasetName)}`, { method: "DELETE" }, token);
}

export function getDatasetHistory(token: string, datasetName: string): Promise<UploadHistoryEntry[]> {
  return request<UploadHistoryEntry[]>(`/admin/datasets/${encodeURIComponent(datasetName)}/history`, {}, token);
}

export async function uploadDataset(
  token: string,
  datasetName: string,
  file: File,
  force = false,
): Promise<UploadResult> {
  const formData = new FormData();
  formData.append("file", file);
  const query = force ? "?force=true" : "";
  return request<UploadResult>(
    `/admin/datasets/${encodeURIComponent(datasetName)}/upload${query}`,
    { method: "POST", body: formData },
    token,
  );
}

export function rollbackDataset(token: string, datasetName: string, uploadId: string): Promise<UploadResult> {
  return request<UploadResult>(
    `/admin/datasets/${encodeURIComponent(datasetName)}/rollback/${uploadId}`,
    { method: "POST" },
    token,
  );
}
