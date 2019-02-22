using Microsoft.AspNetCore.Mvc;
using sample_dotnet_app.Configuration;

namespace sample_dotnet_app.Controllers
{

    [Route("[controller]")]
    [ApiController]
    public class InfoController : ControllerBase
    {

        private readonly AppMetadata appMetadata;

        public InfoController(AppMetadata appMetadata) {
            this.appMetadata = appMetadata;
        }

        // GET info
        [HttpGet]
        public ActionResult<object> Get()
        {
            return appMetadata;
        }
    }

}