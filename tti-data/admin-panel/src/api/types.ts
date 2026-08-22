export type ColumnType = "string" | "number" | "boolean";

export interface ColumnDefinition {
  excel_header: string;
  field: string;
  type: ColumnType;
  required: boolean;
}

export interface SchemaDefinition {
  columns: ColumnDefinition[];
}

export interface Dataset {
  id: string;
  name: string;
  description: string | null;
  schema_definition: SchemaDefinition;
  created_at: string;
}

export interface DatasetSummary {
  id: string;
  name: string;
  description: string | null;
  last_uploaded_at: string | null;
  last_uploaded_by: string | null;
}

export type UploadStatus = "success" | "failed" | "rolled_back";

export interface ValidationError {
  row: number;
  field: string;
  message: string;
}

export interface UploadResult {
  upload_id: string;
  status: UploadStatus;
  row_count: number;
  validation_errors: ValidationError[];
  warning?: string | null;
}

export interface UploadHistoryEntry {
  id: string;
  uploaded_by: string;
  uploaded_at: string;
  original_filename: string;
  status: UploadStatus;
  row_count: number;
  validation_errors: ValidationError[] | null;
}
