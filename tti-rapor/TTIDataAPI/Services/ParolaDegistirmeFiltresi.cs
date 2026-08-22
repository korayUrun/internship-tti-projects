using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.Mvc.Filters;

namespace TTIDataAPI.Services
{
    /// <summary>
    /// İlk girişini yapmış ama parolasını henüz değiştirmemiş kullanıcıyı veri uçlarından uzak tutar.
    /// </summary>
    /// <remarks>
    /// Kural SUNUCUDA duruyor, arayüzde değil. Yalnızca arayüzde olsaydı, parola değiştirme
    /// ekranını atlayıp doğrudan API'ye istek atan biri varsayılan parolayla çalışmaya devam eder
    /// ve hiçbir hata çıkmazdı — düzeltmeye çalıştığımız sorunun aynısı.
    /// </remarks>
    public class ParolaDegistirmeFiltresi : IAsyncActionFilter
    {
        public const string IlkGirisClaim = "ilkGiris";

        public async Task OnActionExecutionAsync(
            ActionExecutingContext baglam, ActionExecutionDelegate sonraki)
        {
            var kimliksizErisilebilir = baglam.ActionDescriptor.EndpointMetadata
                .OfType<AllowAnonymousAttribute>().Any();

            var oturumUcu = baglam.Controller is Controllers.OturumController;

            if (kimliksizErisilebilir || oturumUcu ||
                baglam.HttpContext.User.Identity?.IsAuthenticated != true)
            {
                await sonraki();
                return;
            }

            var ilkGiris = baglam.HttpContext.User.FindFirst(IlkGirisClaim)?.Value == "true";
            if (ilkGiris)
            {
                baglam.Result = new ObjectResult(new
                {
                    hata = "Devam etmeden önce parolanızı değiştirmelisiniz.",
                    kod = "PAROLA_DEGISTIR",
                })
                {
                    StatusCode = StatusCodes.Status403Forbidden,
                };
                return;
            }

            await sonraki();
        }
    }
}
