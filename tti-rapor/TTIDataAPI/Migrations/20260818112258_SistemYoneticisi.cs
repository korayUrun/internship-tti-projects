using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace TTIDataAPI.Migrations
{
    /// <inheritdoc />
    public partial class SistemYoneticisi : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<bool>(
                name: "sistem_yoneticisi_mi",
                schema: "rapor",
                table: "kullanici",
                type: "boolean",
                nullable: false,
                defaultValue: false);

            migrationBuilder.CreateIndex(
                name: "ix_kullanici_sistem_yoneticisi_mi",
                schema: "rapor",
                table: "kullanici",
                column: "sistem_yoneticisi_mi",
                unique: true,
                filter: "sistem_yoneticisi_mi");
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropIndex(
                name: "ix_kullanici_sistem_yoneticisi_mi",
                schema: "rapor",
                table: "kullanici");

            migrationBuilder.DropColumn(
                name: "sistem_yoneticisi_mi",
                schema: "rapor",
                table: "kullanici");
        }
    }
}
