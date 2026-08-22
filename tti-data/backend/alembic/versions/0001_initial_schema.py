"""initial schema

Revision ID: 0001
Revises:
Create Date: 2026-08-13

"""
from typing import Sequence, Union

import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects import postgresql

revision: str = "0001"
down_revision: Union[str, None] = None
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.create_table(
        "datasets",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column("name", sa.String(100), nullable=False, unique=True),
        sa.Column("description", sa.Text(), nullable=True),
        sa.Column("schema_definition", postgresql.JSONB(), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now()),
    )
    op.create_index("ix_datasets_name", "datasets", ["name"])

    op.create_table(
        "admin_users",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column("username", sa.String(100), nullable=False, unique=True),
        sa.Column("hashed_password", sa.String(255), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now()),
    )
    op.create_index("ix_admin_users_username", "admin_users", ["username"])

    # create_type=False: tipi burada elle oluşturuyoruz; create_table sırasında
    # SQLAlchemy'nin tekrar CREATE TYPE denemesini (ve "already exists" hatasını) önler.
    upload_status = postgresql.ENUM("success", "failed", "rolled_back", name="upload_status", create_type=False)
    upload_status.create(op.get_bind(), checkfirst=True)

    op.create_table(
        "dataset_uploads",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column("dataset_id", postgresql.UUID(as_uuid=True), sa.ForeignKey("datasets.id", ondelete="CASCADE"), nullable=False),
        sa.Column("uploaded_by", sa.String(100), nullable=False),
        sa.Column("uploaded_at", sa.DateTime(timezone=True), server_default=sa.func.now()),
        sa.Column("original_filename", sa.String(255), nullable=False),
        sa.Column("backup_file_path", sa.String(500), nullable=True),
        sa.Column("status", upload_status, nullable=False),
        sa.Column("row_count", sa.Integer(), server_default="0"),
        sa.Column("validation_errors", postgresql.JSONB(), nullable=True),
    )
    op.create_index("ix_dataset_uploads_dataset_id", "dataset_uploads", ["dataset_id"])

    op.create_table(
        "dataset_records",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column("dataset_id", postgresql.UUID(as_uuid=True), sa.ForeignKey("datasets.id", ondelete="CASCADE"), nullable=False),
        sa.Column("row_data", postgresql.JSONB(), nullable=False),
        sa.Column("is_active", sa.Boolean(), server_default=sa.true()),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.func.now()),
    )
    op.create_index("ix_dataset_records_dataset_id", "dataset_records", ["dataset_id"])
    op.create_index("ix_dataset_records_is_active", "dataset_records", ["is_active"])

    op.create_table(
        "dataset_permissions",
        sa.Column("api_key", sa.String(64), primary_key=True),
        sa.Column("app_name", sa.String(100), nullable=False),
        sa.Column("dataset_id", postgresql.UUID(as_uuid=True), sa.ForeignKey("datasets.id", ondelete="CASCADE"), nullable=False),
    )
    op.create_index("ix_dataset_permissions_dataset_id", "dataset_permissions", ["dataset_id"])


def downgrade() -> None:
    op.drop_table("dataset_permissions")
    op.drop_table("dataset_records")
    op.drop_table("dataset_uploads")
    postgresql.ENUM(name="upload_status").drop(op.get_bind(), checkfirst=True)
    op.drop_table("admin_users")
    op.drop_table("datasets")
