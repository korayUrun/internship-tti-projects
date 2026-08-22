using Microsoft.EntityFrameworkCore.Migrations;
using Npgsql.EntityFrameworkCore.PostgreSQL.Metadata;

#nullable disable

namespace TTIDataAPI.Migrations
{
    /// <summary>
    /// Proje kapsamı listesini tabloya taşır; iki uçlu GPS sütunlarını güzergâh DOSYASIYLA değiştirir.
    /// </summary>
    /// <remarks>
    /// ⚠️ Bu migration VERİ SİLER: `konum_a_lat/lon` ve `konum_b_lat/lon` sütunları düşürülür ve
    /// `Down` onları geri eklese bile İÇLERİNDEKİ DEĞERLER geri gelmez. Sahada bu alanlar
    /// neredeyse hiç dolmuyordu (masaüstü tarayıcıda ve kurumsal VPN arkasında konum izni
    /// reddediliyor) ve uygulandığı sırada tablodaki kayıtlar zaten test verisiydi.
    ///
    /// Elde gerçek koordinat bulunan bir kuruluma uygulanacaksa, önce yedek alın:
    /// `tti-sunucu/yedek.sh` ya da
    /// `pg_dump -t rapor.kablo_talep`.
    /// </remarks>
    public partial class KapsamTablosuVeGuzergahDosyasi : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropColumn(
                name: "konum_a_lat",
                schema: "rapor",
                table: "kablo_talep");

            migrationBuilder.DropColumn(
                name: "konum_a_lon",
                schema: "rapor",
                table: "kablo_talep");

            migrationBuilder.DropColumn(
                name: "konum_b_lat",
                schema: "rapor",
                table: "kablo_talep");

            migrationBuilder.DropColumn(
                name: "konum_b_lon",
                schema: "rapor",
                table: "kablo_talep");

            migrationBuilder.AddColumn<string>(
                name: "guzergah_dosya_adi",
                schema: "rapor",
                table: "kablo_talep",
                type: "character varying(260)",
                maxLength: 260,
                nullable: true);

            migrationBuilder.AddColumn<string>(
                name: "guzergah_dosya_yolu",
                schema: "rapor",
                table: "kablo_talep",
                type: "character varying(512)",
                maxLength: 512,
                nullable: true);

            migrationBuilder.CreateTable(
                name: "proje_kapsami",
                schema: "rapor",
                columns: table => new
                {
                    id = table.Column<int>(type: "integer", nullable: false)
                        .Annotation("Npgsql:ValueGenerationStrategy", NpgsqlValueGenerationStrategy.IdentityByDefaultColumn),
                    ad = table.Column<string>(type: "character varying(128)", maxLength: 128, nullable: false),
                    sira = table.Column<int>(type: "integer", nullable: false),
                    aktif = table.Column<bool>(type: "boolean", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("pk_proje_kapsami", x => x.id);
                });

            migrationBuilder.CreateIndex(
                name: "ix_proje_kapsami_ad",
                schema: "rapor",
                table: "proje_kapsami",
                column: "ad",
                unique: true);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropTable(
                name: "proje_kapsami",
                schema: "rapor");

            migrationBuilder.DropColumn(
                name: "guzergah_dosya_adi",
                schema: "rapor",
                table: "kablo_talep");

            migrationBuilder.DropColumn(
                name: "guzergah_dosya_yolu",
                schema: "rapor",
                table: "kablo_talep");

            migrationBuilder.AddColumn<double>(
                name: "konum_a_lat",
                schema: "rapor",
                table: "kablo_talep",
                type: "double precision",
                nullable: true);

            migrationBuilder.AddColumn<double>(
                name: "konum_a_lon",
                schema: "rapor",
                table: "kablo_talep",
                type: "double precision",
                nullable: true);

            migrationBuilder.AddColumn<double>(
                name: "konum_b_lat",
                schema: "rapor",
                table: "kablo_talep",
                type: "double precision",
                nullable: true);

            migrationBuilder.AddColumn<double>(
                name: "konum_b_lon",
                schema: "rapor",
                table: "kablo_talep",
                type: "double precision",
                nullable: true);
        }
    }
}
