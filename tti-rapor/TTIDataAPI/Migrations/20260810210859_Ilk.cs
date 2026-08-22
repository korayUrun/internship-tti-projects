using System;
using Microsoft.EntityFrameworkCore.Migrations;
using Npgsql.EntityFrameworkCore.PostgreSQL.Metadata;

#nullable disable

namespace TTIDataAPI.Migrations
{
    /// <inheritdoc />
    public partial class Ilk : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.EnsureSchema(
                name: "rapor");

            migrationBuilder.CreateTable(
                name: "kablo_katalog",
                schema: "rapor",
                columns: table => new
                {
                    id = table.Column<int>(type: "integer", nullable: false)
                        .Annotation("Npgsql:ValueGenerationStrategy", NpgsqlValueGenerationStrategy.IdentityByDefaultColumn),
                    nosid = table.Column<string>(type: "character varying(128)", maxLength: 128, nullable: false),
                    saha_sehir = table.Column<string>(type: "character varying(128)", maxLength: 128, nullable: true),
                    uzunluk = table.Column<int>(type: "integer", nullable: true),
                    location_a = table.Column<string>(type: "character varying(1024)", maxLength: 1024, nullable: true),
                    location_b = table.Column<string>(type: "character varying(1024)", maxLength: 1024, nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("pk_kablo_katalog", x => x.id);
                });

            migrationBuilder.CreateTable(
                name: "kablo_talep",
                schema: "rapor",
                columns: table => new
                {
                    id = table.Column<int>(type: "integer", nullable: false)
                        .Annotation("Npgsql:ValueGenerationStrategy", NpgsqlValueGenerationStrategy.IdentityByDefaultColumn),
                    olusturan = table.Column<string>(type: "character varying(64)", maxLength: 64, nullable: false),
                    nosid = table.Column<string>(type: "character varying(128)", maxLength: 128, nullable: false),
                    location_a = table.Column<string>(type: "character varying(1024)", maxLength: 1024, nullable: true),
                    location_b = table.Column<string>(type: "character varying(1024)", maxLength: 1024, nullable: true),
                    proje_guzergah = table.Column<string>(type: "character varying(128)", maxLength: 128, nullable: true),
                    saha_sehir = table.Column<string>(type: "character varying(128)", maxLength: 128, nullable: true),
                    ek_araligi = table.Column<string>(type: "character varying(128)", maxLength: 128, nullable: true),
                    guzergah_uzunlugu = table.Column<int>(type: "integer", nullable: true),
                    ariza_riski = table.Column<string>(type: "character varying(32)", maxLength: 32, nullable: false),
                    konum_a_lat = table.Column<double>(type: "double precision", nullable: true),
                    konum_a_lon = table.Column<double>(type: "double precision", nullable: true),
                    konum_b_lat = table.Column<double>(type: "double precision", nullable: true),
                    konum_b_lon = table.Column<double>(type: "double precision", nullable: true),
                    fotograf_yolu = table.Column<string>(type: "character varying(512)", maxLength: 512, nullable: true),
                    ilk_aciklama = table.Column<string>(type: "character varying(4000)", maxLength: 4000, nullable: true),
                    statu = table.Column<string>(type: "character varying(32)", maxLength: 32, nullable: false),
                    son_aciklama = table.Column<string>(type: "character varying(4000)", maxLength: 4000, nullable: true),
                    son_degistiren = table.Column<string>(type: "character varying(64)", maxLength: 64, nullable: true),
                    olusturulma_tarihi = table.Column<DateTime>(type: "timestamptz", nullable: false),
                    son_degistirilme_tarihi = table.Column<DateTime>(type: "timestamptz", nullable: false),
                    xmin = table.Column<uint>(type: "xid", rowVersion: true, nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("pk_kablo_talep", x => x.id);
                });

            migrationBuilder.CreateTable(
                name: "kullanici",
                schema: "rapor",
                columns: table => new
                {
                    id = table.Column<int>(type: "integer", nullable: false)
                        .Annotation("Npgsql:ValueGenerationStrategy", NpgsqlValueGenerationStrategy.IdentityByDefaultColumn),
                    kullanici_adi = table.Column<string>(type: "character varying(64)", maxLength: 64, nullable: false),
                    ad_soyad = table.Column<string>(type: "character varying(128)", maxLength: 128, nullable: false),
                    parola_ozeti = table.Column<string>(type: "character varying(256)", maxLength: 256, nullable: false),
                    rol = table.Column<string>(type: "character varying(16)", maxLength: 16, nullable: false),
                    ilk_giris_mi = table.Column<bool>(type: "boolean", nullable: false),
                    aktif = table.Column<bool>(type: "boolean", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("pk_kullanici", x => x.id);
                });

            migrationBuilder.CreateTable(
                name: "guncelleme_gecmisi",
                schema: "rapor",
                columns: table => new
                {
                    id = table.Column<int>(type: "integer", nullable: false)
                        .Annotation("Npgsql:ValueGenerationStrategy", NpgsqlValueGenerationStrategy.IdentityByDefaultColumn),
                    kablo_talep_id = table.Column<int>(type: "integer", nullable: false),
                    degistiren = table.Column<string>(type: "character varying(64)", maxLength: 64, nullable: false),
                    eski_statu = table.Column<string>(type: "character varying(32)", maxLength: 32, nullable: false),
                    yeni_statu = table.Column<string>(type: "character varying(32)", maxLength: 32, nullable: false),
                    aciklama = table.Column<string>(type: "character varying(4000)", maxLength: 4000, nullable: true),
                    tarih = table.Column<DateTime>(type: "timestamptz", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("pk_guncelleme_gecmisi", x => x.id);
                    table.ForeignKey(
                        name: "fk_guncelleme_gecmisi_kablo_talep_kablo_talep_id",
                        column: x => x.kablo_talep_id,
                        principalSchema: "rapor",
                        principalTable: "kablo_talep",
                        principalColumn: "id",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateIndex(
                name: "ix_guncelleme_gecmisi_kablo_talep_id",
                schema: "rapor",
                table: "guncelleme_gecmisi",
                column: "kablo_talep_id");

            migrationBuilder.CreateIndex(
                name: "ix_kablo_katalog_nosid",
                schema: "rapor",
                table: "kablo_katalog",
                column: "nosid",
                unique: true);

            migrationBuilder.CreateIndex(
                name: "ix_kablo_talep_nosid",
                schema: "rapor",
                table: "kablo_talep",
                column: "nosid");

            migrationBuilder.CreateIndex(
                name: "ix_kablo_talep_olusturan",
                schema: "rapor",
                table: "kablo_talep",
                column: "olusturan");

            migrationBuilder.CreateIndex(
                name: "ix_kablo_talep_olusturulma_tarihi",
                schema: "rapor",
                table: "kablo_talep",
                column: "olusturulma_tarihi");

            migrationBuilder.CreateIndex(
                name: "ix_kablo_talep_statu",
                schema: "rapor",
                table: "kablo_talep",
                column: "statu");

            migrationBuilder.CreateIndex(
                name: "ix_kullanici_kullanici_adi",
                schema: "rapor",
                table: "kullanici",
                column: "kullanici_adi",
                unique: true);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropTable(
                name: "guncelleme_gecmisi",
                schema: "rapor");

            migrationBuilder.DropTable(
                name: "kablo_katalog",
                schema: "rapor");

            migrationBuilder.DropTable(
                name: "kullanici",
                schema: "rapor");

            migrationBuilder.DropTable(
                name: "kablo_talep",
                schema: "rapor");
        }
    }
}
