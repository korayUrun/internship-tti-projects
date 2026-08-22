"""dataset_permissions: composite primary key (api_key, dataset_id)

Bir API key'in yalnızca tek bir dataset'e izinli olabilmesi hataydı — bir uygulamanın
birden fazla dataset'e aynı key ile erişebilmesi gerekiyor. api_key tek başına primary
key olduğu için aynı key ile ikinci bir dataset satırı eklenemiyordu (sessizce
atlanıyordu). Bu migration, primary key'i (api_key, dataset_id) ikilisine genişletir.

Revision ID: 0002
Revises: 0001
Create Date: 2026-08-19

"""
from typing import Sequence, Union

from alembic import op

revision: str = "0002"
down_revision: Union[str, None] = "0001"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.drop_constraint("dataset_permissions_pkey", "dataset_permissions", type_="primary")
    op.create_primary_key(
        "dataset_permissions_pkey", "dataset_permissions", ["api_key", "dataset_id"]
    )


def downgrade() -> None:
    op.drop_constraint("dataset_permissions_pkey", "dataset_permissions", type_="primary")
    op.create_primary_key("dataset_permissions_pkey", "dataset_permissions", ["api_key"])
